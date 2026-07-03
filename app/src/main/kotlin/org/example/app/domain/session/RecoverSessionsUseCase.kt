package org.example.app.domain.session

import org.example.app.domain.Clock
import org.example.app.domain.audio.CaptureFormat
import org.example.app.domain.timeline.TimelineCompactor
import org.example.app.domain.timeline.TimelineEventType
import org.example.app.domain.timeline.TimelineRepository
import java.nio.file.Path

/** Outcome of recovering (or not needing to recover) one session (§8.4). */
sealed interface RecoveryOutcome {
    /** Nothing to recover: the master (if any) was cleanly finalized and
     * `timeline_original.json` already exists. */
    data class NotRecovered(val folderName: String) : RecoveryOutcome

    /** The session was recovered: any leftover `*.partial.wav` files were finalized, the
     * event log was compacted (with a synthetic `SESSION_RECORDING_STOPPED` if the log had
     * no clean stop), and `examination.json.recovered` was set to `true`. */
    data class Recovered(val folderName: String, val sessionId: String, val lastWrittenSample: Long?) : RecoveryOutcome

    data class Failed(val folderName: String, val error: StorageError) : RecoveryOutcome
}

/**
 * §5.5/§8.4 `RecoverSessionsUseCase`: run once at startup, before the session browser opens.
 * A session needs recovery if it has a leftover partial master WAV file under `master/` and/or an
 * uncompacted `timeline.events.jsonl` (no `timeline_original.json` yet) — the two safeguards
 * are independent and both mandatory (§8.4), so either alone triggers recovery.
 *
 * WAV headers of leftover partial files are rebuilt from a known [CaptureFormat] and the
 * file's actual on-disk length, never from the file's own (possibly torn) header — see
 * [SessionRepository.finalizePartialMasterFile]. The format used is resolved per part (§13
 * decision 28): a partial file is matched by name against `examination.interruptions[].partFile`
 * and, if found, that interruption's own `captureFormat` is used — this is what makes recovery
 * correct across a mid-session device swap to a *different* negotiated format (§8.5). Only
 * part 1 (or a session with no interruptions at all) falls back to the session's top-level
 * `examination.captureFormat`.
 */
class RecoverSessionsUseCase(
    private val sessionRepository: SessionRepository,
    private val timelineRepository: TimelineRepository,
    private val clock: Clock,
) {
    fun recoverAll(): List<RecoveryOutcome> =
        sessionRepository.listSessionFolderNames().map { recoverOne(it) }

    fun recoverOne(folderName: String): RecoveryOutcome {
        return try {
            val partialFiles = sessionRepository.findPartialMasterFiles(folderName)
            val hasUncompactedLog = timelineRepository.eventLogExists(folderName) &&
                !timelineRepository.originalExists(folderName)

            if (partialFiles.isEmpty() && !hasUncompactedLog) {
                return RecoveryOutcome.NotRecovered(folderName)
            }

            val examination = sessionRepository.readExamination(folderName)
                ?: return RecoveryOutcome.Failed(folderName, StorageError.CorruptSessionMetadata("examination.json missing"))

            val format: CaptureFormat? = examination.captureFormat

            var latestFinalizedFrames: Long? = null
            for (partial in partialFiles) {
                val partFormat = resolveFormatForPart(examination, partial)
                    ?: return RecoveryOutcome.Failed(
                        folderName,
                        StorageError.CorruptSessionMetadata("no captureFormat resolvable for partial master file"),
                    )
                val finalized = sessionRepository.finalizePartialMasterFile(partial, partFormat)
                latestFinalizedFrames = finalized.frameCount
            }

            val original = if (timelineRepository.originalExists(folderName)) {
                timelineRepository.readOriginal(folderName)
                    ?: return RecoveryOutcome.Failed(folderName, StorageError.CorruptSessionMetadata("timeline_original.json unreadable"))
            } else {
                val parseResult = timelineRepository.readEventLog(folderName)
                val events = parseResult.events
                val alreadyStopped = events.any { it.type == TimelineEventType.SESSION_RECORDING_STOPPED }

                val compacted = if (alreadyStopped) {
                    TimelineCompactor.compact(examination.sessionId, format?.sampleRate ?: 0, events)
                } else {
                    val baseOffset = events.lastOrNull { it.type == TimelineEventType.RECORDING_RESUMED }?.sampleOffset ?: 0L
                    val tailFrames = latestFinalizedFrames
                        ?: format?.let { sessionRepository.latestFinalizedMasterPartFrames(folderName, it) }
                        ?: 0L
                    TimelineCompactor.compact(
                        sessionId = examination.sessionId,
                        sampleRate = format?.sampleRate ?: 0,
                        events = events,
                        lastWrittenSample = baseOffset + tailFrames,
                        syntheticWallClock = clock.now().toString(),
                    )
                }
                timelineRepository.writeOriginal(folderName, compacted)
                compacted
            }

            sessionRepository.writeExamination(folderName, examination.copy(recovered = true))

            val lastSample = original.events.lastOrNull { it.sampleOffset != null }?.sampleOffset
            RecoveryOutcome.Recovered(folderName, examination.sessionId, lastSample)
        } catch (e: Exception) {
            RecoveryOutcome.Failed(folderName, StorageError.CorruptSessionMetadata(e.message ?: e::class.simpleName.orEmpty()))
        }
    }

    /**
     * The [CaptureFormat] to reconstruct [partialFile]'s header with (§13 decision 28):
     * the format of the interruption that created this specific part, matched by the part's
     * final file name (`session_master.wav` / `session_master.partN.wav`) against
     * `interruptions[].partFile` — falling back to the session's top-level `captureFormat`
     * for part 1 or a session with no matching interruption entry. If more than one
     * interruption entry names the same part (shouldn't normally happen), the last one wins.
     */
    private fun resolveFormatForPart(examination: Examination, partialFile: Path): CaptureFormat? {
        val finalName = partialFile.fileName.toString().removeSuffix(".partial.wav") + ".wav"
        val interruptionFormat = examination.interruptions.lastOrNull { it.partFile == finalName }?.captureFormat
        return interruptionFormat ?: examination.captureFormat
    }
}
