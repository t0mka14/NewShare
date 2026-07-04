package org.example.app.infrastructure.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AudioConversionTest {

    @Test
    fun `read-write sample round trip for interleaved stereo`() {
        val data = ByteArray(8) // 2 frames, 2 channels
        AudioConversion.writeSample(data, 0, 0, 2, 1234)
        AudioConversion.writeSample(data, 0, 1, 2, -1234)
        AudioConversion.writeSample(data, 1, 0, 2, Short.MAX_VALUE)
        AudioConversion.writeSample(data, 1, 1, 2, Short.MIN_VALUE)

        assertEquals(1234, AudioConversion.readSample(data, 0, 0, 2).toInt())
        assertEquals(-1234, AudioConversion.readSample(data, 0, 1, 2).toInt())
        assertEquals(Short.MAX_VALUE.toInt(), AudioConversion.readSample(data, 1, 0, 2).toInt())
        assertEquals(Short.MIN_VALUE.toInt(), AudioConversion.readSample(data, 1, 1, 2).toInt())
    }

    @Test
    fun `downmix to mono averages left and right exactly`() {
        val channels = 2
        val frameCount = 3L
        val data = ByteArray((frameCount * channels * 2).toInt())
        // frame 0: L=100 R=200 -> avg 150; frame 1: L=-100 R=-200 -> avg -150 (integer div toward zero: -150);
        // frame 2: L=Short.MAX R=Short.MAX -> avg Short.MAX
        AudioConversion.writeSample(data, 0, 0, channels, 100)
        AudioConversion.writeSample(data, 0, 1, channels, 200)
        AudioConversion.writeSample(data, 1, 0, channels, -100)
        AudioConversion.writeSample(data, 1, 1, channels, -200)
        AudioConversion.writeSample(data, 2, 0, channels, Short.MAX_VALUE)
        AudioConversion.writeSample(data, 2, 1, channels, Short.MAX_VALUE)

        val mono = AudioConversion.downmixToMono(data, frameCount, channels)

        assertEquals(150, AudioConversion.readSample(mono, 0, 0, 1).toInt())
        assertEquals(-150, AudioConversion.readSample(mono, 1, 0, 1).toInt())
        assertEquals(Short.MAX_VALUE.toInt(), AudioConversion.readSample(mono, 2, 0, 1).toInt())
    }

    @Test
    fun `downmix is a no-op copy when already mono`() {
        val data = ByteArray(6)
        AudioConversion.writeSample(data, 0, 0, 1, 42)
        AudioConversion.writeSample(data, 1, 0, 1, -42)
        AudioConversion.writeSample(data, 2, 0, 1, 7)

        val result = AudioConversion.downmixToMono(data, 3, 1)

        assertEquals(42, AudioConversion.readSample(result, 0, 0, 1).toInt())
        assertEquals(-42, AudioConversion.readSample(result, 1, 0, 1).toInt())
        assertEquals(7, AudioConversion.readSample(result, 2, 0, 1).toInt())
    }

    @Test
    fun `resample is a byte-identical copy when rates match`() {
        val samples = monoRamp(100)
        val data = ByteArray(samples.size * 2)
        for (i in samples.indices) AudioConversion.writeSample(data, i.toLong(), 0, 1, samples[i])

        val result = AudioConversion.resampleLinear(data, 100, 1, 48_000, 48_000)

        assertEquals(data.size, result.size)
        for (i in samples.indices) {
            assertEquals(samples[i].toInt(), AudioConversion.readSample(result, i.toLong(), 0, 1).toInt())
        }
    }

    @Test
    fun `resample upsampling doubles frame count and preserves endpoints`() {
        val frameCount = 100L
        val data = ByteArray((frameCount * 2).toInt())
        for (i in 0 until frameCount) {
            AudioConversion.writeSample(data, i, 0, 1, (i * 10).toInt().toShort())
        }

        val result = AudioConversion.resampleLinear(data, frameCount, 1, 24_000, 48_000)
        val outFrames = result.size / 2

        assertEquals(200, outFrames)
        // First output frame aligns with the first input frame exactly.
        assertEquals(0, AudioConversion.readSample(result, 0, 0, 1).toInt())
        // Linear interpolation at the midpoint between input frame 0 (value 0) and
        // frame 1 (value 10) should land on 5.
        assertEquals(5, AudioConversion.readSample(result, 1, 0, 1).toInt())
    }

    @Test
    fun `resample downsampling halves frame count`() {
        val frameCount = 100L
        val data = ByteArray((frameCount * 2).toInt())
        for (i in 0 until frameCount) {
            AudioConversion.writeSample(data, i, 0, 1, (i * 10).toInt().toShort())
        }

        val result = AudioConversion.resampleLinear(data, frameCount, 1, 48_000, 24_000)
        val outFrames = result.size / 2

        assertEquals(50, outFrames)
        assertEquals(0, AudioConversion.readSample(result, 0, 0, 1).toInt())
        // Output frame i samples input position i*2 -> value (i*2)*10
        assertEquals(20, AudioConversion.readSample(result, 1, 0, 1).toInt())
    }

    @Test
    fun `resample of an empty source yields an empty result`() {
        val result = AudioConversion.resampleLinear(ByteArray(0), 0, 1, 44_100, 48_000)
        assertTrue(result.isEmpty())
    }
}
