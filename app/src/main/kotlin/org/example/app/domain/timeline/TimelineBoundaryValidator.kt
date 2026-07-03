package org.example.app.domain.timeline

/** Editor boundary validation rules (§8.7): start < stop, within recorded range, no overlap
 * with adjacent segments' final boundaries. */
sealed interface BoundaryError {
    data class Inverted(val segment: TimelineEditedSegment) : BoundaryError
    data class OutOfRange(val segment: TimelineEditedSegment, val validRange: LongRange) : BoundaryError
    data class OverlapsAdjacent(val segment: TimelineEditedSegment, val neighbor: TimelineEditedSegment) : BoundaryError
}

object TimelineBoundaryValidator {
    /** Validates a full segment list, e.g. before persisting `timeline_edited.json`. */
    fun validate(segments: List<TimelineEditedSegment>, totalSamples: Long): List<BoundaryError> {
        val errors = mutableListOf<BoundaryError>()

        for (segment in segments) {
            errors += validateOwnBounds(segment, totalSamples)
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
    ): List<BoundaryError> {
        val errors = mutableListOf<BoundaryError>()
        errors += validateOwnBounds(segment, totalSamples)

        if (previous != null && segment.startSample < previous.stopSample) {
            errors += BoundaryError.OverlapsAdjacent(segment, previous)
        }
        if (next != null && segment.stopSample > next.startSample) {
            errors += BoundaryError.OverlapsAdjacent(segment, next)
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
