package org.example.app.fakes

import org.example.app.domain.video.VideoInputDevice
import org.example.app.domain.video.VideoInputDeviceProvider

/** Deterministic camera list, so no test ever shells out to enumerate real devices. */
class FakeVideoInputDeviceProvider(
    private val devices: List<VideoInputDevice> = listOf(DEFAULT_CAMERA),
) : VideoInputDeviceProvider {

    /**
     * How many times the list was asked for. Real enumeration spawns a process and waits for it,
     * so "was this called at all, and how often" is a property worth asserting.
     */
    var enumerationCount: Int = 0
        private set

    override fun availableDevices(): List<VideoInputDevice> {
        enumerationCount++
        return devices
    }

    companion object {
        val DEFAULT_CAMERA = VideoInputDevice(
            id = "fake-camera-1",
            name = "Fake PTZ Camera",
            nameOrdinal = 0,
            platformIndex = 0,
            eligible = true,
        )
    }
}
