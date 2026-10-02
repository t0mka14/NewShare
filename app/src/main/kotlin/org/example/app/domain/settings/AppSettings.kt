package org.example.app.domain.settings

import kotlinx.serialization.Serializable

/**
 * `config/settings.json` (§5.4, §8.2) — local-only settings, distinct from the remote
 * `RemoteConfig` (which is never mixed with these). `micDeviceId` matches
 * [org.example.app.domain.audio.AudioInputDevice.id].
 */
@Serializable
data class AppSettings(
    val version: Int = 1,
    val micDeviceId: String? = null,
    /** Selected camera for VIDEO tasks; null falls back to the first eligible camera. */
    val cameraDeviceId: String? = null,
    /**
     * Identifies this computer: a UUID generated on first use by [InstallationIdProvider], never
     * typed. Sent with uploads to trace a session back to its device and available as the
     * `${installationId}` filename variable — it is *not* a credential.
     */
    val installationId: String? = null,
    /**
     * The site's `access_token` from the web admin (config alignment row 1): the bearer
     * credential for `GET /site-config/{token}`, shared by every computer of the site. Entered
     * in Settings; never logged (§11).
     */
    val siteToken: String? = null,
    val language: String? = null,
    /**
     * Microphone level 0..100 set on the Settings slider (§13 decision 44). Overrides the
     * config's `defaultMicGain` for whichever device the session opens; null follows the config.
     */
    val micGain: Int? = null,
    /**
     * Calibration target band `[min, max]`, linear RMS normalized to full scale (0.0–1.0). A
     * technician override edited in `settings.json` only — no UI; null (or malformed) uses
     * [DEFAULT_OPTIMAL_LOUDNESS]. See [loudnessRange].
     */
    val optimalLoudness: List<Double>? = null,
    /** Server base URL entered in Settings (config fetch + upload); null uses [DEFAULT_SERVER_URL]. */
    val serverUrl: String? = null,
)

/**
 * Base URL both the config fetch and the upload default to. The web backend's reverse proxy
 * mounts the backend under `/api` (config is `GET /api/site-config/{token}`, config alignment
 * row 1); uploads (`POST /api/upload`, §8.9) go to the same host — today the demo mock server in
 * `tools/mock-server`. HTTPS (§6.1 pt 7) is still open.
 */
const val DEFAULT_SERVER_URL: String = "http://192.168.122.183:10001/api"

/** [AppSettings.serverUrl] when set, else [DEFAULT_SERVER_URL]. */
fun AppSettings?.serverUrl(): String = this?.serverUrl ?: DEFAULT_SERVER_URL

val DEFAULT_OPTIMAL_LOUDNESS: ClosedFloatingPointRange<Double> = 0.2..0.5

/** [AppSettings.optimalLoudness] when well-formed (two values, 0 ≤ min < max ≤ 1), else the default. */
fun AppSettings?.loudnessRange(): ClosedFloatingPointRange<Double> {
    val band = this?.optimalLoudness ?: return DEFAULT_OPTIMAL_LOUDNESS
    if (band.size != 2) return DEFAULT_OPTIMAL_LOUDNESS
    val (min, max) = band
    return if (min >= 0.0 && min < max && max <= 1.0) min..max else DEFAULT_OPTIMAL_LOUDNESS
}
