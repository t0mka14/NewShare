package org.example.updater

/** What the updater should do at startup, before checking for a new version (§11 "app failed to
 * launch post-update ⇒ restore backup on next run"). */
enum class StartupRecoveryAction {
    /** The last update's marker is still present: its post-update launch was never confirmed.
     * Restore `app.backup/` over `app/`. */
    RESTORE_BACKUP,

    /** No marker (last update, if any, confirmed a successful launch and deleted it), but a
     * backup directory is still lying around — leftover from a completed update. Delete it. */
    DELETE_STALE_BACKUP,

    /** Nothing to do. */
    NONE,
}

/**
 * Pure decision table, deliberately factored out of [Updater] so it is table-testable without any
 * filesystem or process interaction (§10.1 "backup/restore decision logic").
 */
object BackupDecision {
    fun decide(markerPresent: Boolean, backupPresent: Boolean): StartupRecoveryAction = when {
        markerPresent && backupPresent -> StartupRecoveryAction.RESTORE_BACKUP
        // Marker present but no backup to restore from is an anomaly (e.g. the backup was
        // manually removed) — there's nothing to roll back to, so we just fall through to
        // deleting the stale marker in the caller and proceed with whatever `app/` currently is.
        markerPresent && !backupPresent -> StartupRecoveryAction.NONE
        !markerPresent && backupPresent -> StartupRecoveryAction.DELETE_STALE_BACKUP
        else -> StartupRecoveryAction.NONE
    }
}
