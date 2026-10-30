package org.example.app.infrastructure.audio

import org.example.app.domain.audio.CaptureFormat
import kotlin.math.exp

/**
 * Peak level, normalized 0.0-1.0 full scale, with instant attack and an exponential
 * release over a ~300 ms window (§5.3.1) — the *one* formula used in both Monitoring and
 * Writing modes. F-143: this used to be RMS, which for speech reads 2-3x below the peak an
 * editor waveform shows, so the meters looked dead and the calibration band was unreachable.
 * Pure PCM16 byte-buffer math; no line/thread dependency, so it is unit-testable with
 * synthesized buffers.
 *
 * Stateful only in the release accumulator: one instance per recorder session (call
 * [reset] between sessions/parts to avoid a stale carry-over).
 */
class LevelMeter(private val windowMs: Double = DEFAULT_WINDOW_MS) {
    @Volatile
    var currentLevel: Float = 0f
        private set

    fun reset() {
        currentLevel = 0f
    }

    /** Computes the peak of [length] bytes of PCM16 audio starting at [offset] in
     * [buffer] and folds it into the level (instant attack, exponential release), and
     * returns the new value. */
    fun process(buffer: ByteArray, offset: Int, length: Int, format: CaptureFormat): Float {
        val peak = computePeak(buffer, offset, length, format)
        currentLevel = if (peak >= currentLevel) {
            peak
        } else {
            val alpha = smoothingAlpha(format.millisIn(length), windowMs)
            (currentLevel + alpha * (peak - currentLevel)).toFloat().coerceIn(0f, 1f)
        }
        return currentLevel
    }

    companion object {
        /** Release window of the calibration bar and any threshold decision (§5.3.1): how
         * slowly the peak level falls back after loud audio. */
        const val DEFAULT_WINDOW_MS = 300.0

        /**
         * Release window behind `ContinuousSessionRecorder.fastLevels`, which drives both live
         * task-screen indicators (§6.2) — **the knob to turn if they look too sticky or too
         * twitchy.** Attack is always instant; the window only sets how fast the trace falls
         * back after a loud chunk: at 40 ms with 50 ms chunks a drop keeps ~29% of the old
         * level per chunk. `0.0` disables the release entirely and yields the plain per-chunk
         * peak; raising it toward [DEFAULT_WINDOW_MS] makes both the WAVEFORM trace and the
         * CIRCLE's swell linger longer after each peak.
         */
        const val FAST_WINDOW_MS = 40.0

        /** Exponential-moving-average weight for a chunk of [chunkMs] folded into a
         * smoothing window of [windowMs]: larger chunks (relative to the window) pull
         * the average further toward the new sample. */
        fun smoothingAlpha(chunkMs: Double, windowMs: Double): Double {
            if (windowMs <= 0.0) return 1.0
            return (1.0 - exp(-chunkMs / windowMs)).coerceIn(0.0, 1.0)
        }

        /** Un-smoothed peak of one chunk of 16-bit PCM: max |sample| normalized 0.0-1.0
         * full scale. All channels are folded into a single value. */
        fun computePeak(buffer: ByteArray, offset: Int, length: Int, format: CaptureFormat): Float {
            require(format.bits == 16) { "LevelMeter currently supports 16-bit PCM only, was ${format.bits}-bit" }
            var peak = 0
            var i = offset
            val end = offset + length
            while (i + 1 < end) {
                val lo = buffer[i].toInt() and 0xFF
                val hi = buffer[i + 1].toInt() and 0xFF
                val sample = ((hi shl 8) or lo).toShort().toInt()
                val magnitude = if (sample < 0) -sample else sample
                if (magnitude > peak) peak = magnitude
                i += 2
            }
            return (peak / 32768.0).toFloat().coerceIn(0f, 1f)
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
