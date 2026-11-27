package org.example.app.fakes

import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.audio.AudioInputDeviceProvider

/**
 * Configurable device list for device-selection UI and device-loss scenarios
 * (§10.3 workflow 6: interrupt on one device, select another fake device to
 * resume).
 */
class FakeAudioInputDeviceProvider(
    initialDevices: List<AudioInputDevice> = listOf(DEFAULT_DEVICE),
) : AudioInputDeviceProvider {
    private var devices: List<AudioInputDevice> = initialDevices

    /** Test-only counters for hot-plug polling tests. */
    var availableDevicesCalls = 0
        private set
    var fingerprintCalls = 0
        private set

    /** Test-only: when set, [deviceFingerprint] / [availableDevices] throw it. */
    var failure: RuntimeException? = null

    override fun availableDevices(): List<AudioInputDevice> {
        availableDevicesCalls++
        failure?.let { throw it }
        return devices
    }

    override fun deviceFingerprint(): Any {
        fingerprintCalls++
        failure?.let { throw it }
        return devices.map { it.id }
    }

    /** Test-only: replace the enumerated device list, e.g. after a simulated hot-plug. */
    fun setDevices(newDevices: List<AudioInputDevice>) {
        devices = newDevices
    }

    companion object {
        val DEFAULT_DEVICE = AudioInputDevice(id = "default", name = "Default Microphone", eligible = true)
        val SECONDARY_DEVICE = AudioInputDevice(id = "secondary", name = "USB Headset Mic", eligible = true)
        val INELIGIBLE_DEVICE = AudioInputDevice(id = "no-pcm", name = "Unsupported Device", eligible = false)
    }
}
