package org.example.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * F-091: the run-03 questionnaire (two OPEN, one single and one three-option multiple choice, the
 * "Previous task skipped" banner above) lost the last option to zero bounds at 1000x820. The
 * content must scroll so every option and the Next button stay reachable.
 */
@OptIn(ExperimentalTestApi::class)
class QuestionnaireLayoutUiTest {

    private val localization = UiLocalization(
        LocalizedStringProvider(
            builtins = mapOf(
                "task.previousSkipped" to "Předchozí úloha přeskočena",
                "questionnaire.error.invalid" to "Odpověď není platná",
                "q.feel" to "Jak se dnes cítíte",
                "q.note" to "Další poznámka",
                "q.smoke" to "Kouříte",
                "q.symptoms" to "Které příznaky se vás týkají",
                "yes" to "Ano", "no" to "Ne",
                "sweat" to "Chrapot", "cough" to "Kašel", "none" to "Žádné",
            ),
        ),
        "en",
        null,
    )

    private val task = QuestionnaireTask(
        titleKey = "E2E 1026 dotazník",
        questions = listOf(
            Question(QuestionType.OPEN, "feel", "q.feel", questionRegex = ".+"),
            Question(QuestionType.OPEN, "note", "q.note", questionRegex = ".+"),
            Question(QuestionType.SINGLE_CHOICE, "smoke", "q.smoke", questionOptions = listOf("yes", "no")),
            Question(QuestionType.MULTIPLE_CHOICE, "symptoms", "q.symptoms", questionOptions = listOf("sweat", "cough", "none")),
        ),
    )

    private fun component() = DefaultTaskComponent(
        componentContext = DefaultComponentContext(LifecycleRegistry()),
        taskInstance = TaskInstance(taskIndex = 5, repetition = 1, task = task),
        recorder = FakeContinuousSessionRecorder(FakeClock()),
        dispatchers = TestCoroutineDispatchers(),
        positionInProtocol = 6,
        totalInstanceCount = 6,
        previousTaskSkipped = true,
        availableDevices = listOf(AudioInputDevice(id = "default", name = "Default Mic", eligible = true)),
        currentDevice = AudioInputDevice(id = "default", name = "Default Mic", eligible = true),
        resolvedAudioExamplePath = null,
        audioPlaybackService = FakeAudioPlaybackService(),
        eventLogger = { _, _, _ -> },
        videoFrames = MutableStateFlow(null),
        videoState = MutableStateFlow(VideoRecorderState.Previewing),
        nextPartFile = { Path.of("part2.wav") },
        onResumeRecorded = { _, _ -> },
        onTaskFinished = {},
    )

    private fun check(width: Int, height: Int, fillOpen: Boolean) = runDesktopComposeUiTest(width, height) {
        setContent { ShareTheme { TaskContent(component(), localization) } }
        waitForIdle()
        if (fillOpen) {
            onNodeWithTag(TestTags.Questionnaire.answerField("feel")).performScrollTo().performTextInput("Dobre")
            onNodeWithTag(TestTags.Questionnaire.answerField("note")).performScrollTo().performTextInput("Zadna poznamka")
            waitForIdle()
        }

        val last = TestTags.Questionnaire.answerOption("symptoms", "none")
        onNodeWithTag(last).performScrollTo().assertIsDisplayed()
        val bounds = onNodeWithTag(last).getUnclippedBoundsInRoot()
        assertTrue(bounds.height > 10.dp, "last option has height ${bounds.height} at ${width}x$height")
        onNodeWithTag(last).assertIsOff().performClick()
        onNodeWithTag(last).assertIsOn()

        onNodeWithTag(TestTags.Task.NEXT_BUTTON).assertIsDisplayed()
    }

    @Test
    fun `the last option is reachable at 984x781 with validation messages showing`() = check(984, 781, fillOpen = false)

    @Test
    fun `the last option is reachable at 984x781 with the open answers filled`() = check(984, 781, fillOpen = true)

    @Test
    fun `the last option is reachable at 944x681 with validation messages showing`() = check(944, 681, fillOpen = false)

    @Test
    fun `the last option is reachable at 944x681 with the open answers filled`() = check(944, 681, fillOpen = true)

    @Test
    fun `the last option is reachable in a 1000x820 root`() = check(1000, 820, fillOpen = false)

    @Test
    fun `the last option is reachable in a 960x720 root`() = check(960, 720, fillOpen = false)

    @Test
    fun `the mouse wheel scrolls the questions and a scrollbar shows they overflow at 984x781`() = runDesktopComposeUiTest(984, 781) {
        setContent { ShareTheme { TaskContent(component(), localization) } }
        waitForIdle()

        onNodeWithTag(TestTags.Questionnaire.SCROLLBAR).assertIsDisplayed()
        onNodeWithTag(TestTags.Questionnaire.answerField("feel")).performMouseInput { moveTo(center); scroll(5f) }
        waitForIdle()
        onNodeWithTag(TestTags.Questionnaire.answerOption("symptoms", "none")).assertIsDisplayed()
    }

    @Test
    fun `no scrollbar shows when the questions fit`() = runDesktopComposeUiTest(984, 1400) {
        setContent { ShareTheme { TaskContent(component(), localization) } }
        waitForIdle()

        onNodeWithTag(TestTags.Questionnaire.SCROLLBAR).assertDoesNotExist()
        onNodeWithTag(TestTags.Questionnaire.answerOption("symptoms", "none")).assertIsDisplayed()
    }
}
