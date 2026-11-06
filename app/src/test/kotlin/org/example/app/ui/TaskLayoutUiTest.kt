package org.example.app.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.config.IndicatorType
import org.example.app.domain.config.Question
import org.example.app.domain.config.QuestionType
import org.example.app.domain.config.QuestionnaireTask
import org.example.app.domain.config.Task
import org.example.app.domain.config.VideoTask
import org.example.app.domain.config.VocalSubtype
import org.example.app.domain.config.VocalTask
import org.example.app.domain.localization.LocalizedStringProvider
import org.example.app.domain.timeline.TaskInstance
import org.example.app.domain.video.PtzAction
import org.example.app.domain.video.VideoError
import org.example.app.domain.video.VideoInputDevice
import org.example.app.domain.video.VideoRecorderState
import org.example.app.fakes.FakeAudioPlaybackService
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeContinuousSessionRecorder
import org.example.app.fakes.FakeSessionVideoRecorder
import org.example.app.fakes.TestCoroutineDispatchers
import org.example.app.navigation.DefaultTaskComponent
import org.example.app.navigation.TaskButtonState
import org.example.app.navigation.TaskComponent
import org.example.app.navigation.TaskScreenState
import org.example.app.navigation.VideoFeed
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs

/**
 * VOCAL task layout (AC 1-6 of docs/SPEC-app-notes-round.md): the instructions take their natural
 * height, limited only by what is left after the title, timer and buttons; the indicator and the
 * 200x150 camera preview get the leftover height and are left out of the UI when it is too small.
 * Questionnaire, standalone video and info tasks keep their weighted middle area.
 *
 * `runDesktopComposeUiTest(w, h)` runs at density 1, so a dp equals a pixel here.
 */
@OptIn(ExperimentalTestApi::class)
class TaskLayoutUiTest {

    // Thresholds are literals on purpose: changing a layout constant must fail a test, not move it along.
    private val MIN_INDICATOR = 80 // dp of room below which the indicator is left out
    private val PREVIEW_THRESHOLD = 198 // dp of room the preview block needs: 150 picture + 48 message slot
    private val GAP = 8f // dp clear between the instructions and what is below them
    /**
     * The waveform's tag sits inside the box's 4 dp padding (the 1 dp border is drawn inside the box and takes no layout
     * space), so the box edge is this far outside the tag's edge on every side.
     */
    private val WAVEFORM_TAG_INSET = 4f

    private val longText = List(12) { "Take a deep breath and then say the vowel steadily for as long as you comfortably can." }
        .joinToString(" ")
    /** Far more than the passage panel's 320 dp cap holds at any width the tests use. */
    private val passage = List(40) { "The rainbow is a division of white light into many beautiful colours." }.joinToString(" ")
    private val localization = UiLocalization(
        LocalizedStringProvider(
            builtins = mapOf(
                "ins.first" to longText,
                "ins.second" to longText,
                "ins.short" to "Say aaah for {length} seconds.",
                "ins.passage" to passage,
            ),
        ),
        "en",
        null,
    )

    // region fixtures

    /** A [TaskComponent] that only holds a state, so a test can put any screen state in front of the layout. */
    private class StateOnlyTask(initial: TaskComponent.State) : TaskComponent {
        private val mutable = MutableValue(initial)
        override val state: Value<TaskComponent.State> = mutable
        fun set(next: TaskComponent.State) { mutable.value = next }

        override fun onStart() = Unit
        override fun onStop() = Unit
        override fun onRepeat() = Unit
        override fun onNext() = Unit
        override fun onSkipRequested() = Unit
        override fun onSkipCancelled() = Unit
        override fun onSkip() = Unit
        override fun onOpenAnswerChanged(questionKey: String, value: String) = Unit
        override fun onOptionToggled(questionKey: String, option: String, selected: Boolean) = Unit
        override fun onDeviceReselected(device: AudioInputDevice) = Unit
        override fun onPtz(action: PtzAction) = Unit
        override fun onPlayExampleAudio() = Unit
        override fun onStopExampleAudio() = Unit
    }

    /** AUDIO_ONLY: the camera never came up but nothing failed (ready = false, error = null, audioOnly = true). */
    private enum class Film { OFF, ON, FAILED, AUDIO_ONLY }
    private enum class Phase { IDLE, CAPTURING, STOPPED }
    private enum class Instructions(val keys: List<String>) {
        SHORT(listOf("ins.short")),
        LONG(listOf("ins.first", "ins.second")),
    }

    private val device = AudioInputDevice(id = "default", name = "Default Mic", eligible = true)

    private fun feed(film: Film): VideoFeed? = when (film) {
        Film.OFF -> null
        Film.ON -> VideoFeed(frames = MutableStateFlow(null), decodeDispatcher = Dispatchers.Unconfined, ready = true, error = null)
        Film.AUDIO_ONLY -> VideoFeed(
            frames = MutableStateFlow(null),
            decodeDispatcher = Dispatchers.Unconfined,
            ready = false,
            error = null,
            audioOnly = true,
        )
        Film.FAILED -> VideoFeed(
            frames = MutableStateFlow(null),
            decodeDispatcher = Dispatchers.Unconfined,
            ready = false,
            error = VideoError.DeviceUnavailable("none"),
            audioOnly = true,
        )
    }

    private fun stateOf(
        indicator: IndicatorType,
        phase: Phase,
        instructions: Instructions,
        film: Film,
        showIndicator: Boolean = true,
        repetition: Int = 1,
        readingPassageKey: String? = null,
    ): TaskComponent.State {
        val screenState = when (phase) {
            Phase.IDLE -> TaskScreenState.Idle
            Phase.CAPTURING -> TaskScreenState.Capturing
            Phase.STOPPED -> TaskScreenState.Stopped
        }
        return TaskComponent.State(
            taskIndex = 0,
            repetition = repetition,
            readingPassageKey = readingPassageKey,
            positionInProtocol = 1,
            totalInstanceCount = 1,
            titleKey = "t",
            instructionKeys = instructions.keys,
            taskLengthSeconds = 15,
            nextTaskTitleKey = null,
            canSkip = false,
            content = TaskComponent.Content.Vocal(
                screenState = screenState,
                takeNumber = if (phase == Phase.IDLE) 0 else 1,
                level = if (phase == Phase.CAPTURING) 0.6f else 0f,
                levelHistory = when (phase) {
                    Phase.IDLE -> emptyList()
                    Phase.CAPTURING -> List(80) { 0.2f + 0.5f * (it % 7) / 7f }
                    Phase.STOPPED -> List(200) { 0.2f + 0.5f * (it % 7) / 7f }
                },
                deviceLost = false,
                showIndicator = showIndicator,
                exampleAudioAvailable = true,
                exampleAudioPlaying = false,
                video = feed(film),
                indicatorType = indicator,
            ),
            buttons = TaskButtonState.of(screenState, canRepeat = true, canSkip = false),
            availableDevices = listOf(device),
            currentDevice = device,
        )
    }

    /** Top edge of the indicator's visible box: the tag itself for the circle, [WAVEFORM_TAG_INSET] above it for the waveform. */
    private fun boxTop(type: IndicatorType, tag: DpRect): Float = tag.top.value - if (type == IndicatorType.WAVEFORM) WAVEFORM_TAG_INSET else 0f

    private fun ComposeUiTest.bounds(tag: String): DpRect = onNodeWithTag(tag).getUnclippedBoundsInRoot()
    private fun ComposeUiTest.count(tag: String): Int = onAllNodesWithTag(tag).fetchSemanticsNodes().size
    private fun ComposeUiTest.exists(tag: String): Boolean = count(tag) > 0

    private fun ComposeUiTest.show(task: TaskComponent) {
        setContent { TaskContent(task, localization) }
        waitForIdle()
    }

    /** Extra height around the instructions/middle body (title, timer, buttons, padding), found by measuring: window height minus the body's own height. */
    private fun chromeHeight(): Int {
        var chrome = 0
        val windowHeight = 1000
        runDesktopComposeUiTest(1000, windowHeight) {
            show(StateOnlyTask(stateOf(IndicatorType.CIRCLE, Phase.IDLE, Instructions.SHORT, Film.OFF)))
            val instructions = bounds(TestTags.Task.INSTRUCTIONS)
            val indicator = bounds(TestTags.Task.LEVEL_INDICATOR)
            // the circle (160) is centred in the room under the instructions, less the gap it keeps from them
            val room = 2 * (indicator.top.value - instructions.bottom.value) + indicator.height.value - GAP
            chrome = windowHeight - (instructions.height.value + room).toInt()
        }
        return chrome
    }

    private val chrome by lazy { chromeHeight() }

    /** Window height that leaves [room] dp under the short single-card instructions. */
    private fun windowHeightFor(room: Int, shortInstructionsHeight: Int = 133) = chrome + shortInstructionsHeight + room

    private fun nearly(expected: Float, actual: Float, what: String, tolerance: Float = 1f) =
        assertTrue(abs(expected - actual) <= tolerance, "$what: expected ~$expected but was $actual")

    // endregion

    // region AC 1: natural height, scrolling only on overflow

    @Test
    fun `instructions that fit get exactly their natural height, whatever space is left`() {
        val heights = listOf(1400, 1700).map { windowHeight ->
            var height = 0f
            runDesktopComposeUiTest(1000, windowHeight) {
                show(StateOnlyTask(stateOf(IndicatorType.CIRCLE, Phase.IDLE, Instructions.LONG, Film.OFF)))
                val range = onNodeWithTag(TestTags.Task.INSTRUCTIONS).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
                assertEquals(0f, range.maxValue(), "nothing to scroll at 1000x$windowHeight")
                assertTrue(!exists(TestTags.Task.INSTRUCTIONS_SCROLLBAR), "no scrollbar at 1000x$windowHeight")
                height = bounds(TestTags.Task.INSTRUCTIONS).height.value
            }
            height
        }
        assertEquals(heights[0], heights[1], "a taller window must not stretch the instructions")
        // the same holds for a single short card: no minimum height pads it
        runDesktopComposeUiTest(1000, 1000) {
            show(StateOnlyTask(stateOf(IndicatorType.CIRCLE, Phase.IDLE, Instructions.SHORT, Film.OFF)))
            assertEquals(133f, bounds(TestTags.Task.INSTRUCTIONS).height.value, "short card + example button")
        }
    }

    @Test
    fun `instructions that do not fit take all the space down to the timer and scroll`() = runDesktopComposeUiTest(944, 681) {
        show(StateOnlyTask(stateOf(IndicatorType.CIRCLE, Phase.IDLE, Instructions.LONG, Film.OFF)))

        val instructions = bounds(TestTags.Task.INSTRUCTIONS)
        val start = bounds(TestTags.Task.START_BUTTON)
        // What separates them is the timer's line and the button row's 16 dp top padding (52 dp measured); the
        // pre-change layout left a 160 dp indicator slot there as well (instructions 213 dp tall).
        assertTrue(start.top - instructions.bottom <= 60.dp, "instructions $instructions leave a gap above $start")
        assertTrue(instructions.height >= 400.dp, "instructions only $instructions")
        val range = onNodeWithTag(TestTags.Task.INSTRUCTIONS).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(range.maxValue() > 0f, "long instructions should scroll at 944x681")
        onNodeWithTag(TestTags.Task.INSTRUCTIONS_SCROLLBAR).assertIsDisplayed()
        assertTrue(!exists(TestTags.Task.LEVEL_INDICATOR), "no room left for the indicator")
    }

    // endregion

    // region AC 3: instruction height identical across Idle / Capturing / Stopped

    private fun checkInstructionsHeightStable(
        width: Int,
        height: Int,
        indicator: IndicatorType,
        instructions: Instructions,
        film: Film,
        repetition: Int = 1,
    ) =
        runDesktopComposeUiTest(width, height) {
            val task = StateOnlyTask(stateOf(indicator, Phase.IDLE, instructions, film, repetition = repetition))
            show(task)
            val idle = bounds(TestTags.Task.INSTRUCTIONS)
            for (phase in listOf(Phase.CAPTURING, Phase.STOPPED)) {
                task.set(stateOf(indicator, phase, instructions, film, repetition = repetition))
                waitForIdle()
                val now = bounds(TestTags.Task.INSTRUCTIONS)
                val label = "$indicator $instructions film=$film rep=$repetition at ${width}x$height, Idle $idle vs $phase $now"
                nearly(idle.top.value, now.top.value, "instructions top, $label", 0.5f)
                nearly(idle.height.value, now.height.value, "instructions height, $label", 0.5f)
            }
        }

    @Test
    fun `instruction height is the same in Idle, Capturing and Stopped for both indicators`() {
        for ((w, h) in listOf(1000 to 820, 944 to 681)) {
            for (indicator in IndicatorType.entries) {
                for (instructions in Instructions.entries) {
                    for (film in listOf(Film.OFF, Film.ON)) checkInstructionsHeightStable(w, h, indicator, instructions, film)
                }
            }
        }
    }

    // endregion

    // region AC 2: indicator takes the leftover height, scaled, absent below 80 dp

    @Test
    fun `the indicator is full size with short text in a tall window`() = runDesktopComposeUiTest(1000, 900) {
        val task = StateOnlyTask(stateOf(IndicatorType.CIRCLE, Phase.IDLE, Instructions.SHORT, Film.OFF))
        show(task)
        val circle = bounds(TestTags.Task.LEVEL_INDICATOR)
        assertEquals(160f, circle.width.value, "circle width")
        assertEquals(160f, circle.height.value, "circle height")

        task.set(stateOf(IndicatorType.WAVEFORM, Phase.STOPPED, Instructions.SHORT, Film.OFF))
        waitForIdle()
        val wave = bounds(TestTags.Task.LEVEL_INDICATOR)
        // 500 x 250 including the 4 dp padding the tag sits inside
        assertTrue(wave.width.value in 480f..500f && wave.height.value in 240f..250f, "waveform $wave should be ~500x250")
    }

    @Test
    fun `the indicator is scaled down to the room left and stays square for the circle`() {
        val room = 120
        runDesktopComposeUiTest(1000, windowHeightFor(room)) {
            val task = StateOnlyTask(stateOf(IndicatorType.CIRCLE, Phase.IDLE, Instructions.SHORT, Film.OFF))
            show(task)
            val instructions = bounds(TestTags.Task.INSTRUCTIONS)
            val start = bounds(TestTags.Task.START_BUTTON)
            val circle = bounds(TestTags.Task.LEVEL_INDICATOR)
            // 40 dp of room are spare above the 80 dp minimum, so the full 8 dp gap comes out of the room and the circle gets the rest
            val gap = minOf(GAP, (room - MIN_INDICATOR).toFloat())
            assertEquals(8f, gap, "this room leaves the full gap")
            val middle = room - gap
            nearly(middle, circle.height.value, "circle height should use the room less the actual gap")
            nearly(circle.height.value, circle.width.value, "the circle's canvas stays square")
            assertTrue(circle.top >= instructions.bottom && circle.bottom <= start.top, "circle $circle between $instructions and $start")
            nearly(500f, (circle.left.value + circle.right.value) / 2, "circle centred horizontally")

            task.set(stateOf(IndicatorType.WAVEFORM, Phase.STOPPED, Instructions.SHORT, Film.OFF))
            waitForIdle()
            val wave = bounds(TestTags.Task.LEVEL_INDICATOR)
            // the tag sits inside the box's padding: the box itself is 2 x the inset taller than the tag
            nearly(middle, wave.height.value + 2 * WAVEFORM_TAG_INSET, "waveform box should use the room less the actual gap", 1f)
            assertTrue(wave.top >= instructions.bottom && wave.bottom <= start.top, "waveform $wave between $instructions and $start")
        }
    }

    @Test
    fun `the indicator is absent below 80 dp of room and present at 80 dp`() {
        val min = MIN_INDICATOR
        for (type in IndicatorType.entries) {
            runDesktopComposeUiTest(1000, windowHeightFor(min - 1)) {
                show(StateOnlyTask(stateOf(type, Phase.CAPTURING, Instructions.SHORT, Film.OFF)))
                assertTrue(!exists(TestTags.Task.LEVEL_INDICATOR), "$type indicator should not exist with ${min - 1} dp of room")
            }
            runDesktopComposeUiTest(1000, windowHeightFor(min)) {
                show(StateOnlyTask(stateOf(type, Phase.CAPTURING, Instructions.SHORT, Film.OFF)))
                assertTrue(exists(TestTags.Task.LEVEL_INDICATOR), "$type indicator should exist with $min dp of room")
                assertTrue(bounds(TestTags.Task.LEVEL_INDICATOR).height.value <= min + 1f)
            }
        }
    }

    @Test
    fun `the gap is the full 8 dp whenever 8 dp are spare above the shown minimum, for both indicators and the preview`() {
        val gap = 8f // the required clearance, deliberately not read from the constant under test
        val minIndicator = MIN_INDICATOR
        for (type in IndicatorType.entries) {
            for (room in listOf(minIndicator + 8, 120)) {
                runDesktopComposeUiTest(1000, windowHeightFor(room)) {
                    show(StateOnlyTask(stateOf(type, Phase.CAPTURING, Instructions.SHORT, Film.OFF)))
                    val instructions = bounds(TestTags.Task.INSTRUCTIONS)
                    val indicator = bounds(TestTags.Task.LEVEL_INDICATOR)
                    assertEquals(133f, instructions.height.value, "$type: instructions keep their natural height at $room dp of room")
                    assertTrue(boxTop(type, indicator) - instructions.bottom.value >= gap - 0.5f, "$type at $room dp: indicator box (tag $indicator) is under 8 dp below $instructions")
                }
            }
            // preview and indicator together (the waveform + preview case included), 8 dp spare above the preview block
            runDesktopComposeUiTest(1000, windowHeightFor(previewBlock + 8)) {
                show(StateOnlyTask(stateOf(type, Phase.CAPTURING, Instructions.SHORT, Film.ON)))
                val instructions = bounds(TestTags.Task.INSTRUCTIONS)
                val preview = bounds(TestTags.Task.VIDEO_PREVIEW)
                val indicator = bounds(TestTags.Task.LEVEL_INDICATOR)
                assertEquals(133f, instructions.height.value, "$type: instructions keep their natural height with the preview")
                assertTrue(preview.top.value - instructions.bottom.value >= gap - 0.5f, "$type: preview $preview is under 8 dp below $instructions")
                assertTrue(boxTop(type, indicator) - instructions.bottom.value >= gap - 0.5f, "$type: indicator box (tag $indicator) is under 8 dp below $instructions")
            }
        }
    }

    @Test
    fun `at exactly the thresholds the gap collapses to nothing, so indicator at 80 dp and preview at 198 dp are shown and do not overlap the instructions`() {
        for (type in IndicatorType.entries) {
            runDesktopComposeUiTest(1000, windowHeightFor(MIN_INDICATOR)) {
                show(StateOnlyTask(stateOf(type, Phase.CAPTURING, Instructions.SHORT, Film.OFF)))
                val instructions = bounds(TestTags.Task.INSTRUCTIONS)
                val indicator = bounds(TestTags.Task.LEVEL_INDICATOR)
                assertEquals(133f, instructions.height.value, "$type: instructions untouched at the indicator threshold")
                assertTrue(indicator.top >= instructions.bottom, "$type: indicator $indicator overlaps $instructions")
                // the collapse is what keeps the indicator at its full 80 dp minimum here (the waveform tag sits inside its box)
                val box = if (type == IndicatorType.WAVEFORM) indicator.height.value + 2 * WAVEFORM_TAG_INSET else indicator.height.value
                nearly(MIN_INDICATOR.toFloat(), box, "$type: indicator box at the threshold", 1f)
            }
            runDesktopComposeUiTest(1000, windowHeightFor(previewBlock)) {
                show(StateOnlyTask(stateOf(type, Phase.CAPTURING, Instructions.SHORT, Film.ON)))
                val instructions = bounds(TestTags.Task.INSTRUCTIONS)
                val preview = bounds(TestTags.Task.VIDEO_PREVIEW)
                assertEquals(133f, instructions.height.value, "$type: instructions untouched at the preview threshold")
                assertEquals(150f, preview.height.value, "$type: preview at full size")
                assertTrue(preview.top >= instructions.bottom, "$type: preview $preview overlaps $instructions")
                assertTrue(bounds(TestTags.Task.LEVEL_INDICATOR).top >= instructions.bottom, "$type: indicator overlaps the instructions")
            }
        }
    }

    @Test
    fun `with nothing shown the instructions still fill all the available height and no gap is reserved`() =
        runDesktopComposeUiTest(944, 681) {
            show(StateOnlyTask(stateOf(IndicatorType.CIRCLE, Phase.IDLE, Instructions.LONG, Film.OFF)))
            assertTrue(!exists(TestTags.Task.LEVEL_INDICATOR))
            val instructions = bounds(TestTags.Task.INSTRUCTIONS)
            val start = bounds(TestTags.Task.START_BUTTON)
            // the timer's line and the button row's top padding, as measured before the gap existed
            nearly(52f, start.top.value - instructions.bottom.value, "space between the instructions and the buttons", 1f)
        }

    // endregion

    // region AC 4 / 4a: the camera preview and its message

    private val previewBlock = PREVIEW_THRESHOLD

    @Test
    fun `the preview is shown when its block fits and left out, without shrinking, when it does not`() {
        runDesktopComposeUiTest(1000, windowHeightFor(previewBlock)) {
            show(StateOnlyTask(stateOf(IndicatorType.CIRCLE, Phase.IDLE, Instructions.SHORT, Film.ON)))
            val preview = bounds(TestTags.Task.VIDEO_PREVIEW)
            assertEquals(200f, preview.width.value, "preview width at exactly $previewBlock dp of room")
            assertEquals(150f, preview.height.value, "preview height")
            val indicator = bounds(TestTags.Task.LEVEL_INDICATOR)
            assertTrue(indicator.right <= preview.left, "indicator $indicator is left of the preview $preview")
        }
        runDesktopComposeUiTest(1000, windowHeightFor(previewBlock - 1)) {
            show(StateOnlyTask(stateOf(IndicatorType.CIRCLE, Phase.IDLE, Instructions.SHORT, Film.ON)))
            assertTrue(!exists(TestTags.Task.VIDEO_PREVIEW), "no preview with ${previewBlock - 1} dp of room")
            // the indicator follows its own rule, and on its own is centred
            val indicator = bounds(TestTags.Task.LEVEL_INDICATOR)
            assertEquals(160f, indicator.width.value, "indicator keeps its full size")
            nearly(500f, (indicator.left.value + indicator.right.value) / 2, "indicator centred")
        }
    }

    @Test
    fun `with the preview hidden and the room under 80 dp both are absent, and without showIndicator only the preview shows`() {
        runDesktopComposeUiTest(1000, windowHeightFor(60)) {
            show(StateOnlyTask(stateOf(IndicatorType.CIRCLE, Phase.IDLE, Instructions.SHORT, Film.ON)))
            assertTrue(!exists(TestTags.Task.VIDEO_PREVIEW) && !exists(TestTags.Task.LEVEL_INDICATOR))
            onNodeWithTag(TestTags.Task.START_BUTTON).assertIsDisplayed()
        }
        runDesktopComposeUiTest(1000, windowHeightFor(previewBlock)) {
            show(StateOnlyTask(stateOf(IndicatorType.CIRCLE, Phase.IDLE, Instructions.SHORT, Film.ON, showIndicator = false)))
            onNodeWithTag(TestTags.Task.VIDEO_PREVIEW).assertIsDisplayed()
            assertTrue(!exists(TestTags.Task.LEVEL_INDICATOR))
        }
    }

    @Test
    fun `a failed camera with the preview hidden keeps its message as a line, and only then do the instructions give way`() =
        runDesktopComposeUiTest(944, 681) {
            val task = StateOnlyTask(stateOf(IndicatorType.CIRCLE, Phase.IDLE, Instructions.LONG, Film.OFF))
            show(task)
            val withoutCamera = bounds(TestTags.Task.INSTRUCTIONS)

            task.set(stateOf(IndicatorType.CIRCLE, Phase.IDLE, Instructions.LONG, Film.ON))
            waitForIdle()
            assertTrue(!exists(TestTags.Task.VIDEO_PREVIEW), "no room for the preview")
            assertTrue(!exists(TestTags.Task.VIDEO_AUDIO_ONLY), "a healthy hidden camera has no message")
            assertEquals(withoutCamera.height.value, bounds(TestTags.Task.INSTRUCTIONS).height.value, "instructions keep their height while there is no message")

            task.set(stateOf(IndicatorType.CIRCLE, Phase.IDLE, Instructions.LONG, Film.FAILED))
            waitForIdle()
            assertTrue(!exists(TestTags.Task.VIDEO_PREVIEW), "the failure must not bring the preview back")
            assertEquals(1, count(TestTags.Task.VIDEO_AUDIO_ONLY), "the message is one line in the middle area")
            onNodeWithTag(TestTags.Task.VIDEO_AUDIO_ONLY).assertIsDisplayed()
            val message = bounds(TestTags.Task.VIDEO_AUDIO_ONLY)
            val instructions = bounds(TestTags.Task.INSTRUCTIONS)
            val start = bounds(TestTags.Task.START_BUTTON)
            assertTrue(instructions.bottom <= message.top && message.bottom <= start.top, "message $message between $instructions and $start")
            // the line (text + its 8 dp top padding) came out of the instructions, nothing else
            nearly(withoutCamera.height.value - (message.height.value + 8f), instructions.height.value, "instructions give up exactly the message line", 1.5f)
            onNodeWithTag(TestTags.Task.START_BUTTON).assertIsDisplayed()
            onNodeWithTag(TestTags.Task.NEXT_BUTTON).assertIsDisplayed()
        }

    @Test
    fun `a failed camera with the preview shown has its message once, under the picture, and moves nothing`() =
        runDesktopComposeUiTest(1000, 900) {
            val task = StateOnlyTask(stateOf(IndicatorType.CIRCLE, Phase.IDLE, Instructions.SHORT, Film.ON))
            show(task)
            val before = listOf(TestTags.Task.INSTRUCTIONS, TestTags.Task.LEVEL_INDICATOR, TestTags.Task.VIDEO_PREVIEW).map { bounds(it) }

            task.set(stateOf(IndicatorType.CIRCLE, Phase.IDLE, Instructions.SHORT, Film.FAILED))
            waitForIdle()
            assertEquals(1, count(TestTags.Task.VIDEO_AUDIO_ONLY), "exactly one message with the preview visible")
            onNodeWithTag(TestTags.Task.VIDEO_PREVIEW).assertIsDisplayed()
            assertTrue(bounds(TestTags.Task.VIDEO_AUDIO_ONLY).top >= bounds(TestTags.Task.VIDEO_PREVIEW).bottom, "message under the picture")
            val after = listOf(TestTags.Task.INSTRUCTIONS, TestTags.Task.LEVEL_INDICATOR, TestTags.Task.VIDEO_PREVIEW).map { bounds(it) }
            assertEquals(before, after, "the message must not move the instructions, the indicator or the preview")
        }

    @Test
    fun `showing the camera message never flips the preview, at the edge of fitting`() {
        for (room in listOf(previewBlock, previewBlock - 1)) {
            runDesktopComposeUiTest(1000, windowHeightFor(room)) {
                val task = StateOnlyTask(stateOf(IndicatorType.CIRCLE, Phase.IDLE, Instructions.SHORT, Film.ON))
                show(task)
                val shown = exists(TestTags.Task.VIDEO_PREVIEW)
                assertEquals(room == previewBlock, shown, "preview at $room dp of room")
                for (film in listOf(Film.FAILED, Film.ON, Film.FAILED)) {
                    task.set(stateOf(IndicatorType.CIRCLE, Phase.IDLE, Instructions.SHORT, film))
                    waitForIdle()
                    assertEquals(shown, exists(TestTags.Task.VIDEO_PREVIEW), "preview with film=$film at $room dp of room")
                    assertEquals(if (film == Film.FAILED) 1 else 0, count(TestTags.Task.VIDEO_AUDIO_ONLY), "message count with film=$film at $room dp of room")
                    onNodeWithTag(TestTags.Task.START_BUTTON).assertIsDisplayed()
                }
            }
        }
    }

    // endregion

    // region reading passage, repetitions, audio-only without error, display density

    private val mainButton = mapOf(
        Phase.IDLE to TestTags.Task.START_BUTTON,
        Phase.CAPTURING to TestTags.Task.STOP_BUTTON,
        Phase.STOPPED to TestTags.Task.REPEAT_BUTTON,
    )

    /** Everything that is shown sits between the instructions and the buttons, in order, without overlap (the timer's line is in that gap). */
    private fun ComposeUiTest.assertMiddleIsClear(type: IndicatorType, phase: Phase, label: String) {
        val instructions = bounds(TestTags.Task.INSTRUCTIONS)
        val button = bounds(mainButton.getValue(phase))
        assertTrue(instructions.bottom <= button.top, "$label: instructions $instructions run into the buttons $button")
        if (exists(TestTags.Task.LEVEL_INDICATOR)) {
            val indicator = bounds(TestTags.Task.LEVEL_INDICATOR)
            assertTrue(boxTop(type, indicator) >= instructions.bottom.value - 0.5f, "$label: indicator $indicator overlaps the instructions $instructions")
            assertTrue(indicator.bottom <= button.top, "$label: indicator $indicator overlaps the timer/buttons $button")
            if (exists(TestTags.Task.VIDEO_PREVIEW)) {
                assertTrue(indicator.right <= bounds(TestTags.Task.VIDEO_PREVIEW).left, "$label: indicator $indicator overlaps the preview")
            }
        }
        if (exists(TestTags.Task.VIDEO_PREVIEW)) {
            val preview = bounds(TestTags.Task.VIDEO_PREVIEW)
            assertTrue(preview.top.value >= instructions.bottom.value - 0.5f, "$label: preview $preview overlaps the instructions $instructions")
            assertTrue(preview.bottom <= button.top, "$label: preview $preview overlaps the timer/buttons $button")
        }
        onNodeWithTag(mainButton.getValue(phase)).assertIsDisplayed()
        onNodeWithTag(TestTags.Task.NEXT_BUTTON).assertIsDisplayed()
    }

    @Test
    fun `a reading passage longer than its 320 dp cap stays capped and the layout around it stays whole`() {
        // short card + example row (133) + the 10 dp between children + the capped passage panel
        val natural = 133f + 10f + 320f
        fun reading(phase: Phase = Phase.IDLE, indicator: IndicatorType = IndicatorType.CIRCLE) =
            stateOf(indicator, phase, Instructions.SHORT, Film.OFF, readingPassageKey = "ins.passage")

        // room to spare: instructions take exactly natural, no scrolling, indicator below them
        runDesktopComposeUiTest(1000, 1500) {
            val task = StateOnlyTask(reading())
            show(task)
            assertEquals(320f, bounds(TestTags.Task.READING_PASSAGE).height.value, "passage capped at 320 dp")
            nearly(natural, bounds(TestTags.Task.INSTRUCTIONS).height.value, "instructions height with a capped passage")
            val range = onNodeWithTag(TestTags.Task.INSTRUCTIONS).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            assertEquals(0f, range.maxValue(), "nothing to scroll: the capped passage fits")
            assertTrue(!exists(TestTags.Task.INSTRUCTIONS_SCROLLBAR))
            assertTrue(exists(TestTags.Task.LEVEL_INDICATOR), "indicator has room")
            val idle = bounds(TestTags.Task.INSTRUCTIONS)
            for (phase in listOf(Phase.IDLE, Phase.CAPTURING, Phase.STOPPED)) {
                task.set(reading(phase, IndicatorType.WAVEFORM))
                waitForIdle()
                assertMiddleIsClear(IndicatorType.WAVEFORM, phase, "reading, room to spare, $phase")
                nearly(idle.height.value, bounds(TestTags.Task.INSTRUCTIONS).height.value, "instructions height in $phase", 0.5f)
            }
        }
        // 79 dp left under them: the indicator is left out, the instructions are not squeezed, nothing is clipped
        runDesktopComposeUiTest(1000, windowHeightFor(MIN_INDICATOR - 1, natural.toInt())) {
            show(StateOnlyTask(reading(Phase.IDLE)))
            nearly(natural, bounds(TestTags.Task.INSTRUCTIONS).height.value, "instructions keep their natural height")
            assertTrue(!exists(TestTags.Task.LEVEL_INDICATOR), "no indicator with ${MIN_INDICATOR - 1} dp of room")
            assertMiddleIsClear(IndicatorType.CIRCLE, Phase.IDLE, "reading, 79 dp of room")
        }
        // exactly 80 dp: the indicator is shown and touches neither the passage nor the buttons
        runDesktopComposeUiTest(1000, windowHeightFor(MIN_INDICATOR, natural.toInt())) {
            show(StateOnlyTask(reading(Phase.CAPTURING)))
            nearly(natural, bounds(TestTags.Task.INSTRUCTIONS).height.value, "instructions keep their natural height")
            assertTrue(exists(TestTags.Task.LEVEL_INDICATOR), "indicator at exactly $MIN_INDICATOR dp")
            assertMiddleIsClear(IndicatorType.CIRCLE, Phase.CAPTURING, "reading, 80 dp of room")
        }
        // 100 dp short of natural: the field takes everything down to the timer and scrolls; the passage panel is still capped
        runDesktopComposeUiTest(1000, windowHeightFor(0, natural.toInt()) - 100) {
            show(StateOnlyTask(reading()))
            assertEquals(320f, bounds(TestTags.Task.READING_PASSAGE).height.value, "passage still 320 dp inside the scrolling field")
            assertTrue(!exists(TestTags.Task.LEVEL_INDICATOR), "no indicator when the instructions overflow")
            val range = onNodeWithTag(TestTags.Task.INSTRUCTIONS).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            nearly(100f, range.maxValue(), "scrollable distance", 2f)
            onNodeWithTag(TestTags.Task.INSTRUCTIONS_SCROLLBAR).assertIsDisplayed()
            assertMiddleIsClear(IndicatorType.CIRCLE, Phase.IDLE, "reading, overflowing")
            assertTrue(bounds(TestTags.Task.START_BUTTON).top - bounds(TestTags.Task.INSTRUCTIONS).bottom <= 60.dp, "the field reaches down to the timer")
        }
    }

    @Test
    fun `from the second repetition the second card is hidden, so the instructions are shorter and their height is stable`() {
        var first = 0f
        var second = 0f
        runDesktopComposeUiTest(1000, 1500) {
            show(StateOnlyTask(stateOf(IndicatorType.CIRCLE, Phase.IDLE, Instructions.LONG, Film.OFF, repetition = 1)))
            first = bounds(TestTags.Task.INSTRUCTIONS).height.value
        }
        runDesktopComposeUiTest(1000, 1500) {
            show(StateOnlyTask(stateOf(IndicatorType.CIRCLE, Phase.IDLE, Instructions.LONG, Film.OFF, repetition = 2)))
            second = bounds(TestTags.Task.INSTRUCTIONS).height.value
        }
        // two long cards plus the example row against the first card alone: about half
        assertTrue(second < first - 200f, "repetition 2 instructions ($second dp) should be much shorter than repetition 1 ($first dp)")
        assertTrue(second > 100f, "the first card is still there ($second dp)")
        runDesktopComposeUiTest(1000, 1500) {
            show(StateOnlyTask(stateOf(IndicatorType.CIRCLE, Phase.IDLE, Instructions.SHORT, Film.OFF, repetition = 2)))
            assertTrue(bounds(TestTags.Task.INSTRUCTIONS).height.value < 133f, "the example row goes with the second card")
        }
        for ((w, h) in listOf(1000 to 1500, 1000 to 820, 944 to 681)) {
            for (indicator in IndicatorType.entries) {
                for (instructions in Instructions.entries) {
                    for (film in listOf(Film.OFF, Film.ON)) checkInstructionsHeightStable(w, h, indicator, instructions, film, repetition = 2)
                }
            }
        }
    }

    @Test
    fun `an audio-only camera without an error shows its message once on its own line when the preview is hidden`() {
        runDesktopComposeUiTest(1000, windowHeightFor(previewBlock - 1)) {
            val task = StateOnlyTask(stateOf(IndicatorType.CIRCLE, Phase.IDLE, Instructions.SHORT, Film.AUDIO_ONLY))
            show(task)
            for (phase in Phase.entries) {
                task.set(stateOf(IndicatorType.CIRCLE, phase, Instructions.SHORT, Film.AUDIO_ONLY))
                waitForIdle()
                assertTrue(!exists(TestTags.Task.VIDEO_PREVIEW), "preview hidden with ${previewBlock - 1} dp of room, $phase")
                assertEquals(1, count(TestTags.Task.VIDEO_AUDIO_ONLY), "the message shows exactly once, $phase")
                assertEquals(0, count(TestTags.Task.VIDEO_ERROR), "there is no error to show, $phase")
                onNodeWithTag(TestTags.Task.VIDEO_AUDIO_ONLY).assertIsDisplayed()
                val message = bounds(TestTags.Task.VIDEO_AUDIO_ONLY)
                assertTrue(
                    bounds(TestTags.Task.INSTRUCTIONS).bottom <= message.top && message.bottom <= bounds(mainButton.getValue(phase)).top,
                    "message $message is between the instructions and the buttons, $phase",
                )
                assertMiddleIsClear(IndicatorType.CIRCLE, phase, "audio-only, $phase")
            }
        }
        // with the preview shown the one message is under the picture, not an extra line
        runDesktopComposeUiTest(1000, windowHeightFor(previewBlock)) {
            show(StateOnlyTask(stateOf(IndicatorType.CIRCLE, Phase.IDLE, Instructions.SHORT, Film.AUDIO_ONLY)))
            assertTrue(exists(TestTags.Task.VIDEO_PREVIEW))
            assertEquals(1, count(TestTags.Task.VIDEO_AUDIO_ONLY), "the message shows exactly once beside the preview")
            assertTrue(bounds(TestTags.Task.VIDEO_AUDIO_ONLY).top >= bounds(TestTags.Task.VIDEO_PREVIEW).bottom, "message under the picture")
        }
    }

    @Test
    fun `at display densities 1_5 and 2_0 the instruction height is stable and nothing overlaps or overflows`() {
        for (density in listOf(1.5f, 2.0f)) {
            for ((widthDp, heightDp) in listOf(1000 to 820, 944 to 681, 800 to 600)) {
                for (indicator in IndicatorType.entries) {
                    for (instructions in Instructions.entries) {
                        for (film in listOf(Film.OFF, Film.ON, Film.AUDIO_ONLY)) {
                            runSkikoComposeUiTest(Size(widthDp * density, heightDp * density), Density(density)) {
                                val task = StateOnlyTask(stateOf(indicator, Phase.IDLE, instructions, film))
                                show(task)
                                // proves the density took effect: the window is widthDp wide in dp, not in px
                                nearly(widthDp.toFloat(), bounds(TestTags.Task.CONTENT).width.value, "content width in dp at density $density", 0.5f)
                                val idle = bounds(TestTags.Task.INSTRUCTIONS)
                                for (phase in Phase.entries) {
                                    task.set(stateOf(indicator, phase, instructions, film))
                                    waitForIdle()
                                    val label = "density $density, ${widthDp}x$heightDp dp, $indicator $instructions film=$film $phase"
                                    val now = bounds(TestTags.Task.INSTRUCTIONS)
                                    nearly(idle.top.value, now.top.value, "instructions top, $label", 0.5f)
                                    nearly(idle.height.value, now.height.value, "instructions height, $label", 0.5f)
                                    assertMiddleIsClear(indicator, phase, label)
                                    for (tag in listOf(TestTags.Task.INSTRUCTIONS, TestTags.Task.LEVEL_INDICATOR, TestTags.Task.VIDEO_PREVIEW)) {
                                        if (exists(tag)) assertTrue(bounds(tag).bottom.value <= heightDp + 0.5f, "$tag below the window, $label")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // endregion

    // region AC 5: Start/Stop and Next stay visible

    @Test
    fun `Start Stop Repeat and Next stay displayed at 944x681 and 784x561 with long instructions`() {
        for ((w, h) in listOf(944 to 681, 784 to 561)) {
            for (indicator in IndicatorType.entries) {
                for (film in Film.entries) {
                    for (phase in Phase.entries) {
                        runDesktopComposeUiTest(w, h) {
                            show(StateOnlyTask(stateOf(indicator, phase, Instructions.LONG, film)))
                            val main = when (phase) {
                                Phase.IDLE -> TestTags.Task.START_BUTTON
                                Phase.CAPTURING -> TestTags.Task.STOP_BUTTON
                                Phase.STOPPED -> TestTags.Task.REPEAT_BUTTON
                            }
                            for (tag in listOf(main, TestTags.Task.NEXT_BUTTON)) {
                                assertTrue(exists(tag), "$tag missing: $indicator film=$film $phase at ${w}x$h")
                                onNodeWithTag(tag).assertIsDisplayed()
                                val b = bounds(tag)
                                assertTrue(b.bottom <= h.dp && b.right <= w.dp, "$tag $b outside ${w}x$h: $indicator film=$film $phase")
                            }
                        }
                    }
                }
            }
        }
    }

    // endregion

    // region AC 6: other task types keep their weighted middle area

    private fun defaultComponent(task: Task): TaskComponent {
        val recorder = FakeContinuousSessionRecorder(FakeClock())
        kotlinx.coroutines.runBlocking { recorder.startWriting(Path.of("master.wav")) }
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

    @Test
    fun `a questionnaire task still fills the weighted middle area down to the timer`() = runDesktopComposeUiTest(1000, 820) {
        val task = QuestionnaireTask(
            titleKey = "q",
            questions = listOf(Question(QuestionType.OPEN, "age", "q.age", questionRegex = ".*")),
        )
        show(defaultComponent(task))

        val frame = bounds(TestTags.Questionnaire.FRAME)
        val next = bounds(TestTags.Task.NEXT_BUTTON)
        // frame, then the timer's line and the button row's top padding (52 dp) - however short the question list is
        assertTrue(next.top - frame.bottom <= 60.dp, "questionnaire frame $frame does not reach down to $next")
        assertTrue(frame.height >= 400.dp, "questionnaire frame only $frame")
        assertTrue(!exists(TestTags.Task.INSTRUCTIONS), "non-scrolling instructions branch is unchanged")
    }

    @Test
    fun `a standalone video task still fills the weighted middle area with a full-size preview`() = runDesktopComposeUiTest(1000, 820) {
        show(defaultComponent(VideoTask(titleKey = "v")))

        val preview = bounds(TestTags.Task.VIDEO_PREVIEW)
        val start = bounds(TestTags.Task.START_BUTTON)
        // picture, the 32 dp message slot, the timer line and the buttons' 16 dp top padding
        assertTrue(start.top - preview.bottom <= 100.dp, "video preview $preview does not reach down to $start")
        assertTrue(preview.height >= 400.dp, "video preview only $preview")
        assertTrue(!exists(TestTags.Task.INSTRUCTIONS), "non-scrolling instructions branch is unchanged")
    }

    // endregion

    // region AC 4: hiding the preview does not touch the capture

    /** What the capture side saw during one take, observed at the recorder rather than the screen. */
    private data class CaptureTrace(val recordingStarts: Int, val stopRecordings: Int, val previewStarts: Int, val framesWritten: Long, val fileBytes: Long)

    private fun captureTrace(instructions: Instructions, expectPreview: Boolean): CaptureTrace {
        var trace: CaptureTrace? = null
        val dir = Files.createTempDirectory("capture-independence")
        try {
            runDesktopComposeUiTest(1000, 1000) {
                val recorder = FakeSessionVideoRecorder()
                kotlinx.coroutines.runBlocking {
                    recorder.startPreview(VideoInputDevice(id = "cam", name = "Cam", nameOrdinal = 0, platformIndex = 0, eligible = true))
                }
                val master = FakeContinuousSessionRecorder(FakeClock())
                kotlinx.coroutines.runBlocking { master.startWriting(Path.of("master.wav")) }
                val task = VocalTask(titleKey = "t", subtype = VocalSubtype.PHONATION, instructionKeys = instructions.keys, recordVideo = true)
                val component = DefaultTaskComponent(
                    componentContext = DefaultComponentContext(LifecycleRegistry()),
                    taskInstance = TaskInstance(taskIndex = 0, repetition = 1, task = task),
                    recorder = master,
                    dispatchers = TestCoroutineDispatchers(),
                    positionInProtocol = 1,
                    totalInstanceCount = 1,
                    availableDevices = listOf(device),
                    currentDevice = device,
                    resolvedAudioExamplePath = null,
                    audioPlaybackService = FakeAudioPlaybackService(),
                    eventLogger = { _, _, _ -> },
                    videoFrames = recorder.previewFrames,
                    videoState = recorder.state,
                    nextPartFile = { Path.of("part2.wav") },
                    onResumeRecorded = { _, _ -> },
                    onTaskFinished = {},
                    onVideoTakeStarted = { take -> kotlinx.coroutines.runBlocking { recorder.startRecording(dir.resolve("take$take.mjpeg")) } },
                    onVideoTakeStopped = { kotlinx.coroutines.runBlocking { recorder.stopRecording() } },
                )
                show(component)
                assertEquals(expectPreview, exists(TestTags.Task.VIDEO_PREVIEW), "preview presence for $instructions")

                onNodeWithTag(TestTags.Task.START_BUTTON).performClick()
                waitForIdle()
                repeat(5) { recorder.emitFrame() }
                waitForIdle()
                onNodeWithTag(TestTags.Task.STOP_BUTTON).performClick()
                waitForIdle()

                trace = CaptureTrace(
                    recordingStarts = recorder.recordingStarts.size,
                    stopRecordings = recorder.stopRecordingCallCount,
                    previewStarts = recorder.previewStarts.size,
                    framesWritten = recorder.framesWritten.value,
                    fileBytes = Files.size(recorder.recordingStarts.single()),
                )
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
        return trace!!
    }

    @Test
    fun `hiding the preview leaves the capture calls and the recorded frames unchanged`() {
        val withPreview = captureTrace(Instructions.SHORT, expectPreview = true)
        val withoutPreview = captureTrace(Instructions.LONG, expectPreview = false)

        assertEquals(1, withPreview.recordingStarts)
        assertEquals(1, withPreview.stopRecordings)
        assertEquals(5L, withPreview.framesWritten)
        assertTrue(withPreview.fileBytes > 0, "the take wrote frames")
        assertEquals(withPreview, withoutPreview, "capture with the preview composed vs hidden")
    }

    // endregion

    // region existing intents, restated for the new priority

    private fun checkPreviewAndIndicatorFit(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        show(defaultComponent(VocalTask(titleKey = "t", subtype = VocalSubtype.PHONATION, instructionKeys = listOf("ins.short"), recordVideo = true)))

        val preview = bounds(TestTags.Task.VIDEO_PREVIEW)
        assertTrue(preview.width >= 200.dp && preview.height >= 150.dp, "preview at ${width}x$height was $preview")
        val indicator = bounds(TestTags.Task.LEVEL_INDICATOR)
        assertTrue(indicator.width >= 160.dp && indicator.height >= 160.dp, "indicator at ${width}x$height was $indicator")
        onNodeWithTag(TestTags.Task.VIDEO_PREVIEW).assertIsDisplayed()
        onNodeWithTag(TestTags.Task.START_BUTTON).assertIsDisplayed()
        onNodeWithTag(TestTags.Task.NEXT_BUTTON).assertIsDisplayed()
        assertTrue(bounds(TestTags.Task.INSTRUCTIONS).height >= 110.dp, "instructions at ${width}x$height were ${bounds(TestTags.Task.INSTRUCTIONS)}")
    }

    @Test
    fun `a filming phonation task with short instructions keeps its preview and indicator at 1000x820`() = checkPreviewAndIndicatorFit(1000, 820)

    @Test
    fun `a filming phonation task with short instructions keeps its preview and indicator at 800x600`() = checkPreviewAndIndicatorFit(800, 600)

    @Test
    fun `a filming phonation task drops its preview but keeps the indicator and controls in the 784x561 content of an 800x600 window`() =
        runDesktopComposeUiTest(784, 561) {
            // Below the 944x681 minimum window: with the single short card only ~163 dp is left, less than the preview's 198 dp
            show(defaultComponent(VocalTask(titleKey = "t", subtype = VocalSubtype.PHONATION, instructionKeys = listOf("ins.short"), recordVideo = true)))

            assertTrue(!exists(TestTags.Task.VIDEO_PREVIEW), "the preview does not fit")
            val indicator = bounds(TestTags.Task.LEVEL_INDICATOR)
            // the 8 dp gap under the instructions may take a little of the ~163 dp left, so the circle is scaled, still square
            assertTrue(indicator.width >= 150.dp && abs(indicator.width.value - indicator.height.value) <= 1f, "indicator was $indicator")
            onNodeWithTag(TestTags.Task.START_BUTTON).assertIsDisplayed()
            onNodeWithTag(TestTags.Task.NEXT_BUTTON).assertIsDisplayed()
        }

    @Test
    fun `long instructions scroll in a short window`() =
        runDesktopComposeUiTest(1000, 820) {
            show(defaultComponent(VocalTask(titleKey = "t", subtype = VocalSubtype.PHONATION, instructionKeys = listOf("ins.first", "ins.second"), recordVideo = true)))

            val range = onNodeWithTag(TestTags.Task.INSTRUCTIONS).fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue(range.maxValue() > 0f, "long instructions should scroll at 1000x820")
            onNodeWithTag(TestTags.Task.INSTRUCTIONS_SCROLLBAR).assertIsDisplayed()
        }

    @Test
    fun `a tall window shows the whole instruction text without scrolling`() = runDesktopComposeUiTest(1000, 1400) {
        show(defaultComponent(VocalTask(titleKey = "t", subtype = VocalSubtype.PHONATION, instructionKeys = listOf("ins.first", "ins.second"), recordVideo = true)))

        val range = onNodeWithTag(TestTags.Task.INSTRUCTIONS).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals(0f, range.maxValue(), "nothing should be left to scroll to at 1000x1400")
        onNodeWithTag(TestTags.Task.INSTRUCTIONS_SCROLLBAR).assertDoesNotExist()
        onNodeWithTag(TestTags.Task.VIDEO_PREVIEW).assertIsDisplayed()
    }

    @Test
    fun `a PATAKA indicator keeps its size with short instructions and gives way to long ones`() {
        runDesktopComposeUiTest(1000, 820) {
            show(defaultComponent(VocalTask(titleKey = "t", subtype = VocalSubtype.PATAKA, instructionKeys = listOf("ins.short"))))
            val indicator = bounds(TestTags.Task.LEVEL_INDICATOR)
            assertTrue(indicator.width >= 160.dp && indicator.height >= 160.dp, "PATAKA indicator was $indicator")
            onNodeWithTag(TestTags.Task.LEVEL_INDICATOR).assertIsDisplayed()
        }
        runDesktopComposeUiTest(1000, 820) {
            show(defaultComponent(VocalTask(titleKey = "t", subtype = VocalSubtype.PATAKA, instructionKeys = listOf("ins.first", "ins.second"))))
            assertTrue(!exists(TestTags.Task.LEVEL_INDICATOR), "long instructions use all the room")
            onNodeWithTag(TestTags.Task.START_BUTTON).assertIsDisplayed()
        }
    }

    // endregion
}
