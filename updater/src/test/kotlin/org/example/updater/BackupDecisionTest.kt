package org.example.updater

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** §10.1 "backup/restore decision logic", table-tested (kept dependency-light: a plain loop
 * instead of pulling in junit-jupiter-params). */
class BackupDecisionTest {

    private data class Case(val markerPresent: Boolean, val backupPresent: Boolean, val expected: StartupRecoveryAction)

    @Test
    fun `decision table covers all four combinations`() {
        val cases = listOf(
            Case(markerPresent = true, backupPresent = true, expected = StartupRecoveryAction.RESTORE_BACKUP),
            Case(markerPresent = true, backupPresent = false, expected = StartupRecoveryAction.NONE),
            Case(markerPresent = false, backupPresent = true, expected = StartupRecoveryAction.DELETE_STALE_BACKUP),
            Case(markerPresent = false, backupPresent = false, expected = StartupRecoveryAction.NONE),
        )

        for (case in cases) {
            val actual = BackupDecision.decide(case.markerPresent, case.backupPresent)
            assertEquals(case.expected, actual, "marker=${case.markerPresent} backup=${case.backupPresent}")
        }
    }
}
