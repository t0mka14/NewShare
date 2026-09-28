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
)
