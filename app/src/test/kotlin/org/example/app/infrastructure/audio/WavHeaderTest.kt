package org.example.app.infrastructure.audio

import org.example.app.domain.audio.CaptureFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class WavHeaderTest {

    @Test
    fun `build then parse round-trips format and data size`() {
        val format = CaptureFormat(sampleRate = 48_000, bits = 16, channels = 1)
        val header = WavHeader.build(format, dataSize = 96_000L)

        assertEquals(WavHeader.HEADER_SIZE, header.size)

        val parsed = WavHeader.parse(header)
        assertEquals(format, parsed.format)
        assertEquals(96_000L, parsed.dataSize)
        assertEquals(36L + 96_000L, parsed.riffChunkSize)
    }

    @Test
    fun `zero-length data produces a still-parseable header`() {
        val format = CaptureFormat.PREFERRED
        val header = WavHeader.build(format, dataSize = 0L)
        val parsed = WavHeader.parse(header)
        assertEquals(0L, parsed.dataSize)
        assertEquals(format, parsed.format)
    }

    @Test
    fun `stereo 24-bit format round-trips`() {
        val format = CaptureFormat(sampleRate = 44_100, bits = 24, channels = 2)
        val header = WavHeader.build(format, dataSize = 12_345L)
        val parsed = WavHeader.parse(header)
        assertEquals(format, parsed.format)
        assertEquals(12_345L, parsed.dataSize)
    }

    @Test
    fun `parse rejects a non-RIFF buffer`() {
        val garbage = ByteArray(WavHeader.HEADER_SIZE) { 0x42 }
        assertThrows(IllegalArgumentException::class.java) { WavHeader.parse(garbage) }
    }

    @Test
    fun `parse rejects a truncated buffer`() {
        val header = WavHeader.build(CaptureFormat.PREFERRED, 1000L)
        assertThrows(IllegalArgumentException::class.java) { WavHeader.parse(header.copyOf(20)) }
    }

    @Test
    fun `header bytes at known offsets match the canonical WAV layout`() {
        val format = CaptureFormat(sampleRate = 16_000, bits = 16, channels = 1)
        val header = WavHeader.build(format, dataSize = 4000L)

        assertEquals("RIFF", String(header, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(header, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(header, 12, 4, Charsets.US_ASCII))
        assertEquals("data", String(header, 36, 4, Charsets.US_ASCII))
    }
}
