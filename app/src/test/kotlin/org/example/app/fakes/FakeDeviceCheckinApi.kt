package org.example.app.fakes

import org.example.app.domain.upload.DeviceCheckin
import org.example.app.domain.upload.DeviceCheckinApi
import org.example.app.domain.upload.DeviceCheckinResult

/** Records every [DeviceCheckin] and answers [nextResult] (default `Recorded`); no network. */
class FakeDeviceCheckinApi : DeviceCheckinApi {
    var nextResult: DeviceCheckinResult = DeviceCheckinResult.Recorded
    val calls = mutableListOf<DeviceCheckin>()

    override suspend fun checkIn(checkin: DeviceCheckin): DeviceCheckinResult {
        calls += checkin
        return nextResult
    }
}
