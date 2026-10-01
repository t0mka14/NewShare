package org.example.app.domain.session

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.example.app.domain.AppDirectories
import org.example.app.domain.Clock
import org.example.app.domain.CoroutineDispatchers
import org.example.app.domain.audio.AudioClipService
import org.example.app.domain.audio.CaptureFormat
import org.example.app.domain.config.ConfigDecoder
import org.example.app.domain.config.findProtocol
import org.example.app.domain.timeline.TimelineRepository
import org.example.app.domain.upload.UploadStatus
import org.example.app.domain.upload.UploadStatusRepository
import org.example.app.domain.upload.UploadStatusValue
import org.example.app.domain.video.VideoRemuxException
import org.example.app.domain.video.VideoRemuxService
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

private val logger = KotlinLogging.logger {}

/**
 * §5.5/§8.8 `ProcessSessionUseCase`: picks the timeline (edited > original), cuts the last
 * take of every VOCAL task instance into `clips/`, remuxes the last take's video of every
 * filming instance into `clips/` as an MP4 named like its audio clip (stream copy, every frame
 * kept — [VideoExportPlanner], [VideoRemuxService]), builds the converted/concatenated archived
 * master, and zips everything into `archive/<PatientCode>_<SessionId>.zip` with a
 * `manifest.json`. The raw `video/` takes stay on disk and out of the ZIP. Progress is reported as a cold [Flow] (one [Progress] per step change, with
 * a terminal [Progress.Completed] carrying the [Outcome]) so a progress-screen UI can collect it
 * directly; a plain callback would work just as well but the rest of this codebase already
 * models "what's currently true" as a `Flow` (§5.4 `ConfigurationRepository.activeConfig`), so
 * this follows the same shape.
 *
 * Target format for every cut/converted/concatenated clip and archived master is always
 * [CaptureFormat.PREFERRED] (48 kHz/16-bit/mono) — never the session's own negotiated capture
 * format — per §8.1 ("preferred format") and §8.8 ("resampling to target format if the capture
 * format differs"); this is true even when the negotiated format already *is* the preferred one
 * (in which case [AudioClipService.convert]/`cutClip` are no-op resamples, §5.3.1 of the audio
 * ports).
 *
 * Deliberately performs a small amount of raw filesystem work itself
 * ([java.nio.file.Files.walk] over the session directory to discover archive contents per
 * [SessionArchiveContents], and creating/cleaning a `archive/.staging/` scratch directory for
 * the synthesized master) rather than adding bespoke tree-walking methods to [SessionRepository]
 * — that repository's contract is schema-file-shaped (one method per named JSON/WAV artifact),
 * not a generic filesystem abstraction, and [org.example.app.fakes.FakeSessionRepository]
 * deliberately never touches real disk. This is a narrow, documented exception to the "domain
 * never does raw I/O" convention (§12) for exactly this bulk operation.
 */
class ProcessSessionUseCase(
    private val directories: AppDirectories,
    private val sessionRepository: SessionRepository,
    private val timelineRepository: TimelineRepository,
    private val uploadStatusRepository: UploadStatusRepository,
    private val audioClipService: AudioClipService,
    private val videoRemuxService: VideoRemuxService,
    private val archiveService: SessionArchiveService,
    private val clock: Clock,
    private val dispatchers: CoroutineDispatchers,
) {
    enum class Step { SELECTING_TIMELINE, CUTTING_CLIPS, CONVERTING_VIDEO, BUILDING_ARCHIVE, UPDATING_METADATA }

    /** What clip and video names are rendered from: the protocol's template and the entered field values. */
    private data class Naming(val template: String, val fieldValues: Map<String, String>)

    sealed interface Outcome {
        data class Success(val examination: Examination, val archiveFile: Path) : Outcome
        data class Failed(val error: ProcessingError) : Outcome
    }

    sealed interface Progress {
        data class InProgress(val step: Step, val fraction: Float) : Progress
        data class Completed(val outcome: Outcome) : Progress
    }

    fun process(folderName: String): Flow<Progress> = flow {
        val outcome = runProcess(folderName) { step, fraction -> emit(Progress.InProgress(step, fraction)) }
        emit(Progress.Completed(outcome))
    }.flowOn(dispatchers.io)

    private suspend fun runProcess(folderName: String, onStep: suspend (Step, Float) -> Unit): Outcome {
        fun fail(examination: Examination?, error: ProcessingError): Outcome.Failed {
            if (examination != null) {
                val now = clock.now().toString()
                runCatching {
                    sessionRepository.writeExamination(
                        folderName,
                        examination.copy(processing = ProcessingInfo(status = ProcessingStatus.Failed, processedAt = now, timelineUsed = null)),
                    )
                }
            }
            return Outcome.Failed(error)
        }

        return try {
            onStep(Step.SELECTING_TIMELINE, 0.0f)

            val examination = sessionRepository.readExamination(folderName)
                ?: return fail(null, ProcessingError.MissingExamination(folderName))

            val editedTimeline = if (timelineRepository.editedExists(folderName)) {
                timelineRepository.readEdited(folderName)
            } else {
                null
            }
            val originalTimeline = timelineRepository.readOriginal(folderName)
                ?: return fail(examination, ProcessingError.MissingTimeline)

            val timelineUsed = if (editedTimeline != null) TimelineSource.EDITED else TimelineSource.ORIGINAL

            sessionRepository.writeExamination(
                folderName,
                examination.copy(processing = ProcessingInfo(status = ProcessingStatus.Processing)),
            )

            val sessionDir = directories.sessionDir(folderName)
            val hasMaster = examination.captureFormat != null
            val masterParts = if (hasMaster) {
                MasterPartMap.build(
                    defaultMasterFile = sessionRepository.defaultMasterFile(folderName),
                    defaultFormat = examination.captureFormat!!,
                    interruptions = examination.interruptions,
                    sessionDir = sessionDir,
                )
            } else {
                emptyList()
            }

            val vocalRecords = examination.tasks.filter { it.type == "VOCAL" && !it.skipped }
            val cutsClips = hasMaster && vocalRecords.isNotEmpty()
            val hasVideo = examination.videoTakes.isNotEmpty()

            var updatedTasks = examination.tasks

            // Clip and video names both come from the protocol's `recordingsFileName` and the
            // participant's values — needed even without a master, by a video-only protocol.
            var naming: Naming? = null
            if (cutsClips || hasVideo) {
                val rawConfig = sessionRepository.readConfigSnapshot(folderName)
                    ?: return fail(examination, ProcessingError.MissingConfigSnapshot("task_configuration_snapshot.json missing"))
                val decoded = try {
                    ConfigDecoder.decode(rawConfig)
                } catch (e: Exception) {
                    return fail(examination, ProcessingError.MissingConfigSnapshot(e.message ?: "malformed config snapshot"))
                }
                val protocol = decoded.config.findProtocol(examination.protocolName, examination.protocolProject)
                    ?: return fail(examination, ProcessingError.ProtocolNotFound(examination.protocolName))

                val participant = sessionRepository.readParticipant(folderName)
                    ?: return fail(examination, ProcessingError.MissingParticipant(folderName))

                naming = Naming(protocol.recordingsFileName, participant.fields)
            }

            onStep(Step.CUTTING_CLIPS, 0.1f)

            if (cutsClips && naming != null) {
                val planningResult = ClipExportPlanner.plan(
                    vocalTaskRecords = vocalRecords,
                    originalEvents = originalTimeline.events,
                    editedSegments = editedTimeline?.segments,
                    masterParts = masterParts,
                    recordingsFileNameTemplate = naming.template,
                    installationId = examination.installationId,
                    fieldValues = naming.fieldValues,
                )
                if (planningResult.errors.isNotEmpty()) {
                    return fail(examination, ProcessingError.ClipPlanning(planningResult.errors))
                }

                val clipsDir = sessionRepository.clipsDir(folderName)
                val total = planningResult.plans.size.coerceAtLeast(1)
                planningResult.plans.forEachIndexed { index, plan ->
                    audioClipService.cutClip(
                        sourceWav = plan.sourcePart.file,
                        startSample = plan.localRange.first,
                        stopSample = plan.localRange.last + 1,
                        targetFormat = CaptureFormat.PREFERRED,
                        output = clipsDir.resolve(plan.clipFileName),
                    )
                    onStep(Step.CUTTING_CLIPS, 0.1f + 0.4f * (index + 1) / total)
                }

                val clipByKey = planningResult.plans.associateBy { it.taskIndex to it.repetition }
                updatedTasks = updatedTasks.map { task ->
                    val plan = clipByKey[task.taskIndex to task.repetition]
                    if (plan != null) task.copy(clipFile = "clips/${plan.clipFileName}") else task
                }
            }

            onStep(Step.CONVERTING_VIDEO, 0.5f)

            if (hasVideo && naming != null) {
                val plans = VideoExportPlanner.plan(
                    taskRecords = examination.tasks,
                    videoTakes = examination.videoTakes,
                    originalEvents = originalTimeline.events,
                    recordingsFileNameTemplate = naming.template,
                    installationId = examination.installationId,
                    fieldValues = naming.fieldValues,
                )
                val clipsDir = sessionRepository.clipsDir(folderName)
                val total = plans.size.coerceAtLeast(1)
                val videoByKey = mutableMapOf<Pair<Int, Int>, String>()
                plans.forEachIndexed { index, plan ->
                    val source = sessionDir.resolve(plan.source)
                    // A camera that died before its first frame leaves an empty file, or none. There
                    // is no video to keep, and the audio take is still good.
                    if (!Files.isRegularFile(source) || Files.size(source) == 0L) {
                        logger.warn { "skipping video of task ${plan.taskIndex} rep ${plan.repetition}: ${plan.source} is missing or empty" }
                    } else {
                        try {
                            videoRemuxService.remuxToMp4(source, plan.fps, clipsDir.resolve(plan.fileName))
                        } catch (e: VideoRemuxException) {
                            return fail(examination, ProcessingError.VideoRemux(plan.taskIndex, plan.repetition, e.detail))
                        }
                        videoByKey[plan.taskIndex to plan.repetition] = "clips/${plan.fileName}"
                    }
                    onStep(Step.CONVERTING_VIDEO, 0.5f + 0.15f * (index + 1) / total)
                }
                updatedTasks = updatedTasks.map { task ->
                    videoByKey[task.taskIndex to task.repetition]?.let { task.copy(videoFile = it) } ?: task
                }
            }

            onStep(Step.BUILDING_ARCHIVE, 0.65f)

            val stagingDir = sessionRepository.archiveDir(folderName).resolve(".staging")
            val archiveResult = try {
                val archiveEntries = mutableListOf<ArchiveEntry>()

                if (hasMaster) {
                    Files.createDirectories(stagingDir)
                    val stagedMaster = stagingDir.resolve("session_master.wav")
                    if (masterParts.size <= 1) {
                        audioClipService.convert(
                            sourceWav = sessionRepository.defaultMasterFile(folderName),
                            targetFormat = CaptureFormat.PREFERRED,
                            output = stagedMaster,
                        )
                    } else {
                        val convertedParts = masterParts.mapIndexed { index, part ->
                            val convertedPath = stagingDir.resolve("part${index + 1}_converted.wav")
                            audioClipService.convert(part.file, CaptureFormat.PREFERRED, convertedPath)
                            convertedPath
                        }
                        audioClipService.concatenate(convertedParts, CaptureFormat.PREFERRED, stagedMaster)
                    }
                    archiveEntries += ArchiveEntry.FileEntry("master/session_master.wav", stagedMaster)
                }

                val allRelativePaths = if (Files.isDirectory(sessionDir)) {
                    Files.walk(sessionDir).use { stream ->
                        stream.filter { Files.isRegularFile(it) }
                            .map { sessionDir.relativize(it).toString().replace('\\', '/') }
                            .toList()
                    }
                } else {
                    emptyList()
                }
                val includedRelativePaths = SessionArchiveContents.filter(allRelativePaths)
                archiveEntries += includedRelativePaths.map { relative ->
                    ArchiveEntry.FileEntry(relative, sessionDir.resolve(relative))
                }

                val zipFile = SessionArchivePaths.zipFile(sessionRepository, folderName, examination)
                archiveService.build(
                    zipFile = zipFile,
                    entries = archiveEntries,
                    sessionId = examination.sessionId,
                    configVersion = examination.configVersion,
                    generatedAt = clock.now().toString(),
                )
            } finally {
                deleteRecursively(stagingDir)
            }

            onStep(Step.UPDATING_METADATA, 0.9f)

            val nowIso = clock.now().toString()
            val finalExamination = examination.copy(
                tasks = updatedTasks,
                processing = ProcessingInfo(status = ProcessingStatus.Done, processedAt = nowIso, timelineUsed = timelineUsed),
            )
            sessionRepository.writeExamination(folderName, finalExamination)

            val currentUploadStatus = uploadStatusRepository.read(folderName)
            if (currentUploadStatus?.status != UploadStatusValue.Uploaded) {
                uploadStatusRepository.write(
                    folderName,
                    (currentUploadStatus ?: UploadStatus()).copy(status = UploadStatusValue.NotUploaded),
                )
            }

            onStep(Step.UPDATING_METADATA, 1.0f)
            Outcome.Success(finalExamination, archiveResult.zipFile)
        } catch (e: Exception) {
            val current = runCatching { sessionRepository.readExamination(folderName) }.getOrNull()
            fail(current, ProcessingError.IoFailure(e.message ?: e::class.simpleName.orEmpty()))
        }
    }

    private fun deleteRecursively(dir: Path) {
        if (!Files.exists(dir)) return
        Files.walk(dir).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { runCatching { Files.deleteIfExists(it) } }
        }
    }
}
