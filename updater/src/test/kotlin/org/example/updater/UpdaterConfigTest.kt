package org.example.updater

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class UpdaterConfigTest {

    @Test
    fun `endpoint CLI arg wins over the properties file`(@TempDir tempDir: Path) {
        Files.writeString(tempDir.resolve("updater.properties"), "endpoint=http://from-file/api/version/latest\n")

        val endpoint = UpdaterConfig.resolveEndpoint(tempDir, arrayOf("--endpoint=http://from-cli/api/version/latest"))

        assertEquals("http://from-cli/api/version/latest", endpoint)
    }

    @Test
    fun `endpoint falls back to updater-properties beside the install dir`(@TempDir tempDir: Path) {
        Files.writeString(tempDir.resolve("updater.properties"), "endpoint=http://from-file/api/version/latest\n")

        val endpoint = UpdaterConfig.resolveEndpoint(tempDir, emptyArray())

        assertEquals("http://from-file/api/version/latest", endpoint)
    }

    @Test
    fun `endpoint is null when neither source is present`(@TempDir tempDir: Path) {
        val endpoint = UpdaterConfig.resolveEndpoint(tempDir, emptyArray())

        assertNull(endpoint)
    }

    @Test
    fun `install dir defaults to the working directory`() {
        val resolved = UpdaterConfig.resolveInstallDir(emptyArray())

        assertEquals(Path.of("").toAbsolutePath(), resolved)
    }

    @Test
    fun `install dir arg overrides the default`(@TempDir tempDir: Path) {
        val resolved = UpdaterConfig.resolveInstallDir(arrayOf("--install-dir=${tempDir}"))

        assertEquals(tempDir, resolved)
    }
}
