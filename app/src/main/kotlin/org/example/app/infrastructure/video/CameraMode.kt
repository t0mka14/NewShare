package org.example.app.infrastructure.video

import org.example.app.domain.video.VideoCaptureFormat
import kotlin.math.abs

/**
 * One capture mode a camera advertises.
 *
 * @param mjpeg whether this mode delivers MJPEG, so the stream can be copied instead of
 *   re-encoded. AVFoundation does not report the codec, so it is false there for want of
 *   knowing — passthrough is still attempted first, it is just not a ranking signal.
 */
internal data class CameraMode(
    val width: Int,
    val height: Int,
    val maxFps: Int,
    val mjpeg: Boolean,
) {
    val pixels: Int get() = width * height

    /** The mode as something to request, never asking for more frames than it can deliver. */
    fun toFormat(preferredFps: Int): VideoCaptureFormat =
        VideoCaptureFormat(width = width, height = height, fps = minOf(maxFps, preferredFps))
}

/**
 * Orders advertised modes best-first for [target].
 *
 * MJPEG modes come first — those record as a byte copy, with no encoding on the capture path
 * at all. Within that, the mode closest to the target pixel count wins, so a camera whose best
 * mode is 1600x1200 is used at 1600x1200 rather than being dropped to the nearest rung of a
 * blind ladder. Ties break toward the higher frame rate.
 */
internal fun rankModes(modes: List<CameraMode>, target: VideoCaptureFormat): List<CameraMode> {
    val targetPixels = target.width.toLong() * target.height
    return modes.distinct().sortedWith(
        compareByDescending<CameraMode> { it.mjpeg }
            .thenBy { abs(it.pixels - targetPixels) }
            .thenByDescending { it.maxFps },
    )
}
