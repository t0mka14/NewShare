package org.example.app.domain.timeline

/**
 * Pure take-counting / last-take-selection functions used by `ProcessSessionUseCase` (§8.3,
 * §8.8) and, live, by the task screen state machine (§8.6) to number the next take. A "take"
 * is one Start->Stop attempt within a task instance; `Repeat` rejects the current take and
 * starts a new one. Only the last take of each instance is exported; earlier takes remain in
 * the master/timeline as an audit trail (§8.3).
 */
object TakeSelector {
    /**
     * The highest `take` number for which a `STOP_BUTTON_PRESSED` was recorded for
     * (taskIndex, repetition) — i.e. the take that Next implicitly accepted. `null` if the
     * instance has no completed take (e.g. it was skipped before ever starting).
     */
    fun lastTake(events: List<TimelineEvent>, taskIndex: Int, repetition: Int): Int? =
        completedTakeNumbers(events, taskIndex, repetition).maxOrNull()

    /** Number of distinct completed takes recorded for (taskIndex, repetition). */
    fun takeCount(events: List<TimelineEvent>, taskIndex: Int, repetition: Int): Int =
        completedTakeNumbers(events, taskIndex, repetition).distinct().size

    /** The take number to use for a new Start press: one past the highest take number seen
     * so far for this instance (start, stop, or reject), or 1 if none. */
    fun nextTakeNumber(events: List<TimelineEvent>, taskIndex: Int, repetition: Int): Int {
        val highestSoFar = events
            .asSequence()
            .filter { it.taskIndex == taskIndex && it.repetition == repetition }
            .mapNotNull { it.take }
            .maxOrNull() ?: 0
        return highestSoFar + 1
    }

    /** True if `take` was explicitly rejected (Repeat press or auto-reject, §8.5) rather than
     * accepted implicitly by Next. */
    fun isTakeRejected(events: List<TimelineEvent>, taskIndex: Int, repetition: Int, take: Int): Boolean =
        events.any {
            it.type == TimelineEventType.TAKE_REJECTED &&
                it.taskIndex == taskIndex && it.repetition == repetition && it.take == take
        }

    /**
     * The `[start, stop)` sample range of one take, from its START/STOP events. `null` if the
     * take never received a matching STOP event (still open, or the session ended mid-take).
     */
    fun takeSampleRange(events: List<TimelineEvent>, taskIndex: Int, repetition: Int, take: Int): LongRange? {
        val start = events.firstOrNull {
            it.type == TimelineEventType.START_BUTTON_PRESSED &&
                it.taskIndex == taskIndex && it.repetition == repetition && it.take == take
        }?.sampleOffset ?: return null

        val stop = events.firstOrNull {
            it.type == TimelineEventType.STOP_BUTTON_PRESSED &&
                it.taskIndex == taskIndex && it.repetition == repetition && it.take == take
        }?.sampleOffset ?: return null

        return start until stop
    }

    private fun completedTakeNumbers(events: List<TimelineEvent>, taskIndex: Int, repetition: Int): List<Int> =
        events
            .asSequence()
            .filter { it.type == TimelineEventType.STOP_BUTTON_PRESSED }
            .filter { it.taskIndex == taskIndex && it.repetition == repetition }
            .mapNotNull { it.take }
            .toList()
}
