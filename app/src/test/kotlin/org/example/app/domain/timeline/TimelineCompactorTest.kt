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
}
