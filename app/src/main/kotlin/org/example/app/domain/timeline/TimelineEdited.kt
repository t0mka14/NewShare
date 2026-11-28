package org.example.app.domain.timeline

import kotlinx.serialization.Serializable

/**
 * `timeline_edited.json` (§8.7, §8.10) — segment-shaped, directly consumable by the cutter.
 * A segment is the last take of one VOCAL task instance. Written only if at least one
 * boundary was moved or a part cut out in the editor (§8.7); an untouched pass writes nothing.
 *
 * [cuts] are parts removed from the clip (global samples, stop exclusive); the exported clip is
 * the segment minus its cuts, spliced. Omitted from the JSON when empty (default not encoded).
 */
@Serializable
data class TimelineEditedSegment(
    val taskIndex: Int,
    val repetition: Int,
    val startSample: Long,
    val stopSample: Long,
    val cuts: List<SampleRange> = emptyList(),
)

/** A half-open range of global samples `[startSample, stopSample)`. */
@Serializable
data class SampleRange(val startSample: Long, val stopSample: Long)

/**
 * The segment minus its cuts, as inclusive [LongRange]s in global samples, in ascending order.
 * Tolerant of unsorted/overlapping/out-of-segment cuts (they are clamped and merged); use
 * [TimelineBoundaryValidator] to reject such input. Empty pieces are dropped.
 */
fun TimelineEditedSegment.keptRanges(): List<LongRange> {
    val kept = mutableListOf<LongRange>()
    var cursor = startSample
    for (cut in cuts.sortedBy { it.startSample }) {
        val cutStart = cut.startSample.coerceAtLeast(startSample)
        val cutStop = cut.stopSample.coerceAtMost(stopSample)
        if (cutStop <= cutStart) continue
        if (cutStart > cursor) kept += cursor..(cutStart - 1)
        if (cutStop > cursor) cursor = cutStop
    }
    if (cursor < stopSample) kept += cursor..(stopSample - 1)
    return kept
}

/** Total samples of [keptRanges]. */
fun TimelineEditedSegment.keptSampleCount(): Long = keptRanges().sumOf { it.last - it.first + 1 }

@Serializable
data class TimelineEdited(
    val version: Int = 1,
    val sessionId: String,
    val sampleRate: Int,
    val basedOn: String = "original",
    val segments: List<TimelineEditedSegment>,
)
