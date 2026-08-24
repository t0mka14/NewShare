package org.example.app.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.config.VideoTask
import org.example.app.domain.session.TaskRecord
import org.example.app.domain.timeline.TaskInstance
import org.example.app.domain.timeline.TimelineEventType
import org.example.app.domain.video.PtzAction
import org.example.app.domain.video.VideoError
import org.example.app.domain.video.VideoRecorderState
import org.example.app.fakes.FakeAudioPlaybackService
import org.example.app.fakes.FakePtzController
import org.example.app.fakes.TestCoroutineDispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * VIDEO shares VOCAL's Start/Stop/Repeat state machine, but differs in one structural way: the
 * camera is per-take rather than continuous, so this component has to ask the session to open
 * and close a file. It still never touches the hardware itself (§5.2 single-writer) — these
 * tests pin that boundary along with the state machine.
 */
class VideoTaskComponentTest {

    private data class LoggedEvent(val type: TimelineEventType, val take: Int?, val reason: String?)

    private class Harness(
        task: VideoTask,
        ptz: FakePtzController = FakePtzController(isAvailable = false),
    ) {
        val dispatchers = TestCoroutineDispatchers()
        val events = mutableListOf<LoggedEvent>()
        val finished = mutableListOf<TaskRecord>()
        val takeStarts = mutableListOf<Int>()
        var takeStops = 0
        val frames = MutableStateFlow<ByteArray?>(null)
        val videoState = MutableStateFlow<VideoRecorderState>(VideoRecorderState.Previewing)
        val ptzController = ptz

        val component: TaskComponent = DefaultTaskComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            taskInstance = TaskInstance(taskIndex = 2, repetition = 1, task = task),
            recorder = null,
            dispatchers = dispatchers,
            positionInProtocol = 3,
            totalInstanceCount = 5,
            availableDevices = listOf(AudioInputDevice(id = "d", name = "Mic", eligible = true)),
            currentDevice = null,
            resolvedAudioExamplePath = null,
            audioPlaybackService = FakeAudioPlaybackService(),
            videoFrames = frames,
            videoState = videoState,
            ptzController = ptzController,
            onVideoTakeStarted = { take -> takeStarts.add(take) },
            onVideoTakeStopped = { takeStops++ },
            eventLogger = { type, take, reason -> events += LoggedEvent(type, take, reason) },
            nextPartFile = { Path.of("unused.wav") },
            onResumeRecorded = { _, _ -> },
            onTaskFinished = { record -> finished += record },
        )

        val content: TaskComponent.Content.Video
            get() = component.state.value.content as TaskComponent.Content.Video
    }

    private val videoTask = VideoTask(
        titleKey = "emotions.title",
        subtype = "EMOTIONS",
        instructionKeys = listOf("emotions.instructions"),
        length = 30,
        canRepeat = true,
        canSkip = true,
    )

    @Test
    fun `a VIDEO task renders video content and carries its instructions and length`() {
        val harness = Harness(videoTask)

        assertEquals(listOf("emotions.instructions"), harness.component.state.value.instructionKeys)
        assertEquals(30, harness.component.state.value.taskLengthSeconds)
        assertEquals(TaskScreenState.Idle, harness.content.screenState)
    }

    @Test
    fun `start asks the session to open a file and stop asks it to close one`() {
        val harness = Harness(videoTask)

        harness.component.onStart()

        assertEquals(listOf(1), harness.takeStarts, "the component asks; it never opens the file itself")
        assertEquals(TaskScreenState.Capturing, harness.content.screenState)
        assertEquals(1, harness.content.takeNumber)

        harness.component.onStop()

        assertEquals(1, harness.takeStops)
        assertEquals(TaskScreenState.Stopped, harness.content.screenState)
        assertEquals(
            listOf(TimelineEventType.START_BUTTON_PRESSED, TimelineEventType.STOP_BUTTON_PRESSED),
            harness.events.map { it.type }.filter { it != TimelineEventType.TASK_SCREEN_ENTERED },
        )
    }

    @Test
    fun `repeat rejects the take and opens a new one`() {
        val harness = Harness(videoTask)
        harness.component.onStart()
        harness.component.onStop()

        harness.component.onRepeat()

        assertEquals(listOf(1, 2), harness.takeStarts)
        assertEquals(2, harness.content.takeNumber)
        assertTrue(harness.events.any { it.type == TimelineEventType.TAKE_REJECTED })
    }

    @Test
    fun `next records a VIDEO task with its take count`() {
        val harness = Harness(videoTask)
        harness.component.onStart()
        harness.component.onStop()

        harness.component.onNext()

        assertEquals(1, harness.finished.size)
        val record = harness.finished.single()
        assertEquals("VIDEO", record.type)
        assertEquals("EMOTIONS", record.subtype)
        assertEquals(1, record.takes)
        assertFalse(record.skipped)
    }

    @Test
    fun `next is refused while a take is still open`() {
        val harness = Harness(videoTask)
        harness.component.onStart()

        harness.component.onNext()

        assertEquals(emptyList<TaskRecord>(), harness.finished)
    }

    /**
     * If ffmpeg dies mid-take there is no recording to stop, so leaving the screen `Capturing`
     * would show a Stop button that implies one exists. The take is rejected and the error is
     * carried on the content for the screen to show inline.
     */
    @Test
    fun `a capture failure during a take rejects it and surfaces the error`() {
        val harness = Harness(videoTask)
        harness.component.onStart()

        harness.videoState.value = VideoRecorderState.Failed(VideoError.CaptureInterrupted("ffmpeg exited"))

        assertEquals(TaskScreenState.Idle, harness.content.screenState)
        assertEquals(VideoError.CaptureInterrupted("ffmpeg exited"), harness.content.error)
        assertEquals(1, harness.takeStops, "the session is told to close the file it opened")
        assertTrue(harness.events.any { it.type == TimelineEventType.TAKE_REJECTED && it.reason == "CAPTURE_FAILED" })
    }

    @Test
    fun `skip records the task as skipped`() {
        val harness = Harness(videoTask)

        harness.component.onSkip()

        assertEquals(1, harness.finished.size)
        assertEquals("VIDEO", harness.finished.single().type)
        assertTrue(harness.finished.single().skipped)
    }

    // region PTZ

    @Test
    fun `PTZ is unavailable and inert when the host has no backend`() {
        val harness = Harness(videoTask.copy(havePTZ = true), ptz = FakePtzController(isAvailable = false))

        assertFalse(harness.content.ptzAvailable, "havePTZ must not produce controls without a backend")

        harness.component.onPtz(PtzAction.PanLeft)

        assertEquals(emptyList<PtzAction>(), harness.ptzController.actions)
    }

    @Test
    fun `PTZ is unavailable when the backend exists but the task did not ask for it`() {
        val harness = Harness(videoTask.copy(havePTZ = false), ptz = FakePtzController(isAvailable = true))

        assertFalse(harness.content.ptzAvailable)

        harness.component.onPtz(PtzAction.ZoomIn)

        assertEquals(emptyList<PtzAction>(), harness.ptzController.actions)
    }

    @Test
    fun `PTZ actions reach the controller when both the backend and the task allow it`() {
        val harness = Harness(videoTask.copy(havePTZ = true), ptz = FakePtzController(isAvailable = true))

        assertTrue(harness.content.ptzAvailable)

        harness.component.onPtz(PtzAction.PanLeft)
        harness.component.onPtz(PtzAction.Stop)

        assertEquals(listOf(PtzAction.PanLeft, PtzAction.Stop), harness.ptzController.actions)
    }

    // endregion

    /**
     * A protocol may name a VIDEO task on a machine with no camera attached. `SessionComponent`
     * passes a failed state in that case; the screen must say so and refuse to open a take
     * rather than count one that recorded nothing.
     */
    @Test
    fun `start is refused when no camera is available`() {
        val harness = Harness(videoTask)
        harness.videoState.value = VideoRecorderState.Failed(VideoError.DeviceUnavailable("none"))

        assertFalse(harness.component.state.value.buttons.startEnabled)
        assertEquals(VideoError.DeviceUnavailable("none"), harness.content.error)

        harness.component.onStart()

        assertEquals(emptyList<Int>(), harness.takeStarts)
        assertEquals(0, harness.content.takeNumber)
    }

    @Test
    fun `start is available again once capture recovers`() {
        val harness = Harness(videoTask)
        harness.videoState.value = VideoRecorderState.Failed(VideoError.DeviceUnavailable("none"))
        harness.videoState.value = VideoRecorderState.Previewing

        assertTrue(harness.component.state.value.buttons.startEnabled)
        assertEquals(null, harness.content.error)
    }
    // region camera startup

    /**
     * Entering the screen is what opens the camera now, so there is a real window — potentially
     * seconds, since mode negotiation may walk several rungs — where the state is Idle. Start
     * must not be offered during it, or the take counter would advance over nothing.
     */
    @Test
    fun `start is refused while the camera is still opening`() {
        val harness = Harness(videoTask)
        harness.videoState.value = VideoRecorderState.Idle

        assertFalse(harness.content.ready)
        assertFalse(harness.component.state.value.buttons.startEnabled)
        assertEquals(null, harness.content.error, "still opening is not an error")

        harness.component.onStart()

        assertEquals(emptyList<Int>(), harness.takeStarts)
    }

    @Test
    fun `start becomes available once the camera reaches preview`() {
        val harness = Harness(videoTask)
        harness.videoState.value = VideoRecorderState.Idle
        harness.videoState.value = VideoRecorderState.Previewing

        assertTrue(harness.content.ready)
        assertTrue(harness.component.state.value.buttons.startEnabled)

        harness.component.onStart()

        assertEquals(listOf(1), harness.takeStarts)
    }

    /** Releasing the camera on the way out must not read as a take-able state. */
    @Test
    fun `a stopped camera is not ready`() {
        val harness = Harness(videoTask)
        harness.videoState.value = VideoRecorderState.Stopped

        assertFalse(harness.content.ready)
        assertFalse(harness.component.state.value.buttons.startEnabled)
    }

    // endregion
}