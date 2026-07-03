package org.example.app.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.domain.audio.CaptureFormat
import org.example.app.domain.audio.InterruptionReason
import org.example.app.domain.config.CalibrationTask
import org.example.app.domain.config.InfoTask
import org.example.app.domain.config.PatientField
import org.example.app.domain.config.Protocol
import org.example.app.domain.config.QuestionnaireTask
import org.example.app.domain.config.VocalSubtype
import org.example.app.domain.config.VocalTask
import org.example.app.domain.session.StartSessionUseCase
import org.example.app.domain.session.StorageError
import org.example.app.domain.timeline.TimelineEventType
import org.example.app.fakes.FakeAudioInputDeviceProvider
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeContinuousSessionRecorder
import org.example.app.fakes.FakeDiskSpaceProvider
import org.example.app.fakes.FakeIdGenerator
import org.example.app.fakes.FakeSessionRepository
import org.example.app.fakes.FakeTimelineRepository
import org.example.app.fakes.TestAppDirectories
import org.example.app.fakes.TestCoroutineDispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset

class SessionComponentTest {

    private val patientFields = listOf(
        PatientField(name = "code", labelKey = "field.code", required = true, useInFilename = true),
    )

    private val vocalProtocol = Protocol(
        name = "Share",
        recordingsFileName = "\${patientCode}_\${taskIndex}_\${task.subtype}.wav",
        tasks = listOf(
            CalibrationTask(titleKey = "calib", optimalLoudness = listOf(0.2, 0.8)),
            VocalTask(titleKey = "vocal", subtype = VocalSubtype.PHONATION, nrepetition = 2, canRepeat = true),
        ),
    )

    private val mixedProtocol = Protocol(
        name = "Mixed",
        recordingsFileName = "\${patientCode}_\${taskIndex}_\${task.subtype}.wav",
        tasks = listOf(
            CalibrationTask(titleKey = "calib", optimalLoudness = listOf(0.2, 0.8)),
            VocalTask(titleKey = "vocal", subtype = VocalSubtype.PHONATION),
            QuestionnaireTask(titleKey = "q"),
            InfoTask(titleKey = "info"),
        ),
    )

    private val questionnaireOnlyProtocol = Protocol(
        name = "QOnly",
        recordingsFileName = "\${patientCode}_\${taskIndex}.wav",
        tasks = listOf(QuestionnaireTask(titleKey = "q1")),
    )

    private inner class Harness {
        val clock = FakeClock(Instant.parse("2026-07-03T09:00:00Z"))
        val dispatchers = TestCoroutineDispatchers()
        val sessionRepository = FakeSessionRepository()
        val timelineRepository = FakeTimelineRepository()
        val directories = TestAppDirectories(java.nio.file.Files.createTempDirectory("session-component-test"))
        val diskSpaceProvider = FakeDiskSpaceProvider()
        val idGenerator = FakeIdGenerator()
        var recorder: FakeContinuousSessionRecorder? = null
        val recorderFactory: () -> org.example.app.domain.audio.ContinuousSessionRecorder = {
            FakeContinuousSessionRecorder(clock).also { recorder = it }
        }
        val startSessionUseCase = StartSessionUseCase(
            directories = directories,
            sessionRepository = sessionRepository,
            diskSpaceProvider = diskSpaceProvider,
            idGenerator = idGenerator,
            clock = clock,
            clinicZone = ZoneOffset.UTC,
        )
        var ended = 0

        fun build(protocol: Protocol): SessionComponent = DefaultSessionComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            installationId = "install-1",
            protocol = protocol,
            configVersion = "v1",
            rawConfigJson = """{"schemaVersion":1}""",
            patientFields = patientFields,
            participantFieldValues = mapOf("code" to "HC001"),
            initialDevice = FakeAudioInputDeviceProvider.DEFAULT_DEVICE,
            availableDevices = listOf(FakeAudioInputDeviceProvider.DEFAULT_DEVICE, FakeAudioInputDeviceProvider.SECONDARY_DEVICE),
            recorderFactory = recorderFactory,
            startSessionUseCase = startSessionUseCase,
            sessionRepository = sessionRepository,
            timelineRepository = timelineRepository,
            clock = clock,
            dispatchers = dispatchers,
            onSessionEnded = { ended++ },
        )
    }

    @Test
    fun `master-bearing protocol starts on the calibration screen and negotiates the format up front`() {
        val h = Harness()
        val component = h.build(vocalProtocol)
        h.dispatchers.scheduler.advanceUntilIdle()

        val child = component.stack.value.active.instance
        assertTrue(child is SessionComponent.Child.Calibration)

        assertEquals(1, h.recorder!!.monitoringStarts.size)
        val examination = h.sessionRepository.readExamination(h.sessionRepository.listSessionFolderNames().single())
        assertEquals(CaptureFormat.PREFERRED, examination!!.captureFormat)
    }

    @Test
    fun `confirming calibration starts writing the master and logs SESSION_RECORDING_STARTED`() {
        val h = Harness()
        val component = h.build(vocalProtocol)
        h.dispatchers.scheduler.advanceUntilIdle()

        val calibration = (component.stack.value.active.instance as SessionComponent.Child.Calibration).component
        calibration.onConfirm()
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals(1, h.recorder!!.writingStarts.size)
        val folderName = h.sessionRepository.listSessionFolderNames().single()
        val events = h.timelineRepository.readEventLog(folderName).events
        assertTrue(events.any { it.type == TimelineEventType.SESSION_RECORDING_STARTED })

        assertTrue(component.stack.value.active.instance is SessionComponent.Child.TaskScreen)
    }

    @Test
    fun `no-master protocol skips calibration entirely and never creates a recorder`() {
        val h = Harness()
        val component = h.build(questionnaireOnlyProtocol)
        h.dispatchers.scheduler.advanceUntilIdle()

        assertTrue(component.stack.value.active.instance is SessionComponent.Child.TaskScreen)
        assertNull(h.recorder)

        val folderName = h.sessionRepository.listSessionFolderNames().single()
        assertNull(h.sessionRepository.readExamination(folderName)!!.captureFormat)
    }

    @Test
    fun `completing every task instance ends the session, stops the recorder, and compacts the timeline`() {
        val h = Harness()
        val component = h.build(mixedProtocol)
        h.dispatchers.scheduler.advanceUntilIdle()

        val calibration = (component.stack.value.active.instance as SessionComponent.Child.Calibration).component
        calibration.onConfirm()
        h.dispatchers.scheduler.advanceUntilIdle()

        // Task 1: VOCAL
        var task = (component.stack.value.active.instance as SessionComponent.Child.TaskScreen).component
        task.onStart()
        task.onStop()
        task.onNext()
        h.dispatchers.scheduler.advanceUntilIdle()

        // Task 2: QUESTIONNAIRE (no questions configured -> immediately valid)
        task = (component.stack.value.active.instance as SessionComponent.Child.TaskScreen).component
        task.onNext()
        h.dispatchers.scheduler.advanceUntilIdle()

        // Task 3: INFO -> last task instance, session should end afterwards.
        task = (component.stack.value.active.instance as SessionComponent.Child.TaskScreen).component
        task.onNext()
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals(1, h.ended)
        assertEquals(1, h.recorder!!.stopCallCount)

        val folderName = h.sessionRepository.listSessionFolderNames().single()
        val examination = h.sessionRepository.readExamination(folderName)!!
        assertEquals(3, examination.tasks.size)
        assertNotNull(examination.endedAt)

        val original = h.timelineRepository.readOriginal(folderName)
        assertNotNull(original)
        assertTrue(original!!.events.any { it.type == TimelineEventType.SESSION_RECORDING_STOPPED })
    }

    @Test
    fun `preflight rejection surfaces as a Failed child with the StorageError`() {
        val h = Harness()
        h.diskSpaceProvider.usableBytesValue = 100 // far below any real requirement
        val component = h.build(vocalProtocol)
        h.dispatchers.scheduler.advanceUntilIdle()

        assertTrue(component.stack.value.active.instance is SessionComponent.Child.Failed)
        val error = component.startError.value.error
        assertTrue(error is StorageError.InsufficientDiskSpace)
    }

    @Test
    fun `device-loss resume during a task instance logs RECORDING_RESUMED and records the interruption`() {
        val h = Harness()
        val component = h.build(vocalProtocol)
        h.dispatchers.scheduler.advanceUntilIdle()

        val calibration = (component.stack.value.active.instance as SessionComponent.Child.Calibration).component
        calibration.onConfirm()
        h.dispatchers.scheduler.advanceUntilIdle()

        val task = (component.stack.value.active.instance as SessionComponent.Child.TaskScreen).component
        task.onStart()

        h.recorder!!.simulateInterruption(InterruptionReason.DEVICE_LOST)
        h.dispatchers.scheduler.advanceUntilIdle()

        val folderName = h.sessionRepository.listSessionFolderNames().single()
        var events = h.timelineRepository.readEventLog(folderName).events
        assertTrue(events.any { it.type == TimelineEventType.RECORDING_INTERRUPTED })

        task.onDeviceReselected(FakeAudioInputDeviceProvider.SECONDARY_DEVICE)
        h.dispatchers.scheduler.advanceUntilIdle()

        events = h.timelineRepository.readEventLog(folderName).events
        assertTrue(events.any { it.type == TimelineEventType.RECORDING_RESUMED })

        val examination = h.sessionRepository.readExamination(folderName)!!
        assertEquals(1, examination.interruptions.size)
        assertEquals("secondary", examination.interruptions.single().newDevice)
        assertEquals("session_master.part2.wav", examination.interruptions.single().partFile)
    }
}
