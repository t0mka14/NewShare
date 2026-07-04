package org.example.app.domain.audio

import java.nio.file.Path

/**
 * Downsampled min/max peak pairs for a WAV file's visible portion (§8.7). One
 * pair per bucket, signed and normalized to [-1.0, 1.0] full scale (unlike the
 * recorder's unsigned RMS [ContinuousSessionRecorder.levels] — a waveform
 * needs the sign to draw the envelope above/below its center line).
 *
 * [min] and [max] each have exactly [bucketCount] entries, one per equal-width
 * slice of the requested `[startSample, endSample)` range, in order.
 */
data class WaveformPeaks(
    val bucketCount: Int,
    val min: List<Float>,
    val max: List<Float>,
) {
    init {
        require(min.size == bucketCount) { "min has ${min.size} entries, expected $bucketCount" }
        require(max.size == bucketCount) { "max has ${max.size} entries, expected $bucketCount" }
    }
}

/**
 * Computes/caches downsampled waveform peaks for editor rendering (§8.7).
 * Only the visible portion is ever computed — the UI passes the exact range
 * it needs, at the exact bucket resolution it will draw.
 */
interface WaveformService {
    /**
     * Returns [bucketCount] min/max peak pairs covering `[startSample,
     * endSample)` of [wav] (frame indices in that file's own sample rate).
     * The range must lie within the file's frame count — out-of-range or
     * empty ranges are rejected with [IllegalArgumentException].
     *
     * Implementations may cache intermediate results on disk keyed to [wav]
     * (regenerated automatically if missing or stale) — callers do not need
     * to manage the cache themselves.
     */
    fun peaks(wav: Path, bucketCount: Int, startSample: Long, endSample: Long): WaveformPeaks
}
