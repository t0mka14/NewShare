package org.example.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
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
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * READING's passage (its last instruction paragraph, config alignment row 12) is rendered in its
 * own panel on every repetition — the legacy rule hides the *second instructions card* from the
 * second repetition on, and the passage must not go with it.
 */
class ReadingPassageUiTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the reading passage is shown on the second repetition`() = runComposeUiTest {
        val clock = FakeClock()
        val recorder = FakeContinuousSessionRecorder(clock)
        kotlinx.coroutines.runBlocking { recorder.startWriting(Path.of("master.wav")) }
        val device = AudioInputDevice(id = "default", name = "Default Mic", eligible = true)
        val task = VocalTask(
            titleKey = "reading_title",
            subtype = VocalSubtype.READING,
            instructionKeys = listOf("reading_instr1", "reading_instr2", "reading_passage"),
            nrepetition = 2,
        )
        val component = DefaultTaskComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            taskInstance = TaskInstance(taskIndex = 0, repetition = 2, task = task),
            recorder = recorder,
            dispatchers = TestCoroutineDispatchers(),
            positionInProtocol = 2,
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

        setContent { TaskContent(component, UiLocalization(LocalizedStringProvider(), "en", null)) }
        waitForIdle()

        onNodeWithTag(TestTags.Task.READING_PASSAGE).assertIsDisplayed()
    }
}
