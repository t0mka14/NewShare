package org.example.app.domain.session

import org.example.app.domain.audio.CaptureFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.file.Path

class MasterPartMapTest {

    private val format48k = CaptureFormat(48_000, 16, 1)
    private val format44k = CaptureFormat(44_100, 16, 1)
    private val sessionDir = Path.of("fake-session-dir")
    private val defaultMasterFile = sessionDir.resolve("master/session_master.wav")

    @Test
    fun `no interruptions yields a single open-ended part`() {
        val parts = MasterPartMap.build(defaultMasterFile, format48k, emptyList(), sessionDir)

        assertEquals(1, parts.size)
        assertEquals(defaultMasterFile, parts[0].file)
        assertEquals(format48k, parts[0].format)
        assertEquals(0L, parts[0].globalStart)
        assertNull(parts[0].globalEndExclusive)
    }

    @Test
    fun `one interruption splits into two parts at the interruption's sampleOffset`() {
        val interruption = Interruption(
            sampleOffset = 480_000L,
            start = "2026-07-01T09:05:00Z",
            end = "2026-07-01T09:05:10Z",
            partFile = "master/session_master.part2.wav",
            captureFormat = format44k,
        )
        val parts = MasterPartMap.build(defaultMasterFile, format48k, listOf(interruption), sessionDir)

        assertEquals(2, parts.size)
        assertEquals(0L, parts[0].globalStart)
        assertEquals(480_000L, parts[0].globalEndExclusive)
        assertEquals(format48k, parts[0].format)

        assertEquals(sessionDir.resolve("master/session_master.part2.wav"), parts[1].file)
        assertEquals(480_000L, parts[1].globalStart)
        assertNull(parts[1].globalEndExclusive)
        assertEquals(format44k, parts[1].format)
    }

    @Test
    fun `resolve finds the containing part and translates to local offsets`() {
        val interruption = Interruption(
            sampleOffset = 1000L,
            start = "t",
            partFile = "master/session_master.part2.wav",
            captureFormat = format44k,
        )
        val parts = MasterPartMap.build(defaultMasterFile, format48k, listOf(interruption), sessionDir)

        val inPart1 = MasterPartMap.resolve(parts, 100L, 900L)
        assertEquals(parts[0], inPart1?.first)
        assertEquals(100L until 900L, inPart1?.second)

        val inPart2 = MasterPartMap.resolve(parts, 1200L, 1500L)
        assertEquals(parts[1], inPart2?.first)
        assertEquals(200L until 500L, inPart2?.second) // 1200-1000, 1500-1000
    }

    @Test
    fun `resolve returns null for a range spanning a part boundary`() {
        val interruption = Interruption(sampleOffset = 1000L, start = "t", partFile = "master/session_master.part2.wav", captureFormat = format44k)
        val parts = MasterPartMap.build(defaultMasterFile, format48k, listOf(interruption), sessionDir)

        assertNull(MasterPartMap.resolve(parts, 900L, 1100L))
    }

    @Test
    fun `resolve returns null for a range entirely out of range`() {
        val parts = MasterPartMap.build(defaultMasterFile, format48k, emptyList(), sessionDir)
        // A single open-ended part starting at 0 always "contains" any start >= 0 in this
        // model (no known total length) — a negative start is the only representable
        // out-of-range case at this layer; true upper-bound validation happens in
        // AudioClipService.cutClip against the real file's frame count.
        assertNull(MasterPartMap.resolve(parts, -10L, 5L))
    }

    @Test
    fun `two interruptions with a format-changing swap produce three parts`() {
        val interruption1 = Interruption(sampleOffset = 500L, start = "t1", partFile = "master/session_master.part2.wav", captureFormat = format44k)
        val interruption2 = Interruption(sampleOffset = 900L, start = "t2", partFile = "master/session_master.part3.wav", captureFormat = format48k)
        val parts = MasterPartMap.build(defaultMasterFile, format48k, listOf(interruption1, interruption2), sessionDir)

        assertEquals(3, parts.size)
        assertEquals(0L, parts[0].globalStart); assertEquals(500L, parts[0].globalEndExclusive); assertEquals(format48k, parts[0].format)
        assertEquals(500L, parts[1].globalStart); assertEquals(900L, parts[1].globalEndExclusive); assertEquals(format44k, parts[1].format)
        assertEquals(900L, parts[2].globalStart); assertNull(parts[2].globalEndExclusive); assertEquals(format48k, parts[2].format)

        val resolved = MasterPartMap.resolve(parts, 950L, 980L)
        assertEquals(parts[2], resolved?.first)
        assertEquals(50L until 80L, resolved?.second)
    }
}
