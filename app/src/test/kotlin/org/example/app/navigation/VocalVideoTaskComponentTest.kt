package org.example.app.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.audio.InterruptionReason
import org.example.app.domain.config.VocalSubtype
import org.example.app.domain.config.VocalTask
import org.example.app.domain.session.TaskRecord
import org.example.app.domain.timeline.TaskInstance
import org.example.app.domain.timeline.TimelineEventType
import org.example.app.domain.video.VideoError
import org.example.app.domain.video.VideoRecorderState
import org.example.app.fakes.FakeAudioPlaybackService
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeContinuousSessionRecorder
import org.example.app.fakes.TestCoroutineDispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A VOCAL task with `recordVideo` films each take, but audio is what it is for: Start waits
 * for a camera that is still opening, yet a missing, failed or slow camera falls back to audio
 * only rather than holding the examination up, and a camera failure never costs the audio take.
 */
class VocalVideoTaskComponentTest {

    private data class LoggedEvent(val type: TimelineEventType, val take: Int?, val reason: String?)

    private class Harness(
        task: VocalTask,
        initialVideoState: VideoRecorderState = VideoRecorderState.Previewing,
    ) {
        val clock = FakeClock()
        val dispatchers = TestCoroutineDispatchers()
        val recorder = FakeContinuousSessionRecorder(clock)
        val events = mutableListOf<LoggedEvent>()
        val finished = mutableListOf<TaskRecord>()
        val takeStarts = mutableListOf<Int>()
        var takeStops = 0
        val videoState = MutableStateFlow(initialVideoState)

        init {
            kotlinx.coroutines.runBlocking { recorder.startWriting(Path.of("master.wav")) }
        }

        val component: TaskComponent = DefaultTaskComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            taskInstance = TaskInstance(taskIndex = 1, repetition = 1, task = task),
            recorder = recorder,
            dispatchers = dispatchers,
            positionInProtocol = 2,
            totalInstanceCount = 3,
            availableDevices = listOf(AudioInputDevice(id = "d", name = "Mic", eligible = true)),
            currentDevice = null,
            resolvedAudioExamplePath = null,
            audioPlaybackService = FakeAudioPlaybackService(),
            videoFrames = MutableStateFlow(null),
            videoState = videoState,
            onVideoTakeStarted = { take -> takeStarts.add(take) },
            onVideoTakeStopped = { takeStops++ },
            eventLogger = { type, take, reason -> events += LoggedEvent(type, take, reason) },
            nextPartFile = { Path.of("unused.wav") },
            onResumeRecorded = { _, _ -> },
            onTaskFinished = { record -> finished += record },
        )

        val content: TaskComponent.Content.Vocal
            get() = component.state.value.content as TaskComponent.Content.Vocal
        val startEnabled: Boolean
            get() = component.state.value.buttons.startEnabled
    }

    private val filmingTask = VocalTask(
        titleKey = "monologue.title",
        subtype = VocalSubtype.MONOLOGUE,
        canRepeat = true,
        recordVideo = true,
    )

    @Test
    fun `the preview replaces the indicator only when the task asks for video`() {
        assertNotNull(Harness(filmingTask).content.video)
        assertNull(Harness(filmingTask.copy(recordVideo = false)).content.video)
    }

    @Test
    fun `start and stop film the take alongside the audio`() {
        val h = Harness(filmingTask)

        h.component.onStart()
        assertEquals(listOf(1), h.takeStarts)
        assertEquals(TaskScreenState.Capturing, h.content.screenState)

        h.component.onStop()
        assertEquals(1, h.takeStops)
        assertEquals(
            listOf(TimelineEventType.START_BUTTON_PRESSED, TimelineEventType.STOP_BUTTON_PRESSED),
            h.events.map { it.type }.filter { it != TimelineEventType.TASK_SCREEN_ENTERED },
        )
    }

    @Test
    fun `repeat films the new take too`() {
        val h = Harness(filmingTask)
        h.component.onStart()
        h.component.onStop()

        h.component.onRepeat()

        assertEquals(listOf(1, 2), h.takeStarts)
    }

    // region waiting for the camera

    @Test
    fun `start is refused while the camera is still opening`() {
        val h = Harness(filmingTask, initialVideoState = VideoRecorderState.Idle)

        assertFalse(h.startEnabled)
        assertFalse(h.content.video!!.audioOnly)

        h.component.onStart()

        assertEquals(0, h.content.takeNumber)
        assertEquals(emptyList<Int>(), h.takeStarts)
    }

    @Test
    fun `start becomes available as soon as the camera previews`() {
        val h = Harness(filmingTask, initialVideoState = VideoRecorderState.Idle)

        h.videoState.value = VideoRecorderState.Previewing

        assertTrue(h.startEnabled)
        assertFalse(h.content.video!!.audioOnly)
    }

    @Test
    fun `a camera that never comes up falls back to audio only after the wait`() {
        val h = Harness(filmingTask, initialVideoState = VideoRecorderState.Idle)

        h.dispatchers.scheduler.advanceTimeBy(9.seconds + 900.milliseconds)
        h.dispatchers.scheduler.runCurrent()
        assertFalse(h.startEnabled, "still within the wait")

        h.dispatchers.scheduler.advanceTimeBy(200.milliseconds)
        h.dispatchers.scheduler.runCurrent()
        assertTrue(h.startEnabled)
        assertTrue(h.content.video!!.audioOnly)

        h.component.onStart()
        assertEquals(TaskScreenState.Capturing, h.content.screenState)
    }

    /** The session films only a previewing camera, so a late one is picked up by the next take. */
    @Test
    fun `a camera that comes up after the wait films the next take`() {
        val h = Harness(filmingTask, initialVideoState = VideoRecorderState.Idle)
        h.dispatchers.scheduler.advanceUntilIdle()
        h.videoState.value = VideoRecorderState.Previewing

        h.component.onStart()

        assertTrue(h.content.video!!.audioOnly, "the fallback is not taken back")
        assertEquals(listOf(1), h.takeStarts)
    }

    @Test
    fun `no camera at all allows start at once`() {
        val h = Harness(
            filmingTask,
            initialVideoState = VideoRecorderState.Failed(VideoError.DeviceUnavailable("none")),
        )

        assertTrue(h.startEnabled)
        assertTrue(h.content.video!!.audioOnly)
        assertEquals(VideoError.DeviceUnavailable("none"), h.content.video!!.error)
    }

    @Test
    fun `a failed camera allows start`() {
        val h = Harness(filmingTask, initialVideoState = VideoRecorderState.Idle)

        h.videoState.value = VideoRecorderState.Failed(VideoError.CaptureStartFailed("no mode"))

        assertTrue(h.startEnabled)
        assertTrue(h.content.video!!.audioOnly)
    }

    // endregion

    /** Unlike a VIDEO task, the audio is the recording here, so it survives the camera. */
    @Test
    fun `a camera failure mid-take stops the video but keeps the audio take`() {
        val h = Harness(filmingTask)
        h.component.onStart()

        h.videoState.value = VideoRecorderState.Failed(VideoError.CaptureInterrupted("ffmpeg exited"))

        assertEquals(TaskScreenState.Capturing, h.content.screenState)
        assertEquals(1, h.takeStops)
        assertEquals(0, h.events.count { it.type == TimelineEventType.TAKE_REJECTED })
        assertEquals(VideoError.CaptureInterrupted("ffmpeg exited"), h.content.video!!.error)

        h.component.onStop()
        h.component.onNext()
        assertEquals(1, h.finished.single().takes)
    }

    @Test
    fun `losing the microphone mid-take stops the video with the rejected take`() {
        val h = Harness(filmingTask)
        h.component.onStart()

        h.recorder.simulateInterruption(InterruptionReason.DEVICE_LOST)
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals(TaskScreenState.Idle, h.content.screenState)
        assertEquals(1, h.takeStops)
    }

    @Test
    fun `the task is recorded as VOCAL`() {
        val h = Harness(filmingTask)
        h.component.onStart()
        h.component.onStop()
        h.component.onNext()

        assertEquals("VOCAL", h.finished.single().type)
        assertEquals("MONOLOGUE", h.finished.single().subtype)
    }
}
