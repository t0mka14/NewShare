package org.example.app.domain.timeline

import kotlinx.serialization.Serializable

/** The 10 timeline event types (§8.3). */
@Serializable
enum class TimelineEventType {
    SESSION_RECORDING_STARTED,
    TASK_SCREEN_ENTERED,
    START_BUTTON_PRESSED,
    STOP_BUTTON_PRESSED,
    TAKE_REJECTED,
    TASK_SKIPPED,
    TASK_COMPLETED,
    RECORDING_INTERRUPTED,
    RECORDING_RESUMED,
    SESSION_RECORDING_STOPPED,
}

/**
 * One line of the append-only `timeline.events.jsonl` log (§8.3). `sampleOffset` (from
 * `writtenSamples` at event time) is the primary reference for cutting; it is `null` in
 * no-master sessions (§8.8). `wallClock` (ISO-8601 UTC) is metadata only. `taskIndex` is the
 * position in the expanded task-instance list (see [TaskInstanceExpander]); `take` is
 * 1-based, counted per task instance (§8.3).
 *
 * `reason` is not in the §8.3 schema sketch but is required to distinguish *why* a
 * `TAKE_REJECTED` event fired (explicit Repeat press vs. auto-reject on `DEVICE_LOST`, §8.5)
 * for the audit trail; it is `null` for every other event type. Flagged as an assumption in
 * the Phase 1 report pending lead confirmation.
 */
@Serializable
data class TimelineEvent(
    val type: TimelineEventType,
    val sampleOffset: Long?,
    val wallClock: String,
    val taskIndex: Int?,
    val repetition: Int?,
    val take: Int?,
    val reason: String? = null,
)
