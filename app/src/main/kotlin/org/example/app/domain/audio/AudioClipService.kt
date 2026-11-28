package org.example.app.domain.audio

import java.nio.file.Path

/**
 * Sample-accurate cutting/conversion/concatenation of WAV master (parts) into
 * archived clips and the archived `session_master.wav` (§8.5, §8.8). This is
 * pure mechanics: the caller (`ProcessSessionUseCase`, domain layer) decides
 * *which* ranges to cut and how timeline offsets map onto master parts — this
 * port only operates on WAV files it is given.
 *
 * All sample indices are frame indices in the *source* file's own sample
 * rate/channel layout (§5.3.1: "cutting is sample-exact with respect to
 * recorded offsets") — never a cross-file/cross-rate global timeline.
 */
interface AudioClipService {

    /**
     * Cuts `[startSample, stopSample)` out of [sourceWav] and writes it to
     * [output], converting to [targetFormat] (resample and/or downmix) if the
     * source's own format differs. The range must lie within the source
     * file's frame count (`0 <= startSample < stopSample <= totalFrames`) —
     * out-of-range or empty ranges are rejected with [IllegalArgumentException],
     * never silently clamped or truncated (clinical data).
     */
    fun cutClip(sourceWav: Path, startSample: Long, stopSample: Long, targetFormat: CaptureFormat, output: Path) =
        cutClip(sourceWav, listOf(startSample until stopSample), targetFormat, output)

    /**
     * Splices [keepRanges] (inclusive frame ranges in [sourceWav]'s own frames) in list order into
     * one clip at [targetFormat], written atomically to [output]. Each range is converted on its
     * own and the pieces are joined raw (no fade). Ranges must be non-empty, ascending,
     * non-overlapping and within the file, and the list must not be empty. Violations throw
     * [IllegalArgumentException] and write nothing.
     */
    fun cutClip(sourceWav: Path, keepRanges: List<LongRange>, targetFormat: CaptureFormat, output: Path)

    /**
     * Zero-crossing snap for cut boundaries. Scans frames `[sample - maxRadiusSamples,
     * sample + maxRadiusSamples]` of [sourceWav] (clamped to the file; multi-channel mixed to
     * mono; PCM16) for the frame index `i` closest to [sample] where the signal changes sign
     * between frame `i-1` and `i`, or where frame `i` is exactly 0 (ties go to the earlier
     * index). Returns that index, usable as a `[start, stop)` boundary, or [sample] unchanged
     * if there is no such frame.
     */
    fun nearestZeroCrossing(sourceWav: Path, sample: Long, maxRadiusSamples: Long): Long

    /** Converts the whole [sourceWav] to [targetFormat] (resample/downmix as needed), writing [output]. */
    fun convert(sourceWav: Path, targetFormat: CaptureFormat, output: Path)

    /**
     * Concatenates [sources] in list order into [output]. Every source must
     * already be in [targetFormat] (checked against each file's own header;
     * a mismatch throws [IllegalArgumentException]) — callers convert each
     * master part with [convert] first, then join the converted parts here
     * (§8.5: interrupted-session parts are converted individually, then
     * concatenated into the archived master).
     */
    fun concatenate(sources: List<Path>, targetFormat: CaptureFormat, output: Path)
}
