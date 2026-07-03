package org.example.app.domain.settings

/** Persists `config/settings.json` (§5.4) via atomic tmp+rename. Not a `Preferences` API
 * (§12) — a plain, explicit, testable repository. */
interface AppSettingsRepository {
    /** `null` if no settings have ever been saved (first run). */
    fun read(): AppSettings?
    fun write(settings: AppSettings)
}
