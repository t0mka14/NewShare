package org.example.app.domain.audio

/**
 * Enumerates selectable audio input devices (§5.3). Devices with no supported
 * PCM format are reported ineligible (§5.3.1) so Settings can grey them out.
 */
interface AudioInputDeviceProvider {
    fun availableDevices(): List<AudioInputDevice>
}

data class AudioInputDevice(
    /** Stable identifier for persistence in settings (derived from mixer info). */
    val id: String,
    /** Display name shown in Settings. */
    val name: String,
    /** False when the device offers no PCM format the recorder can use. */
    val eligible: Boolean,
)
