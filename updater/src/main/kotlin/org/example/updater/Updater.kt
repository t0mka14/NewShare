package org.example.updater

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.example.shared.model.AppVersion
import org.example.shared.model.AppVersionFile
import org.example.shared.model.VersionCheckResponse
import org.example.updater.model.UpdateMarker
import java.io.IOException
import java.nio.file.Files
import java.time.Instant

/**
 * Orchestrates the full §9 flow: startup recovery → version check → (download, verify, backup,
 * replace) → launch. Depends only on the small interfaces above ([VersionFetcher], [Downloader],
 * [AppLauncher]) plus [Replacer] and [BackupDecision], so the whole flow runs end-to-end in tests
 * against a fake `@TempDir` install layout with no real network and no process actually spawned.
 *
 * Every exit path ends in exactly one call to [launcher] — matching §9 pt 4 ("unreachable ⇒
 * launch existing app") and §11 (download/checksum/replacement failures all fall back to
 * launching whatever `app/` currently is, never leaving the user stranded).
 */
class Updater(
    private val layout: InstallLayout,
    private val fetcher: VersionFetcher,
    private val downloader: Downloader,
    private val launcher: AppLauncher,
    private val replacer: Replacer,
    private val log: UpdaterLog,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun run() {
        recoverFromPreviousRun()

        val update = decideUpdate()
        if (update != null) {
            applyUpdate(update)
        }

        launcher.launch(layout)
    }

    /** §11 "app failed to launch post-update ⇒ restore backup on next run": if the marker from a
     * previous run is still present, that update's post-replace launch was never confirmed. */
    private fun recoverFromPreviousRun() {
        val markerPresent = Files.exists(layout.markerFile)
        val backupPresent = Files.exists(layout.backupDir)
        when (BackupDecision.decide(markerPresent, backupPresent)) {
            StartupRecoveryAction.RESTORE_BACKUP -> {
                log.warn("update_pending.json present from a previous run: restoring app.backup/")
                replacer.restoreBackup(layout)
            }
            StartupRecoveryAction.DELETE_STALE_BACKUP -> {
                log.info("Deleting stale app.backup/ left over from a confirmed update")
                replacer.deleteBackup(layout)
            }
            StartupRecoveryAction.NONE -> Unit
        }
        if (markerPresent) {
            Files.deleteIfExists(layout.markerFile)
        }
    }

    /** Returns the update to apply, or null if there is none (unreachable, malformed remote
     * version, or remote not newer than local — §10.1 "malformed versions treated as not-newer"). */
    private fun decideUpdate(): VersionCheckResponse? {
        val localVersion = readLocalVersion()
        return when (val result = fetcher.fetchLatest()) {
            is VersionCheckResult.Unreachable -> null
            is VersionCheckResult.Available -> {
                val remoteVersion = AppVersion.parse(result.response.version)
                when {
                    remoteVersion == null -> {
                        log.warn("Malformed remote version '${result.response.version}'; treating as not newer")
                        null
                    }
                    localVersion != null && remoteVersion <= localVersion -> null
                    else -> result.response
                }
            }
        }
    }

    private fun readLocalVersion(): AppVersion? {
        if (!Files.isRegularFile(layout.versionFile)) return null
        return try {
            val content = Files.readString(layout.versionFile)
            val file = json.decodeFromString(AppVersionFile.serializer(), content)
            AppVersion.parse(file.appVersion)
        } catch (e: IOException) {
            log.warn("Could not read local version.json: ${e.message}")
            null
        } catch (e: SerializationException) {
            log.warn("Could not parse local version.json: ${e.message}")
            null
        }
    }

    private fun applyUpdate(update: VersionCheckResponse) {
        val tempFile = Files.createTempFile(layout.installDir, "update", ".zip")
        try {
            log.info("Downloading update ${update.version} from ${update.downloadUrl}")
            if (!downloader.download(update.downloadUrl, tempFile)) {
                log.warn("Download failed or incomplete; keeping current app")
                return
            }
            if (!Sha256.matches(tempFile, update.checksum)) {
                log.warn("Checksum mismatch for downloaded update; discarding")
                return
            }

            try {
                replacer.backup(layout)
            } catch (e: IOException) {
                log.error("Could not back up app/; keeping current app", e)
                return
            }

            if (!replacer.replace(layout, tempFile)) {
                // Replacer already rolled back to the backup internally.
                log.error("Replacement failed; rolled back to previous app")
                return
            }

            writeMarker(update.version)
            log.info("Update ${update.version} applied; awaiting confirmed launch")
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    private fun writeMarker(appliedVersion: String) {
        val marker = UpdateMarker(appliedVersion = appliedVersion, appliedAt = Instant.now().toString())
        Files.writeString(layout.markerFile, json.encodeToString(UpdateMarker.serializer(), marker))
    }
}
