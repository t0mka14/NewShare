package org.example.app.domain.session

import org.example.app.domain.audio.CaptureFormat
import org.example.app.domain.config.CalibrationTask
import org.example.app.domain.config.PatientField
import org.example.app.domain.config.Protocol
import org.example.app.domain.config.QuestionnaireTask
import org.example.app.domain.config.VocalSubtype
import org.example.app.domain.config.VocalTask
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeDiskSpaceProvider
import org.example.app.fakes.FakeIdGenerator
import org.example.app.fakes.FakeSessionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset

class StartSessionUseCaseTest {

    private val format = CaptureFormat.PREFERRED

    private val patientFields = listOf(
        PatientField(name = "code", labelKey = "field.code", required = true, useInFilename = true),
        PatientField(name = "sex", labelKey = "field.sex", useInFilename = true),
    )

    private val vocalProtocol = Protocol(
        name = "Share",
        recordingsFileName = "\${patientCode}_\${taskIndex}_\${task.subtype}.wav",
        tasks = listOf(
            CalibrationTask(titleKey = "calib", optimalLoudness = listOf(0.2, 0.8)),
            VocalTask(titleKey = "vocal", subtype = VocalSubtype.PHONATION, nrepetition = 2),
        ),
    )

    private val questionnaireOnlyProtocol = Protocol(
        name = "QOnly",
        recordingsFileName = "\${patientCode}_\${taskIndex}.wav",
        tasks = listOf(QuestionnaireTask(titleKey = "q1")),
    )

    private fun newUseCase(
        sessionRepository: FakeSessionRepository = FakeSessionRepository(),
        diskSpaceProvider: FakeDiskSpaceProvider = FakeDiskSpaceProvider(),
        clock: FakeClock = FakeClock(Instant.parse("2026-07-03T09:00:00Z")),
        idGenerator: FakeIdGenerator = FakeIdGenerator(),
    ) = StartSessionUseCase(
        directories = TestDirectoriesStub,
        sessionRepository = sessionRepository,
        diskSpaceProvider = diskSpaceProvider,
        idGenerator = idGenerator,
        clock = clock,
        clinicZone = ZoneOffset.UTC,
    )

    private fun params(protocol: Protocol, format: CaptureFormat?) = StartSessionUseCase.Params(
        installationId = "install-1",
        protocol = protocol,
        configVersion = "2026-07-01.1",
        rawConfigJson = """{"schemaVersion":1}""",
        patientFields = patientFields,
        participantFieldValues = mapOf("code" to "HC001", "sex" to "F"),
        negotiatedFormat = format,
    )

    @Test
    fun `starts a master session — creates dir, writes participant, config snapshot, examination`() {
        val sessionRepository = FakeSessionRepository()
        val useCase = newUseCase(sessionRepository = sessionRepository)

        val outcome = useCase.start(params(vocalProtocol, format))

        assertTrue(outcome is StartSessionUseCase.Outcome.Started)
        val result = (outcome as StartSessionUseCase.Outcome.Started).result

        assertEquals("2026-07-03_HC001_F_session-0001", result.folderName)
        assertNotNull(result.masterFile)
        assertEquals(sessionRepository.readParticipant(result.folderName)?.fields?.get("code"), "HC001")
        assertEquals("""{"schemaVersion":1}""", sessionRepository.readConfigSnapshot(result.folderName))

        val examination = sessionRepository.readExamination(result.folderName)!!
        assertEquals(format, examination.captureFormat)
        assertEquals("2026-07-03T09:00:00Z", examination.startedAt)
        assertFalse(examination.recovered)

        // Calibration (1 instance) + VocalTask expanded into 2 repetitions = 3 task instances.
        assertEquals(3, result.expansion.instances.size)
    }

    @Test
    fun `no-master protocol produces a null masterFile and null captureFormat`() {
        val useCase = newUseCase()
        val outcome = useCase.start(params(questionnaireOnlyProtocol, format = null))

        assertTrue(outcome is StartSessionUseCase.Outcome.Started)
        val result = (outcome as StartSessionUseCase.Outcome.Started).result
        assertNull(result.masterFile)
        assertNull(result.examination.captureFormat)
    }

    @Test
    fun `rejects when free space is below the preflight requirement`() {
        val diskSpaceProvider = FakeDiskSpaceProvider(usableBytesValue = 100) // far below any real requirement
        val useCase = newUseCase(diskSpaceProvider = diskSpaceProvider)

        val outcome = useCase.start(params(vocalProtocol, format))

        assertTrue(outcome is StartSessionUseCase.Outcome.Rejected)
        val error = (outcome as StartSessionUseCase.Outcome.Rejected).error
        assertTrue(error is StorageError.InsufficientDiskSpace)
        error as StorageError.InsufficientDiskSpace
        assertEquals(100L, error.availableBytes)
    }

    @Test
    fun `no-master protocol only needs the small fixed preflight allowance`() {
        val diskSpaceProvider = FakeDiskSpaceProvider(usableBytesValue = SessionDiskPreflight.NO_MASTER_MINIMUM_BYTES)
        val useCase = newUseCase(diskSpaceProvider = diskSpaceProvider)

        val outcome = useCase.start(params(questionnaireOnlyProtocol, format = null))

        assertTrue(outcome is StartSessionUseCase.Outcome.Started)
    }

    @Test
    fun `two sessions on the same day get distinct folder names via distinct session ids`() {
        val sessionRepository = FakeSessionRepository()
        val idGenerator = FakeIdGenerator()
        val useCase = newUseCase(sessionRepository = sessionRepository, idGenerator = idGenerator)

        val first = useCase.start(params(vocalProtocol, format)) as StartSessionUseCase.Outcome.Started
        val second = useCase.start(params(vocalProtocol, format)) as StartSessionUseCase.Outcome.Started

        assertFalse(first.result.folderName == second.result.folderName)
    }
}

/** [org.example.app.domain.AppDirectories] stub — [StartSessionUseCase] only needs
 * `sessionsDir` (for the preflight free-space check) since all other paths go through
 * [FakeSessionRepository]. */
private object TestDirectoriesStub : org.example.app.domain.AppDirectories {
    override val dataRoot: java.nio.file.Path = java.nio.file.Paths.get("fake-data-root")
    override val configDir: java.nio.file.Path = dataRoot.resolve("config")
    override val sessionsDir: java.nio.file.Path = dataRoot.resolve("sessions")
    override val uploadQueueDir: java.nio.file.Path = dataRoot.resolve("upload_queue")
    override val logsDir: java.nio.file.Path = dataRoot.resolve("logs")
    override fun sessionDir(folderName: String): java.nio.file.Path = sessionsDir.resolve(folderName)
}
