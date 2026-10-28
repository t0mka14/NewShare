package org.example.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** F-093 / F-125: every editor control stays visible, on one line and un-overlapped at the minimum window size. */
@OptIn(ExperimentalTestApi::class)
class EditorLayoutUiTest {

    private fun component(segments: Int, dispatchers: TestCoroutineDispatchers): DefaultEditorComponent {
        val sessionRepository = FakeSessionRepository()
        val timelineRepository = FakeTimelineRepository()
        val folderName = "folder-1"
        val format = CaptureFormat(sampleRate = 1000, bits = 16, channels = 1)
        val events = buildList {
            repeat(segments) { i ->
                add(TimelineEvent(TimelineEventType.START_BUTTON_PRESSED, 100L + i * 1000, "t", i, 1, 1))
                add(TimelineEvent(TimelineEventType.STOP_BUTTON_PRESSED, 600L + i * 1000, "t", i, 1, 1))
            }
            add(TimelineEvent(TimelineEventType.SESSION_RECORDING_STOPPED, 1000L * segments + 1000, "t", null, null, null))
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
                tasks = List(segments) { TaskRecord(taskIndex = it, type = "VOCAL", subtype = "PHONATION", repetition = 1, takes = 1) },
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

    private fun overlaps(a: DpRect, b: DpRect) =
        a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    private fun checkButtons(tags: List<String>, width: Int, height: Int, androidx: androidx.compose.ui.test.ComposeUiTest) = with(androidx) {
        val bounds = tags.associateWith { tag ->
            onNodeWithTag(tag).assertIsDisplayed()
            val rect = onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue(rect.height > 10.dp, "$tag has height ${rect.height} at ${width}x$height")
            // A single-line Material button is 40dp high; a wrapped label makes it taller.
            assertTrue(rect.height <= 41.dp, "$tag label wraps (height ${rect.height}) at ${width}x$height")
            rect
        }
        tags.forEachIndexed { i, a ->
            tags.drop(i + 1).forEach { b ->
                assertTrue(!overlaps(bounds.getValue(a), bounds.getValue(b)), "$a overlaps $b at ${width}x$height")
            }
        }
    }

    private fun check(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        val dispatchers = TestCoroutineDispatchers()
        val component = component(segments = 4, dispatchers = dispatchers)
        dispatchers.scheduler.advanceUntilIdle()
        setContent { ShareTheme { EditorContent(component, UiLocalization(LocalizedStringProvider(), "en", null)) } }
        waitForIdle()

        checkButtons(
            listOf(
                TestTags.Editor.PREVIOUS_SEGMENT_BUTTON,
                TestTags.Editor.PLAY_SEGMENT_BUTTON,
                TestTags.Editor.RESET_SEGMENT_BUTTON,
                TestTags.Editor.NEXT_SEGMENT_BUTTON,
                TestTags.Editor.DISCARD_BUTTON,
                TestTags.Editor.ACCEPT_BUTTON,
            ),
            width, height, this,
        )

        val canvas = onNodeWithTag(TestTags.Editor.WAVEFORM_CANVAS).getUnclippedBoundsInRoot()
        assertTrue(canvas.height >= 150.dp, "waveform lane only ${canvas.height} high at ${width}x$height")
        listOf(TestTags.Editor.START_BOUNDARY_HANDLE, TestTags.Editor.STOP_BOUNDARY_HANDLE).forEach { tag ->
            val handle = onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue(
                handle.top >= canvas.top - 0.5.dp && handle.bottom <= canvas.bottom + 0.5.dp,
                "$tag $handle leaves the waveform lane $canvas at ${width}x$height",
            )
        }
    }

    private fun checkEmpty(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        val dispatchers = TestCoroutineDispatchers()
        val component = component(segments = 0, dispatchers = dispatchers)
        dispatchers.scheduler.advanceUntilIdle()
        setContent { ShareTheme { EditorContent(component, UiLocalization(LocalizedStringProvider(), "en", null)) } }
        waitForIdle()
        checkButtons(listOf(TestTags.Editor.DISCARD_BUTTON, TestTags.Editor.ACCEPT_BUTTON), width, height, this)
    }

    @Test
    fun `editor controls fit on one line at 1000x820`() = check(1000, 820)

    @Test
    fun `editor controls fit on one line at 984x781`() = check(984, 781)

    @Test
    fun `editor controls fit on one line at 960x720`() = check(960, 720)

    @Test
    fun `editor controls fit on one line in the 944x681 content of a 960x720 window`() = check(944, 681)

    @Test
    fun `editor controls fit on one line at the old 800x600 default`() = check(800, 600)

    @Test
    fun `no-segment editor buttons fit on one line at 944x681`() = checkEmpty(944, 681)

    @Test
    fun `no-segment editor buttons fit on one line at 800x600`() = checkEmpty(800, 600)
}
