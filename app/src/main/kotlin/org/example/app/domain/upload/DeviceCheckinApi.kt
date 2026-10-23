package org.example.app.domain.upload

/**
 * One `POST /device-checkin` report (§13 decision 62): how many sessions wait on this computer,
 * so the web admin's "Devices" page can show it. [siteToken] and [installationId] are
 * credentials and are never logged (§11).
 */
data class DeviceCheckin(
    val siteToken: String,
    val installationId: String,
    val appVersion: String,
    /** Processed sessions waiting to upload (`NotUploaded` in [EligibleUploadsQuery]). */
    val pending: Int,
    /** Sessions whose last upload attempt failed (`Failed` in [EligibleUploadsQuery]). */
    val failed: Int,
)

sealed interface DeviceCheckinResult {
    data object Recorded : DeviceCheckinResult

    /** HTTP 404: unknown or deactivated site token. */
    data object InvalidToken : DeviceCheckinResult

    /** HTTP 409: this computer belongs to another site than the token's; nothing was recorded. */
    data object RegisteredToAnotherSite : DeviceCheckinResult

    /** Transport failure or any other response (400, 429, 5xx). */
    data object Failed : DeviceCheckinResult
}

/** Sends [DeviceCheckin] reports to the web backend. Implementations never throw on transport
 * or HTTP failure; they return [DeviceCheckinResult.Failed]. */
interface DeviceCheckinApi {
    suspend fun checkIn(checkin: DeviceCheckin): DeviceCheckinResult
}
