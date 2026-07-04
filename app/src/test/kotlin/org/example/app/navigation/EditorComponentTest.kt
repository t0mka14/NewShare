package org.example.app.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.domain.audio.CaptureFormat
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** §8.7 waveform editor component tests (fakes only, no Compose, §10.1 pattern). */
class EditorComponentTest {

    private val folderName = "2026-07-03_HC001_session-1"
    private val format = CaptureFormat(sampleRate = 1000, bits = 16, channels = 1) // 1000 Hz for simple sample math

    private class Harness(
        folderName: String,
        format: CaptureFormat,
        configureExamination: (Examination) -> Examination = { it },
    ) {
        val dispatchers = TestCoroutineDispatchers()
        val sessionRepository = FakeSessionRepository()
        val timelineRepository = FakeTimelineRepository()
        val waveformService = FakeWaveformService()
        val audioPlaybackService = FakeAudioPlaybackService()
        var doneCalledWith: String? = null

        init {
            // Two VOCAL instances: task 0 rep 1 take [100,300), task 1 rep 1 take [400,600) — the
            // "last take" per instance (only take), out of a 1000-sample session.
            timelineRepository.writeOriginal(
                folderName,
                TimelineOriginal(
                    sessionId = "session-1",
                    sampleRate = format.sampleRate,
                    events = listOf(
                        event(TimelineEventType.START_BUTTON_PRESSED, 100, 0, 1, 1),
                        event(TimelineEventType.STOP_BUTTON_PRESSED, 300, 0, 1, 1),
                        event(TimelineEventType.START_BUTTON_PRESSED, 400, 1, 1, 1),
                        event(TimelineEventType.STOP_BUTTON_PRESSED, 600, 1, 1, 1),
                        event(TimelineEventType.SESSION_RECORDING_STOPPED, 1000, null, null, null),
                    ),
                ),
            )
            val examination = configureExamination(
                Examination(
                    sessionId = "session-1",
                    installationId = "install-1",
                    protocolName = "Share",
                    configVersion = "v1",
                    startedAt = "2026-07-03T09:00:00Z",
                    captureFormat = format,
                    tasks = listOf(
                        TaskRecord(taskIndex = 0, type = "VOCAL", subtype = "PHONATION", repetition = 1, takes = 1),
                        TaskRecord(taskIndex = 1, type = "VOCAL", subtype = "PATAKA", repetition = 1, takes = 1),
                    ),
                ),
            )
            sessionRepository.writeExamination(folderName, examination)
        }

        val component: EditorComponent = DefaultEditorComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            folderName = folderName,
            sessionRepository = sessionRepository,
            timelineRepository = timelineRepository,
            waveformService = waveformService,
            audioPlaybackService = audioPlaybackService,
            dispatchers = dispatchers,
            onDone = { folder -> doneCalledWith = folder },
        )

        private fun event(type: TimelineEventType, offset: Long, taskIndex: Int?, repetition: Int?, take: Int?) =
            TimelineEvent(type = type, sampleOffset = offset, wallClock = "2026-07-03T09:00:00Z", taskIndex = taskIndex, repetition = repetition, take = take)
    }

    @Test
    fun `loads one segment per non-skipped VOCAL instance with boundaries at the last take's events`() {
        val h = Harness(folderName, format)
        h.dispatchers.scheduler.advanceUntilIdle()

        val state = h.component.state.value
        assertFalse(state.loading)
        assertEquals(2, state.segments.size)
        assertEquals(100L, state.segments[0].startSample)
        assertEquals(300L, state.segments[0].stopSample)
        assertEquals(400L, state.segments[1].startSample)
        assertEquals(600L, state.segments[1].stopSample)
        assertFalse(state.segments[0].moved)
    }

    @Test
    fun `visible window is clamped to +-5s and to the neighboring segment`() {
        val h = Harness(folderName, format)
        h.dispatchers.scheduler.advanceUntilIdle()

        // sampleRate=1000 -> 5s = 5000 samples, far beyond [0, 1000) file range and the neighbor,
        // so the window is clamped to file-range/neighbor boundaries, not the full +-5s span.
        val state = h.component.state.value
        assertEquals(0L, state.visibleStartSample) // clamped to file start (no previous segment)
        assertTrue(state.visibleStopSample <= 400L) // clamped by the next segment's start (400)
    }

    @Test
    fun `dragging a boundary within valid bounds updates the segment and marks it moved`() {
        val h = Harness(folderName, format)
        h.dispatchers.scheduler.advanceUntilIdle()

        h.component.onDragStart(150L)

        val segment = h.component.state.value.segments[0]
        assertEquals(150L, segment.startSample)
        assertTrue(segment.moved)
    }

    @Test
    fun `dragging a boundary past its own stop is rejected — state unchanged`() {
        val h = Harness(folderName, format)
        h.dispatchers.scheduler.advanceUntilIdle()

        h.component.onDragStart(350L) // would invert start(350) > stop(300)

        val segment = h.component.state.value.segments[0]
        assertEquals(100L, segment.startSample) // unchanged
        assertFalse(segment.moved)
    }

    @Test
    fun `dragging a boundary into the neighboring segment's take is rejected`() {
        val h = Harness(folderName, format)
        h.dispatchers.scheduler.advanceUntilIdle()

        h.component.onDragStop(450L) // segment 0's stop would overlap segment 1's [400,600)

        val segment = h.component.state.value.segments[0]
        assertEquals(300L, segment.stopSample) // unchanged
    }

    @Test
    fun `onNext and onPrevious move within bounds only`() {
        val h = Harness(folderName, format)
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals(0, h.component.state.value.currentIndex)
        h.component.onPrevious() // already at 0 — no-op
        assertEquals(0, h.component.state.value.currentIndex)

        h.component.onNext()
        assertEquals(1, h.component.state.value.currentIndex)
        h.component.onNext() // already at last — no-op
        assertEquals(1, h.component.state.value.currentIndex)

        h.component.onPrevious()
        assertEquals(0, h.component.state.value.currentIndex)
    }

    @Test
    fun `play toggle forwards playRange for the current segment and stop toggles it off`() {
        val h = Harness(folderName, format)
        h.dispatchers.scheduler.advanceUntilIdle()

        h.component.onPlayToggle()
        assertEquals(1, h.audioPlaybackService.playRangeCalls.size)
        val call = h.audioPlaybackService.playRangeCalls.single()
        assertEquals(100L, call.startSample)
        assertEquals(300L, call.stopSample)
        h.dispatchers.scheduler.advanceUntilIdle()
        assertTrue(h.component.state.value.isPlaying)

        h.component.onPlayToggle()
        assertEquals(1, h.audioPlaybackService.stopCallCount)
    }

    @Test
    fun `an untouched pass writes no timeline_edited json`() {
        val h = Harness(folderName, format)
        h.dispatchers.scheduler.advanceUntilIdle()

        h.component.onAccept()

        assertFalse(h.timelineRepository.editedExists(folderName))
        assertEquals(folderName, h.doneCalledWith)
        assertTrue(h.component.state.value.accepted)
    }

    @Test
    fun `accepting after a move writes the complete segment list for every VOCAL instance, in global samples`() {
        val h = Harness(folderName, format)
        h.dispatchers.scheduler.advanceUntilIdle()

        h.component.onDragStart(150L) // only segment 0 touched

        h.component.onAccept()

        assertTrue(h.timelineRepository.editedExists(folderName))
        val edited = h.timelineRepository.readEdited(folderName)!!
        assertEquals(2, edited.segments.size) // decision 33: complete list, not just the moved one
        val seg0 = edited.segments.single { it.taskIndex == 0 }
        val seg1 = edited.segments.single { it.taskIndex == 1 }
        assertEquals(150L, seg0.startSample)
        assertEquals(300L, seg0.stopSample)
        // Untouched segment 1 keeps its original (global == local here, single part) bounds.
        assertEquals(400L, seg1.startSample)
        assertEquals(600L, seg1.stopSample)
    }

    @Test
    fun `no VOCAL instances yields an empty segment list and accept still completes`() {
        val h = Harness(folderName, format) { examination -> examination.copy(tasks = emptyList()) }
        h.dispatchers.scheduler.advanceUntilIdle()

        assertTrue(h.component.state.value.segments.isEmpty())
        assertNull(h.component.state.value.currentSegment)

        h.component.onAccept()
        assertFalse(h.timelineRepository.editedExists(folderName))
        assertEquals(folderName, h.doneCalledWith)
    }
}
