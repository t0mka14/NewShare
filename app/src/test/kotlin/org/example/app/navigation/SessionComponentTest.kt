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
import org.example.app.domain.config.VideoTask
import org.example.app.domain.config.VocalTask
import org.example.app.domain.session.StartSessionUseCase
import org.example.app.domain.session.StorageError
import org.example.app.domain.video.VideoCaptureFormat
import org.example.app.domain.timeline.TimelineEventType
import org.example.app.fakes.FakeAudioInputDeviceProvider
import org.example.app.fakes.FakeAudioPlaybackService
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeContinuousSessionRecorder
import org.example.app.fakes.FakeDiskSpaceProvider
import org.example.app.fakes.FakeIdGenerator
import org.example.app.fakes.FakePtzController
import org.example.app.fakes.FakeSessionRepository
import org.example.app.fakes.FakeSessionVideoRecorder
import org.example.app.fakes.FakeVideoInputDeviceProvider
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

    /** A VOCAL screen precedes the VIDEO one, so "camera off until entered" is observable. */
    private val vocalThenVideoProtocol = Protocol(
        name = "VocalThenVideo",
        recordingsFileName = "\${patientCode}_\${taskIndex}.wav",
        tasks = listOf(
            CalibrationTask(titleKey = "calib", optimalLoudness = listOf(0.2, 0.8)),
            VocalTask(titleKey = "vocal", subtype = VocalSubtype.PHONATION),
            VideoTask(titleKey = "video"),
            InfoTask(titleKey = "info"),
        ),
    )

    /** `nrepetition = 2` gives two consecutive VIDEO screens — the counter's reason to exist. */
    private val repeatedVideoProtocol = Protocol(
        name = "RepeatedVideo",
        recordingsFileName = "\${patientCode}_\${taskIndex}.wav",
        tasks = listOf(VideoTask(titleKey = "video", nrepetition = 2), InfoTask(titleKey = "info")),
    )

    /** `canRepeat`, so a take can be rejected and re-taken — which is what produces a take02 file. */
    private val repeatableVideoProtocol = Protocol(
        name = "RepeatableVideo",
        recordingsFileName = "\${patientCode}_\${taskIndex}.wav",
        tasks = listOf(VideoTask(titleKey = "video", canRepeat = true), InfoTask(titleKey = "info")),
    )

    private inner class Harness(
        withCamera: Boolean = false,
        cameras: List<org.example.app.domain.video.VideoInputDevice>? = null,
        private val savedCameraDeviceId: String? = null,
        /** Applied at construction: the recorder is created during bootstrap, so a test cannot
         *  reach in and set this afterwards. */
        private val negotiatedVideoFormat: VideoCaptureFormat = VideoCaptureFormat.PREFERRED,
    ) {
        val videoDeviceProvider = FakeVideoInputDeviceProvider(
            cameras ?: if (withCamera) listOf(FakeVideoInputDeviceProvider.DEFAULT_CAMERA) else emptyList(),
        )

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
        var videoRecorder: FakeSessionVideoRecorder? = null
        val ptzControllers = mutableListOf<FakePtzController>()
        val startSessionUseCase = StartSessionUseCase(
            directories = directories,
            sessionRepository = sessionRepository,
            diskSpaceProvider = diskSpaceProvider,
            idGenerator = idGenerator,
            clock = clock,
            clinicZone = ZoneOffset.UTC,
        )
        val audioPlaybackService = FakeAudioPlaybackService()
        var ended = 0
        var endedFolderName: String? = null

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
            videoInputDeviceProvider = videoDeviceProvider,
            savedCameraDeviceId = savedCameraDeviceId,
            videoRecorderFactory = {
                FakeSessionVideoRecorder()
                    .also { it.negotiatedFormat = negotiatedVideoFormat }
                    .also { videoRecorder = it }
            },
            ptzControllerFactory = { FakePtzController().also { ptzControllers += it } },
            recorderFactory = recorderFactory,
            startSessionUseCase = startSessionUseCase,
            sessionRepository = sessionRepository,
            timelineRepository = timelineRepository,
            clock = clock,
            dispatchers = dispatchers,
            directories = directories,
            audioPlaybackService = audioPlaybackService,
            onSessionEnded = { folderName -> ended++; endedFolderName = folderName },
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
    // region camera lifecycle

    /**
     * The camera used to be opened once at session bootstrap and held for the whole
     * examination, which left the device's indicator light on through every unrelated task and
     * put a continuous UVC load on the bus shared with the USB microphone. Nothing asserted
     * that it was ever opened, which is how that shipped — these cases are the guard.
     */
    /**
     * Listing cameras spawns ffmpeg and waits for it, so a protocol with no VIDEO task must not
     * pay for it — and it must not happen in `RootComponent.buildSessionChild` either, which is a
     * Decompose `childFactory` and therefore runs on the Swing EDT. Bootstrap is the one place that
     * is both off the EDT and finished before any task screen can exist.
     */
    @Test
    fun `a protocol with no VIDEO task never lists cameras`() {
        val h = Harness(withCamera = true)
        h.build(vocalProtocol)
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals(0, h.videoDeviceProvider.enumerationCount)
    }

    /** One process per session, not one per VIDEO screen. */
    @Test
    fun `cameras are listed exactly once, during bootstrap`() {
        val h = Harness(withCamera = true)
        val component = h.build(vocalThenVideoProtocol)
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals(1, h.videoDeviceProvider.enumerationCount, "listed during bootstrap")

        val calibration = (component.stack.value.active.instance as SessionComponent.Child.Calibration).component
        calibration.onConfirm()
        h.dispatchers.scheduler.advanceUntilIdle()
        var task = (component.stack.value.active.instance as SessionComponent.Child.TaskScreen).component
        task.onStart(); task.onStop(); task.onNext()
        h.dispatchers.scheduler.advanceUntilIdle()
        task = (component.stack.value.active.instance as SessionComponent.Child.TaskScreen).component
        task.onStart(); task.onStop(); task.onNext()
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals(1, h.videoDeviceProvider.enumerationCount, "and not again per VIDEO screen")
    }

    /** Camera selection lives here now, so the saved choice has to be honoured here. */
    @Test
    fun `the saved camera wins over the first eligible one`() {
        val first = FakeVideoInputDeviceProvider.DEFAULT_CAMERA
        val saved = first.copy(id = "saved-camera", name = "Saved Camera", platformIndex = 1)
        val h = Harness(cameras = listOf(first, saved), savedCameraDeviceId = "saved-camera")
        h.build(repeatedVideoProtocol)
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals(listOf(saved), h.videoRecorder!!.previewStarts)
    }

    /** An unknown saved id — a camera that was unplugged — must not strand the session. */
    @Test
    fun `an unrecognised saved camera falls back to the first eligible one`() {
        val h = Harness(withCamera = true, savedCameraDeviceId = "a-camera-that-is-gone")
        h.build(repeatedVideoProtocol)
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals(listOf(FakeVideoInputDeviceProvider.DEFAULT_CAMERA), h.videoRecorder!!.previewStarts)
    }

    @Test
    fun `the camera stays closed until a VIDEO task screen is entered`() {
        val h = Harness(withCamera = true)
        val component = h.build(vocalThenVideoProtocol)
        h.dispatchers.scheduler.advanceUntilIdle()

        val calibration = (component.stack.value.active.instance as SessionComponent.Child.Calibration).component
        calibration.onConfirm()
        h.dispatchers.scheduler.advanceUntilIdle()

        // On the VOCAL screen: the recorder object exists, but no camera has been opened.
        assertNotNull(h.videoRecorder)
        assertEquals(emptyList<Any>(), h.videoRecorder!!.previewStarts)

        var task = (component.stack.value.active.instance as SessionComponent.Child.TaskScreen).component
        task.onStart()
        task.onStop()
        task.onNext()
        h.dispatchers.scheduler.advanceUntilIdle()

        // Now on the VIDEO screen.
        assertEquals(1, h.videoRecorder!!.previewStarts.size, "entering the VIDEO screen opens the camera")
        assertEquals(0, h.videoRecorder!!.stopCallCount)

        task = (component.stack.value.active.instance as SessionComponent.Child.TaskScreen).component
        task.onStart()
        task.onStop()
        task.onNext()
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals(1, h.videoRecorder!!.stopCallCount, "leaving the VIDEO screen closes the camera")
    }

    /**
     * A VIDEO task with `nrepetition = 2` produces two consecutive VIDEO screens. Whether
     * Decompose creates the incoming child before destroying the outgoing one is not something
     * this code controls, so the screen count — not a boolean — decides when to open and close.
     * Either ordering must leave the camera open exactly once across the pair.
     */
    @Test
    fun `consecutive VIDEO screens do not reopen the camera`() {
        val h = Harness(withCamera = true)
        val component = h.build(repeatedVideoProtocol)
        h.dispatchers.scheduler.advanceUntilIdle()

        var task = (component.stack.value.active.instance as SessionComponent.Child.TaskScreen).component
        assertEquals(1, h.videoRecorder!!.previewStarts.size)

        task.onStart()
        task.onStop()
        task.onNext()
        h.dispatchers.scheduler.advanceUntilIdle()

        // Second repetition: still a VIDEO screen, so the camera must not have been cycled.
        task = (component.stack.value.active.instance as SessionComponent.Child.TaskScreen).component
        assertEquals(1, h.videoRecorder!!.previewStarts.size, "the camera should not be reopened between takes")
        assertEquals(0, h.videoRecorder!!.stopCallCount, "the camera should not be closed between takes")

        task.onStart()
        task.onStop()
        task.onNext()
        h.dispatchers.scheduler.advanceUntilIdle()

        // Now on the INFO screen: the last VIDEO screen is gone, so the camera is released.
        assertEquals(1, h.videoRecorder!!.stopCallCount)
    }

    @Test
    fun `a protocol with no VIDEO task never creates a camera recorder`() {
        val h = Harness(withCamera = true)
        h.build(mixedProtocol)
        h.dispatchers.scheduler.advanceUntilIdle()

        assertNull(h.videoRecorder)
    }

    /** PTZ handles are per-screen too, so nothing holds a COM device across the session. */
    @Test
    fun `the PTZ controller is created per VIDEO screen and closed on leaving`() {
        val h = Harness(withCamera = true)
        val component = h.build(repeatedVideoProtocol)
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals(1, h.ptzControllers.size)

        val task = (component.stack.value.active.instance as SessionComponent.Child.TaskScreen).component
        task.onStart()
        task.onStop()
        task.onNext()
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals(1, h.ptzControllers.first().closeCallCount, "the first screen's controller is released")
        assertEquals(2, h.ptzControllers.size, "the second VIDEO screen gets its own controller")
    }

    /** A take must not be opened while the camera is still starting up. */
    @Test
    fun `no video file is written while the camera is still opening`() {
        val h = Harness(withCamera = true)
        val component = h.build(repeatedVideoProtocol)
        h.dispatchers.scheduler.advanceUntilIdle()

        val task = (component.stack.value.active.instance as SessionComponent.Child.TaskScreen).component
        val content = task.state.value.content as TaskComponent.Content.Video
        assertTrue(content.ready, "the fake reaches Previewing immediately")

        // Drop back to a not-ready state and confirm Start is refused.
        h.videoRecorder!!.simulateFailure(org.example.app.domain.video.VideoError.CaptureInterrupted("gone"))
        h.dispatchers.scheduler.advanceUntilIdle()

        task.onStart()
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals(emptyList<Any>(), h.videoRecorder!!.recordingStarts)
    }

    /**
     * A take is a bare MJPEG elementary stream, so it carries no timestamps and nothing else in the
     * archive says what rate it plays back at. `examination.json` has to.
     */
    @Test
    fun `a video take records its file and frame rate`() {
        val h = Harness(withCamera = true)
        val component = h.build(repeatedVideoProtocol)
        h.dispatchers.scheduler.advanceUntilIdle()

        val task = (component.stack.value.active.instance as SessionComponent.Child.TaskScreen).component
        task.onStart()
        h.dispatchers.scheduler.advanceUntilIdle()

        val takes = h.sessionRepository.examinationWrites.last().videoTakes
        assertEquals(1, takes.size, "one take started, one record")
        assertEquals("video/task00_rep01_take01.mjpeg", takes[0].file)
        assertEquals(0, takes[0].taskIndex)
        assertEquals(1, takes[0].repetition)
        assertEquals(1, takes[0].take)
        assertEquals(VideoCaptureFormat.PREFERRED, takes[0].captureFormat)
        // The path in the record must name the file that was actually opened.
        assertTrue(h.videoRecorder!!.recordingStarts.single().toString().endsWith(takes[0].file))
    }

    /** A rejected take is still a file on disk, so it stays listed; the timeline says which counts. */
    @Test
    fun `a repeated take appends a second record rather than replacing the first`() {
        val h = Harness(withCamera = true)
        val component = h.build(repeatableVideoProtocol)
        h.dispatchers.scheduler.advanceUntilIdle()

        val task = (component.stack.value.active.instance as SessionComponent.Child.TaskScreen).component
        task.onStart()
        task.onStop()
        task.onRepeat()
        h.dispatchers.scheduler.advanceUntilIdle()

        val takes = h.sessionRepository.examinationWrites.last().videoTakes
        assertEquals(
            listOf("video/task00_rep01_take01.mjpeg", "video/task00_rep01_take02.mjpeg"),
            takes.map { it.file },
        )
        assertEquals(listOf(1, 2), takes.map { it.take })
    }

    /** Not every camera is 1080p30, and the recorded rate has to be the one that was used. */
    @Test
    fun `the recorded format is whatever the camera negotiated`() {
        val h = Harness(withCamera = true, negotiatedVideoFormat = VideoCaptureFormat(1280, 720, 15))
        val component = h.build(repeatedVideoProtocol)
        h.dispatchers.scheduler.advanceUntilIdle()

        val task = (component.stack.value.active.instance as SessionComponent.Child.TaskScreen).component
        task.onStart()
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals(
            VideoCaptureFormat(1280, 720, 15),
            h.sessionRepository.examinationWrites.last().videoTakes.single().captureFormat,
        )
    }

    /** No VIDEO task, nothing to describe — the list must not gain speculative entries. */
    @Test
    fun `a protocol with no VIDEO task records no video takes`() {
        val h = Harness(withCamera = true)
        val component = h.build(vocalProtocol)
        h.dispatchers.scheduler.advanceUntilIdle()
        val calibration = (component.stack.value.active.instance as SessionComponent.Child.Calibration).component
        calibration.onConfirm()
        h.dispatchers.scheduler.advanceUntilIdle()

        val task = (component.stack.value.active.instance as SessionComponent.Child.TaskScreen).component
        task.onStart()
        task.onStop()
        h.dispatchers.scheduler.advanceUntilIdle()

        assertTrue(h.sessionRepository.examinationWrites.all { it.videoTakes.isEmpty() })
    }

    // endregion
}