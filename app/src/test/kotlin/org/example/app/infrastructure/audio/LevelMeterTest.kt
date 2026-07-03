package org.example.app.infrastructure.audio

import org.example.app.domain.audio.CaptureFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

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

    @Test
    fun `all-zero buffer has zero RMS`() {
        val buffer = pcm16Of(IntArray(800)) // silence
        val rms = LevelMeter.computeRms(buffer, 0, buffer.size, format)
        assertEquals(0f, rms)
    }

    @Test
    fun `full-scale square wave has RMS of approximately 1_0`() {
        val samples = IntArray(800) { if (it % 2 == 0) 32767 else -32768 }
        val buffer = pcm16Of(samples)
        val rms = LevelMeter.computeRms(buffer, 0, buffer.size, format)
        assertTrue(rms > 0.999f, "expected ~1.0, was $rms")
    }

    @Test
    fun `sine wave RMS matches amplitude div sqrt2 within tolerance`() {
        val amplitude = 0.5
        val freqHz = 440.0
        val n = 4000
        val samples = IntArray(n) { i ->
            (amplitude * 32767.0 * sin(2 * PI * freqHz * i / format.sampleRate)).toInt()
        }
        val buffer = pcm16Of(samples)
        val rms = LevelMeter.computeRms(buffer, 0, buffer.size, format)
        val expected = (amplitude / sqrt(2.0)).toFloat()
        assertTrue(kotlin.math.abs(rms - expected) < 0.01f, "expected ~$expected, was $rms")
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
    fun `process smooths rather than jumping instantly to a loud chunk`() {
        val meter = LevelMeter(windowMs = 300.0)
        val loud = pcm16Of(IntArray(800) { if (it % 2 == 0) 32767 else -32768 }) // ~100ms @ 8kHz
        val first = meter.process(loud, 0, loud.size, format)
        assertTrue(first in 0.0f..1.0f)
        assertTrue(first < 0.9f, "a single ~100ms chunk into a 300ms window should not reach full scale yet, was $first")
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
    fun `process decays back toward zero once silence resumes`() {
        val meter = LevelMeter(windowMs = 300.0)
        val loud = pcm16Of(IntArray(800) { if (it % 2 == 0) 32767 else -32768 })
        val silence = pcm16Of(IntArray(800))
        repeat(20) { meter.process(loud, 0, loud.size, format) }
        val loudLevel = meter.process(loud, 0, loud.size, format)
        repeat(20) { meter.process(silence, 0, silence.size, format) }
        val quietLevel = meter.process(silence, 0, silence.size, format)
        assertTrue(quietLevel < loudLevel)
        assertTrue(quietLevel < 0.05f, "expected near-zero after sustained silence, was $quietLevel")
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
