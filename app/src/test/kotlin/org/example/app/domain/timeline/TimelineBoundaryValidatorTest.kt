package org.example.app.domain.timeline

import org.junit.jupiter.api.Assertions.assertEquals
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

    // ---- cuts ----

    private fun cutSeg(start: Long, stop: Long, vararg cuts: Pair<Long, Long>) =
        TimelineEditedSegment(0, 1, start, stop, cuts.map { SampleRange(it.first, it.second) })

    private val rate = 1000 // 0.5 s == 500 samples

    @Test
    fun `valid cuts produce no errors`() {
        val seg = cutSeg(0, 5000, 1000L to 1500L, 2000L to 2500L)
        assertTrue(TimelineBoundaryValidator.validate(listOf(seg), 10_000, rate).isEmpty())
        assertTrue(TimelineBoundaryValidator.validateSingle(seg, null, null, 10_000, rate).isEmpty())
    }

    @Test
    fun `segment without cuts is unaffected even when sampleRate is given`() {
        assertTrue(TimelineBoundaryValidator.validate(listOf(segment(0, 100, 200)), 10_000, rate).isEmpty())
    }

    @Test
    fun `cut touching start or stop edge is rejected`() {
        assertTrue(TimelineBoundaryValidator.validateCuts(cutSeg(0, 5000, 0L to 100L), rate).any { it is BoundaryError.CutOutsideSegment })
        assertTrue(TimelineBoundaryValidator.validateCuts(cutSeg(0, 5000, 4900L to 5000L), rate).any { it is BoundaryError.CutOutsideSegment })
    }

    @Test
    fun `cut outside segment or inverted is rejected`() {
        assertTrue(TimelineBoundaryValidator.validateCuts(cutSeg(1000, 5000, 500L to 1500L), rate).any { it is BoundaryError.CutOutsideSegment })
        assertTrue(TimelineBoundaryValidator.validateCuts(cutSeg(0, 5000, 4000L to 6000L), rate).any { it is BoundaryError.CutOutsideSegment })
        assertTrue(TimelineBoundaryValidator.validateCuts(cutSeg(0, 5000, 2000L to 2000L), rate).any { it is BoundaryError.CutOutsideSegment })
        assertTrue(TimelineBoundaryValidator.validateCuts(cutSeg(0, 5000, 3000L to 2000L), rate).any { it is BoundaryError.CutOutsideSegment })
    }

    @Test
    fun `overlapping, touching and unsorted cuts are rejected`() {
        assertTrue(TimelineBoundaryValidator.validateCuts(cutSeg(0, 5000, 1000L to 2000L, 1500L to 2500L), rate).any { it is BoundaryError.CutsOverlap })
        assertTrue(TimelineBoundaryValidator.validateCuts(cutSeg(0, 5000, 1000L to 2000L, 2000L to 2500L), rate).any { it is BoundaryError.CutsOverlap })
        assertTrue(TimelineBoundaryValidator.validateCuts(cutSeg(0, 5000, 3000L to 3500L, 1000L to 1500L), rate).any { it is BoundaryError.CutsOverlap })
    }

    @Test
    fun `keeping less than 0_5 s is rejected, exactly 0_5 s is accepted`() {
        val tooShort = cutSeg(0, 600, 100L to 300L) // kept 400
        assertTrue(TimelineBoundaryValidator.validateCuts(tooShort, rate).single() is BoundaryError.KeptTooShort)
        val exact = cutSeg(0, 700, 100L to 300L) // kept 500
        assertTrue(TimelineBoundaryValidator.validateCuts(exact, rate).isEmpty())
        // without a sample rate the check is skipped
        assertTrue(TimelineBoundaryValidator.validateCuts(tooShort).isEmpty())
    }

    @Test
    fun `boundary dragged into or past a cut is rejected`() {
        val ok = cutSeg(1000, 5000, 2000L to 2500L)
        assertTrue(TimelineBoundaryValidator.validateSingle(ok, null, null, 10_000, rate).isEmpty())
        val cases = listOf(
            cutSeg(2200, 5000, 2000L to 2500L), // start inside cut
            cutSeg(3000, 5000, 2000L to 2500L), // start past cut
            cutSeg(1000, 2300, 2000L to 2500L), // stop inside cut
            cutSeg(1000, 1500, 2000L to 2500L), // stop before cut
        )
        for (s in cases) {
            assertTrue(
                TimelineBoundaryValidator.validateSingle(s, null, null, 10_000, rate).any { it is BoundaryError.CutOutsideSegment },
                "expected rejection for $s",
            )
        }
    }

    @Test
    fun `validate with cuts flags the same errors`() {
        val errors = TimelineBoundaryValidator.validate(listOf(cutSeg(0, 5000, 0L to 100L)), 10_000, rate)
        assertTrue(errors.any { it is BoundaryError.CutOutsideSegment })
    }

    // ---- keptRanges / keptSampleCount ----

    @Test
    fun `keptRanges without cuts is the whole segment`() {
        val seg = segment(0, 100, 200)
        assertEquals(listOf(100L..199L), seg.keptRanges())
        assertEquals(100L, seg.keptSampleCount())
    }

    @Test
    fun `keptRanges removes cuts`() {
        val seg = cutSeg(100, 1000, 200L to 300L, 500L to 600L)
        assertEquals(listOf(100L..199L, 300L..499L, 600L..999L), seg.keptRanges())
        assertEquals(900L - 200L, seg.keptSampleCount())
    }

    @Test
    fun `keptRanges tolerates unsorted, overlapping and edge cuts`() {
        val seg = cutSeg(100, 1000, 500L to 700L, 100L to 200L, 600L to 800L)
        assertEquals(listOf(200L..499L, 800L..999L), seg.keptRanges())
        assertEquals(listOf<LongRange>(), cutSeg(0, 100, 0L to 100L).keptRanges())
        assertEquals(0L, cutSeg(0, 100, 0L to 100L).keptSampleCount())
    }
}
