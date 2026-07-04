package org.example.app.infrastructure.audio

/**
 * Pure PCM16 sample-array math for cutting/resampling/downmixing (§8.8).
 *
 * Scope: 16-bit PCM only, matching [LevelMeter]'s existing precedent — the
 * format negotiator (§5.3.1) can in theory fall back to 8/24/32-bit for an
 * unusual device, but nothing else in this codebase processes those depths
 * either yet. This is a pre-existing scope limit inherited from Phase 1, not
 * a new one introduced here; flagged to the lead in the task report.
 *
 * Samples are interleaved 16-bit little-endian frames, addressed directly in
 * byte arrays (no intermediate `ShortArray`/`DoubleArray` copy) so memory use
 * stays proportional to file size for up to ~30-minute sessions (§8.1)
 * instead of a multiple of it.
 */
object AudioConversion {

    /** Reads the interleaved 16-bit sample at frame [frameIndex], channel [channel]. */
    fun readSample(data: ByteArray, frameIndex: Long, channel: Int, channels: Int): Short {
        val byteOffset = ((frameIndex * channels + channel) * 2).toInt()
        val lo = data[byteOffset].toInt() and 0xFF
        val hi = data[byteOffset + 1].toInt() and 0xFF
        return ((hi shl 8) or lo).toShort()
    }

    /** Writes a 16-bit sample at frame [frameIndex], channel [channel]. */
    fun writeSample(data: ByteArray, frameIndex: Long, channel: Int, channels: Int, value: Short) {
        val byteOffset = ((frameIndex * channels + channel) * 2).toInt()
        val v = value.toInt()
        data[byteOffset] = (v and 0xFF).toByte()
        data[byteOffset + 1] = ((v shr 8) and 0xFF).toByte()
    }

    /**
     * Downmixes interleaved [channels]-channel PCM16 [source] ([frameCount]
     * frames) to mono by averaging every frame's channel samples (§8.8:
     * "stereo->mono downmix by averaging"). A no-op copy when already mono.
     */
    fun downmixToMono(source: ByteArray, frameCount: Long, channels: Int): ByteArray {
        if (channels == 1) return source.copyOf((frameCount * 2).toInt())
        val out = ByteArray((frameCount * 2).toInt())
        for (frame in 0 until frameCount) {
            var sum = 0
            for (c in 0 until channels) sum += readSample(source, frame, c, channels)
            val avg = (sum.toDouble() / channels).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            writeSample(out, frame, 0, 1, avg)
        }
        return out
    }

    /**
     * Linear-interpolation resample of interleaved PCM16 [source]
     * ([channels] channels, [sourceFrameCount] frames) from [fromRate] to
     * [toRate] Hz.
     *
     * Design choice (§8.8 "resampling to target format"): linear
     * interpolation with no anti-aliasing filter. This is acceptable quality
     * for speech intelligibility — the app's clinical use case — at the
     * rate ratios involved (44.1/48/96 kHz family); it is not suitable for
     * high-fidelity music resampling, where imaging/aliasing artifacts from
     * the missing filter would be audible.
     */
    fun resampleLinear(source: ByteArray, sourceFrameCount: Long, channels: Int, fromRate: Int, toRate: Int): ByteArray {
        if (fromRate == toRate) return source.copyOf((sourceFrameCount * channels * 2).toInt())
        if (sourceFrameCount == 0L) return ByteArray(0)

        val outFrameCount = Math.round(sourceFrameCount * toRate.toDouble() / fromRate.toDouble())
        val out = ByteArray((outFrameCount * channels * 2).toInt())
        val ratio = fromRate.toDouble() / toRate.toDouble()
        for (i in 0 until outFrameCount) {
            val pos = i * ratio
            val i0 = pos.toLong().coerceIn(0, sourceFrameCount - 1)
            val i1 = (i0 + 1).coerceAtMost(sourceFrameCount - 1)
            val frac = pos - i0
            for (c in 0 until channels) {
                val s0 = readSample(source, i0, c, channels).toDouble()
                val s1 = readSample(source, i1, c, channels).toDouble()
                val v = (s0 + (s1 - s0) * frac).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                writeSample(out, i, c, channels, v)
            }
        }
        return out
    }
}
