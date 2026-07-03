package org.example.app.infrastructure.persistence

import kotlinx.serialization.json.Json
import org.example.app.domain.AppDirectories
import org.example.app.domain.settings.AppSettings
import org.example.app.domain.settings.AppSettingsRepository
import java.nio.file.Path

/** Production [AppSettingsRepository] at `config/settings.json` (§8.2). */
class JsonAppSettingsRepository(
    private val directories: AppDirectories,
) : AppSettingsRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val path: Path get() = directories.configDir.resolve("settings.json")

    override fun read(): AppSettings? =
        AtomicFileWriter.readStringOrNull(path)?.let { json.decodeFromString(AppSettings.serializer(), it) }

    override fun write(settings: AppSettings) {
        AtomicFileWriter.writeString(path, json.encodeToString(AppSettings.serializer(), settings))
    }
}
