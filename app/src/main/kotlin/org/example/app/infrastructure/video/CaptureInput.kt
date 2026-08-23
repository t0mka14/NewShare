package org.example.app.infrastructure.video

import org.example.app.domain.video.VideoCaptureFormat
import org.example.app.domain.video.VideoInputDevice
import org.example.app.infrastructure.HostOs

/**
 * One rung of the capture negotiation ladder: what to ask the camera for.
 *
 * @param format the mode to request, or `null` to constrain nothing and let the driver pick
 *   its own default — the rung that lets an unknown camera open at all.
 * @param passthrough request the camera's own MJPEG mode so the stream can be copied rather
 *   than re-encoded.
 */
internal data class CaptureAttempt(
    val format: VideoCaptureFormat?,
    val passthrough: Boolean,
) {
    val describe: String
        get() = "${format?.let { "${it.width}x${it.height}@${it.fps}" } ?: "device default"}" +
            if (passthrough) " (mjpeg passthrough)" else " (encoded)"
}

/**
 * The ffmpeg *input* arguments that select a capture source.
 *
 * This is the only OS-specific part of capture: Windows goes through DirectShow and macOS
 * through AVFoundation, while everything downstream of the input — the MJPEG elementary-stream
 * output, the pipe, the reader thread, the frame splitter and the file sink — is identical.
 * Keeping the split here lets tests drive the real pipeline from a synthetic source.
 */
internal interface CaptureInput {
    val isSupported: Boolean

    fun args(device: VideoInputDevice, attempt: CaptureAttempt): List<String>

    /**
     * ffmpeg arguments that make the platform print the camera's supported modes, or null
     * where that cannot be asked. Both platforms report modes only as a side effect of a
     * command that then fails, so the caller reads stderr and ignores the exit code.
     */
    fun probeArgs(device: VideoInputDevice): List<String>? = null

    /** Parses the output of [probeArgs]; empty when nothing could be read. */
    fun parseModes(output: String): List<CameraMode> = emptyList()
}

internal class PlatformCaptureInput(private val hostOs: HostOs) : CaptureInput {

    override val isSupported: Boolean get() = hostOs == HostOs.WINDOWS || hostOs == HostOs.MAC

    override fun args(device: VideoInputDevice, attempt: CaptureAttempt): List<String> {
        val args = mutableListOf<String>()
        when (hostOs) {
            HostOs.WINDOWS -> {
                args += listOf("-f", "dshow")
                args += common(attempt)
                // Give the driver room so a scheduling hiccup on our side drops nothing.
                args += listOf("-rtbufsize", DSHOW_RT_BUFFER)
                // Counts among devices sharing this FriendlyName, which is what dshow means —
                // not the position in the overall device list.
                if (device.nameOrdinal > 0) {
                    args += listOf("-video_device_number", device.nameOrdinal.toString())
                }
                args += listOf("-i", "video=${device.name}")
            }
            HostOs.MAC -> {
                args += listOf("-f", "avfoundation")
                args += common(attempt)
                // AVFoundation addresses "<video>:<audio>"; `none` keeps this video-only.
                args += listOf("-i", "${device.platformIndex}:none")
            }
            HostOs.LINUX, HostOs.OTHER -> error("capture is unsupported on $hostOs")
        }
        return args
    }

    /**
     * A requested `-video_size`/`-framerate` the camera cannot honour is a hard open failure,
     * not a silent downgrade, which is exactly why the last rung of the ladder omits both.
     */
    private fun common(attempt: CaptureAttempt): List<String> = buildList {
        attempt.format?.let { format ->
            add("-video_size")
            add("${format.width}x${format.height}")
            add("-framerate")
            add(format.fps.toString())
        }
        if (attempt.passthrough) {
            add("-vcodec")
            add("mjpeg")
        }
    }

    override fun probeArgs(device: VideoInputDevice): List<String>? = when (hostOs) {
        // dshow lists every pin's modes, then exits with "Immediate exit requested".
        HostOs.WINDOWS -> buildList {
            addAll(listOf("-hide_banner", "-list_options", "true", "-f", "dshow"))
            if (device.nameOrdinal > 0) addAll(listOf("-video_device_number", device.nameOrdinal.toString()))
            addAll(listOf("-i", "video=${device.name}"))
        }
        // AVFoundation has no listing switch: it prints "Supported modes" only when refusing
        // one, so ask for a size no camera has and read the complaint.
        HostOs.MAC -> listOf(
            "-hide_banner", "-f", "avfoundation", "-video_size", "1x1", "-i", "${device.platformIndex}:none",
        )
        HostOs.LINUX, HostOs.OTHER -> null
    }

    override fun parseModes(output: String): List<CameraMode> = when (hostOs) {
        HostOs.WINDOWS -> parseDshowModes(output)
        HostOs.MAC -> parseAvFoundationModes(output)
        HostOs.LINUX, HostOs.OTHER -> emptyList()
    }

    private companion object {
        const val DSHOW_RT_BUFFER = "128M"
    }
}

/**
 * `vcodec=mjpeg  min s=1920x1080 fps=5 max s=1920x1080 fps=30`, and the older spelling that
 * puts the format in trailing parentheses. The `max` figures are the mode's real ceiling.
 */
private val DSHOW_MODE = Regex(
    """(?:(vcodec|pixel_format)=(\S+)\s+)?min\s+s=\d+x\d+\s+fps=[\d.]+\s+""" +
        """max\s+s=(\d+)x(\d+)\s+fps=([\d.]+)(?:.*\((vcodec|pixel_format)=(\S+?)\))?""",
)

internal fun parseDshowModes(output: String): List<CameraMode> =
    output.lineSequence().mapNotNull { line ->
        val match = DSHOW_MODE.find(line) ?: return@mapNotNull null
        val fps = match.groupValues[5].toDoubleOrNull()?.let { Math.round(it).toInt() } ?: return@mapNotNull null
        val codec = match.groupValues[2].ifEmpty { match.groupValues[7] }
        CameraMode(
            width = match.groupValues[3].toInt(),
            height = match.groupValues[4].toInt(),
            maxFps = fps.coerceAtLeast(1),
            mjpeg = codec.contains("mjpeg", ignoreCase = true),
        )
    }.toList()

/** `  1280x720@[1.000000 30.000000]fps` under a "Supported modes:" header. */
private val AVF_MODE = Regex("""(\d{2,5})x(\d{2,5})@\[[\d.]+\s+([\d.]+)]fps""")

internal fun parseAvFoundationModes(output: String): List<CameraMode> =
    output.lineSequence().mapNotNull { line ->
        val match = AVF_MODE.find(line) ?: return@mapNotNull null
        val fps = match.groupValues[3].toDoubleOrNull()?.let { Math.round(it).toInt() } ?: return@mapNotNull null
        CameraMode(
            width = match.groupValues[1].toInt(),
            height = match.groupValues[2].toInt(),
            maxFps = fps.coerceAtLeast(1),
            // AVFoundation does not report the codec here; passthrough is still tried first.
            mjpeg = false,
        )
    }.toList()
