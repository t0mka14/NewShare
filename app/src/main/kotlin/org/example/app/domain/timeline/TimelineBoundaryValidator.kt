package org.example.app.domain.timeline

/** Editor boundary validation rules (§8.7): start < stop, within recorded range, no overlap
 * with adjacent segments' final boundaries. */
sealed interface BoundaryError {
    data class Inverted(val segment: TimelineEditedSegment) : BoundaryError
    data class OutOfRange(val segment: TimelineEditedSegment, val validRange: LongRange) : BoundaryError
    data class OverlapsAdjacent(val segment: TimelineEditedSegment, val neighbor: TimelineEditedSegment) : BoundaryError

    /**
     * A cut is not strictly inside the segment (start < cut.start < cut.stop < stop), or is
     * inverted. This is also what rejects a start/stop boundary moved into or past a cut.
     */
    data class CutOutsideSegment(val segment: TimelineEditedSegment, val cut: SampleRange) : BoundaryError

    /** Cuts are unsorted, overlap, or touch (touching cuts must be merged by the caller). */
    data class CutsOverlap(val segment: TimelineEditedSegment, val first: SampleRange, val second: SampleRange) : BoundaryError

    /** The audio left after removing the cuts is shorter than [TimelineBoundaryValidator.MIN_KEPT_SECONDS]. */
    data class KeptTooShort(val segment: TimelineEditedSegment, val keptSamples: Long, val minSamples: Long) : BoundaryError
}

object TimelineBoundaryValidator {
    /** Minimum audio that must remain in a segment that has cuts. */
    const val MIN_KEPT_SECONDS = 0.5

    /**
     * Validates a full segment list, e.g. before persisting `timeline_edited.json`.
     * [sampleRate] enables the minimum-kept-audio check for segments with cuts; when null that
     * check is skipped (callers without cuts need not pass it).
     */
    fun validate(segments: List<TimelineEditedSegment>, totalSamples: Long, sampleRate: Int? = null): List<BoundaryError> {
        val errors = mutableListOf<BoundaryError>()

        for (segment in segments) {
            errors += validateOwnBounds(segment, totalSamples)
            errors += validateCuts(segment, sampleRate)
        }

        val ordered = segments.sortedBy { it.startSample }
        for (i in 0 until ordered.size - 1) {
            val current = ordered[i]
            val next = ordered[i + 1]
            if (current.stopSample > next.startSample) {
                errors += BoundaryError.OverlapsAdjacent(current, next)
            }
        }

        return errors
    }

    /**
     * Validates a single segment being dragged in the editor against its immediate neighbors,
     * without needing the full segment list rebuilt on every drag frame.
     */
    fun validateSingle(
        segment: TimelineEditedSegment,
        previous: TimelineEditedSegment?,
        next: TimelineEditedSegment?,
        totalSamples: Long,
        sampleRate: Int? = null,
    ): List<BoundaryError> {
        val errors = mutableListOf<BoundaryError>()
        errors += validateOwnBounds(segment, totalSamples)
        errors += validateCuts(segment, sampleRate)

        if (previous != null && segment.startSample < previous.stopSample) {
            errors += BoundaryError.OverlapsAdjacent(segment, previous)
        }
        if (next != null && segment.stopSample > next.startSample) {
            errors += BoundaryError.OverlapsAdjacent(segment, next)
        }

        return errors
    }

    /**
     * Validates the cuts of one segment: each strictly inside (start < cut.start < cut.stop < stop),
     * sorted, non-overlapping and non-touching, and at least [MIN_KEPT_SECONDS] kept when
     * [sampleRate] is given. A segment without cuts always passes.
     */
    fun validateCuts(segment: TimelineEditedSegment, sampleRate: Int? = null): List<BoundaryError> {
        if (segment.cuts.isEmpty()) return emptyList()
        val errors = mutableListOf<BoundaryError>()
        for (cut in segment.cuts) {
            if (!(segment.startSample < cut.startSample && cut.startSample < cut.stopSample && cut.stopSample < segment.stopSample)) {
                errors += BoundaryError.CutOutsideSegment(segment, cut)
            }
        }
        for (i in 0 until segment.cuts.size - 1) {
            val a = segment.cuts[i]
            val b = segment.cuts[i + 1]
            if (a.stopSample >= b.startSample) errors += BoundaryError.CutsOverlap(segment, a, b)
        }
        if (sampleRate != null && errors.isEmpty()) {
            val kept = segment.keptSampleCount()
            val min = (MIN_KEPT_SECONDS * sampleRate).toLong()
            if (kept < min) errors += BoundaryError.KeptTooShort(segment, kept, min)
        }
        return errors
    }

    private fun validateOwnBounds(segment: TimelineEditedSegment, totalSamples: Long): List<BoundaryError> {
        val errors = mutableListOf<BoundaryError>()
        if (segment.startSample >= segment.stopSample) {
            errors += BoundaryError.Inverted(segment)
        }
        if (segment.startSample < 0 || segment.stopSample > totalSamples) {
            errors += BoundaryError.OutOfRange(segment, 0..totalSamples)
        }
        return errors
    }
}
