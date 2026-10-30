package org.example.app.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.domain.audio.CaptureFormat
import org.example.app.domain.localization.LocalizedStringProvider
import org.example.app.domain.session.Examination
import org.example.app.domain.session.TaskRecord
import org.example.app.domain.timeline.TimelineEvent
import org.example.app.domain.timeline.TimelineEventType
import org.example.app.domain.timeline.TimelineOriginal
import org.example.app.fakes.FakeAudioPlaybackService
import org.example.app.fakes.FakeSessionRepository
import org.example.app.fakes.FakeTimelineRepository
import org.example.app.fakes.FakeWaveformService
import org.example.app.fakes.TestCoroutineDispatchers
import org.example.app.navigation.DefaultEditorComponent
import org.example.app.ui.theme.ShareTheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** F-025 (repetition in the segment label) and F-130 (control groups) at the UI level. */
@OptIn(ExperimentalTestApi::class)
class EditorLabelAndGroupsUiTest {

    /** One VOCAL instance per (subtype, repetition) pair, in order. */
    private fun component(tasks: List<Pair<String, Int>>, dispatchers: TestCoroutineDispatchers): DefaultEditorComponent {
        val sessionRepository = FakeSessionRepository()
        val timelineRepository = FakeTimelineRepository()
        val folderName = "folder-1"
        val format = CaptureFormat(sampleRate = 1000, bits = 16, channels = 1)
        val events = buildList {
            tasks.forEachIndexed { i, (_, rep) ->
                add(TimelineEvent(TimelineEventType.START_BUTTON_PRESSED, 100L + i * 1000, "t", i, rep, 1))
                add(TimelineEvent(TimelineEventType.STOP_BUTTON_PRESSED, 600L + i * 1000, "t", i, rep, 1))
            }
            add(TimelineEvent(TimelineEventType.SESSION_RECORDING_STOPPED, 1000L * tasks.size + 1000, "t", null, null, null))
        }
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
                tasks = tasks.mapIndexed { i, (subtype, rep) ->
                    TaskRecord(taskIndex = i, type = "VOCAL", subtype = subtype, repetition = rep, takes = 1)
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
        )
    }

    private fun ComposeUiTest.show(tasks: List<Pair<String, Int>>) {
        val dispatchers = TestCoroutineDispatchers()
        val component = component(tasks, dispatchers)
        dispatchers.scheduler.advanceUntilIdle()
        setContent { ShareTheme { EditorContent(component, UiLocalization(LocalizedStringProvider(), "en", null)) } }
        waitForIdle()
    }

    private fun ComposeUiTest.label(): String =
        onNodeWithTag(TestTags.Editor.SEGMENT_LABEL).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text]
            .joinToString("") { it.text }

    @Test
    fun `F-025 the segment label names the repetition when a task has several`() = runDesktopComposeUiTest(1000, 820) {
        show(listOf("PHONATION" to 1, "PHONATION" to 2))
        assertEquals("Recording 1 of 2 - PHONATION, repetition 1", label())

        onNodeWithTag(TestTags.Editor.NEXT_SEGMENT_BUTTON).performClick()
        waitForIdle()
        assertEquals("Recording 2 of 2 - PHONATION, repetition 2", label())
    }

    @Test
    fun `F-025 the segment label has no repetition text when the task has one`() = runDesktopComposeUiTest(1000, 820) {
        show(listOf("PHONATION" to 1, "PATAKA" to 1))
        assertEquals("Recording 1 of 2 - PHONATION", label())

        onNodeWithTag(TestTags.Editor.NEXT_SEGMENT_BUTTON).performClick()
        waitForIdle()
        assertEquals("Recording 2 of 2 - PATAKA", label())
    }

    @Test
    fun `F-025 repetitions are counted per task, not across tasks`() = runDesktopComposeUiTest(1000, 820) {
        show(listOf("PHONATION" to 1, "PATAKA" to 1, "PATAKA" to 2))
        assertEquals("Recording 1 of 3 - PHONATION", label())
        onNodeWithTag(TestTags.Editor.NEXT_SEGMENT_BUTTON).performClick()
        waitForIdle()
        assertEquals("Recording 2 of 3 - PATAKA, repetition 1", label())
    }

    private val tasks4 = List(4) { "PHONATION" to 1 }

    @Test
    fun `F-130 Previous and Next share a row at 960 wide`() = runDesktopComposeUiTest(960, 720) {
        show(tasks4)
        val previous = onNodeWithTag(TestTags.Editor.PREVIOUS_SEGMENT_BUTTON).getUnclippedBoundsInRoot()
        val next = onNodeWithTag(TestTags.Editor.NEXT_SEGMENT_BUTTON).getUnclippedBoundsInRoot()
        assertTrue(abs(previous.top.value - next.top.value) <= 2f, "Previous $previous and Next $next are on different rows at 960")
    }

    @Test
    fun `F-130 Previous and Next share a row when the window is narrower than all controls`() = runDesktopComposeUiTest(640, 720) {
        show(tasks4)
        val previous = onNodeWithTag(TestTags.Editor.PREVIOUS_SEGMENT_BUTTON).getUnclippedBoundsInRoot()
        val next = onNodeWithTag(TestTags.Editor.NEXT_SEGMENT_BUTTON).getUnclippedBoundsInRoot()
        assertTrue(abs(previous.top.value - next.top.value) <= 2f, "Previous $previous and Next $next are on different rows at 640")
    }

    @Test
    fun `F-130 Previous and Play are 8dp apart or on different rows at 1024 wide`() = runDesktopComposeUiTest(1024, 768) {
        show(tasks4)
        val previous = onNodeWithTag(TestTags.Editor.PREVIOUS_SEGMENT_BUTTON).getUnclippedBoundsInRoot()
        val next = onNodeWithTag(TestTags.Editor.NEXT_SEGMENT_BUTTON).getUnclippedBoundsInRoot()
        val play = onNodeWithTag(TestTags.Editor.PLAY_SEGMENT_BUTTON).getUnclippedBoundsInRoot()
        val sameRow = abs(previous.top.value - play.top.value) <= 2f
        // The gap that matters is between the groups: Next ends the navigation group.
        assertTrue(!sameRow || play.left.value - next.right.value >= 8f, "groups only ${play.left.value - next.right.value}dp apart: $next $play")
        assertTrue(!sameRow || play.left.value - previous.right.value >= 8f, "Previous $previous and Play $play too close")
    }
}
