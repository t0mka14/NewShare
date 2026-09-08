package org.example.app.domain.settings

import kotlinx.serialization.Serializable

/**
 * `config/settings.json` (§5.4, §8.2) — local-only settings, distinct from the remote
 * `RemoteConfig` (which is never mixed with these). `micDeviceId` matches
 * [org.example.app.domain.audio.AudioInputDevice.id]; `installationId` is entered once at
 * deployment and used as the bearer credential for `ConfigApi`/`UploadApi` (§6.1) — never
 * logged (§11).
 */
@Serializable
data class AppSettings(
    val version: Int = 1,
    val micDeviceId: String? = null,
    /** Selected camera for VIDEO tasks; null falls back to the first eligible camera. */
    val cameraDeviceId: String? = null,
    val installationId: String? = null,
    val language: String? = null,
    /**
     * Microphone level 0..100 set on the Settings slider (§13 decision 44). Overrides the
     * config's `defaultMicGain` for whichever device the session opens; null follows the config.
     */
    val micGain: Int? = null,
)
