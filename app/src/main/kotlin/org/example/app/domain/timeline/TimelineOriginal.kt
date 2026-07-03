package org.example.app.domain.timeline

import kotlinx.serialization.Serializable

/** `timeline_original.json` (§8.3, §8.10) — compacted, immutable once written (§8.1). */
@Serializable
data class TimelineOriginal(
    val version: Int = 1,
    val sessionId: String,
    val sampleRate: Int,
    val events: List<TimelineEvent>,
)

/**
 * Compacts an event list (from `timeline.events.jsonl`) into the [TimelineOriginal] shape
 * (§8.3). On a clean stop the log already ends with `SESSION_RECORDING_STOPPED` and
 * compaction is a straight wrap. During crash recovery (§8.4) the log may have no such event
 * (torn last line / abrupt termination); pass [lastWrittenSample] and [syntheticWallClock] to
 * append a synthetic `SESSION_RECORDING_STOPPED` at the last written sample, as required by
 * `RecoverSessionsUseCase`.
 */
object TimelineCompactor {
    fun compact(
        sessionId: String,
        sampleRate: Int,
        events: List<TimelineEvent>,
        lastWrittenSample: Long? = null,
        syntheticWallClock: String? = null,
    ): TimelineOriginal {
        val alreadyStopped = events.any { it.type == TimelineEventType.SESSION_RECORDING_STOPPED }
        val finalEvents = if (!alreadyStopped && lastWrittenSample != null && syntheticWallClock != null) {
            events + TimelineEvent(
                type = TimelineEventType.SESSION_RECORDING_STOPPED,
                sampleOffset = lastWrittenSample,
                wallClock = syntheticWallClock,
                taskIndex = null,
                repetition = null,
                take = null,
            )
        } else {
            events
        }
        return TimelineOriginal(sessionId = sessionId, sampleRate = sampleRate, events = finalEvents)
    }
}
