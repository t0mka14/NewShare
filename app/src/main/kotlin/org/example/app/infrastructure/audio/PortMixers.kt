package org.example.app.infrastructure.audio

/**
 * Pairs a capture mixer with the Java Sound *port* mixer of the same physical device (§13
 * decision 44). Port mixers are named `Port <x>` on every platform, but `<x>` relates to the
 * capture mixer's name differently:
 *
 * - Windows and macOS: the same endpoint/product name — `Microphone (USB audio CODEC)` ↔
 *   `Port Microphone (USB audio CODEC)` (both cut to 31 characters on Windows, identically);
 * - Linux/ALSA: the capture mixer is `<card id> [plughw:<card>,<device>]` while the port mixer
 *   is `Port <card id> [hw:<card>]` — same card id and number, different suffix.
 */
object PortMixers {
    const val PREFIX = "Port "

    private val alsaCapture = Regex("""^(.*) \[plughw:(\d+),\d+]$""")
    private val alsaPort = Regex("""^(.*) \[hw:(\d+)]$""")

    fun isPortMixer(mixerName: String): Boolean = mixerName.startsWith(PREFIX)

    /** True when the mixer named [portMixerName] controls the device whose capture mixer is [deviceName]. */
    fun belongsTo(portMixerName: String, deviceName: String): Boolean {
        if (!isPortMixer(portMixerName)) return false
        val stripped = portMixerName.removePrefix(PREFIX).trim()
        val device = deviceName.trim()
        if (stripped.equals(device, ignoreCase = true)) return true

        val capture = alsaCapture.matchEntire(device) ?: return false
        val port = alsaPort.matchEntire(stripped) ?: return false
        return capture.groupValues[1].equals(port.groupValues[1], ignoreCase = true) &&
            capture.groupValues[2] == port.groupValues[2]
    }
}
