package org.example.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertIsEnabled
import org.junit.jupiter.api.Assertions.assertTrue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.config.Question
import org.example.app.domain.config.QuestionType
import org.example.app.domain.config.QuestionnaireTask
import org.example.app.domain.localization.LocalizedStringProvider
import org.example.app.domain.timeline.TaskInstance
import org.example.app.domain.video.VideoRecorderState
import org.example.app.fakes.FakeAudioPlaybackService
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeContinuousSessionRecorder
import org.example.app.fakes.TestCoroutineDispatchers
import org.example.app.navigation.DefaultTaskComponent
import org.example.app.ui.theme.ShareTheme
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** F-065: a fresh questionnaire shows no validation error; touched or submitted-invalid questions do. */
@OptIn(ExperimentalTestApi::class)
class QuestionnaireValidationUiTest {

    private val localization = UiLocalization(
        LocalizedStringProvider(builtins = mapOf("q.a" to "A", "q.b" to "B", "yes" to "Yes", "no" to "No")),
        "en",
        null,
    )

    private val task = QuestionnaireTask(
        titleKey = "Q",
        questions = listOf(
            Question(QuestionType.OPEN, "a", "q.a", questionRegex = "[0-9]+"),
            Question(QuestionType.SINGLE_CHOICE, "b", "q.b", questionOptions = listOf("yes", "no")),
        ),
    )

    private fun component(onFinished: (org.example.app.domain.session.TaskRecord) -> Unit = {}) = DefaultTaskComponent(
        componentContext = DefaultComponentContext(LifecycleRegistry()),
        taskInstance = TaskInstance(taskIndex = 0, repetition = 1, task = task),
        recorder = FakeContinuousSessionRecorder(FakeClock()),
        dispatchers = TestCoroutineDispatchers(),
        positionInProtocol = 1,
        totalInstanceCount = 1,
        previousTaskSkipped = false,
        availableDevices = listOf(AudioInputDevice(id = "default", name = "Default Mic", eligible = true)),
        currentDevice = AudioInputDevice(id = "default", name = "Default Mic", eligible = true),
        resolvedAudioExamplePath = null,
        audioPlaybackService = FakeAudioPlaybackService(),
        eventLogger = { _, _, _ -> },
        videoFrames = MutableStateFlow(null),
        videoState = MutableStateFlow(VideoRecorderState.Previewing),
        nextPartFile = { Path.of("part2.wav") },
        onResumeRecorded = { _, _ -> },
        onTaskFinished = onFinished,
    )

    private val errA = TestTags.Questionnaire.validationError("a")
    private val errB = TestTags.Questionnaire.validationError("b")

    @Test
    fun `F-065 a fresh questionnaire shows no validation error`() = runDesktopComposeUiTest(900, 700) {
        setContent { ShareTheme { TaskContent(component(), localization) } }
        waitForIdle()
        onAllNodesWithTag(errA).assertCountEquals(0)
        onAllNodesWithTag(errB).assertCountEquals(0)
    }

    @Test
    fun `F-065 a touched invalid answer shows its error and untouched ones stay clean`() = runDesktopComposeUiTest(900, 700) {
        setContent { ShareTheme { TaskContent(component(), localization) } }
        waitForIdle()
        onNodeWithTag(TestTags.Questionnaire.answerField("a")).performTextInput("abc")
        waitForIdle()
        onNodeWithTag(errA).assertIsDisplayed()
        onAllNodesWithTag(errB).assertCountEquals(0)
    }

    @Test
    fun `F-065 Next with invalid answers shows every invalid question's error`() = runDesktopComposeUiTest(900, 700) {
        val c = component()
        setContent { ShareTheme { TaskContent(c, localization) } }
        waitForIdle()
        onNodeWithTag(TestTags.Task.NEXT_BUTTON).assertIsEnabled().performClick()
        waitForIdle()
        onNodeWithTag(errA).assertIsDisplayed()
        onNodeWithTag(errB).assertIsDisplayed()
    }

    @Test
    fun `F-065 Next with invalid answers stays on the questionnaire`() = runDesktopComposeUiTest(900, 700) {
        val finished = mutableListOf<org.example.app.domain.session.TaskRecord>()
        val c = component { finished += it }
        setContent { ShareTheme { TaskContent(c, localization) } }
        waitForIdle()
        onNodeWithTag(TestTags.Task.NEXT_BUTTON).performClick()
        waitForIdle()
        assertTrue(finished.isEmpty())
        onNodeWithTag(TestTags.Questionnaire.answerField("a")).assertIsDisplayed()
    }
}
