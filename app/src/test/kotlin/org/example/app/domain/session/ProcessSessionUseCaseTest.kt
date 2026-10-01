package org.example.app.domain.session

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.example.app.domain.audio.CaptureFormat
import org.example.app.domain.timeline.TimelineEdited
import org.example.app.domain.timeline.TimelineEditedSegment
import org.example.app.domain.timeline.TimelineEvent
import org.example.app.domain.timeline.TimelineEventType
import org.example.app.domain.timeline.TimelineOriginal
import org.example.app.domain.upload.UploadAttempt
import org.example.app.domain.upload.UploadStatus
import org.example.app.domain.upload.UploadStatusValue
import org.example.app.fakes.FakeAudioClipService
import org.example.app.fakes.FakeSessionArchiveService
import org.example.app.fakes.FakeUploadStatusRepository
import org.example.app.fakes.FakeVideoRemuxService
import org.example.app.domain.video.VideoCaptureFormat
import org.example.app.fakes.ImmediateCoroutineDispatchers
import org.example.app.fakes.FakeClock
import org.example.app.fakes.TestAppDirectories
import org.example.app.infrastructure.persistence.JsonSessionRepository
import org.example.app.infrastructure.persistence.JsonTimelineRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Fast orchestration tests: real [JsonSessionRepository]/[JsonTimelineRepository] over a real
 * temp dir (so `Files.walk` during archive-content discovery sees real JSON files, §8.8), but
 * [FakeAudioClipService]/[FakeSessionArchiveService] so no real WAV bytes or ZIP are ever
 * produced — these assert *which calls happen with which arguments*, not byte-exact audio
 * output (covered by [ProcessSessionUseCaseIntegrationTest]).
 */
class ProcessSessionUseCaseTest {

    private val format48k = CaptureFormat(48_000, 16, 1)
    private val format44k = CaptureFormat(44_100, 16, 1)
    private val configVersion = "2026-07-01.1"
    private val template = "\${installationId}_\${field.patient_code}_\${taskIndex}_\${task.subtype}_\${repetition}"

    private fun rawConfigJson(protocolName: String) = """
        {"schemaVersion":1,"configVersion":"$configVersion","defaultLanguage":"en",
         "protocols":[{"name":"$protocolName","recordingsFileName":"$template","tasks":[]}]}
    """.trimIndent()

    private fun event(type: TimelineEventType, taskIndex: Int?, repetition: Int?, take: Int?, sample: Long) = TimelineEvent(
        type = type, sampleOffset = sample, wallClock = "2026-07-01T09:00:00Z",
        taskIndex = taskIndex, repetition = repetition, take = take,
    )

    private inner class Fixture(tempDir: Path) {
        val directories = TestAppDirectories(tempDir)
        val sessionRepository = JsonSessionRepository(directories)
        val timelineRepository = JsonTimelineRepository(directories)
        val uploadStatusRepository = FakeUploadStatusRepository()
        val audioClipService = FakeAudioClipService()
        val videoRemuxService = FakeVideoRemuxService()
        val archiveService = FakeSessionArchiveService()
        val clock = FakeClock()
        val useCase = ProcessSessionUseCase(
            directories = directories,
            sessionRepository = sessionRepository,
            timelineRepository = timelineRepository,
            uploadStatusRepository = uploadStatusRepository,
            audioClipService = audioClipService,
            videoRemuxService = videoRemuxService,
            archiveService = archiveService,
            clock = clock,
            dispatchers = ImmediateCoroutineDispatchers(),
        )

        val sessionId = "s1"
        val folderName = SessionFolderNaming.build(java.time.LocalDate.of(2026, 7, 1), "HC001", sessionId)

        fun setUpSession(
            captureFormat: CaptureFormat?,
            interruptions: List<Interruption> = emptyList(),
            tasks: List<TaskRecord>,
            protocolName: String = "Share",
            videoTakes: List<VideoTakeRecord> = emptyList(),
        ): Examination {
            sessionRepository.createSessionDirectory(folderName)
            sessionRepository.writeParticipant(folderName, ParticipantRecord(fields = mapOf("patient_code" to "HC001"), createdAt = "2026-07-01T09:00:00Z"))
            sessionRepository.writeConfigSnapshot(folderName, rawConfigJson(protocolName))
            val examination = Examination(
                sessionId = sessionId,
                installationId = "install1",
                protocolName = protocolName,
                configVersion = configVersion,
                startedAt = "2026-07-01T09:00:00Z",
                captureFormat = captureFormat,
                interruptions = interruptions,
                tasks = tasks,
                videoTakes = videoTakes,
            )
            sessionRepository.writeExamination(folderName, examination)
            return examination
        }

        fun writeOriginal(events: List<TimelineEvent>) {
            timelineRepository.writeOriginal(folderName, TimelineOriginal(sessionId = sessionId, sampleRate = 48_000, events = events))
        }

        fun writeEdited(segments: List<TimelineEditedSegment>) {
            timelineRepository.writeEdited(folderName, TimelineEdited(sessionId = sessionId, sampleRate = 48_000, segments = segments))
        }

        fun writeStray(relativePath: String) {
            val path = sessionRepository.sessionDir(folderName).resolve(relativePath)
            Files.createDirectories(path.parent)
            Files.write(path, "stray".toByteArray())
        }

        fun runProcess() = runBlocking { useCase.process(folderName).toList() }
    }

    @Test
    fun `single part - only the last take of each VOCAL instance is cut, target format is always PREFERRED`(@TempDir tempDir: Path) {
        val f = Fixture(tempDir)
        f.setUpSession(
            captureFormat = format44k,
            tasks = listOf(
                TaskRecord(taskIndex = 0, type = "VOCAL", subtype = "PHONATION", repetition = 1, takes = 2),
                TaskRecord(taskIndex = 1, type = "VOCAL", subtype = "PATAKA", repetition = 1, takes = 2),
            ),
        )
        f.writeOriginal(
            listOf(
                event(TimelineEventType.START_BUTTON_PRESSED, 0, 1, 1, 0), event(TimelineEventType.STOP_BUTTON_PRESSED, 0, 1, 1, 1000),
                event(TimelineEventType.TAKE_REJECTED, 0, 1, 1, 1000),
                event(TimelineEventType.START_BUTTON_PRESSED, 0, 1, 2, 1100), event(TimelineEventType.STOP_BUTTON_PRESSED, 0, 1, 2, 2000),
                event(TimelineEventType.START_BUTTON_PRESSED, 1, 1, 1, 2100), event(TimelineEventType.STOP_BUTTON_PRESSED, 1, 1, 1, 3000),
                event(TimelineEventType.TAKE_REJECTED, 1, 1, 1, 3000),
                event(TimelineEventType.START_BUTTON_PRESSED, 1, 1, 2, 3100), event(TimelineEventType.STOP_BUTTON_PRESSED, 1, 1, 2, 4000),
            ),
        )
        // Stray files establishing the §8.8 exclusion list against real files on disk.
        f.writeStray("clips/stale_from_previous_run.wav")
        f.writeStray("master/session_master.wav") // real (empty) master — must never be zipped directly
        f.writeStray("metadata/upload_status.json")
        f.writeStray("waveform_cache/master_waveform.cache")
        f.writeStray("archive/old_leftover.zip")
        f.timelineRepository.appendEvent(f.folderName, event(TimelineEventType.SESSION_RECORDING_STARTED, 0, 0, null, 0))

        val progress = f.runProcess()
        val outcome = (progress.last() as ProcessSessionUseCase.Progress.Completed).outcome
        assertTrue(outcome is ProcessSessionUseCase.Outcome.Success, "expected Success but was $outcome")

        assertEquals(2, f.audioClipService.cutClipCalls.size)
        val cut0 = f.audioClipService.cutClipCalls.first { it.output.fileName.toString().contains("_0_") }
        assertEquals(f.sessionRepository.defaultMasterFile(f.folderName), cut0.sourceWav)
        assertEquals(1100L, cut0.startSample)
        assertEquals(2000L, cut0.stopSample)
        assertEquals(CaptureFormat.PREFERRED, cut0.targetFormat) // never the negotiated 44.1k format

        val cut1 = f.audioClipService.cutClipCalls.first { it.output.fileName.toString().contains("_1_") }
        assertEquals(3100L, cut1.startSample)
        assertEquals(4000L, cut1.stopSample)

        assertEquals(1, f.audioClipService.convertCalls.size)
        assertEquals(CaptureFormat.PREFERRED, f.audioClipService.convertCalls[0].targetFormat)
        assertTrue(f.audioClipService.concatenateCalls.isEmpty())

        assertEquals(1, f.archiveService.buildCalls.size)
        val entries = f.archiveService.buildCalls[0].entries.map { it.zipPath }
        assertTrue("participant.json" in entries)
        assertTrue("examination.json" in entries)
        assertTrue("task_configuration_snapshot.json" in entries)
        assertTrue("timeline_original.json" in entries)
        assertTrue("clips/stale_from_previous_run.wav" in entries) // clips/ is included
        assertEquals(1, entries.count { it == "master/session_master.wav" }) // synthesized once, not from the real file too
        assertTrue("metadata/upload_status.json" !in entries)
        assertTrue("waveform_cache/master_waveform.cache" !in entries)
        assertTrue("archive/old_leftover.zip" !in entries)
        assertTrue(entries.none { it.startsWith("master/") && it != "master/session_master.wav" })
        assertTrue(entries.none { it == "timeline.events.jsonl" })

        // The one "master/session_master.wav" entry is sourced from the staging dir, not the
        // real (stray, immutable) master file on disk.
        val masterEntry = f.archiveService.buildCalls[0].entries.first { it.zipPath == "master/session_master.wav" }
            as ArchiveEntry.FileEntry
        assertTrue(masterEntry.source.toString().contains(".staging"))

        val finalExamination = f.sessionRepository.readExamination(f.folderName)!!
        assertEquals(ProcessingStatus.Done, finalExamination.processing?.status)
        assertEquals(TimelineSource.ORIGINAL, finalExamination.processing?.timelineUsed)
        assertEquals("clips/${cut0.output.fileName}", finalExamination.tasks.first { it.taskIndex == 0 }.clipFile)
    }

    @Test
    fun `edited timeline takes precedence over original when present`(@TempDir tempDir: Path) {
        val f = Fixture(tempDir)
        f.setUpSession(
            captureFormat = format48k,
            tasks = listOf(TaskRecord(taskIndex = 0, type = "VOCAL", subtype = "PHONATION", repetition = 1, takes = 1)),
        )
        f.writeOriginal(
            listOf(
                event(TimelineEventType.START_BUTTON_PRESSED, 0, 1, 1, 0),
                event(TimelineEventType.STOP_BUTTON_PRESSED, 0, 1, 1, 1000),
            ),
        )
        f.writeEdited(listOf(TimelineEditedSegment(taskIndex = 0, repetition = 1, startSample = 100, stopSample = 900)))

        val progress = f.runProcess()
        val outcome = (progress.last() as ProcessSessionUseCase.Progress.Completed).outcome
        assertTrue(outcome is ProcessSessionUseCase.Outcome.Success, "expected Success but was $outcome")

        assertEquals(1, f.audioClipService.cutClipCalls.size)
        assertEquals(100L, f.audioClipService.cutClipCalls[0].startSample)
        assertEquals(900L, f.audioClipService.cutClipCalls[0].stopSample) // edited range, not [0, 1000)

        val finalExamination = f.sessionRepository.readExamination(f.folderName)!!
        assertEquals(TimelineSource.EDITED, finalExamination.processing?.timelineUsed)
    }

    @Test
    fun `multi-part master with a format-changing device swap - each take cuts from its own part`(@TempDir tempDir: Path) {
        val f = Fixture(tempDir)
        val interruption = Interruption(
            sampleOffset = 2000L,
            start = "2026-07-01T09:05:00Z",
            end = "2026-07-01T09:05:10Z",
            partFile = "master/session_master.part2.wav",
            captureFormat = format44k,
        )
        f.setUpSession(
            captureFormat = format48k,
            interruptions = listOf(interruption),
            tasks = listOf(
                TaskRecord(taskIndex = 0, type = "VOCAL", subtype = "PHONATION", repetition = 1, takes = 1),
                TaskRecord(taskIndex = 1, type = "VOCAL", subtype = "PATAKA", repetition = 1, takes = 1),
            ),
        )
        f.writeOriginal(
            listOf(
                event(TimelineEventType.START_BUTTON_PRESSED, 0, 1, 1, 500), event(TimelineEventType.STOP_BUTTON_PRESSED, 0, 1, 1, 1500),
                event(TimelineEventType.RECORDING_INTERRUPTED, null, null, null, 2000),
                event(TimelineEventType.RECORDING_RESUMED, null, null, null, 2000),
                event(TimelineEventType.START_BUTTON_PRESSED, 1, 1, 1, 2500), event(TimelineEventType.STOP_BUTTON_PRESSED, 1, 1, 1, 3500),
            ),
        )

        val progress = f.runProcess()
        val outcome = (progress.last() as ProcessSessionUseCase.Progress.Completed).outcome
        assertTrue(outcome is ProcessSessionUseCase.Outcome.Success, "expected Success but was $outcome")

        val part1File = f.sessionRepository.defaultMasterFile(f.folderName)
        val part2File = f.sessionRepository.sessionDir(f.folderName).resolve("master/session_master.part2.wav")

        val cut0 = f.audioClipService.cutClipCalls.first { it.output.fileName.toString().contains("_0_") }
        assertEquals(part1File, cut0.sourceWav)
        assertEquals(500L, cut0.startSample); assertEquals(1500L, cut0.stopSample)

        val cut1 = f.audioClipService.cutClipCalls.first { it.output.fileName.toString().contains("_1_") }
        assertEquals(part2File, cut1.sourceWav)
        assertEquals(500L, cut1.startSample); assertEquals(1500L, cut1.stopSample) // 2500-2000, 3500-2000

        assertEquals(2, f.audioClipService.convertCalls.size)
        assertEquals(part1File, f.audioClipService.convertCalls[0].sourceWav)
        assertEquals(part2File, f.audioClipService.convertCalls[1].sourceWav)
        assertTrue(f.audioClipService.convertCalls.all { it.targetFormat == CaptureFormat.PREFERRED })

        assertEquals(1, f.audioClipService.concatenateCalls.size)
        val concat = f.audioClipService.concatenateCalls[0]
        assertEquals(listOf(f.audioClipService.convertCalls[0].output, f.audioClipService.convertCalls[1].output), concat.sources)
        assertEquals(CaptureFormat.PREFERRED, concat.targetFormat)
    }

    @Test
    fun `no-master session skips clip cutting and master conversion entirely`(@TempDir tempDir: Path) {
        val f = Fixture(tempDir)
        f.setUpSession(captureFormat = null, tasks = listOf(TaskRecord(taskIndex = 0, type = "QUESTIONNAIRE", repetition = 1)))
        f.writeOriginal(emptyList())

        val progress = f.runProcess()
        val outcome = (progress.last() as ProcessSessionUseCase.Progress.Completed).outcome
        assertTrue(outcome is ProcessSessionUseCase.Outcome.Success, "expected Success but was $outcome")

        assertTrue(f.audioClipService.cutClipCalls.isEmpty())
        assertTrue(f.audioClipService.convertCalls.isEmpty())
        assertTrue(f.audioClipService.concatenateCalls.isEmpty())

        val entries = f.archiveService.buildCalls[0].entries.map { it.zipPath }
        assertTrue(entries.none { it.startsWith("master/") })
    }

    @Test
    fun `reprocessing resets NotUploaded-eligible status but leaves Uploaded terminal`(@TempDir tempDir: Path) {
        val f = Fixture(tempDir)
        f.setUpSession(captureFormat = null, tasks = emptyList())
        f.writeOriginal(emptyList())

        // Case 1: previously Failed -> reprocessing resets to NotUploaded, history preserved.
        f.uploadStatusRepository.write(
            f.folderName,
            UploadStatus(status = UploadStatusValue.Failed, attempts = listOf(UploadAttempt("t1", "NetworkFailure")), attemptCount = 1),
        )
        runBlocking { f.useCase.process(f.folderName).toList() }
        val afterFailedReprocess = f.uploadStatusRepository.read(f.folderName)!!
        assertEquals(UploadStatusValue.NotUploaded, afterFailedReprocess.status)
        assertEquals(1, afterFailedReprocess.attemptCount) // history not wiped

        // Case 2: Uploaded is terminal — reprocessing must not touch it.
        f.uploadStatusRepository.write(f.folderName, UploadStatus(status = UploadStatusValue.Uploaded, uploadedAt = "t2"))
        runBlocking { f.useCase.process(f.folderName).toList() }
        val afterUploadedReprocess = f.uploadStatusRepository.read(f.folderName)!!
        assertEquals(UploadStatusValue.Uploaded, afterUploadedReprocess.status)
        assertEquals("t2", afterUploadedReprocess.uploadedAt)
    }

    @Test
    fun `missing examination fails without throwing`(@TempDir tempDir: Path) {
        val f = Fixture(tempDir)
        f.sessionRepository.createSessionDirectory(f.folderName)

        val progress = f.runProcess()
        val outcome = (progress.last() as ProcessSessionUseCase.Progress.Completed).outcome
        assertTrue(outcome is ProcessSessionUseCase.Outcome.Failed)
        assertTrue((outcome as ProcessSessionUseCase.Outcome.Failed).error is ProcessingError.MissingExamination)
    }

    @Test
    fun `clip planning error (missing edited segment) fails processing and marks examination Failed`(@TempDir tempDir: Path) {
        val f = Fixture(tempDir)
        f.setUpSession(
            captureFormat = format48k,
            tasks = listOf(TaskRecord(taskIndex = 0, type = "VOCAL", subtype = "PHONATION", repetition = 1, takes = 1)),
        )
        f.writeOriginal(
            listOf(
                event(TimelineEventType.START_BUTTON_PRESSED, 0, 1, 1, 0),
                event(TimelineEventType.STOP_BUTTON_PRESSED, 0, 1, 1, 1000),
            ),
        )
        f.writeEdited(emptyList()) // edited file exists but has no segment for taskIndex 0

        val progress = f.runProcess()
        val outcome = (progress.last() as ProcessSessionUseCase.Progress.Completed).outcome
        assertTrue(outcome is ProcessSessionUseCase.Outcome.Failed)
        assertTrue((outcome as ProcessSessionUseCase.Outcome.Failed).error is ProcessingError.ClipPlanning)

        assertEquals(ProcessingStatus.Failed, f.sessionRepository.readExamination(f.folderName)!!.processing?.status)
        assertNull(f.sessionRepository.readExamination(f.folderName)!!.processing?.timelineUsed)
    }

    // region video

    private fun videoTake(taskIndex: Int, take: Int, fps: Int = 30) = VideoTakeRecord(
        file = "video/task%02d_rep01_take%02d.mjpeg".format(taskIndex, take),
        taskIndex = taskIndex,
        repetition = 1,
        take = take,
        captureFormat = VideoCaptureFormat(1920, 1080, fps),
    )

    /** Filming VOCAL (take 1 rejected, take 2 kept), a VIDEO task with no subtype, and a plain VOCAL. */
    private fun Fixture.setUpFilmingSession(captureFormat: CaptureFormat? = format48k) {
        val takes = listOf(videoTake(0, 1), videoTake(0, 2), videoTake(1, 1, fps = 15))
        setUpSession(
            captureFormat = captureFormat,
            tasks = listOf(
                TaskRecord(taskIndex = 0, type = "VOCAL", subtype = "PHONATION", repetition = 1, takes = 2),
                TaskRecord(taskIndex = 1, type = "VIDEO", subtype = null, repetition = 1, takes = 1),
                TaskRecord(taskIndex = 2, type = "VOCAL", subtype = "PATAKA", repetition = 1, takes = 1),
            ),
            videoTakes = takes,
        )
        takes.forEach { writeStray(it.file) }
        writeOriginal(
            listOf(
                event(TimelineEventType.START_BUTTON_PRESSED, 0, 1, 1, 0), event(TimelineEventType.STOP_BUTTON_PRESSED, 0, 1, 1, 1000),
                event(TimelineEventType.TAKE_REJECTED, 0, 1, 1, 1000),
                event(TimelineEventType.START_BUTTON_PRESSED, 0, 1, 2, 1100), event(TimelineEventType.STOP_BUTTON_PRESSED, 0, 1, 2, 2000),
                event(TimelineEventType.START_BUTTON_PRESSED, 1, 1, 1, 2100), event(TimelineEventType.STOP_BUTTON_PRESSED, 1, 1, 1, 3000),
                event(TimelineEventType.START_BUTTON_PRESSED, 2, 1, 1, 3100), event(TimelineEventType.STOP_BUTTON_PRESSED, 2, 1, 1, 4000),
            ),
        )
    }

    @Test
    fun `the kept take of every filming instance is remuxed into clips under its audio clip name`(@TempDir tempDir: Path) {
        val f = Fixture(tempDir)
        f.setUpFilmingSession()

        val outcome = (f.runProcess().last() as ProcessSessionUseCase.Progress.Completed).outcome
        assertTrue(outcome is ProcessSessionUseCase.Outcome.Success, "expected Success but was $outcome")

        val sessionDir = f.sessionRepository.sessionDir(f.folderName)
        val clipsDir = f.sessionRepository.clipsDir(f.folderName)
        assertEquals(
            listOf(
                FakeVideoRemuxService.Call(sessionDir.resolve("video/task00_rep01_take02.mjpeg"), 30, clipsDir.resolve("install1_HC001_0_PHONATION_1.mp4")),
                FakeVideoRemuxService.Call(sessionDir.resolve("video/task01_rep01_take01.mjpeg"), 15, clipsDir.resolve("install1_HC001_1_VIDEO_1.mp4")),
            ),
            f.videoRemuxService.calls,
            "the rejected take 1 is not remuxed, and the video sits next to its audio clip",
        )

        val tasks = f.sessionRepository.readExamination(f.folderName)!!.tasks.associateBy { it.taskIndex }
        assertEquals("clips/install1_HC001_0_PHONATION_1.mp4", tasks.getValue(0).videoFile)
        assertEquals("clips/install1_HC001_0_PHONATION_1.wav", tasks.getValue(0).clipFile)
        assertEquals("clips/install1_HC001_1_VIDEO_1.mp4", tasks.getValue(1).videoFile)
        assertNull(tasks.getValue(2).videoFile, "a VOCAL task that filmed nothing gets no video")

        val entries = f.archiveService.buildCalls.single().entries.map { it.zipPath }
        assertTrue("clips/install1_HC001_0_PHONATION_1.mp4" in entries)
        assertTrue("clips/install1_HC001_1_VIDEO_1.mp4" in entries)
        assertTrue(entries.none { it.startsWith("video/") }, "raw takes stay out of the ZIP: $entries")
    }

    @Test
    fun `a failed remux fails processing`(@TempDir tempDir: Path) {
        val f = Fixture(tempDir)
        f.setUpFilmingSession()
        f.videoRemuxService.failWith = "frame count mismatch"

        val outcome = (f.runProcess().last() as ProcessSessionUseCase.Progress.Completed).outcome

        val error = (outcome as ProcessSessionUseCase.Outcome.Failed).error
        assertEquals(ProcessingError.VideoRemux(0, 1, "frame count mismatch"), error)
        assertEquals(ProcessingStatus.Failed, f.sessionRepository.readExamination(f.folderName)!!.processing?.status)
    }

    /** No master, so no clip cutting — the template and field values are still needed for video. */
    @Test
    fun `a video-only session is remuxed too`(@TempDir tempDir: Path) {
        val f = Fixture(tempDir)
        f.setUpSession(
            captureFormat = null,
            tasks = listOf(TaskRecord(taskIndex = 0, type = "VIDEO", subtype = "EMOTIONS", repetition = 1, takes = 1)),
            videoTakes = listOf(videoTake(0, 1)),
        )
        f.writeStray("video/task00_rep01_take01.mjpeg")
        f.writeOriginal(
            listOf(event(TimelineEventType.START_BUTTON_PRESSED, 0, 1, 1, 0), event(TimelineEventType.STOP_BUTTON_PRESSED, 0, 1, 1, 0)),
        )

        val outcome = (f.runProcess().last() as ProcessSessionUseCase.Progress.Completed).outcome

        assertTrue(outcome is ProcessSessionUseCase.Outcome.Success, "expected Success but was $outcome")
        assertEquals(listOf("install1_HC001_0_EMOTIONS_1.mp4"), f.videoRemuxService.calls.map { it.output.fileName.toString() })
        assertTrue(f.audioClipService.cutClipCalls.isEmpty())
    }

    /** A camera that died before its first frame leaves an empty file; the audio take is still good. */
    @Test
    fun `an empty take file is skipped without failing processing`(@TempDir tempDir: Path) {
        val f = Fixture(tempDir)
        f.setUpFilmingSession()
        Files.write(f.sessionRepository.sessionDir(f.folderName).resolve("video/task00_rep01_take02.mjpeg"), ByteArray(0))

        val outcome = (f.runProcess().last() as ProcessSessionUseCase.Progress.Completed).outcome

        assertTrue(outcome is ProcessSessionUseCase.Outcome.Success, "expected Success but was $outcome")
        assertEquals(listOf(1), f.videoRemuxService.calls.map { it.output.fileName.toString().split('_')[2].toInt() })
        assertNull(f.sessionRepository.readExamination(f.folderName)!!.tasks.first { it.taskIndex == 0 }.videoFile)
    }

    // endregion
}
