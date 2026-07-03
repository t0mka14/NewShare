package org.example.app.infrastructure.audio

import org.example.app.domain.audio.CaptureFormat
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Linear RMS level, normalized 0.0-1.0 full scale, smoothed over a ~300 ms
 * window (§5.3.1) — the *one* formula used in both Monitoring and Writing
 * modes. Pure PCM16 byte-buffer math; no line/thread dependency, so it is
 * unit-testable with synthesized buffers.
 *
 * Stateful only in the smoothing accumulator: one instance per recorder
 * session (call [reset] between sessions/parts to avoid a stale carry-over).
 */
class LevelMeter(private val windowMs: Double = DEFAULT_WINDOW_MS) {
    @Volatile
    var currentLevel: Float = 0f
        private set

    fun reset() {
        currentLevel = 0f
    }

    /** Computes the RMS of [length] bytes of PCM16 audio starting at [offset] in
     * [buffer], folds it into the smoothed level, and returns the new smoothed value. */
    fun process(buffer: ByteArray, offset: Int, length: Int, format: CaptureFormat): Float {
        val rms = computeRms(buffer, offset, length, format)
        val chunkMs = format.millisIn(length)
        val alpha = smoothingAlpha(chunkMs, windowMs)
        currentLevel = (currentLevel + alpha * (rms - currentLevel)).toFloat().coerceIn(0f, 1f)
        return currentLevel
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 300.0

        /** Exponential-moving-average weight for a chunk of [chunkMs] folded into a
         * smoothing window of [windowMs]: larger chunks (relative to the window) pull
         * the average further toward the new sample. */
        fun smoothingAlpha(chunkMs: Double, windowMs: Double): Double {
            if (windowMs <= 0.0) return 1.0
            return (1.0 - exp(-chunkMs / windowMs)).coerceIn(0.0, 1.0)
        }

        /** Un-smoothed linear RMS of one chunk of 16-bit PCM, normalized 0.0-1.0
         * full scale. All channels are folded into a single RMS value. */
        fun computeRms(buffer: ByteArray, offset: Int, length: Int, format: CaptureFormat): Float {
            require(format.bits == 16) { "LevelMeter currently supports 16-bit PCM only, was ${format.bits}-bit" }
            var sumSquares = 0.0
            var sampleCount = 0
            var i = offset
            val end = offset + length
            while (i + 1 < end) {
                val lo = buffer[i].toInt() and 0xFF
                val hi = buffer[i + 1].toInt() and 0xFF
                val sample = ((hi shl 8) or lo).toShort().toInt()
                val normalized = sample / 32768.0
                sumSquares += normalized * normalized
                sampleCount++
                i += 2
            }
            if (sampleCount == 0) return 0f
            return sqrt(sumSquares / sampleCount).toFloat().coerceIn(0f, 1f)
        }

        /** True when every byte in the given range is zero (device-loss silence
         * heuristic input, §8.5) — independent of bit depth/channel layout. */
        fun isAllZero(buffer: ByteArray, offset: Int, length: Int): Boolean {
            for (i in offset until offset + length) {
                if (buffer[i] != 0.toByte()) return false
            }
            return true
        }
    }
}
