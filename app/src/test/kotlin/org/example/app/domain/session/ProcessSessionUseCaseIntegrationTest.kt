package org.example.app.domain.session

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.example.app.domain.audio.CaptureFormat
import org.example.app.domain.timeline.TimelineEvent
import org.example.app.domain.timeline.TimelineEventType
import org.example.app.domain.timeline.TimelineOriginal
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeUploadStatusRepository
import org.example.app.fakes.ImmediateCoroutineDispatchers
import org.example.app.fakes.TestAppDirectories
import org.example.app.infrastructure.audio.JvmAudioClipService
import org.example.app.infrastructure.audio.monoRamp
import org.example.app.infrastructure.audio.readTestWavSamples
import org.example.app.infrastructure.audio.writeTestWav
import org.example.app.infrastructure.persistence.JsonSessionRepository
import org.example.app.infrastructure.persistence.JsonTimelineRepository
import org.example.app.infrastructure.persistence.ZipSessionArchiveService
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDate
import java.util.zip.ZipFile

/**
 * End-to-end [ProcessSessionUseCase] tests against the real [JvmAudioClipService] and
 * [ZipSessionArchiveService] (§10.2): byte-exact cut boundaries, real ZIP contents, and
 * manifest hashes that actually verify against the ZIP's own bytes.
 */
class ProcessSessionUseCaseIntegrationTest {

    private val preferred = CaptureFormat.PREFERRED
    private val template = "\${installationId}_\${patientCode}_\${taskIndex}_\${task.subtype}_\${repetition}"
    private val configJson = """
        {"schemaVersion":1,"configVersion":"2026-07-01.1","defaultLanguage":"en",
         "protocols":[{"name":"Share","recordingsFileName":"$template","tasks":[]}]}
    """.trimIndent()

    private fun event(type: TimelineEventType, taskIndex: Int?, repetition: Int?, take: Int?, sample: Long) = TimelineEvent(
        type = type, sampleOffset = sample, wallClock = "2026-07-01T09:00:00Z",
        taskIndex = taskIndex, repetition = repetition, take = take,
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `single-part session - exact cut boundaries, zip contents, and manifest hashes verify`(@TempDir tempDir: Path) {
        val directories = TestAppDirectories(tempDir)
        val sessionRepository = JsonSessionRepository(directories)
        val timelineRepository = JsonTimelineRepository(directories)
        val useCase = ProcessSessionUseCase(
            directories = directories,
            sessionRepository = sessionRepository,
            timelineRepository = timelineRepository,
            uploadStatusRepository = FakeUploadStatusRepository(),
            audioClipService = JvmAudioClipService(),
            archiveService = ZipSessionArchiveService(),
            clock = FakeClock(),
            dispatchers = ImmediateCoroutineDispatchers(),
        )

        val folderName = SessionFolderNaming.build(LocalDate.of(2026, 7, 1), "HC001", "s1")
        sessionRepository.createSessionDirectory(folderName)
        sessionRepository.writeParticipant(folderName, ParticipantRecord(fields = mapOf("code" to "HC001"), createdAt = "2026-07-01T09:00:00Z"))
        sessionRepository.writeConfigSnapshot(folderName, configJson)

        val masterSamples = monoRamp(5000)
        writeTestWav(sessionRepository.defaultMasterFile(folderName), preferred, masterSamples)

        val examination = Examination(
            sessionId = "s1",
            installationId = "install1",
            protocolName = "Share",
            configVersion = "2026-07-01.1",
            startedAt = "2026-07-01T09:00:00Z",
            captureFormat = preferred,
            tasks = listOf(TaskRecord(taskIndex = 0, type = "VOCAL", subtype = "PHONATION", repetition = 1, takes = 1)),
        )
        sessionRepository.writeExamination(folderName, examination)
        timelineRepository.writeOriginal(
            folderName,
            TimelineOriginal(
                sessionId = "s1",
                sampleRate = 48_000,
                events = listOf(
                    event(TimelineEventType.START_BUTTON_PRESSED, 0, 1, 1, 1000),
                    event(TimelineEventType.STOP_BUTTON_PRESSED, 0, 1, 1, 3000),
                ),
            ),
        )

        val progress = runBlocking { useCase.process(folderName).toList() }
        val outcome = (progress.last() as ProcessSessionUseCase.Progress.Completed).outcome
        assertTrue(outcome is ProcessSessionUseCase.Outcome.Success, "expected Success but was $outcome")
        val archiveFile = (outcome as ProcessSessionUseCase.Outcome.Success).archiveFile

        // 1. Exact cut boundaries: the clip is exactly master[1000, 3000).
        val clipFile = sessionRepository.clipsDir(folderName).resolve("install1_HC001_0_PHONATION_1.wav")
        assertTrue(Files.exists(clipFile))
        val (clipFormat, clipSamples) = readTestWavSamples(clipFile)
        assertEquals(preferred, clipFormat)
        assertArrayEquals(masterSamples.copyOfRange(1000, 3000), clipSamples)

        // 2. The real master/ directory is untouched (§8.1 immutability).
        val realMaster = readTestWavSamples(sessionRepository.defaultMasterFile(folderName))
        assertArrayEquals(masterSamples, realMaster.second)

        // 3. ZIP contents match §8.8's inclusion/exclusion rules.
        assertTrue(Files.exists(archiveFile))
        assertEquals(sessionRepository.archiveDir(folderName).resolve("HC001_s1.zip"), archiveFile)
        ZipFile(archiveFile.toFile()).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertEquals(
                setOf(
                    "participant.json", "examination.json", "task_configuration_snapshot.json",
                    "timeline_original.json", "master/session_master.wav",
                    "clips/install1_HC001_0_PHONATION_1.wav", "manifest.json",
                ),
                names,
            )

            // 4. Manifest hashes verify against the zip's own bytes.
            val manifestBytes = zip.getInputStream(zip.getEntry("manifest.json")).readBytes()
            val manifest = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString(SessionArchiveManifest.serializer(), String(manifestBytes))
            assertEquals("s1", manifest.sessionId)
            assertEquals("2026-07-01.1", manifest.configVersion)
            assertEquals(names.size - 1, manifest.files.size) // every entry except manifest.json itself

            for (entry in manifest.files) {
                val bytes = zip.getInputStream(zip.getEntry(entry.path)).readBytes()
                assertEquals(sha256(bytes), entry.sha256, "hash mismatch for ${entry.path}")
            }

            // The zipped master is the *archived* one (same content here since capture format
            // already equals preferred, but sourced from the temp staging file, not a direct
            // copy of the real master path — verified indirectly: no leftover staging dir).
            assertFalse(Files.exists(sessionRepository.archiveDir(folderName).resolve(".staging")))
        }

        // 5. examination.json was updated with the clip path and Done status.
        val finalExamination = sessionRepository.readExamination(folderName)!!
        assertEquals(ProcessingStatus.Done, finalExamination.processing?.status)
        assertEquals("clips/install1_HC001_0_PHONATION_1.wav", finalExamination.tasks[0].clipFile)
    }

    @Test
    fun `multi-part master - each clip cuts from its own part and the archived master is the concatenation`(@TempDir tempDir: Path) {
        val directories = TestAppDirectories(tempDir)
        val sessionRepository = JsonSessionRepository(directories)
        val timelineRepository = JsonTimelineRepository(directories)
        val useCase = ProcessSessionUseCase(
            directories = directories,
            sessionRepository = sessionRepository,
            timelineRepository = timelineRepository,
            uploadStatusRepository = FakeUploadStatusRepository(),
            audioClipService = JvmAudioClipService(),
            archiveService = ZipSessionArchiveService(),
            clock = FakeClock(),
            dispatchers = ImmediateCoroutineDispatchers(),
        )

        val folderName = SessionFolderNaming.build(LocalDate.of(2026, 7, 1), "HC003", "s3")
        sessionRepository.createSessionDirectory(folderName)
        sessionRepository.writeParticipant(folderName, ParticipantRecord(fields = mapOf("code" to "HC003"), createdAt = "2026-07-01T09:00:00Z"))
        sessionRepository.writeConfigSnapshot(folderName, configJson)

        val part1Samples = monoRamp(2000, step = 11)
        val part2Samples = monoRamp(1500, step = 23)
        writeTestWav(sessionRepository.defaultMasterFile(folderName), preferred, part1Samples)
        val part2File = sessionRepository.masterDir(folderName).resolve("session_master.part2.wav")
        writeTestWav(part2File, preferred, part2Samples)

        val interruption = Interruption(
            sampleOffset = 2000L, start = "2026-07-01T09:05:00Z", end = "2026-07-01T09:05:05Z",
            partFile = "master/session_master.part2.wav", captureFormat = preferred,
        )
        sessionRepository.writeExamination(
            folderName,
            Examination(
                sessionId = "s3", installationId = "install1", protocolName = "Share", configVersion = "2026-07-01.1",
                startedAt = "2026-07-01T09:00:00Z", captureFormat = preferred, interruptions = listOf(interruption),
                tasks = listOf(
                    TaskRecord(taskIndex = 0, type = "VOCAL", subtype = "PHONATION", repetition = 1, takes = 1),
                    TaskRecord(taskIndex = 1, type = "VOCAL", subtype = "PATAKA", repetition = 1, takes = 1),
                ),
            ),
        )
        timelineRepository.writeOriginal(
            folderName,
            TimelineOriginal(
                sessionId = "s3", sampleRate = 48_000,
                events = listOf(
                    event(TimelineEventType.START_BUTTON_PRESSED, 0, 1, 1, 500),
                    event(TimelineEventType.STOP_BUTTON_PRESSED, 0, 1, 1, 1500),
                    event(TimelineEventType.RECORDING_INTERRUPTED, null, null, null, 2000),
                    event(TimelineEventType.RECORDING_RESUMED, null, null, null, 2000),
                    event(TimelineEventType.START_BUTTON_PRESSED, 1, 1, 1, 2300),
                    event(TimelineEventType.STOP_BUTTON_PRESSED, 1, 1, 1, 3300), // local [300, 1300) in part2
                ),
            ),
        )

        val progress = runBlocking { useCase.process(folderName).toList() }
        val outcome = (progress.last() as ProcessSessionUseCase.Progress.Completed).outcome
        assertTrue(outcome is ProcessSessionUseCase.Outcome.Success, "expected Success but was $outcome")

        val clip0 = readTestWavSamples(sessionRepository.clipsDir(folderName).resolve("install1_HC003_0_PHONATION_1.wav")).second
        assertArrayEquals(part1Samples.copyOfRange(500, 1500), clip0)

        val clip1 = readTestWavSamples(sessionRepository.clipsDir(folderName).resolve("install1_HC003_1_PATAKA_1.wav")).second
        assertArrayEquals(part2Samples.copyOfRange(300, 1300), clip1)

        val archiveFile = (outcome as ProcessSessionUseCase.Outcome.Success).archiveFile
        ZipFile(archiveFile.toFile()).use { zip ->
            val masterBytes = zip.getInputStream(zip.getEntry("master/session_master.wav")).readBytes()
            val tempMaster = tempDir.resolve("extracted_master.wav")
            Files.write(tempMaster, masterBytes)
            val (_, archivedSamples) = readTestWavSamples(tempMaster)
            assertArrayEquals(part1Samples + part2Samples, archivedSamples)
        }

        // The immutable raw parts are untouched.
        assertArrayEquals(part1Samples, readTestWavSamples(sessionRepository.defaultMasterFile(folderName)).second)
        assertArrayEquals(part2Samples, readTestWavSamples(part2File).second)
    }

    @Test
    fun `no-master session produces a JSON-only archive with no master or clips entries`(@TempDir tempDir: Path) {
        val directories = TestAppDirectories(tempDir)
        val sessionRepository = JsonSessionRepository(directories)
        val timelineRepository = JsonTimelineRepository(directories)
        val useCase = ProcessSessionUseCase(
            directories = directories,
            sessionRepository = sessionRepository,
            timelineRepository = timelineRepository,
            uploadStatusRepository = FakeUploadStatusRepository(),
            audioClipService = JvmAudioClipService(),
            archiveService = ZipSessionArchiveService(),
            clock = FakeClock(),
            dispatchers = ImmediateCoroutineDispatchers(),
        )

        val folderName = SessionFolderNaming.build(LocalDate.of(2026, 7, 1), "HC002", "s2")
        sessionRepository.createSessionDirectory(folderName)
        sessionRepository.writeParticipant(folderName, ParticipantRecord(fields = mapOf("code" to "HC002"), createdAt = "2026-07-01T09:00:00Z"))
        sessionRepository.writeConfigSnapshot(folderName, configJson)
        sessionRepository.writeExamination(
            folderName,
            Examination(
                sessionId = "s2", installationId = "install1", protocolName = "Share", configVersion = "2026-07-01.1",
                startedAt = "2026-07-01T09:00:00Z", captureFormat = null,
                tasks = listOf(TaskRecord(taskIndex = 0, type = "QUESTIONNAIRE", repetition = 1)),
            ),
        )
        timelineRepository.writeOriginal(folderName, TimelineOriginal(sessionId = "s2", sampleRate = 0, events = emptyList()))

        val progress = runBlocking { useCase.process(folderName).toList() }
        val outcome = (progress.last() as ProcessSessionUseCase.Progress.Completed).outcome
        assertTrue(outcome is ProcessSessionUseCase.Outcome.Success, "expected Success but was $outcome")
        val archiveFile = (outcome as ProcessSessionUseCase.Outcome.Success).archiveFile

        ZipFile(archiveFile.toFile()).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertEquals(
                setOf("participant.json", "examination.json", "task_configuration_snapshot.json", "timeline_original.json", "manifest.json"),
                names,
            )
        }
        val clipsDirIsEmpty = Files.list(sessionRepository.clipsDir(folderName)).use { it.findAny().isEmpty }
        assertTrue(clipsDirIsEmpty)
    }
}
