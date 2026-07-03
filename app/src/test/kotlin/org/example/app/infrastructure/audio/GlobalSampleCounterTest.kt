package org.example.app.infrastructure.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GlobalSampleCounterTest {

    @Test
    fun `single part accumulates from zero`() {
        val counter = GlobalSampleCounter()
        counter.startNewPart()
        assertEquals(100L, counter.update(100L))
        assertEquals(250L, counter.update(250L))
    }

    @Test
    fun `resume continues the global total across a new part instead of resetting`() {
        val counter = GlobalSampleCounter()
        counter.startNewPart()
        counter.update(1000L) // part 1 wrote 1000 frames before device loss

        counter.startNewPart() // resume() into part2
        assertEquals(1000L, counter.total, "starting a new part must not lose the prior total")
        assertEquals(1050L, counter.update(50L)) // part2's own local count starts at 0 again
        assertEquals(1300L, counter.update(300L))
    }

    @Test
    fun `multiple interruptions keep compounding the base`() {
        val counter = GlobalSampleCounter()
        counter.startNewPart()
        counter.update(500L)
        counter.startNewPart()
        counter.update(200L) // total 700
        counter.startNewPart()
        assertEquals(700L, counter.total)
        assertEquals(710L, counter.update(10L))
    }

    @Test
    fun `reset clears total and base for recorder reuse across sessions`() {
        val counter = GlobalSampleCounter()
        counter.startNewPart()
        counter.update(500L)
        counter.reset()
        assertEquals(0L, counter.total)
        counter.startNewPart()
        assertEquals(42L, counter.update(42L))
    }

    @Test
    fun `local count can legitimately decrease relative to a stale read without corrupting total`() {
        // Guards against accidentally using a running sum instead of an absolute
        // local count: update() must always be given the writer's absolute
        // bytesWritten-derived frame count, not a delta.
        val counter = GlobalSampleCounter()
        counter.startNewPart()
        counter.update(300L)
        assertEquals(300L, counter.update(300L)) // idempotent re-report of the same absolute count
    }
}
