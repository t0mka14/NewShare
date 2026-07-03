package org.example.app.infrastructure.persistence

import org.example.app.domain.settings.AppSettings
import org.example.app.fakes.TestAppDirectories
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class JsonAppSettingsRepositoryTest {

    @Test
    fun `read returns null before anything is saved`(@TempDir tempDir: Path) {
        val repository = JsonAppSettingsRepository(TestAppDirectories(tempDir))
        assertNull(repository.read())
    }

    @Test
    fun `write then read round-trips`(@TempDir tempDir: Path) {
        val dirs = TestAppDirectories(tempDir)
        val repository = JsonAppSettingsRepository(dirs)

        val settings = AppSettings(micDeviceId = "mic-1", installationId = "install-abc", language = "en")
        repository.write(settings)

        assertEquals(settings, repository.read())
        assertFalse(Files.exists(dirs.configDir.resolve("settings.json.tmp")))
    }

    @Test
    fun `second write overwrites the first`(@TempDir tempDir: Path) {
        val repository = JsonAppSettingsRepository(TestAppDirectories(tempDir))
        repository.write(AppSettings(language = "en"))
        repository.write(AppSettings(language = "cs"))
        assertEquals("cs", repository.read()?.language)
    }
}
