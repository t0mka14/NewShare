package org.example.app.fakes

import org.example.app.domain.settings.AppSettings
import org.example.app.domain.settings.AppSettingsRepository

class FakeAppSettingsRepository : AppSettingsRepository {
    private var stored: AppSettings? = null

    override fun read(): AppSettings? = stored

    override fun write(settings: AppSettings) {
        stored = settings
    }
}
