package org.example.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
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
    fun `a filming VOCAL task shows the camera preview instead of the level indicator`() = runComposeUiTest {
        val task = VocalTask(titleKey = "t", subtype = VocalSubtype.MONOLOGUE, recordVideo = true)
        setContent { TaskContent(component(task, VideoRecorderState.Previewing), localization) }
        waitForIdle()

        onNodeWithTag(TestTags.Task.VIDEO_PREVIEW).assertIsDisplayed()
        onNodeWithTag(TestTags.Task.LEVEL_INDICATOR).assertDoesNotExist()
        onNodeWithTag(TestTags.Task.START_BUTTON).assertIsDisplayed()
    }

    @Test
    fun `a filming VOCAL task without a camera says it records audio only`() = runComposeUiTest {
        val task = VocalTask(titleKey = "t", subtype = VocalSubtype.MONOLOGUE, recordVideo = true)
        val noCamera = VideoRecorderState.Failed(VideoError.DeviceUnavailable("none"))
        setContent { TaskContent(component(task, noCamera), localization) }
        waitForIdle()

        onNodeWithTag(TestTags.Task.VIDEO_AUDIO_ONLY).assertIsDisplayed()
    }

    @Test
    fun `a VIDEO task shows the start button`() = runComposeUiTest {
        setContent { TaskContent(component(VideoTask(titleKey = "v"), VideoRecorderState.Previewing), localization) }
        waitForIdle()

        onNodeWithTag(TestTags.Task.START_BUTTON).assertIsDisplayed()
    }
}
