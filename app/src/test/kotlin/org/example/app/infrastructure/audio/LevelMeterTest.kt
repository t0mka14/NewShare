package org.example.app.infrastructure.audio

import org.example.app.domain.audio.CaptureFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class LevelMeterTest {

    private val format = CaptureFormat(sampleRate = 8_000, bits = 16, channels = 1)

    private fun pcm16Of(samples: IntArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        for ((i, s) in samples.withIndex()) {
            bytes[i * 2] = (s and 0xFF).toByte()
            bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private val square = IntArray(800) { if (it % 2 == 0) 32767 else -32768 } // ~100ms @ 8kHz

    @Test
    fun `all-zero buffer has zero peak`() {
        val buffer = pcm16Of(IntArray(800)) // silence
        assertEquals(0f, LevelMeter.computePeak(buffer, 0, buffer.size, format))
        assertEquals(0f, LevelMeter.computePeak(buffer, 0, 0, format))
    }

    @Test
    fun `full-scale square wave has peak of approximately 1_0`() {
        val buffer = pcm16Of(square)
        val peak = LevelMeter.computePeak(buffer, 0, buffer.size, format)
        assertTrue(peak > 0.999f, "expected ~1.0, was $peak")
    }

    @Test
    fun `sine peak matches its amplitude`() {
        val amplitude = 0.5
        val samples = IntArray(4000) { i ->
            (amplitude * 32767.0 * sin(2 * PI * 440.0 * i / format.sampleRate)).toInt()
        }
        val buffer = pcm16Of(samples)
        val peak = LevelMeter.computePeak(buffer, 0, buffer.size, format)
        assertTrue(abs(peak - amplitude.toFloat()) < 0.01f, "expected ~$amplitude, was $peak")
    }

    @Test
    fun `stereo peak is the louder channel`() {
        val stereo = CaptureFormat(sampleRate = 8_000, bits = 16, channels = 2)
        // interleaved: L quiet (0.1), R loud (0.6, negative to check |sample|)
        val samples = IntArray(1600) { if (it % 2 == 0) 3277 else -19661 }
        val buffer = pcm16Of(samples)
        val peak = LevelMeter.computePeak(buffer, 0, buffer.size, stereo)
        assertTrue(abs(peak - 0.6f) < 0.01f, "expected ~0.6, was $peak")
    }

    @Test
    fun `isAllZero detects zero and non-zero buffers`() {
        val zero = ByteArray(400)
        val nonZero = ByteArray(400).also { it[399] = 1 }
        assertTrue(LevelMeter.isAllZero(zero, 0, zero.size))
        assertTrue(!LevelMeter.isAllZero(nonZero, 0, nonZero.size))
    }

    @Test
    fun `isAllZero only inspects the given range`() {
        val buffer = ByteArray(400).also { it[10] = 5 } // non-zero outside the checked range
        assertTrue(LevelMeter.isAllZero(buffer, 20, 100))
    }

    @Test
    fun `process jumps to a loud chunk's peak at once`() {
        val meter = LevelMeter(windowMs = 300.0)
        val chunk = pcm16Of(IntArray(800) { if (it % 2 == 0) 26214 else -26214 }) // 0.8 amplitude
        val first = meter.process(chunk, 0, chunk.size, format)
        assertTrue(abs(first - 0.8f) < 0.01f, "instant attack expected ~0.8, was $first")
    }

    @Test
    fun `process converges toward steady-state level over repeated identical chunks`() {
        val meter = LevelMeter(windowMs = 300.0)
        val loud = pcm16Of(IntArray(800) { if (it % 2 == 0) 32767 else -32768 }) // ~100ms @ 8kHz
        var last = 0f
        repeat(50) { last = meter.process(loud, 0, loud.size, format) }
        assertTrue(last > 0.99f, "expected convergence near 1.0 after many chunks, was $last")
    }

    @Test
    fun `process releases gradually and reaches zero under sustained silence`() {
        val meter = LevelMeter(windowMs = 300.0)
        val loud = pcm16Of(square)
        val silence = pcm16Of(IntArray(800))
        repeat(5) { meter.process(loud, 0, loud.size, format) }
        val afterOne = meter.process(silence, 0, silence.size, format)
        assertTrue(afterOne in 0.3f..0.99f, "one silent chunk should release gradually, was $afterOne")
        repeat(20) { meter.process(silence, 0, silence.size, format) }
        val quiet = meter.process(silence, 0, silence.size, format)
        assertTrue(quiet < 0.05f, "expected near-zero after sustained silence, was $quiet")
    }

    @Test
    fun `F-143 speech-like signal reads its peak not its RMS`() {
        val meter = LevelMeter(windowMs = 300.0)
        // 50 ms chunk @ 8 kHz = 400 samples: 0.1-amplitude sine with a 5 ms (40 sample) burst at 0.5
        val samples = IntArray(400) { i ->
            val amp = if (i in 100 until 140) 0.5 else 0.1
            (amp * 32767.0 * sin(2 * PI * 440.0 * i / format.sampleRate)).toInt()
        }
        val chunk = pcm16Of(samples)
        var level = 0f
        repeat(6) { level = meter.process(chunk, 0, chunk.size, format) }
        assertTrue(level >= 0.45f, "expected the burst peak (~0.5), was $level")
    }

    @Test
    fun `reset clears smoothing state`() {
        val meter = LevelMeter(windowMs = 300.0)
        val loud = pcm16Of(IntArray(800) { if (it % 2 == 0) 32767 else -32768 })
        repeat(20) { meter.process(loud, 0, loud.size, format) }
        assertTrue(meter.currentLevel > 0.5f)
        meter.reset()
        assertEquals(0f, meter.currentLevel)
    }

    @Test
    fun `smoothingAlpha is larger for chunks that are a bigger fraction of the window`() {
        val alphaSmallChunk = LevelMeter.smoothingAlpha(chunkMs = 20.0, windowMs = 300.0)
        val alphaBigChunk = LevelMeter.smoothingAlpha(chunkMs = 150.0, windowMs = 300.0)
        assertTrue(alphaBigChunk > alphaSmallChunk)
        assertTrue(alphaSmallChunk in 0.0..1.0)
        assertTrue(alphaBigChunk in 0.0..1.0)
    }
}
