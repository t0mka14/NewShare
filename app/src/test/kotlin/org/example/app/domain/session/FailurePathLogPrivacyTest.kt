package org.example.app.domain.session

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.example.app.domain.timeline.TimelineEvent
import org.example.app.domain.timeline.TimelineEventType
import org.example.app.domain.timeline.TimelineOriginal
import org.example.app.fakes.FakeAudioClipService
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeSessionRepository
import org.example.app.fakes.FakeTimelineRepository
import org.example.app.fakes.FakeUploadStatusRepository
import org.example.app.fakes.FakeVideoRemuxService
import org.example.app.fakes.ImmediateCoroutineDispatchers
import org.example.app.fakes.TestAppDirectories
import org.example.app.infrastructure.logging.captureLogs
import org.example.app.infrastructure.logging.fullText
import org.example.app.infrastructure.persistence.JsonSessionRepository
import org.example.app.infrastructure.persistence.JsonTimelineRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.time.LocalDate

/**
 * Failure paths (AC 11-13): a session folder name, a clip name and `<PatientCode>_<SessionId>.zip.tmp`
 * embed the patient code, so a failure's exception message, nested causes and file names must not
 * reach the log. The type of the failure, a file's extension and the sessionId must.
 */
class FailurePathLogPrivacyTest {

    private val code = "ABC123"
    private val folder = SessionFolderNaming.build(LocalDate.of(2026, 7, 1), code, "s1")
    private val sessionPath = """C:\Users\Jan Novak\data\sessions\$folder\master\session_master.wav"""

    private fun assertClean(lines: List<String>) {
        for (secret in listOf(code, folder, "Jan Novak")) {
            assertFalse(lines.any { secret in it }, "'$secret' leaked: ${lines.filter { secret in it }}")
        }
    }

    // region recovery (AC 11)

    @Test
    fun `a failed recovery whose exception message holds the session path logs only the exception type`() {
        val fake = FakeSessionRepository().also { it.createSessionDirectory(folder) }
        val throwing = object : SessionRepository by fake {
            override fun findPartialMasterFiles(folderName: String): List<Path> = throw IOException("cannot read $sessionPath")
        }
        val recovery = StartupRecovery(RecoverSessionsUseCase(throwing, FakeTimelineRepository(), FakeClock()), throwing)

        val events = captureLogs { recovery.run() }

        val lines = events.map { it.fullText }
        val failure = lines.single { "could not recover" in it }
        assertTrue("IOException" in failure, "the exception type survives: $failure")
        assertClean(lines)
    }

    @Test
    fun `the recovery failure keeps only the exception class as its detail`() {
        val fake = FakeSessionRepository().also { it.createSessionDirectory(folder) }
        val throwing = object : SessionRepository by fake {
            override fun findPartialMasterFiles(folderName: String): List<Path> = throw IOException("cannot read $sessionPath")
        }

        val outcome = RecoverSessionsUseCase(throwing, FakeTimelineRepository(), FakeClock()).recoverOne(folder)

        assertEquals(StorageError.CorruptSessionMetadata("IOException"), (outcome as RecoveryOutcome.Failed).error)
    }

    @Test
    fun `a recovery failure from a parser message that quotes the file content logs no content`(@TempDir tempDir: Path) {
        val directories = TestAppDirectories(tempDir)
        val sessions = JsonSessionRepository(directories)
        val timelines = JsonTimelineRepository(directories)
        sessions.createSessionDirectory(folder)
        // An uncompacted event log makes the session a recovery candidate; the examination is unparseable.
        timelines.appendEvent(folder, TimelineEvent(TimelineEventType.SESSION_RECORDING_STARTED, 0, "2026-07-01T09:00:00Z", null, null, null))
        Files.writeString(sessions.sessionDir(folder).resolve("examination.json"), """{"sessionId":"s1","participant":"$code Novak" oops""")
        val recovery = StartupRecovery(RecoverSessionsUseCase(sessions, timelines, FakeClock()), sessions)

        val events = captureLogs { recovery.run() }

        val lines = events.map { it.fullText }
        val failure = lines.single { "could not recover" in it }
        assertTrue("Exception" in failure, "a parser exception type is named: $failure")
        assertFalse("oops" in failure, failure)
        assertClean(lines)
    }

    // endregion

    // region processing (AC 12)

    @Test
    fun `a processing failure on the zip tmp file logs its extension and the session id, never its name`(@TempDir tempDir: Path) {
        val directories = TestAppDirectories(tempDir)
        val sessions = JsonSessionRepository(directories)
        val timelines = JsonTimelineRepository(directories)
        val zipTmp = sessions.archiveDir(folder).resolve("${code}_s1.zip.tmp")
        val failingArchive = object : SessionArchiveService {
            override fun build(
                zipFile: Path,
                entries: List<ArchiveEntry>,
                sessionId: String,
                configVersion: String,
                generatedAt: String,
            ): ArchiveResult = throw IllegalStateException(
                "could not build $zipTmp",
                UncheckedIOException("write failed: $zipTmp", IOException("outer", NoSuchFileException(zipTmp.toString()))),
            )
        }
        val useCase = ProcessSessionUseCase(
            directories = directories,
            sessionRepository = sessions,
            timelineRepository = timelines,
            uploadStatusRepository = FakeUploadStatusRepository(),
            audioClipService = FakeAudioClipService(),
            videoRemuxService = FakeVideoRemuxService(),
            archiveService = failingArchive,
            clock = FakeClock(),
            dispatchers = ImmediateCoroutineDispatchers(),
        )
        sessions.createSessionDirectory(folder)
        sessions.writeParticipant(folder, ParticipantRecord(fields = mapOf("patient_code" to code), createdAt = "2026-07-01T09:00:00Z"))
        sessions.writeConfigSnapshot(
            folder,
            """{"schemaVersion":1,"configVersion":"v1","defaultLanguage":"en","protocols":[{"name":"Share","tasks":[]}]}""",
        )
        sessions.writeExamination(
            folder,
            Examination(
                sessionId = "s1", installationId = "inst", protocolName = "Share",
                configVersion = "v1", startedAt = "2026-07-01T09:00:00Z", captureFormat = null,
            ),
        )
        timelines.writeOriginal(folder, TimelineOriginal(sessionId = "s1", sampleRate = 48_000, events = emptyList()))

        lateinit var outcome: ProcessSessionUseCase.Outcome
        val events = captureLogs {
            outcome = (runBlocking { useCase.process(folder).toList() }.last() as ProcessSessionUseCase.Progress.Completed).outcome
        }

        assertTrue(outcome is ProcessSessionUseCase.Outcome.Failed, "$outcome")
        val lines = events.map { it.fullText }
        val failure = lines.single { "processing failed" in it }
        assertTrue("NoSuchFileException" in failure, "the nested file-system exception type survives: $failure")
        assertTrue(".zip.tmp" in failure, "the extension survives: $failure")
        assertTrue("session s1" in failure, "the session id survives: $failure")
        assertClean(lines)
        assertFalse(lines.any { "${code}_s1" in it }, "the archive file name leaked: $lines")
    }

    // endregion
}
