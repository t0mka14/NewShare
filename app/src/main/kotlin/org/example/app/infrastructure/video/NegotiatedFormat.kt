package org.example.app.infrastructure.video

import org.example.app.domain.video.VideoCaptureFormat
import kotlin.math.roundToInt

/**
 * Reads the mode ffmpeg reports for the opened capture input out of its stderr banner, e.g.
 *
 * ```
 * Stream #0:0: Video: mjpeg (Baseline), yuvj422p(pc, bt470bg/unknown), 1280x720, 30 fps, 30 tbr, 10000k tbn
 * ```
 *
 * This is the *negotiated* mode, which is not always the requested one — a camera may open at
 * a resolution or rate it prefers. It matters because recordings are bare MJPEG elementary
 * streams with no timestamps of their own: the processing-time remux takes its frame rate from
 * here, so recording a requested 30 when the camera delivered 25 would speed the footage up.
 *
 * Returns null when nothing matches. A build whose banner differs is not worth failing capture
 * over; the caller falls back to what it asked for.
 */
internal fun parseNegotiatedFormat(lines: List<String>): VideoCaptureFormat? =
    lines.asSequence()
        .filter { it.contains("Video:") }
        .mapNotNull { line ->
            val size = STREAM_SIZE.find(line) ?: return@mapNotNull null
            val fps = STREAM_FPS.find(line)?.groupValues?.get(1)?.toDoubleOrNull()?.roundToInt()
            if (fps == null || fps <= 0) return@mapNotNull null
            VideoCaptureFormat(
                width = size.groupValues[1].toInt(),
                height = size.groupValues[2].toInt(),
                fps = fps,
            )
        }
        .firstOrNull()

/**
 * `, 1280x720,` — anchored on the surrounding commas so it cannot match a bitrate, a timestamp
 * or the display-aspect ratio in `[SAR 1:1 DAR 16:9]`.
 */
private val STREAM_SIZE = Regex(""",\s(\d{2,5})x(\d{2,5})[,\s]""")

/** `30 fps` / `29.97 fps`. Deliberately not `tbr`, which is a timebase rather than a rate. */
private val STREAM_FPS = Regex("""([\d.]+)\sfps""")
