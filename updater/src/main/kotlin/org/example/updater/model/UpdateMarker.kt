package org.example.updater.model

import kotlinx.serialization.Serializable

/**
 * `<install_dir>/update_pending.json` — written **before** the first file is moved and deleted by
 * `:app`'s `Main.kt` once the app starts successfully (§9/§11 "restore backup on next run").
 *
 * It therefore means *"an apply was in flight"*, which deliberately covers two situations that
 * want the same remedy: the swap finished but the launch was never confirmed, and the updater died
 * partway through the swap. Either way the next run restores the backup (see
 * [org.example.updater.BackupDecision]).
 *
 * Writing it after the replacement instead — as this originally did — is what made the second case
 * destructive: a backup with no marker reads as `DELETE_STALE_BACKUP`, so the next run deleted the
 * only intact copy of `app/` while `app/` itself held a partial unzip.
 *
 * Not part of `data/` — the updater never touches that directory (§9 pt 5).
 */
@Serializable
data class UpdateMarker(
    val version: Int = 2,
    /** The release being applied — recorded so a launch that never confirms can blacklist it. */
    val release: String,
    val appliedAt: String,
    /** The release the install was on before this apply, so a rollback restores the ledger's
     * release as faithfully as it restores the directories. */
    val previousRelease: String? = null,
    /** What this apply touches. Written before the first swap, so it lists what *will* change;
     * recovery uses it to restore both the directories and the ledger entries. */
    val components: List<MarkedComponent> = emptyList(),
)

@Serializable
data class MarkedComponent(
    val id: String,
    /** Install-dir-relative. */
    val target: String,
    /** The ledger's checksum for this component before the swap; null if it was not installed. */
    val previousChecksum: String? = null,
)
