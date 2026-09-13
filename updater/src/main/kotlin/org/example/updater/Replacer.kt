package org.example.updater

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import java.util.zip.ZipInputStream

/**
 * Backup → replace → rollback file operations for a component's target directory (§9 pt 2).
 *
 * Pure filesystem work, no network and no process, so it is testable against a fake install layout
 * under `@TempDir`. Operates on plain paths so one implementation serves every component; the
 * `InstallLayout` overloads at the bottom are the original single-component API, kept because
 * nothing else needs to change to use them.
 *
 * A backup always sits beside its target as a `.backup` sibling, so `app/` → `app.backup/` keeps
 * the exact name and location [BackupDecision] and the docs already describe.
 */
class Replacer(private val log: UpdaterLog) {

    fun backupPathFor(target: Path): Path =
        target.resolveSibling(target.fileName.toString() + ".backup")

    /**
     * Renames [target] out of the way to [backup], deleting any pre-existing stale backup first.
     * Returns false when there was nothing to move — a first-time install of a component that does
     * not exist yet, which is not an error.
     */
    fun moveAside(target: Path, backup: Path): Boolean {
        if (Files.exists(backup)) deleteRecursively(backup)
        if (!Files.exists(target)) return false
        target.parent?.let { Files.createDirectories(it) }
        Files.move(target, backup, StandardCopyOption.ATOMIC_MOVE)
        return true
    }

    /**
     * Unzips [payload] into a fresh [target]. Throws on any failure, leaving the caller to roll
     * back — with several components in flight only the caller knows what else must be undone.
     */
    fun install(target: Path, payload: Path) {
        Files.createDirectories(target)
        // ZipInputStream reports garbage as an immediate end-of-stream rather than throwing, so a
        // payload that is not a zip at all unzips "successfully" into nothing. Without this check
        // that silently installs an empty directory and reports success.
        if (unzip(payload, target) == 0) {
            throw IOException("Update package for $target contained no files; refusing to install it")
        }
        markExecutables(target)
    }

    /** Restores [backup] over [target]. */
    fun restore(target: Path, backup: Path) {
        if (!Files.exists(backup)) return
        if (Files.exists(target)) deleteRecursively(target)
        target.parent?.let { Files.createDirectories(it) }
        Files.move(backup, target, StandardCopyOption.ATOMIC_MOVE)
    }

    fun discard(backup: Path) = deleteRecursively(backup)

    /**
     * Restores the executable bit that the zip round-trip drops.
     *
     * `java.util.zip.ZipInputStream` cannot read the unix-mode extra field at all, so everything
     * arrives 0644 and `runtime/bin/java` and `ffmpeg` are silently unusable. The rule is
     * hardcoded rather than described by the manifest because it covers every component we ship
     * and one fewer wire field is worth more than the generality.
     */
    private fun markExecutables(root: Path) {
        if (!Files.isDirectory(root)) return
        Files.walk(root).use { stream ->
            stream.filter(Files::isRegularFile).forEach { file ->
                val name = file.fileName.toString()
                val underBin = file.parent?.fileName?.toString() == "bin"
                if (underBin || name == "ffmpeg" || name == "ffmpeg.exe") {
                    runCatching { file.toFile().setExecutable(true, false) }
                        .onFailure { log.warn("Could not mark $file executable: ${it.describe()}") }
                }
            }
        }
    }

    private fun unzip(zipFile: Path, targetDir: Path): Int {
        var filesWritten = 0
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
                    filesWritten++
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return filesWritten
    }

    private fun deleteRecursively(dir: Path) {
        if (!Files.exists(dir)) return
        Files.walk(dir).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
    }

    // -- single-component API (§9's app/ case), expressed on top of the path-based core ---------

    fun backup(layout: InstallLayout) {
        moveAside(layout.appDir, layout.backupDir)
    }

    /** Installs the package into `app/`, rolling back internally on failure so `app/` always ends
     * this call launchable (§11 "replacement failure ⇒ rollback"). */
    fun replace(layout: InstallLayout, updatePackage: Path): Boolean = try {
        install(layout.appDir, updatePackage)
        true
    } catch (e: IOException) {
        log.error("Replacement failed, rolling back to previous app", e)
        deleteRecursively(layout.appDir)
        restore(layout.appDir, layout.backupDir)
        false
    }

    fun restoreBackup(layout: InstallLayout) = restore(layout.appDir, layout.backupDir)

    fun deleteBackup(layout: InstallLayout) = discard(layout.backupDir)
}
