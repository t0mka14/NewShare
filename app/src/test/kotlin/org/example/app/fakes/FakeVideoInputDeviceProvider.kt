package org.example.app.fakes

import org.example.app.domain.video.VideoInputDevice
import org.example.app.domain.video.VideoInputDeviceProvider

/** Deterministic camera list, so no test ever shells out to enumerate real devices. */
class FakeVideoInputDeviceProvider(
    private val devices: List<VideoInputDevice> = listOf(DEFAULT_CAMERA),
) : VideoInputDeviceProvider {

    override fun availableDevices(): List<VideoInputDevice> = devices

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
