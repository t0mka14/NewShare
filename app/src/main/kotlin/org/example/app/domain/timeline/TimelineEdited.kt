package org.example.app.domain.timeline

import kotlinx.serialization.Serializable

/**
 * `timeline_edited.json` (§8.7, §8.10) — segment-shaped, directly consumable by the cutter.
 * A segment is the last take of one VOCAL task instance. Written only if at least one
 * boundary was actually moved in the editor (§8.7); an untouched pass writes nothing.
 */
@Serializable
data class TimelineEditedSegment(
    val taskIndex: Int,
    val repetition: Int,
    val startSample: Long,
    val stopSample: Long,
)

@Serializable
data class TimelineEdited(
    val version: Int = 1,
    val sessionId: String,
    val sampleRate: Int,
    val basedOn: String = "original",
    val segments: List<TimelineEditedSegment>,
)
