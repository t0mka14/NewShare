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
        assertEquals("2026-07-01T09:35:00Z", synthetic.wallClock)
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

        assertEquals(events + event(TimelineEventType.SESSION_RECORDING_STOPPED, 1000).copy(wallClock = "2026-07-01T09:35:00Z"), result.events)
    }

    @Test
    fun `recovery does not close a take that started at or after the last written sample`() {
        val events = listOf(takeEvent(TimelineEventType.START_BUTTON_PRESSED, 1000))

        val result = TimelineCompactor.compact("s", 48000, events, lastWrittenSample = 1000, syntheticWallClock = "2026-07-01T09:35:00Z")

        assertTrue(result.events.none { it.type == TimelineEventType.STOP_BUTTON_PRESSED })
    }
}
