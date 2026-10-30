package org.example.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.DpRect
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
import org.example.app.domain.video.VideoError
import org.example.app.domain.video.VideoRecorderState
import org.example.app.fakes.FakeAudioInputDeviceProvider
import org.example.app.fakes.FakeAudioPlaybackService
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeContinuousSessionRecorder
import org.example.app.fakes.TestCoroutineDispatchers
import org.example.app.navigation.DefaultCalibrationComponent
import org.example.app.navigation.DefaultTaskComponent
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.math.abs

/** Fix round 4, increment D4: F-133 (reserved camera-message line) and F-127 (capped full-screen content). */
@OptIn(ExperimentalTestApi::class)
class FixRound4D4UiTest {

    private val longText = List(12) { "Take a deep breath and then say the vowel steadily for as long as you comfortably can." }
        .joinToString(" ")
    private val localization = UiLocalization(
        LocalizedStringProvider(builtins = mapOf("ins.first" to longText, "ins.second" to longText)),
        "en",
        null,
    )

    private class Rig {
        val dispatchers = TestCoroutineDispatchers()
        val videoState = MutableStateFlow<VideoRecorderState>(VideoRecorderState.Previewing)
        val component: DefaultTaskComponent

        init {
            val recorder = FakeContinuousSessionRecorder(FakeClock())
            kotlinx.coroutines.runBlocking { recorder.startWriting(Path.of("master.wav")) }
            val device = AudioInputDevice(id = "default", name = "Default Mic", eligible = true)
            val task = VocalTask(
                titleKey = "t", subtype = VocalSubtype.PHONATION,
                instructionKeys = listOf("ins.first", "ins.second"), recordVideo = true,
            )
            component = DefaultTaskComponent(
                componentContext = DefaultComponentContext(LifecycleRegistry()),
                taskInstance = TaskInstance(taskIndex = 0, repetition = 1, task = task),
                recorder = recorder,
                dispatchers = dispatchers,
                positionInProtocol = 1,
                totalInstanceCount = 1,
                availableDevices = listOf(device),
                currentDevice = device,
                resolvedAudioExamplePath = Path.of("example.wav"),
                audioPlaybackService = FakeAudioPlaybackService(),
                eventLogger = { _, _, _ -> },
                videoFrames = MutableStateFlow(null),
                videoState = videoState,
                nextPartFile = { Path.of("part2.wav") },
                onResumeRecorded = { _, _ -> },
                onTaskFinished = {},
            )
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.bounds(tag: String): DpRect =
        onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun androidx.compose.ui.test.ComposeUiTest.settle(rig: Rig) {
        waitForIdle(); rig.dispatchers.scheduler.advanceUntilIdle(); waitForIdle()
    }

    private fun assertSame(tag: String, before: DpRect, after: DpRect) {
        val pairs = listOf(
            "left" to (before.left.value to after.left.value),
            "top" to (before.top.value to after.top.value),
            "width" to (before.width.value to after.width.value),
            "height" to (before.height.value to after.height.value),
        )
        for ((name, v) in pairs) {
            assertTrue(abs(v.first - v.second) <= 1f, "$tag $name moved from $before to $after")
        }
    }

    @Test
    fun `F-133 the camera lost message moves neither the instruction panel nor the level meter`() =
        runDesktopComposeUiTest(944, 681) {
            val rig = Rig()
            setContent { TaskContent(rig.component, localization) }
            settle(rig)
            val panel = bounds(TestTags.Task.INSTRUCTIONS)
            val meter = bounds(TestTags.Task.LEVEL_INDICATOR)
            val preview = bounds(TestTags.Task.VIDEO_PREVIEW)
            val start = bounds(TestTags.Task.START_BUTTON)

            rig.videoState.value = VideoRecorderState.Failed(VideoError.CaptureInterrupted("ffmpeg killed"))
            settle(rig)
            assertTrue(
                listOf(TestTags.Task.VIDEO_ERROR, TestTags.Task.VIDEO_AUDIO_ONLY).any {
                    onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty()
                },
                "the camera message should be showing",
            )
            assertSame("instructions", panel, bounds(TestTags.Task.INSTRUCTIONS))
            assertSame("level meter", meter, bounds(TestTags.Task.LEVEL_INDICATOR))
            assertSame("preview", preview, bounds(TestTags.Task.VIDEO_PREVIEW))
            assertSame("start button", start, bounds(TestTags.Task.START_BUTTON))
        }

    @Test
    fun `F-133 at the 960x720 minimum window a filming task keeps all its controls on screen`() =
        runDesktopComposeUiTest(944, 681) {
            val rig = Rig()
            setContent { TaskContent(rig.component, localization) }
            settle(rig)
            rig.videoState.value = VideoRecorderState.Failed(VideoError.CaptureInterrupted("x"))
            settle(rig)
            for (tag in listOf(
                TestTags.Task.INSTRUCTIONS, TestTags.Task.LEVEL_INDICATOR, TestTags.Task.VIDEO_PREVIEW,
                TestTags.Task.START_BUTTON, TestTags.Task.NEXT_BUTTON,
            )) {
                onNodeWithTag(tag).assertIsDisplayed()
                val b = bounds(tag)
                assertTrue(
                    b.left >= 0.dp && b.top >= 0.dp && b.right <= 944.dp && b.bottom <= 681.dp,
                    "$tag $b is clipped by the 944x681 window",
                )
            }
            assertTrue(bounds(TestTags.Task.INSTRUCTIONS).height >= 110.dp, "instructions squeezed: ${bounds(TestTags.Task.INSTRUCTIONS)}")
        }

    @Test
    fun `F-127 at 1512x876 the task content is capped at 1100 dp and centred`() =
        runDesktopComposeUiTest(1512, 876) {
            val rig = Rig()
            setContent { TaskContent(rig.component, localization) }
            settle(rig)
            val content = bounds(TestTags.Task.CONTENT)
            assertTrue(content.width <= 1140.dp, "task content is ${content.width} wide")
            assertTrue(abs(content.left.value - (1512 - content.right.value)) <= 1f, "task content not centred: $content")
            val instructions = bounds(TestTags.Task.INSTRUCTIONS)
            assertTrue(instructions.width <= 1100.dp, "instruction lines are ${instructions.width} wide")
        }

    @Test
    fun `F-127 at 1512x876 the calibration content is capped at 1100 dp with the meter next to the picture`() =
        runDesktopComposeUiTest(1512, 876) {
            val recorder = FakeContinuousSessionRecorder(FakeClock())
            val device = FakeAudioInputDeviceProvider.DEFAULT_DEVICE
            kotlinx.coroutines.runBlocking { recorder.startMonitoring(device) }
            val component = DefaultCalibrationComponent(
                componentContext = DefaultComponentContext(LifecycleRegistry()),
                recorder = recorder,
                dispatchers = TestCoroutineDispatchers(),
                loudness = 0.2..0.6,
                initialDevice = device,
                availableDevices = listOf(device, FakeAudioInputDeviceProvider.SECONDARY_DEVICE),
                onConfirmed = {},
                onDevicePicked = {},
            )
            setContent { CalibrationContent(component, localization, onBack = {}) }
            waitForIdle()

            val content = bounds(TestTags.Calibration.CONTENT)
            assertTrue(content.width <= 1100.dp, "calibration content is ${content.width} wide")
            val picture = bounds(TestTags.Calibration.MIC_IMAGE)
            val meter = bounds(TestTags.Calibration.LEVEL_INDICATOR)
            val centreDistance = abs((meter.left.value + meter.right.value) / 2 - (picture.left.value + picture.right.value) / 2)
            assertTrue(centreDistance <= 350f, "meter $meter is $centreDistance dp from the picture $picture")
        }
}
