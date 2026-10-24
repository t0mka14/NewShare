package org.example.app.domain.session

import org.example.app.domain.timeline.TimelineEvent
import org.example.app.domain.timeline.TimelineEventType
import java.time.Instant

/**
 * F-034: whether a kept take's video ran for the whole take (§8.9: a camera that dies mid-take
 * stops only the video; the take and its audio stay).
 *
 * The signal is the `camera`/`capture_failed` entry in `examination.json` `hardware.events`
 * (written when the recorder reports Failed; a normal camera close is not recorded as one, F-032)
 * whose time falls inside the take's START..STOP wall-clock range in the original timeline.
 * Durations are never compared. Unparseable timestamps or a take without both markers count as
 * complete: no evidence of failure.
 */
object VideoCompleteness {
    fun isComplete(
        originalEvents: List<TimelineEvent>,
        hardwareEvents: List<HardwareEvent>,
        taskIndex: Int,
        repetition: Int,
        take: Int,
    ): Boolean {
        fun marker(type: TimelineEventType) = originalEvents.firstOrNull {
            it.type == type && it.taskIndex == taskIndex && it.repetition == repetition && it.take == take
        }?.wallClock?.let { runCatching { Instant.parse(it) }.getOrNull() }

        val start = marker(TimelineEventType.START_BUTTON_PRESSED) ?: return true
        val stop = marker(TimelineEventType.STOP_BUTTON_PRESSED) ?: return true
        return hardwareEvents.none { e ->
            e.device == "camera" && e.event == "capture_failed" &&
                runCatching { Instant.parse(e.at) }.getOrNull()?.let { it >= start && it <= stop } == true
        }
    }
}
