package org.example.updater

import java.nio.file.Path

/**
 * Paths inside the §9 install layout `<install_dir>/{updater, runtime/, app/, data/}`. The
 * updater operates relative to a single root ([installDir]); [dataDir] is included here only so
 * tests can assert nothing under it is ever touched (§9 pt 5) — no code in this module reads or
 * writes through it.
 */
data class InstallLayout(val installDir: Path) {
    val appDir: Path = installDir.resolve("app")
    val backupDir: Path = installDir.resolve("app.backup")
    val runtimeDir: Path = installDir.resolve("runtime")
    val dataDir: Path = installDir.resolve("data")
    val versionFile: Path = appDir.resolve("version.json")
    val markerFile: Path = installDir.resolve("update_pending.json")
    val logFile: Path = installDir.resolve("updater.log")
}
