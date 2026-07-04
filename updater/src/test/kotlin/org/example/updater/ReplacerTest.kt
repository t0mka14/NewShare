package org.example.updater

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.readText

/** §10.2 "unzip + replace + rollback round-trips in fake install layouts". */
class ReplacerTest {

    private fun installLayout(root: Path): InstallLayout {
        val layout = InstallLayout(root)
        Files.createDirectories(layout.appDir)
        Files.createDirectories(layout.runtimeDir)
        return layout
    }

    private fun writeZip(zipFile: Path, entries: Map<String, String>) {
        ZipOutputStream(Files.newOutputStream(zipFile)).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }

    @Test
    fun `backup renames app to app-backup`(@TempDir tempDir: Path) {
        val layout = installLayout(tempDir)
        Files.writeString(layout.appDir.resolve("app.jar"), "old-jar-bytes")
        val replacer = Replacer(UpdaterLog(layout.logFile))

        replacer.backup(layout)

        assertFalse(Files.exists(layout.appDir))
        assertTrue(Files.exists(layout.backupDir))
        assertEquals("old-jar-bytes", layout.backupDir.resolve("app.jar").readText())
    }

    @Test
    fun `backup deletes a pre-existing stale backup first`(@TempDir tempDir: Path) {
        val layout = installLayout(tempDir)
        Files.createDirectories(layout.backupDir)
        Files.writeString(layout.backupDir.resolve("stale.txt"), "stale")
        Files.writeString(layout.appDir.resolve("app.jar"), "current-bytes")
        val replacer = Replacer(UpdaterLog(layout.logFile))

        replacer.backup(layout)

        assertFalse(Files.exists(layout.backupDir.resolve("stale.txt")))
        assertEquals("current-bytes", layout.backupDir.resolve("app.jar").readText())
    }

    @Test
    fun `replace unzips the update package into app`(@TempDir tempDir: Path) {
        val layout = installLayout(tempDir)
        Files.writeString(layout.appDir.resolve("app.jar"), "old-bytes")
        val replacer = Replacer(UpdaterLog(layout.logFile))
        replacer.backup(layout)

        val updateZip = tempDir.resolve("update.zip")
        writeZip(
            updateZip, mapOf(
                "app.jar" to "new-bytes",
                "version.json" to """{"version":1,"appVersion":"2.0.0"}""",
                "lib/helper.jar" to "helper-bytes",
            )
        )

        val result = replacer.replace(layout, updateZip)

        assertTrue(result)
        assertEquals("new-bytes", layout.appDir.resolve("app.jar").readText())
        assertEquals("helper-bytes", layout.appDir.resolve("lib/helper.jar").readText())
        assertTrue(Files.exists(layout.backupDir), "backup must be kept until a confirmed launch")
    }

    @Test
    fun `replace rolls back automatically on a corrupt package`(@TempDir tempDir: Path) {
        val layout = installLayout(tempDir)
        Files.writeString(layout.appDir.resolve("app.jar"), "old-bytes")
        val replacer = Replacer(UpdaterLog(layout.logFile))
        replacer.backup(layout)

        // A valid zip truncated mid-entry: high-entropy content so DEFLATE barely compresses it,
        // meaning cutting the tail lands inside the compressed data stream and forces the
        // inflater to throw EOFException. (A plain garbage byte string, or truncating only the
        // trailing central directory of a small/compressible entry, is lenient-parsed by
        // ZipInputStream as "zero entries" — that doesn't exercise the rollback path at all.)
        val highEntropyContent = ByteArray(20_000).also { java.util.Random(42).nextBytes(it) }
        val validZipBytes = run {
            val buffer = java.io.ByteArrayOutputStream()
            ZipOutputStream(buffer).use { zip ->
                zip.putNextEntry(ZipEntry("app.jar"))
                zip.write(highEntropyContent)
                zip.closeEntry()
            }
            buffer.toByteArray()
        }
        val corruptZip = tempDir.resolve("corrupt.zip")
        Files.write(corruptZip, validZipBytes.copyOf(validZipBytes.size / 2))

        val result = replacer.replace(layout, corruptZip)

        assertFalse(result)
        assertTrue(Files.exists(layout.appDir), "rollback must restore app/")
        assertFalse(Files.exists(layout.backupDir), "rollback consumes the backup")
        assertEquals("old-bytes", layout.appDir.resolve("app.jar").readText())
    }

    @Test
    fun `restoreBackup swaps app-backup back over app`(@TempDir tempDir: Path) {
        val layout = installLayout(tempDir)
        Files.writeString(layout.appDir.resolve("app.jar"), "broken-new-bytes")
        Files.createDirectories(layout.backupDir)
        Files.writeString(layout.backupDir.resolve("app.jar"), "known-good-bytes")
        val replacer = Replacer(UpdaterLog(layout.logFile))

        replacer.restoreBackup(layout)

        assertEquals("known-good-bytes", layout.appDir.resolve("app.jar").readText())
        assertFalse(Files.exists(layout.backupDir))
    }

    @Test
    fun `rejects zip entries that escape the target directory`(@TempDir tempDir: Path) {
        val layout = installLayout(tempDir)
        Files.writeString(layout.appDir.resolve("app.jar"), "old-bytes")
        val replacer = Replacer(UpdaterLog(layout.logFile))
        replacer.backup(layout)

        val maliciousZip = tempDir.resolve("evil.zip")
        writeZip(maliciousZip, mapOf("../escaped.txt" to "pwned"))

        val result = replacer.replace(layout, maliciousZip)

        assertFalse(result)
        assertFalse(Files.exists(tempDir.resolve("escaped.txt")), "must never write outside app/")
        assertTrue(Files.exists(layout.appDir), "must roll back to the previous app")
    }

    @Test
    fun `deleteBackup removes app-backup`(@TempDir tempDir: Path) {
        val layout = installLayout(tempDir)
        Files.createDirectories(layout.backupDir)
        Files.writeString(layout.backupDir.resolve("app.jar"), "bytes")
        val replacer = Replacer(UpdaterLog(layout.logFile))

        replacer.deleteBackup(layout)

        assertFalse(Files.exists(layout.backupDir))
    }
}
