package org.example.app.fakes

import org.example.app.domain.upload.DeviceCheckin
import org.example.app.domain.upload.DeviceCheckinApi
import org.example.app.domain.upload.DeviceCheckinResult

/** Records every [DeviceCheckin] and answers [nextResult] (default `Recorded`); no network. */
class FakeDeviceCheckinApi : DeviceCheckinApi {
    var nextResult: DeviceCheckinResult = DeviceCheckinResult.Recorded
    val calls = mutableListOf<DeviceCheckin>()

    /** When set, [checkIn] suspends until it completes (to test leaving a screen mid-check-in). */
    var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun checkIn(checkin: DeviceCheckin): DeviceCheckinResult {
        calls += checkin
        gate?.await()
        return nextResult
    }
}
