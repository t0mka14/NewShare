package org.example.app.domain.session

import org.example.app.domain.audio.CaptureFormat
import java.nio.file.Path

/**
 * One contiguous master (part) file's span in the session's continuous, cross-part global
 * sample counter (§13 decision 20). `globalEndExclusive` is `null` for the last (currently
 * open-ended, from the processor's point of view) part.
 */
data class MasterPart(
    val file: Path,
    val format: CaptureFormat,
    val globalStart: Long,
    val globalEndExclusive: Long?,
)

/**
 * Maps global (cross-part) sample offsets — the only offsets ever recorded in timeline events
 * or `interruptions[]` — onto the master part file that actually contains them, and translates
 * to that file's own local frame offsets (§8.5, §13 decision 20).
 *
 * `writtenSamples` is one continuous counter across every part, including a format-changing
 * device swap: the gap between parts exists only in wall-clock time, not in the sample count
 * (an interruption's `sampleOffset` is simultaneously "last global sample written to the old
 * part" and "first global sample of the new part"). Local offsets within a part are therefore
 * just the global offset minus that part's own starting global offset.
 */
object MasterPartMap {
    /**
     * Builds the ordered part list for one session: part 1 is [defaultMasterFile] in
     * [defaultFormat] (the session's top-level `captureFormat`, used from offset 0 until the
     * first interruption, or forever if there were none); each subsequent [interruptions] entry
     * starts a new part, in its own negotiated `captureFormat` (§13 decision 28), from that
     * interruption's `sampleOffset` up to the next interruption's (or open-ended for the last).
     *
     * [interruptions] must be in chronological (recording) order — the same order they appear
     * in `examination.json` (§8.10) — since each entry's global end offset is derived from the
     * *next* entry in the list, not resolved by any other key.
     */
    fun build(
        defaultMasterFile: Path,
        defaultFormat: CaptureFormat,
        interruptions: List<Interruption>,
        sessionDir: Path,
    ): List<MasterPart> {
        val parts = mutableListOf<MasterPart>()
        parts += MasterPart(
            file = defaultMasterFile,
            format = defaultFormat,
            globalStart = 0L,
            globalEndExclusive = interruptions.getOrNull(0)?.sampleOffset,
        )
        for (i in interruptions.indices) {
            val interruption = interruptions[i]
            parts += MasterPart(
                file = sessionDir.resolve(interruption.partFile),
                format = interruption.captureFormat,
                globalStart = interruption.sampleOffset,
                globalEndExclusive = interruptions.getOrNull(i + 1)?.sampleOffset,
            )
        }
        return parts
    }

    /**
     * Resolves the global `[startSample, stopSample)` range to the single part it lies
     * entirely within, translated to that part's local frame offsets. Returns `null` if no
     * part contains the whole range — either it is out of range entirely, or (a data-integrity
     * violation, since a take is always auto-rejected before a device-loss gap, §8.5) it spans
     * a part boundary.
     */
    fun resolve(parts: List<MasterPart>, startSample: Long, stopSample: Long): Pair<MasterPart, LongRange>? {
        val part = parts.firstOrNull { p ->
            startSample >= p.globalStart && (p.globalEndExclusive == null || stopSample <= p.globalEndExclusive)
        } ?: return null
        val localStart = startSample - part.globalStart
        val localStop = stopSample - part.globalStart
        return part to (localStart until localStop)
    }
}
