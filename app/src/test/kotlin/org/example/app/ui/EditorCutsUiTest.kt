package org.example.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.domain.audio.CaptureFormat
import org.example.app.domain.localization.LocalizedStringProvider
import org.example.app.domain.session.Examination
import org.example.app.domain.session.TaskRecord
import org.example.app.domain.session.VideoTakeRecord
import org.example.app.domain.timeline.TimelineEvent
import org.example.app.domain.timeline.TimelineEventType
import org.example.app.domain.timeline.TimelineOriginal
import org.example.app.domain.video.VideoCaptureFormat
import org.example.app.fakes.FakeAudioPlaybackService
import org.example.app.fakes.FakeSessionRepository
import org.example.app.fakes.FakeTimelineRepository
import org.example.app.fakes.FakeWaveformService
import org.example.app.fakes.TestCoroutineDispatchers
import org.example.app.navigation.DefaultEditorComponent
import org.example.app.ui.theme.ShareTheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Enhanced editor UI (selection, cuts, zoom) against the real component; setting off shows none of it. */
@OptIn(ExperimentalTestApi::class)
class EditorCutsUiTest {

    /** One 20 s VOCAL segment (1000..21000 at 1 kHz) in a 40 s recording. */
    private fun component(enhanced: Boolean, withVideo: Boolean = false, dispatchers: TestCoroutineDispatchers): DefaultEditorComponent {
        val sessionRepository = FakeSessionRepository()
        val timelineRepository = FakeTimelineRepository()
        val folderName = "folder-1"
        val format = CaptureFormat(sampleRate = 1000, bits = 16, channels = 1)
        val events = listOf(
            TimelineEvent(TimelineEventType.START_BUTTON_PRESSED, 1000L, "t", 0, 1, 1),
            TimelineEvent(TimelineEventType.STOP_BUTTON_PRESSED, 21000L, "t", 0, 1, 1),
            TimelineEvent(TimelineEventType.SESSION_RECORDING_STOPPED, 40000L, "t", null, null, null),
        )
        timelineRepository.writeOriginal(folderName, TimelineOriginal(sessionId = "session-1", sampleRate = format.sampleRate, events = events))
        sessionRepository.writeExamination(
            folderName,
            Examination(
                sessionId = "session-1",
                installationId = "install-1",
                protocolName = "Share",
                configVersion = "v1",
                startedAt = "2026-07-03T09:00:00Z",
                captureFormat = format,
                tasks = listOf(TaskRecord(taskIndex = 0, type = "VOCAL", subtype = "PHONATION", repetition = 1, takes = 1)),
                videoTakes = if (withVideo) {
                    listOf(VideoTakeRecord("video/task00_rep01_take01.mjpeg", 0, 1, 1, VideoCaptureFormat(320, 240, 30)))
                } else {
                    emptyList()
                },
            ),
        )
        return DefaultEditorComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            folderName = folderName,
            sessionRepository = sessionRepository,
            timelineRepository = timelineRepository,
            waveformService = FakeWaveformService(),
            audioPlaybackService = FakeAudioPlaybackService(),
            dispatchers = dispatchers,
            onDone = { _, _ -> },
            onDiscarded = {},
            enhanced = enhanced,
        )
    }

    private fun run(
        enhanced: Boolean = true,
        withVideo: Boolean = false,
        body: ComposeUiTest.(DefaultEditorComponent) -> Unit,
    ) = runDesktopComposeUiTest(1100, 900) {
        val dispatchers = TestCoroutineDispatchers()
        val component = component(enhanced, withVideo, dispatchers)
        dispatchers.scheduler.advanceUntilIdle()
        setContent { ShareTheme { EditorContent(component, UiLocalization(LocalizedStringProvider(), "en", null)) } }
        waitForIdle()
        body(component)
    }

    private fun ComposeUiTest.dragOnCanvas(fromX: Float, toX: Float) {
        onNodeWithTag(TestTags.Editor.WAVEFORM_CANVAS).performMouseInput {
            val y = height / 2f
            moveTo(Offset(fromX, y))
            press()
            moveTo(Offset((fromX + toX) / 2f, y))
            moveTo(Offset(toX, y))
            release()
        }
        waitForIdle()
    }

    private fun ComposeUiTest.select() {
        // Samples are ~28.9 per px at this window; 300..500 px is well inside 1000..21000.
        dragOnCanvas(300f, 500f)
    }

    @Test
    fun `setting off shows no enhanced controls`() = run(enhanced = false) {
        onNodeWithTag(TestTags.Editor.WAVEFORM_CANVAS).assertIsDisplayed()
        listOf(
            TestTags.Editor.ZOOM_IN_BUTTON, TestTags.Editor.ZOOM_OUT_BUTTON, TestTags.Editor.ZOOM_FIT_BUTTON,
            TestTags.Editor.PLAY_SELECTION_BUTTON, TestTags.Editor.CUT_SELECTION_BUTTON, TestTags.Editor.CLEAR_SELECTION_BUTTON,
        ).forEach { tag ->
            assertTrue(onAllNodesWithTagCount(tag) == 0, "$tag must not exist when the setting is off")
        }
    }

    private fun ComposeUiTest.onAllNodesWithTagCount(tag: String) =
        onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size

    @Test
    fun `setting on shows toolbar and selection buttons`() = run {
        listOf(
            TestTags.Editor.ZOOM_IN_BUTTON, TestTags.Editor.ZOOM_OUT_BUTTON, TestTags.Editor.ZOOM_FIT_BUTTON,
            TestTags.Editor.PLAY_SELECTION_BUTTON, TestTags.Editor.CUT_SELECTION_BUTTON, TestTags.Editor.CLEAR_SELECTION_BUTTON,
        ).forEach { onNodeWithTag(it).assertIsDisplayed() }
        assertEquals(0, onAllNodesWithTagCount(TestTags.Editor.SELECTION_LABEL))
        assertEquals(0, onAllNodesWithTagCount(TestTags.Editor.PAN_BAR))
    }

    @Test
    fun `dragging on the waveform creates a selection`() = run { component ->
        assertNull(component.state.value.selection)
        select()
        val selection = component.state.value.selection
        assertNotNull(selection)
        assertTrue(selection!!.length > 1000, "selection $selection")
        onNodeWithTag(TestTags.Editor.SELECTION_LABEL).assertIsDisplayed()
        onNodeWithTag(TestTags.Editor.SELECTION_BAND).assertIsDisplayed()
    }

    @Test
    fun `a plain click still seeks and creates no selection`() = run { component ->
        onNodeWithTag(TestTags.Editor.WAVEFORM_CANVAS).performMouseInput { moveTo(Offset(400f, 100f)); press(); release() }
        waitForIdle()
        assertNull(component.state.value.selection)
        assertTrue((component.state.value.positionSample ?: 0L) > 5000L)
    }

    @Test
    fun `cut button makes a cut band and restore removes it`() = run { component ->
        select()
        onNodeWithTag(TestTags.Editor.CUT_SELECTION_BUTTON).performClick()
        waitForIdle()
        assertEquals(1, component.state.value.currentSegment!!.cutCount)
        onNodeWithTag(TestTags.Editor.cutBand(0)).assertIsDisplayed()
        onNodeWithTag(TestTags.Editor.restoreCutButton(0)).assertIsDisplayed()
        onNodeWithTag(TestTags.Editor.DURATION_LABEL).assertIsDisplayed()

        onNodeWithTag(TestTags.Editor.restoreCutButton(0)).performClick()
        waitForIdle()
        assertEquals(0, component.state.value.currentSegment!!.cutCount)
        assertEquals(0, onAllNodesWithTagCount(TestTags.Editor.cutBand(0)))
    }

    @Test
    fun `delete key cuts and escape clears`() = run { component ->
        select()
        onAllNodes(isRoot())[0].performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertNull(component.state.value.selection)

        select()
        onAllNodes(isRoot())[0].performKeyInput { pressKey(Key.Delete) }
        waitForIdle()
        assertEquals(1, component.state.value.currentSegment!!.cutCount)
    }

    @Test
    fun `a rejected cut shows the message`() = run { component ->
        // From just inside the start marker to beyond the stop marker: nothing meaningful would remain.
        dragOnCanvas(52f, 1090f)
        assertNotNull(component.state.value.selection)
        onNodeWithTag(TestTags.Editor.CUT_SELECTION_BUTTON).performClick()
        waitForIdle()
        assertNotNull(component.state.value.lastCutRejected)
        onNodeWithTag(TestTags.Editor.CUT_REJECTION_MESSAGE).assertIsDisplayed()
    }

    @Test
    fun `video note shows only when a video task is cut`() = run(withVideo = true) { component ->
        assertEquals(0, onAllNodesWithTagCount(TestTags.Editor.VIDEO_NOT_CUT_NOTE))
        select()
        onNodeWithTag(TestTags.Editor.CUT_SELECTION_BUTTON).performClick()
        waitForIdle()
        onNodeWithTag(TestTags.Editor.VIDEO_NOT_CUT_NOTE).assertIsDisplayed()
        assertTrue(component.state.value.currentSegment!!.videoNotCut)
    }

    @Test
    fun `waveform height does not change when the video note or rejection message appears`() = run(withVideo = true) { component ->
        fun canvasHeight() = onNodeWithTag(TestTags.Editor.WAVEFORM_CANVAS).fetchSemanticsNode().size.height
        val before = canvasHeight()
        select()
        onNodeWithTag(TestTags.Editor.CUT_SELECTION_BUTTON).performClick()
        waitForIdle()
        onNodeWithTag(TestTags.Editor.VIDEO_NOT_CUT_NOTE).assertIsDisplayed()
        assertEquals(before, canvasHeight(), "video note must not resize the waveform")

        dragOnCanvas(52f, 1090f)
        onNodeWithTag(TestTags.Editor.CUT_SELECTION_BUTTON).performClick()
        waitForIdle()
        if (component.state.value.lastCutRejected != null) {
            onNodeWithTag(TestTags.Editor.CUT_REJECTION_MESSAGE).assertIsDisplayed()
        }
        assertEquals(before, canvasHeight(), "rejection message must not resize the waveform")

        onNodeWithTag(TestTags.Editor.restoreCutButton(0)).performClick()
        waitForIdle()
        assertEquals(before, canvasHeight(), "removing the note must not resize the waveform")
    }

    @Test
    fun `zoom buttons change the visible window and fit restores it`() = run { component ->
        val fit = component.state.value.let { it.visibleStartSample to it.visibleStopSample }
        onNodeWithTag(TestTags.Editor.ZOOM_IN_BUTTON).performClick()
        waitForIdle()
        val zoomed = component.state.value
        assertTrue(zoomed.visibleStopSample - zoomed.visibleStartSample < fit.second - fit.first)
        assertTrue(zoomed.zoomed)
        onNodeWithTag(TestTags.Editor.PAN_BAR).assertIsDisplayed()

        onNodeWithTag(TestTags.Editor.ZOOM_FIT_BUTTON).performClick()
        waitForIdle()
        assertEquals(fit, component.state.value.visibleStartSample to component.state.value.visibleStopSample)
        assertEquals(0, onAllNodesWithTagCount(TestTags.Editor.PAN_BAR))
    }

    @Test
    fun `dragging the pan bar pans the window`() = run { component ->
        onNodeWithTag(TestTags.Editor.ZOOM_IN_BUTTON).performClick()
        waitForIdle()
        val before = component.state.value.visibleStartSample
        onNodeWithTag(TestTags.Editor.PAN_BAR).performMouseInput {
            moveTo(Offset(width * 0.1f, height / 2f))
            press()
            moveTo(Offset(width * 0.3f, height / 2f))
            moveTo(Offset(width * 0.5f, height / 2f))
            release()
        }
        waitForIdle()
        assertTrue(component.state.value.visibleStartSample != before)
    }
}
