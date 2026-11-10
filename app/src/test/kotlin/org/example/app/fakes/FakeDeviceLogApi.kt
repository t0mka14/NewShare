package org.example.app.fakes

import org.example.app.domain.logs.DeviceLogApi
import org.example.app.domain.logs.DeviceLogResult
import org.example.app.domain.logs.DeviceLogTrigger
import java.nio.file.Path
import java.time.ZoneOffset

/**
 * Records every send and answers [nextResult] (default `Sent`), or the result scripted for a
 * file name with [respondTo]. [onSend] runs after the call is recorded, so a test can change
 * the world mid-run (start a session). No network.
 */
class FakeDeviceLogApi : DeviceLogApi {
    var nextResult: DeviceLogResult = DeviceLogResult.Sent
    val calls = mutableListOf<Call>()
    var onSend: (Call) -> Unit = {}
    private val scripted = mutableMapOf<String, DeviceLogResult>()

    fun respondTo(fileName: String, result: DeviceLogResult) {
        scripted[fileName] = result
    }

    val sentFileNames: List<String> get() = calls.map { it.fileName }

    override suspend fun send(
        file: Path,
        fileName: String,
        sha256: String,
        siteToken: String,
        installationId: String,
        appVersion: String,
        utcOffset: ZoneOffset,
    ): DeviceLogResult {
        val call = Call(file, fileName, sha256, siteToken, installationId, appVersion, utcOffset)
        calls += call
        onSend(call)
        return scripted[fileName] ?: nextResult
    }

    data class Call(
        val file: Path,
        val fileName: String,
        val sha256: String,
        val siteToken: String,
        val installationId: String,
        val appVersion: String,
        val utcOffset: ZoneOffset,
    )
}

/** Counts [trigger] calls; starts nothing. */
class FakeDeviceLogTrigger : DeviceLogTrigger {
    var count = 0
        private set

    override fun trigger() {
        count++
    }
}
