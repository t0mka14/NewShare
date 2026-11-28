package org.example.app.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.domain.audio.CaptureFormat
import org.example.app.domain.session.Examination
import org.example.app.domain.session.Interruption
import org.example.app.domain.session.TaskRecord
import org.example.app.domain.video.VideoCaptureFormat
import org.example.app.domain.session.VideoTakeRecord
import org.example.app.domain.timeline.SampleRange
import org.example.app.domain.timeline.TimelineEdited
import org.example.app.domain.timeline.TimelineEditedSegment
import org.example.app.domain.timeline.TimelineEvent
import org.example.app.domain.timeline.TimelineEventType
import org.example.app.domain.timeline.TimelineOriginal
import org.example.app.fakes.FakeAudioClipService
import org.example.app.fakes.FakeAudioPlaybackService
import org.example.app.fakes.FakeSessionRepository
import org.example.app.fakes.FakeTimelineRepository
import org.example.app.fakes.FakeWaveformService
import org.example.app.fakes.TestCoroutineDispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Enhanced editor (cuts, selection, zoom) component tests — fakes only, no Compose. */
class EditorCutsComponentTest {

    private val folderName = "2026-07-03_HC001_session-1"
    private val format = CaptureFormat(sampleRate = 1000, bits = 16, channels = 1)

    private fun span(a: Long, b: Long) = EditorComponent.Span(a, b)

    /**
     * Two VOCAL instances in a 20000-sample session: task 0 take [2000, 8000), task 1 take
     * [10000, 12000). With [secondPart], a device-loss part starts at global 9000, so task 1 lives
     * in part 2 at local [1000, 3000).
     */
    private inner class Harness(
        enhanced: Boolean = true,
        priorEdited: TimelineEdited? = null,
        secondPart: Boolean = false,
        videoForTask0: Boolean = false,
    ) {
        val dispatchers = TestCoroutineDispatchers()
        val sessionRepository = FakeSessionRepository()
        val timelineRepository = FakeTimelineRepository()
        val waveformService = FakeWaveformService()
        val audioPlaybackService = FakeAudioPlaybackService()
        val clipService = FakeAudioClipService()
        var doneChanged: Boolean? = null

        init {
            fun ev(type: TimelineEventType, offset: Long, task: Int?, rep: Int?, take: Int?) =
                TimelineEvent(type = type, sampleOffset = offset, wallClock = "2026-07-03T09:00:00Z", taskIndex = task, repetition = rep, take = take)
            timelineRepository.writeOriginal(
                folderName,
                TimelineOriginal(
                    sessionId = "session-1",
                    sampleRate = 1000,
                    events = listOf(
                        ev(TimelineEventType.START_BUTTON_PRESSED, 2000, 0, 1, 1),
                        ev(TimelineEventType.STOP_BUTTON_PRESSED, 8000, 0, 1, 1),
                        ev(TimelineEventType.START_BUTTON_PRESSED, 10000, 1, 1, 1),
                        ev(TimelineEventType.STOP_BUTTON_PRESSED, 12000, 1, 1, 1),
                        ev(TimelineEventType.SESSION_RECORDING_STOPPED, 20000, null, null, null),
                    ),
                ),
            )
            sessionRepository.writeExamination(
                folderName,
                Examination(
                    sessionId = "session-1",
                    installationId = "install-1",
                    protocolName = "Share",
                    configVersion = "v1",
                    startedAt = "2026-07-03T09:00:00Z",
                    captureFormat = format,
                    tasks = listOf(
                        TaskRecord(taskIndex = 0, type = "VOCAL", subtype = "FREE", repetition = 1, takes = 1),
                        TaskRecord(taskIndex = 1, type = "VOCAL", subtype = "FREE", repetition = 1, takes = 1),
                    ),
                    interruptions = if (secondPart) {
                        listOf(Interruption(sampleOffset = 9000, start = "2026-07-03T09:01:00Z", partFile = "session_master.part2.wav", captureFormat = format))
                    } else {
                        emptyList()
                    },
                    videoTakes = if (videoForTask0) {
                        listOf(
                            VideoTakeRecord(
                                file = "video/task00_rep01_take01.mjpeg", taskIndex = 0, repetition = 1, take = 1,
                                captureFormat = VideoCaptureFormat(width = 640, height = 480, fps = 30),
                            ),
                        )
                    } else {
                        emptyList()
                    },
                ),
            )
            priorEdited?.let { timelineRepository.writeEdited(folderName, it) }
        }

        val component: EditorComponent = DefaultEditorComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            folderName = folderName,
            sessionRepository = sessionRepository,
            timelineRepository = timelineRepository,
            waveformService = waveformService,
            audioPlaybackService = audioPlaybackService,
            dispatchers = dispatchers,
            onDone = { _, changed -> doneChanged = changed },
            onDiscarded = { },
            enhanced = enhanced,
            audioClipService = clipService,
        ).also { dispatchers.scheduler.advanceUntilIdle() }

        val state get() = component.state.value
        val seg get() = state.segments[0]

        fun cut(a: Long, b: Long) {
            component.onSelect(a, b)
            component.onCutSelection()
        }
    }

    @Test
    fun `selection is clamped to the segment and normalised`() {
        val h = Harness()
        h.component.onSelect(1000, 3000)
        assertEquals(span(2000, 3000), h.state.selection)

        h.component.onSelect(5000, 4000)
        assertEquals(span(4000, 5000), h.state.selection)

        h.component.onSelect(7000, 9000)
        assertEquals(span(7000, 8000), h.state.selection)

        h.component.onSelect(4000, 4000)
        assertNull(h.state.selection)

        h.component.onSelect(4000, 5000)
        h.component.onClearSelection()
        assertNull(h.state.selection)
    }

    @Test
    fun `selection never overlaps an existing cut`() {
        val h = Harness()
        h.cut(3000, 4000)

        h.component.onSelect(2500, 4500) // drag right-to-left of the cut: stops at its start
        assertEquals(span(2500, 3000), h.state.selection)

        h.component.onSelect(5000, 3500) // dragged left into the cut: stops at its end
        assertEquals(span(4000, 5000), h.state.selection)

        h.component.onSelect(3500, 5000) // anchor inside a cut: ignored, selection unchanged
        assertEquals(span(4000, 5000), h.state.selection)
    }

    @Test
    fun `cut snaps both edges to zero crossings within 5 ms and validates`() {
        val h = Harness()
        h.clipService.zeroCrossingResolver = { _, sample, _ -> sample + 1 }
        h.cut(3000, 4000)

        assertEquals(listOf(3000L to 5L, 4000L to 5L), h.clipService.zeroCrossingCalls)
        assertEquals(listOf(span(3001, 4001)), h.seg.cuts)
        assertNull(h.state.selection)
        assertTrue(h.seg.moved)
        assertTrue(h.seg.changedSinceOpened)
        assertEquals(1, h.seg.cutCount)
        assertEquals(1000L, h.seg.removedSamples)
        assertEquals(6000L, h.seg.originalDurationSamples)
        assertEquals(5000L, h.seg.keptDurationSamples)
    }

    @Test
    fun `cut touching the clip edge, or leaving under 0_5 s, is rejected with a reason`() {
        val h = Harness()
        h.cut(2000, 3000) // edge is not strictly inside
        assertTrue(h.seg.cuts.isEmpty())
        assertEquals(EditorComponent.CutRejection.OUTSIDE_SEGMENT, h.state.lastCutRejected)
        assertEquals(span(2000, 3000), h.state.selection) // kept so the examiner can adjust

        h.cut(2001, 7700) // 1 + 300 samples kept < 500
        assertTrue(h.seg.cuts.isEmpty())
        assertEquals(EditorComponent.CutRejection.KEPT_TOO_SHORT, h.state.lastCutRejected)

        h.component.onClearSelection()
        assertNull(h.state.lastCutRejected)
    }

    @Test
    fun `snapping to an empty selection is rejected`() {
        val h = Harness()
        h.clipService.zeroCrossingResolver = { _, _, _ -> 4000 }
        h.cut(3000, 3002)
        assertTrue(h.seg.cuts.isEmpty())
        assertEquals(EditorComponent.CutRejection.SELECTION_EMPTY, h.state.lastCutRejected)
    }

    @Test
    fun `multiple cuts stay sorted and touching cuts merge`() {
        val h = Harness()
        h.cut(5000, 5500)
        h.cut(3000, 3500)
        assertEquals(listOf(span(3000, 3500), span(5000, 5500)), h.seg.cuts)

        h.cut(3500, 4000) // touches the first cut
        assertEquals(listOf(span(3000, 4000), span(5000, 5500)), h.seg.cuts)
        assertEquals(1500L, h.seg.removedSamples)
    }

    @Test
    fun `restore removes one cut`() {
        val h = Harness()
        h.cut(3000, 3500)
        h.cut(5000, 5500)

        h.component.onRestoreCut(0)
        assertEquals(listOf(span(5000, 5500)), h.seg.cuts)
        h.component.onRestoreCut(7) // out of range: ignored
        assertEquals(1, h.seg.cutCount)
        h.component.onRestoreCut(0)
        assertTrue(h.seg.cuts.isEmpty())
        assertFalse(h.seg.moved)
        assertFalse(h.seg.changedSinceOpened)
    }

    @Test
    fun `reset clears cuts and boundaries back to the opening state`() {
        val h = Harness()
        h.cut(3000, 3500)
        h.component.onDragStart(2500)
        h.component.onSelect(5000, 6000)

        h.component.onResetSegment()

        assertTrue(h.seg.cuts.isEmpty())
        assertEquals(2000L, h.seg.startSample)
        assertNull(h.state.selection)
        assertFalse(h.seg.moved)
    }

    @Test
    fun `accept writes cuts as global samples, and only when something was moved or cut`() {
        val h = Harness(secondPart = true)
        h.component.onNext() // task 1: local [1000, 3000) in part 2 (global start 9000)
        h.dispatchers.scheduler.advanceUntilIdle()
        h.cut(1200, 1800)
        assertEquals(listOf(span(1200, 1800)), h.state.segments[1].cuts)

        h.component.onAccept()

        val written = h.timelineRepository.readEdited(folderName)!!
        assertEquals(
            listOf(
                TimelineEditedSegment(0, 1, 2000, 8000),
                TimelineEditedSegment(1, 1, 10000, 12000, listOf(SampleRange(10200, 10800))),
            ),
            written.segments,
        )
        assertEquals(true, h.doneChanged)
    }

    @Test
    fun `accept without edits deletes nothing new and reports no change`() {
        val h = Harness()
        h.component.onAccept()
        assertNull(h.timelineRepository.readEdited(folderName))
        assertEquals(false, h.doneChanged)
    }

    @Test
    fun `needsReprocess is true when only the cuts differ from the saved edit`() {
        val prior = TimelineEdited(
            sessionId = "session-1", sampleRate = 1000,
            segments = listOf(
                TimelineEditedSegment(0, 1, 2000, 8000, listOf(SampleRange(3000, 3500))),
                TimelineEditedSegment(1, 1, 10000, 12000),
            ),
        )
        val unchanged = Harness(priorEdited = prior)
        unchanged.component.onAccept()
        assertEquals(false, unchanged.doneChanged)

        val changed = Harness(priorEdited = prior)
        changed.component.onRestoreCut(0)
        changed.cut(5000, 5500)
        changed.component.onAccept()
        assertEquals(true, changed.doneChanged)
        assertEquals(listOf(SampleRange(5000, 5500)), changed.timelineRepository.readEdited(folderName)!!.segments[0].cuts)
    }

    @Test
    fun `restoring the last cut of a saved edit with nothing else moved deletes the edit file on accept`() {
        val prior = TimelineEdited(
            sessionId = "session-1", sampleRate = 1000,
            segments = listOf(
                TimelineEditedSegment(0, 1, 2000, 8000, listOf(SampleRange(3000, 3500))),
                TimelineEditedSegment(1, 1, 10000, 12000),
            ),
        )
        val h = Harness(priorEdited = prior)
        h.component.onRestoreCut(0)
        h.component.onAccept()
        assertNull(h.timelineRepository.readEdited(folderName))
        assertEquals(true, h.doneChanged)
    }

    @Test
    fun `not enhanced - select, cut and zoom are no-ops but saved cuts load and can be restored`() {
        val prior = TimelineEdited(
            sessionId = "session-1", sampleRate = 1000,
            segments = listOf(
                TimelineEditedSegment(0, 1, 2000, 8000, listOf(SampleRange(3000, 3500))),
                TimelineEditedSegment(1, 1, 10000, 12000),
            ),
        )
        val h = Harness(enhanced = false, priorEdited = prior)
        assertFalse(h.state.enhanced)
        assertEquals(listOf(span(3000, 3500)), h.seg.cuts)
        assertFalse(h.seg.changedSinceOpened)

        h.component.onSelect(5000, 6000)
        assertNull(h.state.selection)
        h.component.onCutSelection()
        h.component.onZoomIn(4000)
        h.component.onPan(1000)
        assertEquals(1, h.seg.cutCount)
        assertFalse(h.state.zoomed)

        h.component.onPlayToggle() // still honours the cut
        assertEquals(1, h.audioPlaybackService.playRangesCalls.size)

        h.component.onRestoreCut(0)
        assertTrue(h.seg.cuts.isEmpty())
    }

    @Test
    fun `invalid saved cuts are ignored but the saved boundaries are kept`() {
        val prior = TimelineEdited(
            sessionId = "session-1", sampleRate = 1000,
            segments = listOf(
                TimelineEditedSegment(0, 1, 2500, 8000, listOf(SampleRange(2000, 3000))), // cut outside
                TimelineEditedSegment(1, 1, 10000, 12000),
            ),
        )
        val h = Harness(priorEdited = prior)
        assertEquals(2500L, h.seg.startSample)
        assertTrue(h.seg.cuts.isEmpty())
    }

    @Test
    fun `play with cuts uses playRanges over the local kept ranges`() {
        val h = Harness()
        h.cut(3000, 3500)
        h.cut(5000, 6000)

        h.component.onPlayToggle()

        assertTrue(h.audioPlaybackService.playRangeCalls.isEmpty())
        assertEquals(listOf(2000L..2999L, 3500L..4999L, 6000L..7999L), h.audioPlaybackService.playRangesCalls.single().ranges)
    }

    @Test
    fun `play from a parked position trims the ranges, and a position inside a cut starts after it`() {
        val h = Harness()
        h.cut(3000, 3500)
        h.cut(5000, 6000)

        h.component.onSeek(4000)
        h.component.onPlayToggle()
        assertEquals(listOf(4000L..4999L, 6000L..7999L), h.audioPlaybackService.playRangesCalls.last().ranges)

        h.dispatchers.scheduler.advanceUntilIdle()
        h.component.onPlayToggle() // stop
        h.dispatchers.scheduler.advanceUntilIdle()
        h.component.onSeek(5500) // inside a cut: parks at the next kept range's start
        assertEquals(6000L, h.state.positionSample)
        h.component.onPlayToggle()
        assertEquals(listOf(6000L..7999L), h.audioPlaybackService.playRangesCalls.last().ranges)
    }

    @Test
    fun `cutting or restoring while playing stops the playback started with the old ranges`() {
        val h = Harness()
        h.component.onPlayToggle()
        h.dispatchers.scheduler.advanceUntilIdle()
        assertTrue(h.state.isPlaying)

        h.cut(3000, 3500)
        h.dispatchers.scheduler.advanceUntilIdle()
        assertEquals(1, h.audioPlaybackService.stopCallCount)
        assertTrue(!h.state.isPlaying)

        h.component.onPlayToggle()
        h.dispatchers.scheduler.advanceUntilIdle()
        h.component.onRestoreCut(0)
        h.dispatchers.scheduler.advanceUntilIdle()
        assertEquals(2, h.audioPlaybackService.stopCallCount)
        assertTrue(h.seg.cuts.isEmpty())
    }

    @Test
    fun `seeking into a cut while playing restarts at the next kept range`() {
        val h = Harness()
        h.cut(3000, 3500)
        h.component.onPlayToggle()
        h.dispatchers.scheduler.advanceUntilIdle()

        h.component.onSeek(3200)

        assertEquals(listOf(3500L..7999L), h.audioPlaybackService.playRangesCalls.last().ranges)
    }

    @Test
    fun `play selection plays just the selection`() {
        val h = Harness()
        h.component.onPlaySelection() // nothing selected
        assertTrue(h.audioPlaybackService.playRangeCalls.isEmpty())

        h.component.onSelect(3000, 4000)
        h.component.onPlaySelection()

        val call = h.audioPlaybackService.playRangeCalls.single()
        assertEquals(3000L, call.startSample)
        assertEquals(4000L, call.stopSample)
    }

    @Test
    fun `a boundary dragged into a cut is rejected`() {
        val h = Harness()
        h.cut(3000, 3500)

        h.component.onDragStart(3200) // would swallow the start of the cut
        assertEquals(2000L, h.seg.startSample)
        h.component.onDragStop(3200)
        assertEquals(8000L, h.seg.stopSample)

        h.component.onDragStart(2900) // still before the cut: fine
        assertEquals(2900L, h.seg.startSample)
    }

    @Test
    fun `video note applies only to filmed tasks with cuts`() {
        val h = Harness(videoForTask0 = true)
        assertTrue(h.seg.hasVideo)
        assertFalse(h.state.segments[1].hasVideo)
        assertFalse(h.seg.videoNotCut)
        h.cut(3000, 3500)
        assertTrue(h.seg.videoNotCut)
    }

    @Test
    fun `zoom in and out halve and double the window around the anchor, clamped to the fit window`() {
        val h = Harness()
        // Fit = [0, 10000) (clip +-5 s clamped by the file start and the next segment).
        assertEquals(0L, h.state.fitStartSample)
        assertEquals(10000L, h.state.fitStopSample)
        assertFalse(h.state.zoomed)

        h.component.onZoomIn(5000)
        assertEquals(2500L, h.state.visibleStartSample)
        assertEquals(7500L, h.state.visibleStopSample)
        assertTrue(h.state.zoomed)

        h.component.onZoomIn(0) // anchor clamped into the window; keeps its relative position
        assertEquals(2500L, h.state.visibleStartSample)
        assertEquals(5000L, h.state.visibleStopSample)

        h.component.onZoomOut(2500)
        assertEquals(2500L, h.state.visibleStartSample)
        assertEquals(7500L, h.state.visibleStopSample)

        h.component.onZoomOut(2500)
        h.component.onZoomOut(2500) // already at fit: clamped, no further change
        assertEquals(0L, h.state.visibleStartSample)
        assertEquals(10000L, h.state.visibleStopSample)
        assertFalse(h.state.zoomed)
    }

    @Test
    fun `zoom stops at about 2 s`() {
        val h = Harness()
        repeat(6) { h.component.onZoomIn(5000) }
        assertEquals(2000L, h.state.visibleStopSample - h.state.visibleStartSample)
    }

    @Test
    fun `pan keeps the span, clamps to the fit window and zoom-fit restores the window`() {
        val h = Harness()
        h.component.onZoomIn(5000)
        h.component.onPan(1000)
        assertEquals(1000L, h.state.visibleStartSample)
        assertEquals(6000L, h.state.visibleStopSample)
        h.component.onPan(-500)
        assertEquals(0L, h.state.visibleStartSample)
        h.component.onPan(99999)
        assertEquals(5000L, h.state.visibleStartSample)
        assertEquals(10000L, h.state.visibleStopSample)

        h.component.onZoomFit()
        assertEquals(0L, h.state.visibleStartSample)
        assertEquals(10000L, h.state.visibleStopSample)
    }

    @Test
    fun `zoom and pan refetch peaks for the visible window`() {
        val h = Harness()
        val before = h.waveformService.peaksCalls.size

        h.component.onZoomIn(5000)
        val zoomCall = h.waveformService.peaksCalls.last()
        assertEquals(before + 1, h.waveformService.peaksCalls.size)
        assertEquals(2500L, zoomCall.startSample)
        assertEquals(7500L, zoomCall.endSample)

        h.component.onPan(1000)
        assertEquals(1000L, h.waveformService.peaksCalls.last().startSample)

        h.component.onZoomFit()
        assertEquals(0L, h.waveformService.peaksCalls.last().startSample)
        assertEquals(10000L, h.waveformService.peaksCalls.last().endSample)
    }

    @Test
    fun `navigating resets the window and selection to the new segment`() {
        val h = Harness()
        h.component.onZoomIn(5000)
        h.component.onSelect(3000, 4000)

        h.component.onNext()
        h.dispatchers.scheduler.advanceUntilIdle()

        assertNull(h.state.selection)
        assertFalse(h.state.zoomed)
    }
}
