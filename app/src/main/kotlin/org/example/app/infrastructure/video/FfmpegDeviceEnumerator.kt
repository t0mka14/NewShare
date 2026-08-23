package org.example.app.infrastructure.video

import io.github.oshai.kotlinlogging.KotlinLogging
import org.example.app.domain.video.VideoInputDevice
import org.example.app.domain.video.VideoInputDeviceProvider
import org.example.app.infrastructure.HostOs
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Lists cameras by asking the bundled ffmpeg to enumerate the platform's capture devices.
 *
 * Device listing is one of the few genuinely OS-specific pieces: Windows goes through
 * DirectShow, macOS through AVFoundation, and the two print entirely different formats. Both
 * write the listing to *stderr* and then exit non-zero, which is expected — the "error" is
 * ffmpeg complaining that `dummy` is not a real input, after it has already printed what we
 * came for.
 *
 * Never throws: a missing binary or an unparseable listing yields an empty list, and the UI
 * reports it as no cameras found.
 */
class FfmpegDeviceEnumerator(
    private val locator: FfmpegBinaryLocator = FfmpegBinaryLocator(),
    private val hostOs: HostOs = HostOs.current,
) : VideoInputDeviceProvider {

    override fun availableDevices(): List<VideoInputDevice> {
        val binary = locator.locate() ?: run {
            logger.warn { "cannot enumerate cameras: no ffmpeg binary available" }
            return emptyList()
        }

        val command = when (hostOs) {
            HostOs.WINDOWS -> listOf(
                binary.toString(), "-hide_banner", "-list_devices", "true", "-f", "dshow", "-i", "dummy",
            )
            HostOs.MAC -> listOf(
                binary.toString(), "-hide_banner", "-list_devices", "true", "-f", "avfoundation", "-i", "",
            )
            HostOs.LINUX, HostOs.OTHER -> {
                logger.info { "camera enumeration is not supported on ${HostOs.current}" }
                return emptyList()
            }
        }

        val output = runCatching { capture(command) }.getOrElse { e ->
            logger.warn(e) { "camera enumeration failed" }
            return emptyList()
        }

        return when (hostOs) {
            HostOs.WINDOWS -> parseDshow(output)
            HostOs.MAC -> parseAvFoundation(output)
            HostOs.LINUX, HostOs.OTHER -> emptyList()
        }
    }

    private fun capture(command: List<String>): String {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        process.outputStream.close()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(ENUMERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            logger.warn { "camera enumeration timed out after ${ENUMERATION_TIMEOUT_SECONDS}s" }
        }
        return text
    }

    internal companion object {
        private const val ENUMERATION_TIMEOUT_SECONDS = 10L

        /** `[dshow @ 000001] "Logitech PTZ Pro 2" (video)` — the trailing kind tag matters,
         * since the same listing also carries audio devices and each device's alternative name. */
        private val DSHOW_VIDEO = Regex("""^\[dshow @ [^]]*]\s+"(.*)"\s+\(video\)""")

        /** `[AVFoundation indev @ 0x7f8] [0] FaceTime HD Camera`, under a video header and
         * before the audio header. */
        private val AVF_DEVICE = Regex("""^\[AVFoundation[^]]*]\s+\[(\d+)]\s+(.+)$""")
        private val AVF_VIDEO_HEADER = Regex("""AVFoundation video devices""")
        private val AVF_AUDIO_HEADER = Regex("""AVFoundation audio devices""")

        fun parseDshow(output: String): List<VideoInputDevice> {
            val names = output.lineSequence()
                .mapNotNull { DSHOW_VIDEO.find(it.trim())?.groupValues?.get(1) }
                .toList()
            val seen = mutableMapOf<String, Int>()
            return names.mapIndexed { platformIndex, name ->
                val nameOrdinal = seen.getOrDefault(name, 0)
                seen[name] = nameOrdinal + 1
                VideoInputDevice(
                    id = "dshow:$platformIndex:$name",
                    name = name,
                    nameOrdinal = nameOrdinal,
                    platformIndex = platformIndex,
                    eligible = true,
                )
            }
        }

        fun parseAvFoundation(output: String): List<VideoInputDevice> {
            val devices = mutableListOf<VideoInputDevice>()
            var inVideoSection = false
            for (raw in output.lineSequence()) {
                val line = raw.trim()
                when {
                    AVF_AUDIO_HEADER.containsMatchIn(line) -> inVideoSection = false
                    AVF_VIDEO_HEADER.containsMatchIn(line) -> inVideoSection = true
                    inVideoSection -> AVF_DEVICE.find(line)?.let { match ->
                        val index = match.groupValues[1].toInt()
                        val name = match.groupValues[2].trim()
                        devices += VideoInputDevice(
                            id = "avfoundation:$index:$name",
                            name = name,
                            nameOrdinal = devices.count { it.name == name },
                            platformIndex = index,
                            eligible = true,
                        )
                    }
                }
            }
            return devices
        }
    }
}
