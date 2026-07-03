package org.example.app.domain.timeline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TakeSelectorTest {

    private fun start(taskIndex: Int, repetition: Int, take: Int, sample: Long) = TimelineEvent(
        type = TimelineEventType.START_BUTTON_PRESSED,
        sampleOffset = sample,
        wallClock = "2026-07-01T09:00:00Z",
        taskIndex = taskIndex,
        repetition = repetition,
        take = take,
    )

    private fun stop(taskIndex: Int, repetition: Int, take: Int, sample: Long) = TimelineEvent(
        type = TimelineEventType.STOP_BUTTON_PRESSED,
        sampleOffset = sample,
        wallClock = "2026-07-01T09:01:00Z",
        taskIndex = taskIndex,
        repetition = repetition,
        take = take,
    )

    private fun reject(taskIndex: Int, repetition: Int, take: Int, sample: Long) = TimelineEvent(
        type = TimelineEventType.TAKE_REJECTED,
        sampleOffset = sample,
        wallClock = "2026-07-01T09:01:30Z",
        taskIndex = taskIndex,
        repetition = repetition,
        take = take,
        reason = "REPEAT_PRESSED",
    )

    @Test
    fun `spec worked example - 2 repetitions tried 3 times each yields last take 3 for both`() {
        val events = mutableListOf<TimelineEvent>()
        var sample = 0L
        for (repetition in 1..2) {
            for (take in 1..3) {
                events += start(taskIndex = 0, repetition = repetition, take = take, sample = sample); sample += 100
                events += stop(taskIndex = 0, repetition = repetition, take = take, sample = sample); sample += 10
                if (take < 3) {
                    events += reject(taskIndex = 0, repetition = repetition, take = take, sample = sample)
                }
            }
        }

        assertEquals(3, TakeSelector.lastTake(events, taskIndex = 0, repetition = 1))
        assertEquals(3, TakeSelector.lastTake(events, taskIndex = 0, repetition = 2))
        assertEquals(3, TakeSelector.takeCount(events, taskIndex = 0, repetition = 1))
        assertEquals(3, TakeSelector.takeCount(events, taskIndex = 0, repetition = 2))

        // 6 takes total preserved across both instances (audit trail)
        val allTakeCount = (1..2).sumOf { TakeSelector.takeCount(events, taskIndex = 0, repetition = it) }
        assertEquals(6, allTakeCount)
    }

    @Test
    fun `lastTake is null when the instance was never started`() {
        assertNull(TakeSelector.lastTake(emptyList(), taskIndex = 0, repetition = 1))
    }

    @Test
    fun `nextTakeNumber is 1 for a fresh instance`() {
        assertEquals(1, TakeSelector.nextTakeNumber(emptyList(), taskIndex = 0, repetition = 1))
    }

    @Test
    fun `nextTakeNumber increments past the highest recorded take`() {
        val events = listOf(
            start(0, 1, 1, 0), stop(0, 1, 1, 100), reject(0, 1, 1, 100),
            start(0, 1, 2, 200), stop(0, 1, 2, 300),
        )
        assertEquals(3, TakeSelector.nextTakeNumber(events, taskIndex = 0, repetition = 1))
    }

    @Test
    fun `isTakeRejected distinguishes rejected takes from the accepted last take`() {
        val events = listOf(
            start(0, 1, 1, 0), stop(0, 1, 1, 100), reject(0, 1, 1, 100),
            start(0, 1, 2, 200), stop(0, 1, 2, 300),
        )
        assertTrue(TakeSelector.isTakeRejected(events, taskIndex = 0, repetition = 1, take = 1))
        assertFalse(TakeSelector.isTakeRejected(events, taskIndex = 0, repetition = 1, take = 2))
    }

    @Test
    fun `takeSampleRange returns the start-stop range of a specific take`() {
        val events = listOf(start(0, 1, 1, 1000), stop(0, 1, 1, 5000))
        assertEquals(1000L until 5000L, TakeSelector.takeSampleRange(events, taskIndex = 0, repetition = 1, take = 1))
    }

    @Test
    fun `takeSampleRange is null for an incomplete take with no STOP event`() {
        val events = listOf(start(0, 1, 1, 1000))
        assertNull(TakeSelector.takeSampleRange(events, taskIndex = 0, repetition = 1, take = 1))
    }

    @Test
    fun `different task instances do not interfere with each other`() {
        val events = listOf(
            start(0, 1, 1, 0), stop(0, 1, 1, 100),
            start(1, 1, 1, 200), stop(1, 1, 1, 300), reject(1, 1, 1, 300),
            start(1, 1, 2, 400), stop(1, 1, 2, 500),
        )
        assertEquals(1, TakeSelector.lastTake(events, taskIndex = 0, repetition = 1))
        assertEquals(2, TakeSelector.lastTake(events, taskIndex = 1, repetition = 1))
    }
}
