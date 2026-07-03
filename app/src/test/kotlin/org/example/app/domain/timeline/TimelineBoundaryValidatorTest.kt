package org.example.app.domain.timeline

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimelineBoundaryValidatorTest {

    private fun segment(taskIndex: Int, start: Long, stop: Long, repetition: Int = 1) =
        TimelineEditedSegment(taskIndex = taskIndex, repetition = repetition, startSample = start, stopSample = stop)

    @Test
    fun `valid non-overlapping segments produce no errors`() {
        val segments = listOf(segment(0, 0, 1000), segment(1, 1000, 2000))
        val errors = TimelineBoundaryValidator.validate(segments, totalSamples = 5000)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `start after stop is inverted`() {
        val errors = TimelineBoundaryValidator.validate(listOf(segment(0, 500, 100)), totalSamples = 5000)
        assertTrue(errors.any { it is BoundaryError.Inverted })
    }

    @Test
    fun `start equal to stop is inverted`() {
        val errors = TimelineBoundaryValidator.validate(listOf(segment(0, 500, 500)), totalSamples = 5000)
        assertTrue(errors.any { it is BoundaryError.Inverted })
    }

    @Test
    fun `negative start is out of range`() {
        val errors = TimelineBoundaryValidator.validate(listOf(segment(0, -10, 500)), totalSamples = 5000)
        assertTrue(errors.any { it is BoundaryError.OutOfRange })
    }

    @Test
    fun `stop beyond total samples is out of range`() {
        val errors = TimelineBoundaryValidator.validate(listOf(segment(0, 100, 6000)), totalSamples = 5000)
        assertTrue(errors.any { it is BoundaryError.OutOfRange })
    }

    @Test
    fun `overlapping adjacent segments are flagged`() {
        val segments = listOf(segment(0, 0, 1500), segment(1, 1000, 2000))
        val errors = TimelineBoundaryValidator.validate(segments, totalSamples = 5000)
        assertTrue(errors.any { it is BoundaryError.OverlapsAdjacent })
    }

    @Test
    fun `touching boundaries (stop equals next start) do not overlap`() {
        val segments = listOf(segment(0, 0, 1000), segment(1, 1000, 2000))
        val errors = TimelineBoundaryValidator.validate(segments, totalSamples = 5000)
        assertTrue(errors.none { it is BoundaryError.OverlapsAdjacent })
    }

    @Test
    fun `validateSingle checks a dragged segment against its neighbors only`() {
        val previous = segment(0, 0, 1000)
        val next = segment(2, 2000, 3000)
        val dragged = segment(1, 1500, 1800)

        val errors = TimelineBoundaryValidator.validateSingle(dragged, previous, next, totalSamples = 5000)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `validateSingle flags overlap with previous neighbor`() {
        val previous = segment(0, 0, 1000)
        val dragged = segment(1, 500, 1800)

        val errors = TimelineBoundaryValidator.validateSingle(dragged, previous, null, totalSamples = 5000)
        assertTrue(errors.any { it is BoundaryError.OverlapsAdjacent })
    }

    @Test
    fun `validateSingle flags overlap with next neighbor`() {
        val next = segment(2, 2000, 3000)
        val dragged = segment(1, 1500, 2500)

        val errors = TimelineBoundaryValidator.validateSingle(dragged, null, next, totalSamples = 5000)
        assertTrue(errors.any { it is BoundaryError.OverlapsAdjacent })
    }
}
