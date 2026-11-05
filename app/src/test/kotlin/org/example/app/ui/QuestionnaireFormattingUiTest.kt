package org.example.app.ui

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.config.Question
import org.example.app.domain.config.QuestionCondition
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** Increment 2 of SPEC-questionnaire-rules: frame, dividers and indentation of the question pane. */
@OptIn(ExperimentalTestApi::class)
class QuestionnaireFormattingUiTest {

    private val localization = UiLocalization(LocalizedStringProvider(builtins = emptyMap()), "en", null)

    private fun choice(key: String, showIf: QuestionCondition? = null) =
        Question(QuestionType.SINGLE_CHOICE, key, "t.$key", questionOptions = listOf("a", "b"), showIf = showIf)

    private fun open(key: String, showIf: QuestionCondition? = null) =
        Question(QuestionType.OPEN, key, "t.$key", questionRegex = ".*", showIf = showIf)

    /** Web-sample shape: q7 depends on q3 with q4..q6 between them in config order. */
    private val sample = listOf(
        open("q1"), open("q2"), choice("q3"), open("q4"), open("q5"), open("q6"),
        open("q7", QuestionCondition("q3", listOf("a"))),
    )

    /** c1 -> c2 -> c3 -> c4 -> c5: depths 0..4. */
    private val chain = listOf(
        choice("c1"),
        choice("c2", QuestionCondition("c1", listOf("a"))),
        choice("c3", QuestionCondition("c2", listOf("a"))),
        choice("c4", QuestionCondition("c3", listOf("a"))),
        choice("c5", QuestionCondition("c4", listOf("a"))),
    )

    private fun component(questions: List<Question>) = DefaultTaskComponent(
        componentContext = DefaultComponentContext(LifecycleRegistry()),
        taskInstance = TaskInstance(taskIndex = 0, repetition = 1, task = QuestionnaireTask(titleKey = "title", questions = questions)),
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
        onTaskFinished = {},
    )

    private fun test(questions: List<Question>, width: Int = 1200, height: Int = 1600, body: DesktopComposeUiTest.() -> Unit) =
        runDesktopComposeUiTest(width, height) {
            setContent { ShareTheme { TaskContent(component(questions), localization) } }
            waitForIdle()
            body()
        }

    private fun DesktopComposeUiTest.dividers() = onAllNodesWithTag(TestTags.Questionnaire.DIVIDER).fetchSemanticsNodes().size

    private fun DesktopComposeUiTest.select(question: String, option: String) {
        onNodeWithTag(TestTags.Questionnaire.answerOption(question, option)).performClick()
        waitForIdle()
    }

    private fun DesktopComposeUiTest.bounds(tag: String) = onNodeWithTag(tag).getUnclippedBoundsInRoot()

    @Test
    fun `divider count is published questions minus one and follows visibility`() = test(sample) {
        assertEquals(5, dividers()) // q1..q6
        select("q3", "a")
        assertEquals(6, dividers()) // q7 appeared
        select("q3", "b")
        assertEquals(5, dividers())
    }

    @Test
    fun `no divider with a single question`() = test(listOf(open("q1"))) {
        assertEquals(0, dividers())
    }

    @Test
    fun `the frame exists and keeps its size when a conditional question appears and disappears`() = test(sample) {
        val before = bounds(TestTags.Questionnaire.FRAME)
        assertTrue(before.height > 100.dp)
        select("q3", "a")
        val shown = bounds(TestTags.Questionnaire.FRAME)
        assertEquals(before.height, shown.height)
        assertEquals(before.width, shown.width)
        assertEquals(before.top, shown.top)
        select("q3", "b")
        val hidden = bounds(TestTags.Questionnaire.FRAME)
        assertEquals(before.height, hidden.height)
        assertEquals(before.width, hidden.width)
        assertEquals(before.top, hidden.top)
    }

    @Test
    fun `a conditional question is indented and sits directly below its non-adjacent source`() = test(sample) {
        select("q3", "a")
        val sourceLast = bounds(TestTags.Questionnaire.answerOption("q3", "b"))
        val root = bounds(TestTags.Questionnaire.answerField("q4"))
        val dependent = bounds(TestTags.Questionnaire.answerField("q7"))
        assertTrue(dependent.left > root.left + 10.dp, "dependent left ${dependent.left} vs root ${root.left}")
        val sourceOption = bounds(TestTags.Questionnaire.answerOption("q3", "a"))
        assertTrue(dependent.top >= sourceLast.bottom, "q7 below q3's last option")
        assertTrue(dependent.bottom <= root.top, "q7 above q4")
        assertTrue(sourceOption.left < dependent.left + 1.dp)
    }

    @Test
    fun `depth 4 is indented no further than depth 3`() = test(chain) {
        listOf("c1", "c2", "c3", "c4").forEach { select(it, "a") }
        val left = { k: String -> bounds(TestTags.Questionnaire.answerOption(k, "a")).left }
        assertTrue(left("c2") > left("c1"))
        assertTrue(left("c3") > left("c2"))
        assertTrue(left("c4") > left("c3"))
        assertEquals(left("c4"), left("c5"))
    }

    @Test
    fun `the scrollbar shows in a short window inside the frame and clear of the content`() = test(sample, width = 984, height = 500) {
        val frame = bounds(TestTags.Questionnaire.FRAME)
        val bar = bounds(TestTags.Questionnaire.SCROLLBAR)
        assertTrue(bar.left >= frame.left && bar.right <= frame.right, "scrollbar $bar inside frame $frame")
        assertTrue(bar.top >= frame.top && bar.bottom <= frame.bottom, "scrollbar $bar inside frame $frame")
        val field = bounds(TestTags.Questionnaire.answerField("q1"))
        assertTrue(field.right <= bar.left, "answer field right ${field.right} clear of scrollbar left ${bar.left}")
        assertTrue(field.left > frame.left, "answer field inside the frame padding")
    }

    @Test
    fun `typed text stays with its question when a conditional question is inserted above it`() = test(sample) {
        onNodeWithTag(TestTags.Questionnaire.answerField("q4")).performTextInput("hello")
        waitForIdle()
        select("q3", "a")
        onNodeWithTag(TestTags.Questionnaire.answerField("q4")).assertTextEquals("hello")
        onNodeWithTag(TestTags.Questionnaire.answerField("q7")).assertTextEquals("")
        onNodeWithTag(TestTags.Questionnaire.answerField("q5")).assertTextEquals("")
    }

    @Test
    fun `focus stays on its question when a conditional question is inserted above and removed again`() =
        runDesktopComposeUiTest(1200, 1600) {
            // Driven through the component: clicking a radio button would itself move focus
            val component = component(sample)
            setContent { ShareTheme { TaskContent(component, localization) } }
            waitForIdle()
            onNodeWithTag(TestTags.Questionnaire.answerField("q4")).performClick()
            waitForIdle()
            onNodeWithTag(TestTags.Questionnaire.answerField("q4")).assertIsFocused()

            component.onOptionToggled("q3", "a", true)
            waitForIdle()
            onNodeWithTag(TestTags.Questionnaire.answerField("q7")).assertIsNotFocused()
            onNodeWithTag(TestTags.Questionnaire.answerField("q4")).assertIsFocused()

            component.onOptionToggled("q3", "b", true)
            waitForIdle()
            onNodeWithTag(TestTags.Questionnaire.answerField("q4")).assertIsFocused()
        }

    @Test
    fun `the frame border is drawn in the primary colour on its left edge`() = runDesktopComposeUiTest(1200, 1000) {
        var primary = Color.Unspecified
        setContent {
            ShareTheme {
                primary = MaterialTheme.colorScheme.primary
                TaskContent(component(sample), localization)
            }
        }
        waitForIdle()
        val frame = onNodeWithTag(TestTags.Questionnaire.FRAME).getBoundsInRoot()
        val image = onRoot().captureToImage().toAwtImage()
        val x = (frame.left.value * density.density).toInt()
        val y = (((frame.top.value + frame.bottom.value) / 2) * density.density).toInt()
        fun matches(px: Int): Boolean {
            val c = java.awt.Color(px, true)
            fun near(actual: Int, expected: Float) = kotlin.math.abs(actual - expected * 255f) <= 16f
            return near(c.red, primary.red) && near(c.green, primary.green) && near(c.blue, primary.blue)
        }
        assertTrue(matches(image.getRGB(x, y)), "pixel on the frame edge ($x,$y) is the primary colour")
        assertTrue(!matches(image.getRGB(x - 3, y)), "pixel outside the edge is not the primary colour")
        assertTrue(!matches(image.getRGB(x + 3, y)), "pixel inside the edge is not the primary colour")
    }
}
