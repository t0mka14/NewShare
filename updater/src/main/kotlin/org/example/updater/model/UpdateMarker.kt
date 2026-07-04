package org.example.updater.model

import kotlinx.serialization.Serializable

/**
 * `<install_dir>/update_pending.json` — written right after a successful `app/` replacement and
 * deleted by `:app`'s `Main.kt` once it starts successfully (§9/§11 "restore backup on next run").
 * If the updater finds this marker still present on its *next* run, the previous update never
 * got far enough to confirm a successful launch, so it restores `app.backup/` (see
 * [org.example.updater.BackupDecision]). Not part of `data/` — the updater never touches that
 * directory (§9 pt 5).
 */
@Serializable
data class UpdateMarker(
    val version: Int = 1,
    val appliedVersion: String,
    val appliedAt: String,
)
