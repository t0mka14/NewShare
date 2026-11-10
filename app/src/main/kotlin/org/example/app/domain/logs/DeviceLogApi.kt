package org.example.app.domain.logs

import java.nio.file.Path
import java.time.ZoneOffset

/**
 * Outcome of one [DeviceLogApi.send] (web `docs/device_logs/01_spec.md` §2.3). The caller
 * only needs to know what to remember about the file, so the HTTP status is not carried.
 */
sealed interface DeviceLogResult {
    /** HTTP 200, including a `duplicate` resend: never send this file again. */
    data object Sent : DeviceLogResult

    /** HTTP 400 or 413: the server will never take this file; never retry it. */
    data object Unsendable : DeviceLogResult

    /** An HTTP answer that concerns the whole device (404, 409, 429, 5xx, any other non-2xx
     * except 400 and 413): nothing is recorded; the run ends and the next trigger retries. */
    data object RetryLater : DeviceLogResult

    /** No answer (transport exception) or the file could not be read: nothing is recorded, the
     * run goes on with the next file and this one is retried at the next trigger. */
    data object FileFailed : DeviceLogResult
}

/**
 * Sends one rotated log file (`POST /device-logs`). Implementations never throw (except for
 * coroutine cancellation). [siteToken] and [installationId] are credentials: never logged (§11).
 * [utcOffset] is the computer's offset at send time; the log timestamps carry none.
 */
interface DeviceLogApi {
    suspend fun send(
        file: Path,
        fileName: String,
        sha256: String,
        siteToken: String,
        installationId: String,
        appVersion: String,
        utcOffset: ZoneOffset,
    ): DeviceLogResult
}

/** Starts a log-sending run without waiting for it (fire-and-forget); never throws. */
fun interface DeviceLogTrigger {
    fun trigger()
}
