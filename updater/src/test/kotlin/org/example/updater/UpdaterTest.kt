package org.example.updater

import org.example.shared.model.ComponentDescriptor
import org.example.shared.model.VersionCheckResponse
import org.example.updater.fakes.FakeAppLauncher
import org.example.updater.fakes.FakeDownloader
import org.example.updater.model.InstalledState
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
 * filesystem side ([Replacer], backups, marker, ledger) is real, against a `@TempDir` install.
 */
class UpdaterTest {

    private fun buildLayout(root: Path): InstallLayout {
        val layout = InstallLayout(root)
        Files.createDirectories(layout.appDir)
        Files.createDirectories(layout.runtimeDir)
        Files.writeString(layout.appDir.resolve("app.jar"), "old-jar-bytes")
        Files.writeString(layout.versionFile, """{"version":1,"appVersion":"1.0.0"}""")
        return layout
    }

    /** Seeds the ledger, which is where the installed release and component checksums live. */
    private fun seedLedger(layout: InstallLayout, release: String?, vararg components: Pair<String, Pair<String, String>>) {
        var state = InstalledState(release = release)
        for ((id, targetAndChecksum) in components) {
            state = state.withComponent(id, targetAndChecksum.first, targetAndChecksum.second, "2026-01-01T00:00:00Z")
        }
        InstalledStateStore(layout.stateFile, UpdaterLog(layout.logFile)).save(state)
    }

    private fun readLedger(layout: InstallLayout): InstalledState =
        InstalledStateStore(layout.stateFile, UpdaterLog(layout.logFile)).load()

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

    private fun component(id: String, target: String, url: String, checksum: String) =
        ComponentDescriptor(id = id, target = target, url = url, checksum = checksum)

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

    private fun responding(response: VersionCheckResponse) =
        VersionFetcher { VersionCheckResult.Available(response) }

    // -- no-op paths ---------------------------------------------------------------------------

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
    fun `malformed remote release is treated as not newer`(@TempDir tempDir: Path) {
        val layout = buildLayout(tempDir)
        val (updater, launcher) = newUpdater(
            layout,
            responding(VersionCheckResponse("not-a-version", listOf(component("app", "app", "http://x/a.zip", "irrelevant")))),
        )

        updater.run()

        assertEquals(1, launcher.launchCount)
        assertEquals("old-jar-bytes", layout.appDir.resolve("app.jar").readText())
    }

    @Test
    fun `release not newer than the installed one skips the update`(@TempDir tempDir: Path) {
        val layout = buildLayout(tempDir)
        seedLedger(layout, release = "2.0.0")
        val (updater, launcher) = newUpdater(
            layout,
            responding(VersionCheckResponse("2.0.0", listOf(component("app", "app", "http://x/a.zip", "irrelevant")))),
        )

        updater.run()

        assertEquals(1, launcher.launchCount)
        assertEquals("old-jar-bytes", layout.appDir.resolve("app.jar").readText())
    }

    @Test
    fun `a release whose components all match what is installed downloads nothing`(@TempDir tempDir: Path) {
        val layout = buildLayout(tempDir)
        seedLedger(layout, release = "1.0.0", "app" to ("app" to "same-checksum"))
        val downloader = FakeDownloader()
        val (updater, launcher) = newUpdater(
            layout,
            responding(VersionCheckResponse("2.0.0", listOf(component("app", "app", "http://x/a.zip", "same-checksum")))),
            downloader = downloader,
        )

        updater.run()

        assertEquals(1, launcher.launchCount)
        assertTrue(downloader.requestedUrls.isEmpty(), "an unchanged component must not be re-downloaded")
    }

    // -- the happy path ------------------------------------------------------------------------

    @Test
    fun `a newer release with a matching checksum is downloaded and applied`(@TempDir tempDir: Path) {
        val layout = buildLayout(tempDir)
        seedLedger(layout, release = "1.0.0", "app" to ("app" to "old-checksum"))
        val payload = zipPackage("app.jar" to "new-jar-bytes", "version.json" to """{"version":1,"appVersion":"2.0.0"}""")
        val url = "http://example.test/app-2.0.0.zip"
        val downloader = FakeDownloader(payloads = mapOf(url to payload))
        val (updater, launcher) = newUpdater(
            layout,
            responding(VersionCheckResponse("2.0.0", listOf(component("app", "app", url, sha256(payload))))),
            downloader = downloader,
        )

        updater.run()

        assertEquals(1, launcher.launchCount)
        assertEquals("new-jar-bytes", layout.appDir.resolve("app.jar").readText())
        assertTrue(Files.exists(layout.backupDir), "backup is kept until a confirmed launch")
        assertEquals("old-jar-bytes", layout.backupDir.resolve("app.jar").readText())
        assertTrue(Files.exists(layout.markerFile))
        assertFalse(Files.exists(layout.stagingDir), "staging must not survive the run")
        val ledger = readLedger(layout)
        assertEquals("2.0.0", ledger.release)
        assertEquals(sha256(payload), ledger.checksumOf("app"))
    }

    @Test
    fun `only the stale component of a multi-component release is downloaded`(@TempDir tempDir: Path) {
        val layout = buildLayout(tempDir)
        val ffmpegTarget = "native/ffmpeg/linux-x86_64"
        Files.createDirectories(tempDir.resolve(ffmpegTarget))
        Files.writeString(tempDir.resolve(ffmpegTarget).resolve("ffmpeg"), "old-ffmpeg")
        seedLedger(
            layout,
            release = "1.0.0",
            "app" to ("app" to "app-checksum-unchanged"),
            "ffmpeg" to (ffmpegTarget to "old-ffmpeg-checksum"),
        )
        val ffmpegPayload = zipPackage("ffmpeg" to "new-ffmpeg")
        val ffmpegUrl = "http://example.test/ffmpeg.zip"
        val appUrl = "http://example.test/app.zip"
        val downloader = FakeDownloader(payloads = mapOf(ffmpegUrl to ffmpegPayload))
        val (updater, _) = newUpdater(
            layout,
            responding(
                VersionCheckResponse(
                    "1.0.1",
                    listOf(
                        component("app", "app", appUrl, "app-checksum-unchanged"),
                        component("ffmpeg", ffmpegTarget, ffmpegUrl, sha256(ffmpegPayload)),
                    ),
                ),
            ),
            downloader = downloader,
        )

        updater.run()

        assertEquals(listOf(ffmpegUrl), downloader.requestedUrls, "the unchanged app must not be fetched")
        assertEquals("new-ffmpeg", tempDir.resolve(ffmpegTarget).resolve("ffmpeg").readText())
        assertEquals("old-jar-bytes", layout.appDir.resolve("app.jar").readText(), "app is untouched")
        assertFalse(Files.exists(layout.backupDir), "an untouched component gets no backup")
    }

    @Test
    fun `a component with nothing installed yet is created`(@TempDir tempDir: Path) {
        val layout = buildLayout(tempDir)
        seedLedger(layout, release = "1.0.0", "app" to ("app" to "unchanged"))
        val target = "native/ffmpeg/linux-x86_64"
        val payload = zipPackage("ffmpeg" to "fresh-ffmpeg")
        val url = "http://example.test/ffmpeg.zip"
        val (updater, _) = newUpdater(
            layout,
            responding(
                VersionCheckResponse("1.0.1", listOf(
                    component("app", "app", "http://x/a.zip", "unchanged"),
                    component("ffmpeg", target, url, sha256(payload)),
                )),
            ),
            downloader = FakeDownloader(payloads = mapOf(url to payload)),
        )

        updater.run()

        assertEquals("fresh-ffmpeg", tempDir.resolve(target).resolve("ffmpeg").readText())
        assertEquals(sha256(payload), readLedger(layout).checksumOf("ffmpeg"))
    }

    // -- all-or-nothing ------------------------------------------------------------------------

    @Test
    fun `one bad checksum in a multi-component release swaps nothing at all`(@TempDir tempDir: Path) {
        val layout = buildLayout(tempDir)
        val ffmpegTarget = "native/ffmpeg/linux-x86_64"
        seedLedger(layout, release = "1.0.0")
        val appPayload = zipPackage("app.jar" to "new-jar-bytes")
        val ffmpegPayload = zipPackage("ffmpeg" to "new-ffmpeg")
        val appUrl = "http://x/app.zip"
        val ffmpegUrl = "http://x/ffmpeg.zip"
        val downloader = FakeDownloader(payloads = mapOf(appUrl to appPayload, ffmpegUrl to ffmpegPayload))
        val (updater, launcher) = newUpdater(
            layout,
            responding(
                VersionCheckResponse("2.0.0", listOf(
                    component("ffmpeg", ffmpegTarget, ffmpegUrl, "0".repeat(64)), // wrong
                    component("app", "app", appUrl, sha256(appPayload)),          // right
                )),
            ),
            downloader = downloader,
        )

        updater.run()

        assertEquals("old-jar-bytes", layout.appDir.resolve("app.jar").readText(), "the good component must not land either")
        assertFalse(Files.exists(tempDir.resolve(ffmpegTarget)))
        assertFalse(Files.exists(layout.backupDir))
        assertFalse(Files.exists(layout.markerFile))
        assertFalse(Files.exists(layout.stagingDir))
        assertEquals("1.0.0", readLedger(layout).release, "the ledger must not move")
        assertEquals(1, launcher.launchCount, "the user still gets the app they had")
    }

    @Test
    fun `a failed download applies nothing`(@TempDir tempDir: Path) {
        val layout = buildLayout(tempDir)
        seedLedger(layout, release = "1.0.0")
        val (updater, launcher) = newUpdater(
            layout,
            responding(VersionCheckResponse("2.0.0", listOf(component("app", "app", "http://x/a.zip", "irrelevant")))),
            downloader = FakeDownloader(succeeds = false),
        )

        updater.run()

        assertEquals(1, launcher.launchCount)
        assertEquals("old-jar-bytes", layout.appDir.resolve("app.jar").readText())
        assertFalse(Files.exists(layout.backupDir))
        assertFalse(Files.exists(layout.markerFile))
    }

    @Test
    fun `a corrupt package rolls back and leaves no marker behind`(@TempDir tempDir: Path) {
        val layout = buildLayout(tempDir)
        seedLedger(layout, release = "1.0.0")
        // Verifiable bytes that are not a readable zip: the download and checksum gates pass, so
        // the failure lands in the swap.
        val payload = "not-a-zip-file".toByteArray()
        val url = "http://x/app.zip"
        val (updater, launcher) = newUpdater(
            layout,
            responding(VersionCheckResponse("2.0.0", listOf(component("app", "app", url, sha256(payload))))),
            downloader = FakeDownloader(payloads = mapOf(url to payload)),
        )

        updater.run()

        assertEquals("old-jar-bytes", layout.appDir.resolve("app.jar").readText(), "app must be rolled back")
        assertFalse(Files.exists(layout.backupDir), "the backup is consumed by the rollback")
        assertFalse(Files.exists(layout.markerFile), "a leftover marker would trigger a bogus restore next run")
        assertEquals(1, launcher.launchCount)
    }

    @Test
    fun `a release naming a target outside the install is ignored entirely`(@TempDir tempDir: Path) {
        val layout = buildLayout(tempDir)
        seedLedger(layout, release = "1.0.0")
        val payload = zipPackage("app.jar" to "new-jar-bytes")
        val downloader = FakeDownloader(contentIfSuccessful = payload)
        val (updater, launcher) = newUpdater(
            layout,
            responding(
                VersionCheckResponse("2.0.0", listOf(
                    component("app", "app", "http://x/app.zip", sha256(payload)),
                    component("evil", "../escape", "http://x/evil.zip", sha256(payload)),
                )),
            ),
            downloader = downloader,
        )

        updater.run()

        assertTrue(downloader.requestedUrls.isEmpty(), "a rejected component must abort the whole release")
        assertEquals("old-jar-bytes", layout.appDir.resolve("app.jar").readText())
        assertEquals(1, launcher.launchCount)
    }

    // -- recovery ------------------------------------------------------------------------------

    @Test
    fun `a leftover marker from a failed launch restores the backup and blacklists the release`(@TempDir tempDir: Path) {
        val layout = buildLayout(tempDir)
        seedLedger(layout, release = "2.0.0", "app" to ("app" to "new-checksum"))
        Files.writeString(layout.appDir.resolve("app.jar"), "unconfirmed-new-bytes")
        Files.createDirectories(layout.backupDir)
        Files.writeString(layout.backupDir.resolve("app.jar"), "known-good-old-bytes")
        Files.writeString(
            layout.markerFile,
            """{"version":2,"release":"2.0.0","appliedAt":"2026-01-01T00:00:00Z","previousRelease":"1.0.0",
               "components":[{"id":"app","target":"app","previousChecksum":"old-checksum"}]}""",
        )

        val (updater, launcher) = newUpdater(layout, VersionFetcher { VersionCheckResult.Unreachable })

        updater.run()

        assertEquals("known-good-old-bytes", layout.appDir.resolve("app.jar").readText())
        assertFalse(Files.exists(layout.backupDir))
        assertFalse(Files.exists(layout.markerFile))
        val ledger = readLedger(layout)
        assertTrue(ledger.failedReleases.contains("2.0.0"), "the release must not be retried")
        assertEquals("old-checksum", ledger.checksumOf("app"), "the ledger must be reverted with the files")
        assertEquals("1.0.0", ledger.release, "the release must be reverted too, not dropped")
        assertEquals(1, launcher.launchCount)
    }

    @Test
    fun `a blacklisted release is not applied again`(@TempDir tempDir: Path) {
        val layout = buildLayout(tempDir)
        InstalledStateStore(layout.stateFile, UpdaterLog(layout.logFile))
            .save(InstalledState(release = "1.0.0", failedReleases = listOf("2.0.0")))
        val downloader = FakeDownloader()
        val (updater, launcher) = newUpdater(
            layout,
            responding(VersionCheckResponse("2.0.0", listOf(component("app", "app", "http://x/a.zip", "whatever")))),
            downloader = downloader,
        )

        updater.run()

        assertTrue(downloader.requestedUrls.isEmpty(), "a release that already failed must not be re-downloaded")
        assertEquals("old-jar-bytes", layout.appDir.resolve("app.jar").readText())
        assertEquals(1, launcher.launchCount)
    }

    @Test
    fun `a newer release is still applied after an earlier one failed`(@TempDir tempDir: Path) {
        val layout = buildLayout(tempDir)
        InstalledStateStore(layout.stateFile, UpdaterLog(layout.logFile))
            .save(InstalledState(release = "1.0.0", failedReleases = listOf("2.0.0")))
        val payload = zipPackage("app.jar" to "fixed-jar-bytes")
        val url = "http://x/app-2.0.1.zip"
        val (updater, _) = newUpdater(
            layout,
            responding(VersionCheckResponse("2.0.1", listOf(component("app", "app", url, sha256(payload))))),
            downloader = FakeDownloader(payloads = mapOf(url to payload)),
        )

        updater.run()

        assertEquals("fixed-jar-bytes", layout.appDir.resolve("app.jar").readText(), "a fixed release must not be pinned out")
    }

    @Test
    fun `an apply interrupted mid-unzip is rolled back, not treated as a stale backup`(@TempDir tempDir: Path) {
        // The exact state a crash between the backup rename and a completed unzip leaves behind.
        // Because the marker is written before anything moves, this is recoverable; when it was
        // written afterwards the next run read it as a stale backup and deleted the only good copy.
        val layout = buildLayout(tempDir)
        Files.writeString(layout.appDir.resolve("app.jar"), "half-written-garbage")
        Files.createDirectories(layout.backupDir)
        Files.writeString(layout.backupDir.resolve("app.jar"), "known-good-old-bytes")
        Files.writeString(
            layout.markerFile,
            """{"version":2,"release":"2.0.0","appliedAt":"2026-01-01T00:00:00Z",
               "components":[{"id":"app","target":"app"}]}""",
        )

        val (updater, launcher) = newUpdater(layout, VersionFetcher { VersionCheckResult.Unreachable })

        updater.run()

        assertEquals(
            "known-good-old-bytes",
            layout.appDir.resolve("app.jar").readText(),
            "the partial unzip must be discarded in favour of the backup",
        )
        assertFalse(Files.exists(layout.backupDir))
        assertFalse(Files.exists(layout.markerFile))
        assertEquals(1, launcher.launchCount)
    }

    @Test
    fun `a stale backup with no marker is deleted at startup`(@TempDir tempDir: Path) {
        val layout = buildLayout(tempDir)
        seedLedger(layout, release = "1.0.0", "app" to ("app" to "checksum"))
        Files.createDirectories(layout.backupDir)
        Files.writeString(layout.backupDir.resolve("app.jar"), "stale-backup-bytes")

        val (updater, launcher) = newUpdater(layout, VersionFetcher { VersionCheckResult.Unreachable })

        updater.run()

        assertFalse(Files.exists(layout.backupDir))
        assertEquals(1, launcher.launchCount)
    }

    @Test
    fun `staging left by a crashed run is cleared before anything else`(@TempDir tempDir: Path) {
        val layout = buildLayout(tempDir)
        Files.createDirectories(layout.stagingDir)
        Files.writeString(layout.stagingDir.resolve("app.zip"), "half-downloaded")

        val (updater, _) = newUpdater(layout, VersionFetcher { VersionCheckResult.Unreachable })

        updater.run()

        assertFalse(Files.exists(layout.stagingDir))
    }
}
