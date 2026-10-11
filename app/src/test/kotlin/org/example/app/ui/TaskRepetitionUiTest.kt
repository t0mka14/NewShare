package org.example.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.config.VocalSubtype
import org.example.app.domain.config.VocalTask
import org.example.app.domain.localization.LocalizedStringProvider
import org.example.app.domain.timeline.TaskInstance
import org.example.app.fakes.FakeAudioPlaybackService
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeContinuousSessionRecorder
import org.example.app.fakes.TestCoroutineDispatchers
import org.example.app.navigation.DefaultTaskComponent
import org.example.app.navigation.TaskComponent
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** §13 decision 58: from the second repetition the only instruction card is the "same task again" prompt. */
@OptIn(ExperimentalTestApi::class)
class TaskRepetitionUiTest {

    private val task = VocalTask(
        titleKey = "phonation_title",
        subtype = VocalSubtype.PHONATION,
        instructionKeys = listOf("phonation_instr1", "phonation_instr2"),
        nrepetition = 2,
    )

    private fun component(repetition: Int): TaskComponent {
        val recorder = FakeContinuousSessionRecorder(FakeClock())
        kotlinx.coroutines.runBlocking { recorder.startWriting(Path.of("master.wav")) }
        val device = AudioInputDevice(id = "default", name = "Default Mic", eligible = true)
        return DefaultTaskComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            taskInstance = TaskInstance(taskIndex = 0, repetition = repetition, task = task),
            recorder = recorder,
            dispatchers = TestCoroutineDispatchers(),
            positionInProtocol = repetition,
            totalInstanceCount = 2,
            availableDevices = listOf(device),
            currentDevice = device,
            resolvedAudioExamplePath = null,
            audioPlaybackService = FakeAudioPlaybackService(),
            eventLogger = { _, _, _ -> },
            nextPartFile = { Path.of("part2.wav") },
            onResumeRecorded = { _, _ -> },
            onTaskFinished = {},
        )
    }

    // No config: unknown keys render as the key itself, built-ins as their English text.
    private val localization = UiLocalization(LocalizedStringProvider(), "en", null)

    @Test
    fun `the first repetition shows the configured instruction cards`() = runComposeUiTest {
        setContent { TaskContent(component(repetition = 1), localization) }
        waitForIdle()

        onNodeWithText("phonation_instr1").assertIsDisplayed()
        onNodeWithText("phonation_instr2").assertIsDisplayed()
        onNodeWithText("Please perform the same task again.").assertDoesNotExist()
    }

    @Test
    fun `the second repetition replaces the first card's text and hides the second card`() = runComposeUiTest {
        setContent { TaskContent(component(repetition = 2), localization) }
        waitForIdle()

        onNodeWithText("Please perform the same task again.").assertIsDisplayed()
        onNodeWithText("phonation_instr1").assertDoesNotExist()
        onNodeWithText("phonation_instr2").assertDoesNotExist()
    }
}
