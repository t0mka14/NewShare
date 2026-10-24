package org.example.app.domain.timeline

import kotlinx.serialization.Serializable
import java.time.Instant

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
 * `RecoverSessionsUseCase`. The synthetic events' `wallClock` is derived from the samples (F-054): the
 * last logged event with both a `wallClock` and a `sampleOffset`, plus the samples written since it;
 * [syntheticWallClock] (the recovery time) is only the fallback when no such event exists.
 * Any take still open at that point (a `START_BUTTON_PRESSED` with neither a
 * `STOP_BUTTON_PRESSED` nor a `TAKE_REJECTED`) is first closed at the same sample with a
 * `STOP_BUTTON_PRESSED` whose `reason` is [REASON_RECOVERED_INTERRUPTED], so it is cut as a normal
 * (partial) clip.
 */
object TimelineCompactor {
    /** `reason` of a `STOP_BUTTON_PRESSED` synthesized by recovery for a take open at the crash. */
    const val REASON_RECOVERED_INTERRUPTED = "RECOVERED_INTERRUPTED"

    fun compact(
        sessionId: String,
        sampleRate: Int,
        events: List<TimelineEvent>,
        lastWrittenSample: Long? = null,
        syntheticWallClock: String? = null,
    ): TimelineOriginal {
        val alreadyStopped = events.any { it.type == TimelineEventType.SESSION_RECORDING_STOPPED }
        val finalEvents = if (!alreadyStopped && lastWrittenSample != null && syntheticWallClock != null) {
            val wallClock = derivedWallClock(events, lastWrittenSample, sampleRate) ?: syntheticWallClock
            events + closeOpenTakes(events, lastWrittenSample, wallClock) + TimelineEvent(
                type = TimelineEventType.SESSION_RECORDING_STOPPED,
                sampleOffset = lastWrittenSample,
                wallClock = wallClock,
                taskIndex = null,
                repetition = null,
                take = null,
            )
        } else {
            events
        }
        return TimelineOriginal(sessionId = sessionId, sampleRate = sampleRate, events = finalEvents)
    }

    /** When [lastWrittenSample] was written, from the last event that pins a sample to a clock time; null if none does. */
    private fun derivedWallClock(events: List<TimelineEvent>, lastWrittenSample: Long, sampleRate: Int): String? {
        if (sampleRate <= 0) return null
        for (event in events.asReversed()) {
            val offset = event.sampleOffset ?: continue
            val at = runCatching { Instant.parse(event.wallClock) }.getOrNull() ?: continue
            val elapsedNanos = (lastWrittenSample - offset).coerceAtLeast(0) * 1_000_000_000L / sampleRate
            return at.plusNanos(elapsedNanos).toString()
        }
        return null
    }

    private fun closeOpenTakes(events: List<TimelineEvent>, lastWrittenSample: Long, wallClock: String): List<TimelineEvent> {
        fun key(e: TimelineEvent) = Triple(e.taskIndex, e.repetition, e.take)
        val ended = events
            .filter { it.type == TimelineEventType.STOP_BUTTON_PRESSED || it.type == TimelineEventType.TAKE_REJECTED }
            .map(::key)
            .toSet()
        return events
            .filter { it.type == TimelineEventType.START_BUTTON_PRESSED && it.taskIndex != null && key(it) !in ended }
            // A take that started at or after the last written sample holds no audio.
            .filter { (it.sampleOffset ?: Long.MAX_VALUE) < lastWrittenSample }
            .map {
                TimelineEvent(
                    type = TimelineEventType.STOP_BUTTON_PRESSED,
                    sampleOffset = lastWrittenSample,
                    wallClock = wallClock,
                    taskIndex = it.taskIndex,
                    repetition = it.repetition,
                    take = it.take,
                    reason = REASON_RECOVERED_INTERRUPTED,
                )
            }
    }
}
