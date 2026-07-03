package org.example.app.domain.session

import org.example.app.domain.audio.CaptureFormat

/**
 * Disk preflight sizing (§8.1): "requires free space for a worst-case session computed from
 * the negotiated capture format (30 min × bytes/s, with margin for clips + zip; at least
 * 1 GiB at the target format) or refuses to start."
 *
 * The spec gives the 30-minute/1-GiB numbers but not an exact multiplier for "margin for
 * clips + zip"; this implementation assumes clips are a subset of the master (only the last
 * take per instance is exported, §8.3) and the archive ZIP contains the master once plus those
 * clips, so a 2x multiplier over the raw master size is a safe worst-case upper bound. Flagged
 * as an assumption for the lead pending a firmer number.
 *
 * No-master (questionnaire/info-only) protocols (§6.2) have no master WAV to size against;
 * they still need a small allowance for the session's JSON files and archive ZIP.
 */
object SessionDiskPreflight {
    const val EXPECTED_SESSION_SECONDS: Int = 30 * 60
    const val MINIMUM_BYTES_AT_TARGET_FORMAT: Long = 1L * 1024 * 1024 * 1024 // 1 GiB
    const val NO_MASTER_MINIMUM_BYTES: Long = 10L * 1024 * 1024 // 10 MiB — assumption, see class doc
    private const val MARGIN_MULTIPLIER: Long = 2 // master + (clips ⊆ master, + zip) — assumption, see class doc

    /** Worst-case bytes required to safely start a session negotiated at [format].
     * `null` means a no-master (questionnaire/info-only) protocol. */
    fun requiredBytes(format: CaptureFormat?): Long {
        if (format == null) return NO_MASTER_MINIMUM_BYTES
        val frameSize = format.channels * (format.bits / 8)
        val masterBytes = format.sampleRate.toLong() * frameSize * EXPECTED_SESSION_SECONDS
        return maxOf(masterBytes * MARGIN_MULTIPLIER, MINIMUM_BYTES_AT_TARGET_FORMAT)
    }
}
