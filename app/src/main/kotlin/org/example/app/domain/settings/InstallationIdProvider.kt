package org.example.app.domain.settings

import java.util.UUID

/**
 * The single place [AppSettings.installationId] is read for use: returns the saved ID, or
 * generates a UUID, persists it (merged onto the other settings) and returns that. Generated
 * lazily on first use, so no startup hook is needed; an ID saved by an older version (when it was
 * typed in Settings) is kept as is.
 */
class InstallationIdProvider(
    private val settingsRepository: AppSettingsRepository,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    fun get(): String {
        val saved = settingsRepository.read() ?: AppSettings()
        saved.installationId?.takeIf { it.isNotBlank() }?.let { return it }
        val id = newId()
        settingsRepository.write(saved.copy(installationId = id))
        return id
    }
}
