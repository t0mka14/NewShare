package org.example.app.domain.timeline

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Thrown when a JSONL line other than the final one fails to parse (§8.4: only the *last*
 * line of a crash-interrupted log is expected to be torn). */
class TimelineCorruptionException(lineNumber: Int, cause: Throwable) :
    Exception("Malformed timeline event on line $lineNumber", cause)

data class TimelineParseResult(
    val events: List<TimelineEvent>,
    /** True if the final non-blank line failed to parse and was dropped (power-loss torn write, §8.4). */
    val tornLastLine: Boolean,
)

/**
 * (De)serializes `timeline.events.jsonl` lines (§8.3): one [TimelineEvent] JSON object per
 * line, appended and `force()`d after every event by the writer (I/O is Phase 2 — this codec
 * is pure string <-> model). Decoding tolerates a torn final line (§8.4 recovery).
 */
object TimelineEventCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeLine(event: TimelineEvent): String = json.encodeToString(TimelineEvent.serializer(), event)

    /**
     * Parses newline-delimited JSON events. If the last non-blank line fails to parse, it is
     * dropped and [TimelineParseResult.tornLastLine] is set — this is the expected shape of a
     * log truncated mid-write by a crash/power loss. A malformed line anywhere else indicates
     * unexpected corruption and throws [TimelineCorruptionException].
     */
    fun decodeLines(content: String): TimelineParseResult {
        val lines = content.split('\n').filter { it.isNotBlank() }
        val events = mutableListOf<TimelineEvent>()
        var tornLastLine = false

        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            try {
                events += json.decodeFromString(TimelineEvent.serializer(), line)
            } catch (e: SerializationException) {
                if (index == lines.lastIndex) {
                    tornLastLine = true
                } else {
                    throw TimelineCorruptionException(index + 1, e)
                }
            }
        }

        return TimelineParseResult(events, tornLastLine)
    }
}
