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
    val installationId: String? = null,
    val language: String? = null,
)
