package org.example.app.domain.timeline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimelineEventCodecTest {

    private fun event(take: Int = 1, sampleOffset: Long? = 1000L) = TimelineEvent(
        type = TimelineEventType.START_BUTTON_PRESSED,
        sampleOffset = sampleOffset,
        wallClock = "2026-07-01T09:30:00Z",
        taskIndex = 3,
        repetition = 1,
        take = take,
    )

    @Test
    fun `round-trips a single event through encode-decode`() {
        val original = event()
        val line = TimelineEventCodec.encodeLine(original)
        val result = TimelineEventCodec.decodeLines(line)

        assertEquals(listOf(original), result.events)
        assertFalse(result.tornLastLine)
    }

    @Test
    fun `round-trips multiple lines in order`() {
        val events = listOf(event(take = 1), event(take = 2), event(take = 3))
        val content = events.joinToString("\n") { TimelineEventCodec.encodeLine(it) }

        val result = TimelineEventCodec.decodeLines(content)
        assertEquals(events, result.events)
        assertFalse(result.tornLastLine)
    }

    @Test
    fun `null sampleOffset round-trips for no-master sessions`() {
        val original = event(sampleOffset = null)
        val line = TimelineEventCodec.encodeLine(original)
        val result = TimelineEventCodec.decodeLines(line)
        assertEquals(listOf(original), result.events)
    }

    @Test
    fun `tolerates a torn final line from a crash mid-write`() {
        val goodLine = TimelineEventCodec.encodeLine(event(take = 1))
        val tornLine = TimelineEventCodec.encodeLine(event(take = 2)).dropLast(10) // simulate truncation
        val content = "$goodLine\n$tornLine"

        val result = TimelineEventCodec.decodeLines(content)

        assertEquals(listOf(event(take = 1)), result.events)
        assertTrue(result.tornLastLine)
    }

    @Test
    fun `throws on corruption that is not the last line`() {
        val tornLine = TimelineEventCodec.encodeLine(event(take = 1)).dropLast(10)
        val goodLine = TimelineEventCodec.encodeLine(event(take = 2))
        val content = "$tornLine\n$goodLine"

        assertThrows(TimelineCorruptionException::class.java) {
            TimelineEventCodec.decodeLines(content)
        }
    }

    @Test
    fun `ignores blank lines`() {
        val goodLine = TimelineEventCodec.encodeLine(event())
        val content = "\n$goodLine\n\n"
        val result = TimelineEventCodec.decodeLines(content)
        assertEquals(listOf(event()), result.events)
        assertFalse(result.tornLastLine)
    }

    @Test
    fun `empty content decodes to no events`() {
        val result = TimelineEventCodec.decodeLines("")
        assertTrue(result.events.isEmpty())
        assertFalse(result.tornLastLine)
    }
}
