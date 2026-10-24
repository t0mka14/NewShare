package org.example.app.domain.session

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.example.app.domain.video.VideoCaptureFormat
import org.example.app.fakes.FakeAudioClipService
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeUploadStatusRepository
import org.example.app.fakes.ImmediateCoroutineDispatchers
import org.example.app.fakes.TestAppDirectories
import org.example.app.infrastructure.logging.captureLogs
import org.example.app.infrastructure.logging.fullText
import org.example.app.infrastructure.persistence.JsonSessionRepository
import org.example.app.infrastructure.persistence.JsonTimelineRepository
import org.example.app.infrastructure.persistence.ZipSessionArchiveService
import org.example.app.infrastructure.video.FfmpegBinaryLocator
import org.example.app.infrastructure.video.FfmpegVideoRemuxService
import org.example.app.domain.timeline.TimelineEvent
import org.example.app.domain.timeline.TimelineEventType
import org.example.app.domain.timeline.TimelineOriginal
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

/**
 * A clip or archive name is rendered from `recordingsFileName` (patient fields, installation ID,
 * site name) or from the patient code, so it is participant data: no log line may carry it.
 * Runs the real [FfmpegVideoRemuxService] so the actual remux log lines are exercised.
 */
class ProcessSessionLogPrivacyTest {

    private val patientCode = "PATIENT-XY7"
    private val installationId = "inst-7f3a9c21-secret"
    private val siteName = "BrnoClinic"

    @Test
    fun `processing a filmed session logs no patient code, installation id or rendered clip name`(@TempDir tempDir: Path) {
        val locator = FfmpegBinaryLocator()
        val ffmpeg = locator.locate()
        check(ffmpeg != null) { "the bundled ffmpeg is required for this test" }

        val directories = TestAppDirectories(tempDir)
        val sessionRepository = JsonSessionRepository(directories)
        val timelineRepository = JsonTimelineRepository(directories)
        val useCase = ProcessSessionUseCase(
            directories = directories,
            sessionRepository = sessionRepository,
            timelineRepository = timelineRepository,
            uploadStatusRepository = FakeUploadStatusRepository(),
            audioClipService = FakeAudioClipService(),
            videoRemuxService = FfmpegVideoRemuxService(locator),
            archiveService = ZipSessionArchiveService(),
            clock = FakeClock(),
            dispatchers = ImmediateCoroutineDispatchers(),
        )

        val template = "\${installationId}_\${field.patient_code}_\${siteName}_\${taskIndex}_\${task.subtype}_\${repetition}"
        val config = """
            {"schemaVersion":1,"configVersion":"2026-07-01.1","defaultLanguage":"en","siteName":"$siteName",
             "protocols":[{"name":"Share","recordingsFileName":"$template","tasks":[]}]}
        """.trimIndent()
        val folderName = SessionFolderNaming.build(LocalDate.of(2026, 7, 1), patientCode, "s1")
        sessionRepository.createSessionDirectory(folderName)
        sessionRepository.writeParticipant(
            folderName,
            ParticipantRecord(fields = mapOf("patient_code" to patientCode), createdAt = "2026-07-01T09:00:00Z"),
        )
        sessionRepository.writeConfigSnapshot(folderName, config)

        val takeRecord = VideoTakeRecord(
            file = "video/task00_rep01_take01.mjpeg", taskIndex = 0, repetition = 1, take = 1,
            captureFormat = VideoCaptureFormat(320, 240, 30),
        )
        sessionRepository.writeExamination(
            folderName,
            Examination(
                sessionId = "s1", installationId = installationId, protocolName = "Share",
                configVersion = "2026-07-01.1", startedAt = "2026-07-01T09:00:00Z", captureFormat = null,
                tasks = listOf(TaskRecord(taskIndex = 0, type = "VIDEO", subtype = "EMOTIONS", repetition = 1, takes = 1)),
                videoTakes = listOf(takeRecord),
            ),
        )
        val take = sessionRepository.sessionDir(folderName).resolve(takeRecord.file)
        Files.createDirectories(take.parent)
        val process = ProcessBuilder(
            ffmpeg.toString(), "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "testsrc=size=320x240:rate=30",
            "-frames:v", "20", "-c:v", "mjpeg", "-q:v", "3", "-f", "mjpeg", take.toString(),
        ).redirectErrorStream(true).start()
        val ffmpegOutput = process.inputStream.readBytes().decodeToString()
        check(process.waitFor() == 0) { "could not make the test take: $ffmpegOutput" }
        timelineRepository.writeOriginal(
            folderName,
            TimelineOriginal(
                sessionId = "s1", sampleRate = 48_000,
                events = listOf(
                    TimelineEvent(TimelineEventType.START_BUTTON_PRESSED, 0, "2026-07-01T09:00:00Z", taskIndex = 0, repetition = 1, take = 1),
                    TimelineEvent(TimelineEventType.STOP_BUTTON_PRESSED, 0, "2026-07-01T09:00:01Z", taskIndex = 0, repetition = 1, take = 1),
                ),
            ),
        )

        lateinit var outcome: ProcessSessionUseCase.Outcome
        val events = captureLogs {
            val progress = runBlocking { useCase.process(folderName).toList() }
            outcome = (progress.last() as ProcessSessionUseCase.Progress.Completed).outcome
        }
        assertTrue(outcome is ProcessSessionUseCase.Outcome.Success, "expected Success but was $outcome")
        assertTrue(
            Files.isRegularFile(sessionRepository.clipsDir(folderName).resolve("${installationId}_${patientCode}_${siteName}_0_EMOTIONS_1.mp4")),
            "the clip was written under its rendered name",
        )

        val lines = events.map { it.fullText }
        assertTrue(lines.any { "task00_rep01_take01.mjpeg" in it }, "the remux line names the source take: $lines")
        for (secret in listOf(patientCode, installationId, siteName, "EMOTIONS_1.mp4", ".zip")) {
            assertFalse(lines.any { secret in it }, "'$secret' leaked into the log: ${lines.filter { secret in it }}")
        }
    }
}
