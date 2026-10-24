package org.example.app.domain.timeline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimelineCompactorTest {

    private fun event(type: TimelineEventType, sampleOffset: Long?) = TimelineEvent(
        type = type,
        sampleOffset = sampleOffset,
        wallClock = "2026-07-01T09:30:00Z",
        taskIndex = null,
        repetition = null,
        take = null,
    )

    @Test
    fun `clean stop compacts events as-is without a synthetic stop`() {
        val events = listOf(
            event(TimelineEventType.SESSION_RECORDING_STARTED, 0),
            event(TimelineEventType.SESSION_RECORDING_STOPPED, 5000),
        )

        val result = TimelineCompactor.compact("session-1", sampleRate = 48000, events = events)

        assertEquals(events, result.events)
        assertEquals("session-1", result.sessionId)
        assertEquals(48000, result.sampleRate)
        assertEquals(1, result.version)
    }

    @Test
    fun `recovery appends a synthetic SESSION_RECORDING_STOPPED when the log has none`() {
        val events = listOf(
            event(TimelineEventType.SESSION_RECORDING_STARTED, 0),
            event(TimelineEventType.START_BUTTON_PRESSED, 1000),
        )

        val result = TimelineCompactor.compact(
            sessionId = "session-2",
            sampleRate = 48000,
            events = events,
            lastWrittenSample = 4200,
            syntheticWallClock = "2026-07-01T09:35:00Z",
        )

        assertEquals(3, result.events.size)
        val synthetic = result.events.last()
        assertEquals(TimelineEventType.SESSION_RECORDING_STOPPED, synthetic.type)
        assertEquals(4200, synthetic.sampleOffset)
        // F-054: 3200 samples after the last event at sample 1000 (09:30:00), not the recovery time.
        assertEquals("2026-07-01T09:30:00.066666666Z", synthetic.wallClock)
    }

    @Test
    fun `synthetic wallClock is the last event with a clock and an offset plus the samples since`() {
        val events = listOf(
            event(TimelineEventType.SESSION_RECORDING_STARTED, 0).copy(wallClock = "2026-07-01T09:00:00Z"),
            event(TimelineEventType.START_BUTTON_PRESSED, 48_000).copy(wallClock = "2026-07-01T09:00:10Z", taskIndex = 0, repetition = 1, take = 1),
            // No offset: cannot pin a sample to a clock time, so it is skipped.
            event(TimelineEventType.TASK_SCREEN_ENTERED, null).copy(wallClock = "2026-07-01T09:02:00Z"),
        )

        val result = TimelineCompactor.compact(
            sessionId = "s", sampleRate = 48_000, events = events,
            lastWrittenSample = 48_000 + 48_000 * 90L, syntheticWallClock = "2026-07-01T11:00:00Z",
        )

        val stop = result.events.last { it.type == TimelineEventType.SESSION_RECORDING_STOPPED }
        val interrupted = result.events.single { it.reason == TimelineCompactor.REASON_RECOVERED_INTERRUPTED }
        assertEquals("2026-07-01T09:01:40Z", stop.wallClock)
        assertEquals("2026-07-01T09:01:40Z", interrupted.wallClock)
    }

    @Test
    fun `synthetic wallClock falls back to the recovery time when no event has both a clock and an offset`() {
        val events = listOf(event(TimelineEventType.TASK_SCREEN_ENTERED, null))

        val result = TimelineCompactor.compact(
            sessionId = "s", sampleRate = 48_000, events = events,
            lastWrittenSample = 4200, syntheticWallClock = "2026-07-01T11:00:00Z",
        )

        assertEquals("2026-07-01T11:00:00Z", result.events.last().wallClock)
    }

    @Test
    fun `does not append a synthetic stop if one already exists, even during recovery`() {
        val events = listOf(
            event(TimelineEventType.SESSION_RECORDING_STARTED, 0),
            event(TimelineEventType.SESSION_RECORDING_STOPPED, 5000),
        )

        val result = TimelineCompactor.compact(
            sessionId = "session-3",
            sampleRate = 48000,
            events = events,
            lastWrittenSample = 9999,
            syntheticWallClock = "2026-07-01T09:40:00Z",
        )

        assertEquals(events, result.events)
    }

    @Test
    fun `no synthetic stop appended when recovery params are absent`() {
        val events = listOf(event(TimelineEventType.SESSION_RECORDING_STARTED, 0))
        val result = TimelineCompactor.compact("session-4", sampleRate = 48000, events = events)
        assertEquals(events, result.events)
        assertTrue(result.events.none { it.type == TimelineEventType.SESSION_RECORDING_STOPPED })
    }

    private fun takeEvent(type: TimelineEventType, sample: Long, take: Int = 1) = TimelineEvent(
        type = type, sampleOffset = sample, wallClock = "2026-07-01T09:30:00Z", taskIndex = 2, repetition = 1, take = take,
    )

    @Test
    fun `recovery closes a take left open at lastWrittenSample, flagged, before the synthetic stop`() {
        val events = listOf(
            event(TimelineEventType.SESSION_RECORDING_STARTED, 0),
            takeEvent(TimelineEventType.START_BUTTON_PRESSED, 345_600),
        )

        val result = TimelineCompactor.compact("s", 48000, events, lastWrittenSample = 508_800, syntheticWallClock = "2026-07-01T09:35:00Z")

        assertEquals(4, result.events.size)
        val close = result.events[2]
        assertEquals(TimelineEventType.STOP_BUTTON_PRESSED, close.type)
        assertEquals(508_800L, close.sampleOffset)
        assertEquals(Triple(2, 1, 1), Triple(close.taskIndex, close.repetition, close.take))
        assertEquals(TimelineCompactor.REASON_RECOVERED_INTERRUPTED, close.reason)
        assertEquals(TimelineEventType.SESSION_RECORDING_STOPPED, result.events[3].type)
        assertEquals(345_600L until 508_800L, TakeSelector.takeSampleRange(result.events, 2, 1, 1))
    }

    @Test
    fun `recovery leaves already ended and rejected takes untouched`() {
        val events = listOf(
            takeEvent(TimelineEventType.START_BUTTON_PRESSED, 100, take = 1),
            takeEvent(TimelineEventType.STOP_BUTTON_PRESSED, 200, take = 1),
            takeEvent(TimelineEventType.TAKE_REJECTED, 200, take = 1),
            takeEvent(TimelineEventType.START_BUTTON_PRESSED, 300, take = 2),
            takeEvent(TimelineEventType.STOP_BUTTON_PRESSED, 400, take = 2),
        )

        val result = TimelineCompactor.compact("s", 48000, events, lastWrittenSample = 1000, syntheticWallClock = "2026-07-01T09:35:00Z")

        // 600 samples (48 kHz) after the last event at sample 400, which is stamped 09:30:00.
        assertEquals(events + event(TimelineEventType.SESSION_RECORDING_STOPPED, 1000).copy(wallClock = "2026-07-01T09:30:00.012500Z"), result.events)
    }

    @Test
    fun `recovery does not close a take that started at or after the last written sample`() {
        val events = listOf(takeEvent(TimelineEventType.START_BUTTON_PRESSED, 1000))

        val result = TimelineCompactor.compact("s", 48000, events, lastWrittenSample = 1000, syntheticWallClock = "2026-07-01T09:35:00Z")

        assertTrue(result.events.none { it.type == TimelineEventType.STOP_BUTTON_PRESSED })
    }
}
