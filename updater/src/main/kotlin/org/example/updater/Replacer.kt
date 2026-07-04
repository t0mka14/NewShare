package org.example.updater

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import java.util.zip.ZipInputStream

/**
 * Backup → replace → rollback file operations for `app/` (§9 pt 2). Pure filesystem work, no
 * network, so it is testable against a fake install layout under `@TempDir` without touching
 * HTTP or spawning any process.
 */
class Replacer(private val log: UpdaterLog) {

    /** Renames `app/` to `app.backup/`. Deletes any pre-existing stale backup first — the startup
     * recovery decision ([BackupDecision]) should already have cleared one, but this stays
     * defensive. */
    fun backup(layout: InstallLayout) {
        if (Files.exists(layout.backupDir)) {
            deleteRecursively(layout.backupDir)
        }
        Files.move(layout.appDir, layout.backupDir, StandardCopyOption.ATOMIC_MOVE)
    }

    /** Unzips [updatePackage] (containing the new `app/`, including its `version.json`) into
     * [InstallLayout.appDir]. On any failure, restores the backup so `app/` always ends the call
     * in a launchable state (§11 "replacement failure ⇒ rollback"), and returns false. Assumes
     * [backup] has already run, i.e. `app/` does not exist and `app.backup/` does. */
    fun replace(layout: InstallLayout, updatePackage: Path): Boolean {
        return try {
            Files.createDirectories(layout.appDir)
            unzip(updatePackage, layout.appDir)
            true
        } catch (e: IOException) {
            log.error("Replacement failed, rolling back to previous app", e)
            deleteRecursively(layout.appDir)
            restoreBackup(layout)
            false
        }
    }

    /** Restores `app.backup/` over `app/` — used both for replace-failure rollback and for the
     * failed-launch recovery path (§11 "restore backup on next run"). */
    fun restoreBackup(layout: InstallLayout) {
        if (Files.exists(layout.appDir)) {
            deleteRecursively(layout.appDir)
        }
        Files.move(layout.backupDir, layout.appDir, StandardCopyOption.ATOMIC_MOVE)
    }

    fun deleteBackup(layout: InstallLayout) {
        deleteRecursively(layout.backupDir)
    }

    private fun unzip(zipFile: Path, targetDir: Path) {
        ZipInputStream(Files.newInputStream(zipFile)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val outPath = targetDir.resolve(entry.name).normalize()
                if (!outPath.startsWith(targetDir)) {
                    throw IOException("Zip entry escapes target directory: ${entry.name}")
                }
                if (entry.isDirectory) {
                    Files.createDirectories(outPath)
                } else {
                    outPath.parent?.let { Files.createDirectories(it) }
                    Files.newOutputStream(outPath).use { out -> zip.copyTo(out) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun deleteRecursively(dir: Path) {
        if (!Files.exists(dir)) return
        Files.walk(dir).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
    }
}
