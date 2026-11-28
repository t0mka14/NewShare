package org.example.app.domain.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RangeTimeMapTest {
    // kept: 10..19 (10 frames), 30..34 (5), 50..59 (10) -> total 25
    private val map = RangeTimeMap(listOf(10L..19L, 30L..34L, 50L..59L))

    @Test
    fun `total played frames is the sum of range lengths`() {
        assertEquals(25L, map.totalPlayedFrames)
    }

    @Test
    fun `sourceFrameAt maps within ranges`() {
        assertEquals(10L, map.sourceFrameAt(0))
        assertEquals(19L, map.sourceFrameAt(9))
        assertEquals(34L, map.sourceFrameAt(14))
        assertEquals(50L, map.sourceFrameAt(15))
        assertEquals(59L, map.sourceFrameAt(24))
    }

    @Test
    fun `sourceFrameAt at a boundary reports the start of the next range, never a cut sample`() {
        assertEquals(30L, map.sourceFrameAt(10))
        assertEquals(50L, map.sourceFrameAt(15))
    }

    @Test
    fun `sourceFrameAt clamps below zero and at or past the end`() {
        assertEquals(10L, map.sourceFrameAt(-5))
        assertEquals(60L, map.sourceFrameAt(25))
        assertEquals(60L, map.sourceFrameAt(1_000))
    }

    @Test
    fun `playedFramesAt maps kept frames`() {
        assertEquals(0L, map.playedFramesAt(10))
        assertEquals(9L, map.playedFramesAt(19))
        assertEquals(10L, map.playedFramesAt(30))
        assertEquals(14L, map.playedFramesAt(34))
        assertEquals(24L, map.playedFramesAt(59))
    }

    @Test
    fun `playedFramesAt for a source frame in a cut maps to the start of the next kept range`() {
        assertEquals(0L, map.playedFramesAt(0))
        assertEquals(0L, map.playedFramesAt(9))
        assertEquals(10L, map.playedFramesAt(20))
        assertEquals(10L, map.playedFramesAt(29))
        assertEquals(15L, map.playedFramesAt(35))
        assertEquals(15L, map.playedFramesAt(49))
    }

    @Test
    fun `playedFramesAt past the last range is the total`() {
        assertEquals(25L, map.playedFramesAt(60))
        assertEquals(25L, map.playedFramesAt(10_000))
    }

    @Test
    fun `round trip for every kept source frame`() {
        for (r in map.ranges) for (s in r) assertEquals(s, map.sourceFrameAt(map.playedFramesAt(s)))
    }

    @Test
    fun `touching ranges and a single range work`() {
        val touching = RangeTimeMap(listOf(0L..9L, 10L..19L))
        assertEquals(20L, touching.totalPlayedFrames)
        assertEquals(10L, touching.sourceFrameAt(10))
        val single = RangeTimeMap(listOf(100L until 300L))
        assertEquals(100L, single.sourceFrameAt(0))
        assertEquals(300L, single.sourceFrameAt(200))
        assertEquals(50L, single.playedFramesAt(150))
    }

    @Test
    fun `invalid ranges are rejected`() {
        assertThrows<IllegalArgumentException> { RangeTimeMap(emptyList()) }
        assertThrows<IllegalArgumentException> { RangeTimeMap(listOf(5L..4L)) }
        assertThrows<IllegalArgumentException> { RangeTimeMap(listOf(-1L..4L)) }
        assertThrows<IllegalArgumentException> { RangeTimeMap(listOf(0L..10L, 10L..20L)) }
        assertThrows<IllegalArgumentException> { RangeTimeMap(listOf(20L..30L, 0L..5L)) }
    }
}
