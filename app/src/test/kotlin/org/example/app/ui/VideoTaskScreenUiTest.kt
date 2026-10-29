package org.example.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import androidx.compose.ui.test.runComposeUiTest
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.config.Task
import org.example.app.domain.config.VideoTask
import org.example.app.domain.config.VocalSubtype
import org.example.app.domain.config.VocalTask
import org.example.app.domain.localization.LocalizedStringProvider
import org.example.app.domain.timeline.TaskInstance
import org.example.app.domain.video.VideoError
import org.example.app.domain.video.VideoRecorderState
import org.example.app.fakes.FakeAudioPlaybackService
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeContinuousSessionRecorder
import org.example.app.fakes.TestCoroutineDispatchers
import org.example.app.navigation.DefaultTaskComponent
import org.example.app.navigation.TaskComponent
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** Which middle-area widget and bottom-row button the filming task screens render. */
@OptIn(ExperimentalTestApi::class)
class VideoTaskScreenUiTest {

    private fun component(task: Task, videoState: VideoRecorderState): TaskComponent {
        val recorder = FakeContinuousSessionRecorder(FakeClock())
        kotlinx.coroutines.runBlocking { recorder.startWriting(Path.of("master.wav")) }
        val device = AudioInputDevice(id = "default", name = "Default Mic", eligible = true)
        return DefaultTaskComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            taskInstance = TaskInstance(taskIndex = 0, repetition = 1, task = task),
            recorder = recorder,
            dispatchers = TestCoroutineDispatchers(),
            positionInProtocol = 1,
            totalInstanceCount = 1,
            availableDevices = listOf(device),
            currentDevice = device,
            resolvedAudioExamplePath = null,
            audioPlaybackService = FakeAudioPlaybackService(),
            eventLogger = { _, _, _ -> },
            videoFrames = MutableStateFlow(null),
            videoState = MutableStateFlow(videoState),
            nextPartFile = { Path.of("part2.wav") },
            onResumeRecorded = { _, _ -> },
            onTaskFinished = {},
        )
    }

    private val localization = UiLocalization(LocalizedStringProvider(), "en", null)

    @Test
    fun `a filming VOCAL task shows the small camera preview beside the level indicator`() = runComposeUiTest {
        val task = VocalTask(titleKey = "t", subtype = VocalSubtype.MONOLOGUE, recordVideo = true)
        setContent { TaskContent(component(task, VideoRecorderState.Previewing), localization) }
        waitForIdle()

        onNodeWithTag(TestTags.Task.VIDEO_PREVIEW).assertIsDisplayed()
        onNodeWithTag(TestTags.Task.LEVEL_INDICATOR).assertExists()
        onNodeWithTag(TestTags.Task.START_BUTTON).assertIsDisplayed()
    }

    @Test
    fun `a filming VOCAL task without showIndicator shows the preview alone`() = runComposeUiTest {
        val task = VocalTask(titleKey = "t", subtype = VocalSubtype.MONOLOGUE, recordVideo = true, showIndicator = false)
        setContent { TaskContent(component(task, VideoRecorderState.Previewing), localization) }
        waitForIdle()

        onNodeWithTag(TestTags.Task.VIDEO_PREVIEW).assertIsDisplayed()
        onNodeWithTag(TestTags.Task.LEVEL_INDICATOR).assertDoesNotExist()
    }

    @Test
    fun `a filming VOCAL task without a camera says it records audio only`() = runComposeUiTest {
        val task = VocalTask(titleKey = "t", subtype = VocalSubtype.MONOLOGUE, recordVideo = true)
        val noCamera = VideoRecorderState.Failed(VideoError.DeviceUnavailable("none"))
        setContent { TaskContent(component(task, noCamera), localization) }
        waitForIdle()

        onNodeWithTag(TestTags.Task.VIDEO_AUDIO_ONLY).assertIsDisplayed()
    }

    private val czech = UiLocalization(LocalizedStringProvider(), "cs", null)

    /** F-132: every camera line follows the selected language. */
    private fun cameraLine(task: Task, state: VideoRecorderState, loc: UiLocalization, tag: String, expected: String) =
        runComposeUiTest {
            setContent { TaskContent(component(task, state), loc) }
            waitForIdle()
            onNodeWithTag(tag).assertIsDisplayed()
            onNodeWithTag(tag).assert(androidx.compose.ui.test.hasText(expected))
        }

    private val vocal = VocalTask(titleKey = "t", subtype = VocalSubtype.MONOLOGUE, recordVideo = true)

    @Test
    fun `F-132 starting camera text is Czech in cs and English in en`() {
        cameraLine(vocal, VideoRecorderState.Idle, czech, TestTags.Task.VIDEO_STARTING, "Spouštím kameru…")
        cameraLine(vocal, VideoRecorderState.Idle, localization, TestTags.Task.VIDEO_STARTING, "Starting camera…")
    }

    @Test
    fun `F-132 audio-only text is Czech in cs and English in en`() {
        val noCamera = VideoRecorderState.Failed(VideoError.DeviceUnavailable("none"))
        cameraLine(vocal, noCamera, czech, TestTags.Task.VIDEO_AUDIO_ONLY, "Kamera není k dispozici — nahrává se pouze zvuk.")
        cameraLine(vocal, noCamera, localization, TestTags.Task.VIDEO_AUDIO_ONLY, "Camera not available — recording audio only.")
    }

    @Test
    fun `F-132 camera lost text is Czech in cs and English in en`() {
        val lost = VideoRecorderState.Failed(VideoError.CaptureInterrupted("x"))
        cameraLine(vocal, lost, czech, TestTags.Task.VIDEO_AUDIO_ONLY, "Kamera byla ztracena — nahrává se pouze zvuk.")
        cameraLine(vocal, lost, localization, TestTags.Task.VIDEO_AUDIO_ONLY, "Camera lost — recording audio only.")
    }

    @Test
    fun `F-132 a VIDEO task camera error is Czech in cs and English in en`() {
        val failed = VideoRecorderState.Failed(VideoError.CaptureStartFailed("x"))
        val task = VideoTask(titleKey = "v")
        cameraLine(task, failed, czech, TestTags.Task.VIDEO_ERROR, "Kameru se nepodařilo spustit.")
        cameraLine(task, failed, localization, TestTags.Task.VIDEO_ERROR, "The camera could not be started.")
    }

    @Test
    fun `a VIDEO task shows the start button`() = runComposeUiTest {
        setContent { TaskContent(component(VideoTask(titleKey = "v"), VideoRecorderState.Previewing), localization) }
        waitForIdle()

        onNodeWithTag(TestTags.Task.START_BUTTON).assertIsDisplayed()
    }

    private fun jpeg(): ByteArray {
        val bitmap = org.jetbrains.skia.Bitmap().apply {
            allocPixels(org.jetbrains.skia.ImageInfo.makeN32(64, 48, org.jetbrains.skia.ColorAlphaType.OPAQUE))
            erase(0xFFFF0000.toInt())
        }
        return org.jetbrains.skia.Image.makeFromBitmap(bitmap)
            .encodeToData(org.jetbrains.skia.EncodedImageFormat.JPEG)!!.bytes
    }

    private class Rig(task: Task, state: VideoRecorderState, frame: ByteArray?) {
        val dispatchers = TestCoroutineDispatchers()
        val videoState = MutableStateFlow(state)
        val frames = MutableStateFlow(frame)
        val component: TaskComponent
        init {
            val recorder = FakeContinuousSessionRecorder(FakeClock())
            kotlinx.coroutines.runBlocking { recorder.startWriting(Path.of("master.wav")) }
            val device = AudioInputDevice(id = "default", name = "Default Mic", eligible = true)
            component = DefaultTaskComponent(
                componentContext = DefaultComponentContext(LifecycleRegistry()),
                taskInstance = TaskInstance(taskIndex = 0, repetition = 1, task = task),
                recorder = recorder,
                dispatchers = dispatchers,
                positionInProtocol = 1,
                totalInstanceCount = 1,
                availableDevices = listOf(device),
                currentDevice = device,
                resolvedAudioExamplePath = null,
                audioPlaybackService = FakeAudioPlaybackService(),
                eventLogger = { _, _, _ -> },
                videoFrames = frames,
                videoState = videoState,
                nextPartFile = { Path.of("part2.wav") },
                onResumeRecorded = { _, _ -> },
                onTaskFinished = {},
            )
        }
    }

    /** F-106: a camera lost mid-take shows one message, clear of everything else, and drops the frozen frame. */
    private fun checkCameraLost(width: Int, height: Int) = androidx.compose.ui.test.runDesktopComposeUiTest(width, height) {
        val task = VocalTask(titleKey = "t", subtype = VocalSubtype.MONOLOGUE, recordVideo = true)
        val rig = Rig(task, VideoRecorderState.Previewing, jpeg())
        setContent { TaskContent(rig.component, localization) }
        waitForIdle(); rig.dispatchers.scheduler.advanceUntilIdle(); waitForIdle()
        onNodeWithTag(TestTags.Task.VIDEO_PREVIEW).assert(androidx.compose.ui.test.SemanticsMatcher("shows a frame") {
            it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.StateDescription) == "frame"
        })

        rig.videoState.value = VideoRecorderState.Failed(VideoError.CaptureInterrupted("ffmpeg killed"))
        waitForIdle(); rig.dispatchers.scheduler.advanceUntilIdle(); waitForIdle()

        onNodeWithTag(TestTags.Task.VIDEO_PREVIEW).assert(androidx.compose.ui.test.SemanticsMatcher("frame cleared") {
            it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.StateDescription) == "empty"
        })
        // Exactly one message, whichever of the two tags it carries
        val messages = listOf(TestTags.Task.VIDEO_ERROR, TestTags.Task.VIDEO_AUDIO_ONLY).filter {
            onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, messages.size, "exactly one camera message, got $messages")
        val message = onNodeWithTag(messages.single())
        message.assertIsDisplayed()
        val box = message.getUnclippedBoundsInRoot()
        val others = listOf(TestTags.Task.VIDEO_PREVIEW, TestTags.Task.LEVEL_INDICATOR, TestTags.Task.START_BUTTON, TestTags.Task.NEXT_BUTTON)
        for (tag in others) {
            val b = onNodeWithTag(tag).getUnclippedBoundsInRoot()
            val overlaps = box.left < b.right && b.left < box.right && box.top < b.bottom && b.top < box.bottom
            assertTrue(!overlaps, "message $box overlaps $tag $b at ${width}x$height")
        }
        assertTrue(box.left >= 0.dp && box.top >= 0.dp && box.right <= width.dp && box.bottom <= height.dp, "message $box outside ${width}x$height")
        onNodeWithTag(TestTags.Task.START_BUTTON).assertIsDisplayed()
        onNodeWithTag(TestTags.Task.NEXT_BUTTON).assertIsDisplayed()
    }

    @Test
    fun `a camera lost during a VOCAL take shows one message and clears the frame at 944x681`() = checkCameraLost(944, 681)

    @Test
    fun `a camera lost during a VOCAL take shows one message and clears the frame at 984x781`() = checkCameraLost(984, 781)

    @Test
    fun `the preview shows frames again once the camera recovers`() = androidx.compose.ui.test.runDesktopComposeUiTest(984, 781) {
        val task = VocalTask(titleKey = "t", subtype = VocalSubtype.MONOLOGUE, recordVideo = true)
        val rig = Rig(task, VideoRecorderState.Failed(VideoError.CaptureInterrupted("x")), jpeg())
        setContent { TaskContent(rig.component, localization) }
        waitForIdle(); rig.dispatchers.scheduler.advanceUntilIdle(); waitForIdle()
        rig.videoState.value = VideoRecorderState.Previewing
        waitForIdle(); rig.dispatchers.scheduler.advanceUntilIdle(); waitForIdle()
        onNodeWithTag(TestTags.Task.VIDEO_PREVIEW).assert(androidx.compose.ui.test.SemanticsMatcher("shows a frame") {
            it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.StateDescription) == "frame"
        })
    }
}
