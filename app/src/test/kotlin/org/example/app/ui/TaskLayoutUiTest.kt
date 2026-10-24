package org.example.app.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.config.VocalSubtype
import org.example.app.domain.config.VocalTask
import org.example.app.domain.localization.LocalizedStringProvider
import org.example.app.domain.timeline.TaskInstance
import org.example.app.domain.video.VideoRecorderState
import org.example.app.fakes.FakeAudioPlaybackService
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeContinuousSessionRecorder
import org.example.app.fakes.TestCoroutineDispatchers
import org.example.app.navigation.DefaultTaskComponent
import org.example.app.navigation.TaskComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** F-031: the VOCAL middle area (indicator, 200x150 camera preview) keeps its size; the instruction cards scroll instead. */
@OptIn(ExperimentalTestApi::class)
class TaskLayoutUiTest {

    private val longText = List(12) { "Take a deep breath and then say the vowel steadily for as long as you comfortably can." }
        .joinToString(" ")
    private val localization = UiLocalization(
        LocalizedStringProvider(builtins = mapOf("ins.first" to longText, "ins.second" to longText)),
        "en",
        null,
    )

    private fun component(task: VocalTask): TaskComponent {
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
            resolvedAudioExamplePath = Path.of("example.wav"),
            audioPlaybackService = FakeAudioPlaybackService(),
            eventLogger = { _, _, _ -> },
            videoFrames = MutableStateFlow(null),
            videoState = MutableStateFlow(VideoRecorderState.Previewing),
            nextPartFile = { Path.of("part2.wav") },
            onResumeRecorded = { _, _ -> },
            onTaskFinished = {},
        )
    }

    private fun phonation(recordVideo: Boolean, subtype: VocalSubtype = VocalSubtype.PHONATION) = VocalTask(
        titleKey = "t",
        subtype = subtype,
        instructionKeys = listOf("ins.first", "ins.second"),
        recordVideo = recordVideo,
    )

    private fun checkMiddleArea(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        setContent { TaskContent(component(phonation(recordVideo = true)), localization) }
        waitForIdle()

        val preview = onNodeWithTag(TestTags.Task.VIDEO_PREVIEW).getUnclippedBoundsInRoot()
        assertTrue(preview.width >= 200.dp && preview.height >= 150.dp, "preview at ${width}x$height was $preview")
        val indicator = onNodeWithTag(TestTags.Task.LEVEL_INDICATOR).getUnclippedBoundsInRoot()
        assertTrue(indicator.width >= 160.dp && indicator.height >= 160.dp, "indicator at ${width}x$height was $indicator")
        onNodeWithTag(TestTags.Task.VIDEO_PREVIEW).assertIsDisplayed()
        onNodeWithTag(TestTags.Task.START_BUTTON).assertIsDisplayed()
        onNodeWithTag(TestTags.Task.NEXT_BUTTON).assertIsDisplayed()
        val instructions = onNodeWithTag(TestTags.Task.INSTRUCTIONS).getUnclippedBoundsInRoot()
        assertTrue(instructions.height >= 110.dp, "instructions at ${width}x$height were $instructions")
    }

    @Test
    fun `a filming phonation task keeps its preview and indicator at 1000x820`() = checkMiddleArea(1000, 820)

    @Test
    fun `a filming phonation task keeps its preview and indicator at 800x600`() = checkMiddleArea(800, 600)

    @Test
    fun `a filming phonation task keeps its preview and indicator in the 784x561 content of an 800x600 window`() = checkMiddleArea(784, 561)

    @Test
    fun `the instruction cards scroll instead of squeezing the middle area when the window is short`() =
        runDesktopComposeUiTest(1000, 820) {
            setContent { TaskContent(component(phonation(recordVideo = true)), localization) }
            waitForIdle()

            val range = onNodeWithTag(TestTags.Task.INSTRUCTIONS).fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue(range.maxValue() > 0f, "long instructions should scroll at 1000x820")
            onNodeWithTag(TestTags.Task.INSTRUCTIONS_SCROLLBAR).assertIsDisplayed()
        }

    @Test
    fun `a tall window shows the whole instruction text without scrolling`() = runDesktopComposeUiTest(1000, 1400) {
        setContent { TaskContent(component(phonation(recordVideo = true)), localization) }
        waitForIdle()

        val range = onNodeWithTag(TestTags.Task.INSTRUCTIONS).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals(0f, range.maxValue(), "nothing should be left to scroll to at 1000x1400")
        onNodeWithTag(TestTags.Task.INSTRUCTIONS_SCROLLBAR).assertDoesNotExist()
        onNodeWithTag(TestTags.Task.VIDEO_PREVIEW).assertIsDisplayed()
    }

    @Test
    fun `a PATAKA indicator keeps its size with long instructions`() = runDesktopComposeUiTest(1000, 820) {
        setContent { TaskContent(component(phonation(recordVideo = false, subtype = VocalSubtype.PATAKA)), localization) }
        waitForIdle()

        val indicator = onNodeWithTag(TestTags.Task.LEVEL_INDICATOR).getUnclippedBoundsInRoot()
        assertTrue(indicator.width >= 160.dp && indicator.height >= 160.dp, "PATAKA indicator was $indicator")
        onNodeWithTag(TestTags.Task.LEVEL_INDICATOR).assertIsDisplayed()
    }
}
