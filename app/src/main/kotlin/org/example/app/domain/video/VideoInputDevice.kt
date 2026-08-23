package org.example.app.domain.video

/**
 * Enumerates selectable camera devices. Mirrors
 * [org.example.app.domain.audio.AudioInputDeviceProvider] so Settings can present
 * cameras the same way it presents microphones.
 */
interface VideoInputDeviceProvider {
    fun availableDevices(): List<VideoInputDevice>
}

data class VideoInputDevice(
    /** Stable identifier for persistence in settings. */
    val id: String,
    /** Display name, and the value passed to the capture backend (dshow FriendlyName). */
    val name: String,
    /**
     * Ordinal among devices sharing this [name], 0-based. Two identical cameras report
     * identical dshow FriendlyNames and are told apart only by this — it is what
     * `-video_device_number` counts, so it is deliberately *not* the position in the
     * overall device list. On macOS, AVFoundation addresses devices by their list index
     * instead, which [platformIndex] carries.
     */
    val nameOrdinal: Int,

    /** Position in the platform's own device listing; how AVFoundation addresses a device. */
    val platformIndex: Int,
    /** False when the device cannot be opened for capture. */
    val eligible: Boolean,
)
