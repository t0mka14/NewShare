package org.example.updater

import org.example.shared.model.VersionCheckResponse
import org.example.updater.fakes.FakeAppLauncher
import org.example.updater.fakes.FakeDownloader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.readText

/**
 * End-to-end §9 flow, driven entirely through fakes: no real network ([VersionFetcher],
 * [Downloader] are faked) and no real process spawned ([AppLauncher] is faked). Only the
 * filesystem side ([Replacer], backup/restore, marker file) is real, against a `@TempDir` fake
 * install layout.
 */
class UpdaterTest {

    private fun buildLayout(root: Path, localAppVersion: String? = "1.0.0"): InstallLayout {
        val layout = InstallLayout(root)
        Files.createDirectories(layout.appDir)
        Files.createDirectories(layout.runtimeDir)
        Files.writeString(layout.appDir.resolve("app.jar"), "old-jar-bytes")
        if (localAppVersion != null) {
            Files.writeString(
                layout.versionFile,
                """{"version":1,"appVersion":"$localAppVersion"}""",
            )
        }
        return layout
    }

    private fun zipPackage(vararg entries: Pair<String, String>): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return buffer.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun newUpdater(
        layout: InstallLayout,
        fetcher: VersionFetcher,
        downloader: Downloader = FakeDownloader(succeeds = false),
        launcher: FakeAppLauncher = FakeAppLauncher(),
    ): Pair<Updater, FakeAppLauncher> {
        val updater = Updater(
            layout = layout,
            fetcher = fetcher,
            downloader = downloader,
            launcher = launcher,
            replacer = Replacer(UpdaterLog(layout.logFile)),
            log = UpdaterLog(layout.logFile),
        )
        return updater to launcher
    }

    @Test
    fun `unreachable version check launches the existing app without changes`(@TempDir tempDir: Path) {
        val layout = buildLayout(tempDir)
        val (updater, launcher) = newUpdater(layout, fetcher = VersionFetcher { VersionCheckResult.Unreachable })

        updater.run()

        assertEquals(1, launcher.launchCount)
        assertEquals("old-jar-bytes", layout.appDir.resolve("app.jar").readText())
        assertFalse(Files.exists(layout.backupDir))
    }

    @Test
    fun `malformed remote version is treated as not newer`(@TempDir tempDir: Path) {
        val layout = buildLayout(tempDir)
        val response = VersionCheckResponse(version = "not-a-version", downloadUrl = "http://x/app.zip", checksum = "irrelevant")
        val (updater, launcher) = newUpdater(layout, fetcher = VersionFetcher { VersionCheckResult.Available(response) })

        updater.run()

        assertEquals(1, launcher.launchCount)
        assertEquals("old-jar-bytes", layout.appDir.resolve("app.jar").readText())
    }

    @Test
    fun `remote version not newer than local skips the update`(@TempDir tempDir: Path) {
        val layout = buildLayout(tempDir, localAppVersion = "2.0.0")
        val response = VersionCheckResponse(version = "2.0.0", downloadUrl = "http://x/app.zip", checksum = "irrelevant")
        val (updater, launcher) = newUpdater(layout, fetcher = VersionFetcher { VersionCheckResult.Available(response) })

        updater.run()

        assertEquals(1, launcher.launchCount)
        assertEquals("old-jar-bytes", layout.appDir.resolve("app.jar").readText())
    }

    @Test
    fun `newer version with matching checksum is downloaded and applied`(@TempDir tempDir: Path) {
        val layout = buildLayout(tempDir, localAppVersion = "1.0.0")
        val packageBytes = zipPackage(
            "app.jar" to "new-jar-bytes",
            "version.json" to """{"version":1,"appVersion":"2.0.0"}""",
        )
        val response = VersionCheckResponse(
            version = "2.0.0",
            downloadUrl = "http://example.test/app-2.0.0.zip",
            checksum = sha256(packageBytes),
        )
        val downloader = FakeDownloader(succeeds = true, contentIfSuccessful = packageBytes)
        val (updater, launcher) = newUpdater(
            layout,
            fetcher = VersionFetcher { VersionCheckResult.Available(response) },
            downloader = downloader,
        )

        updater.run()

        assertEquals(1, launcher.launchCount)
        assertEquals("new-jar-bytes", layout.appDir.resolve("app.jar").readText())
        assertTrue(Files.exists(layout.backupDir), "backup is kept until a confirmed launch")
        assertEquals("old-jar-bytes", layout.backupDir.resolve("app.jar").readText())
        assertTrue(Files.exists(layout.markerFile), "marker must be written after a successful replace")
        assertEquals("http://example.test/app-2.0.0.zip", downloader.lastUrl)
    }

    @Test
    fun `checksum mismatch discards the download and keeps the current app`(@TempDir tempDir: Path) {
        val layout = buildLayout(tempDir, localAppVersion = "1.0.0")
        val packageBytes = zipPackage("app.jar" to "new-jar-bytes")
        val response = VersionCheckResponse(
            version = "2.0.0",
            downloadUrl = "http://example.test/app-2.0.0.zip",
            checksum = "0".repeat(64),
        )
        val downloader = FakeDownloader(succeeds = true, contentIfSuccessful = packageBytes)
        val (updater, launcher) = newUpdater(
            layout,
            fetcher = VersionFetcher { VersionCheckResult.Available(response) },
            downloader = downloader,
        )

        updater.run()

        assertEquals(1, launcher.launchCount)
        assertEquals("old-jar-bytes", layout.appDir.resolve("app.jar").readText())
        assertFalse(Files.exists(layout.backupDir), "must not back up when the checksum never verified")
        assertFalse(Files.exists(layout.markerFile))
    }

    @Test
    fun `failed download keeps the current app`(@TempDir tempDir: Path) {
        val layout = buildLayout(tempDir, localAppVersion = "1.0.0")
        val response = VersionCheckResponse(version = "2.0.0", downloadUrl = "http://example.test/app.zip", checksum = "irrelevant")
        val (updater, launcher) = newUpdater(
            layout,
            fetcher = VersionFetcher { VersionCheckResult.Available(response) },
            downloader = FakeDownloader(succeeds = false),
        )

        updater.run()

        assertEquals(1, launcher.launchCount)
        assertEquals("old-jar-bytes", layout.appDir.resolve("app.jar").readText())
        assertFalse(Files.exists(layout.backupDir))
    }

    @Test
    fun `a leftover marker from a failed launch restores the backup before checking for updates`(@TempDir tempDir: Path) {
        val layout = buildLayout(tempDir, localAppVersion = "2.0.0") // the never-confirmed new version
        layout.appDir.resolve("app.jar").let { Files.writeString(it, "unconfirmed-new-bytes") }
        Files.createDirectories(layout.backupDir)
        Files.writeString(layout.backupDir.resolve("app.jar"), "known-good-old-bytes")
        Files.writeString(layout.markerFile, """{"version":1,"appliedVersion":"2.0.0","appliedAt":"2026-01-01T00:00:00Z"}""")

        val (updater, launcher) = newUpdater(layout, fetcher = VersionFetcher { VersionCheckResult.Unreachable })

        updater.run()

        assertEquals("known-good-old-bytes", layout.appDir.resolve("app.jar").readText(), "must roll back to the backup")
        assertFalse(Files.exists(layout.backupDir), "backup is consumed by the restore")
        assertFalse(Files.exists(layout.markerFile), "marker must be deleted once handled")
        assertEquals(1, launcher.launchCount)
    }

    @Test
    fun `a stale backup with no marker is deleted at startup`(@TempDir tempDir: Path) {
        val layout = buildLayout(tempDir)
        Files.createDirectories(layout.backupDir)
        Files.writeString(layout.backupDir.resolve("app.jar"), "stale-backup-bytes")

        val (updater, launcher) = newUpdater(layout, fetcher = VersionFetcher { VersionCheckResult.Unreachable })

        updater.run()

        assertFalse(Files.exists(layout.backupDir))
        assertEquals(1, launcher.launchCount)
    }
}
