package org.example.app.fakes

import org.example.app.domain.timeline.TimelineEdited
import org.example.app.domain.timeline.TimelineEvent
import org.example.app.domain.timeline.TimelineEventCodec
import org.example.app.domain.timeline.TimelineOriginal
import org.example.app.domain.timeline.TimelineParseResult
import org.example.app.domain.timeline.TimelineRepository

/**
 * In-memory [TimelineRepository]. Events are round-tripped through [TimelineEventCodec] (not
 * stored as live objects) so a test can still exercise torn-last-line handling by mutating
 * [rawEventLines] directly.
 */
class FakeTimelineRepository : TimelineRepository {
    private val eventLogs = mutableMapOf<String, MutableList<String>>()
    private val originals = mutableMapOf<String, TimelineOriginal>()
    private val editeds = mutableMapOf<String, TimelineEdited>()

    override fun appendEvent(folderName: String, event: TimelineEvent) {
        eventLogs.getOrPut(folderName) { mutableListOf() }.add(TimelineEventCodec.encodeLine(event))
    }

    /** Direct access to the raw JSONL lines for a session, e.g. to simulate a torn last
     * line by truncating the last entry. */
    fun rawEventLines(folderName: String): MutableList<String> = eventLogs.getOrPut(folderName) { mutableListOf() }

    override fun eventLogExists(folderName: String): Boolean = eventLogs.containsKey(folderName)

    override fun readEventLog(folderName: String): TimelineParseResult {
        val lines = eventLogs[folderName] ?: return TimelineParseResult(emptyList(), tornLastLine = false)
        return TimelineEventCodec.decodeLines(lines.joinToString("\n"))
    }

    override fun originalExists(folderName: String): Boolean = originals.containsKey(folderName)

    override fun writeOriginal(folderName: String, timeline: TimelineOriginal) {
        originals[folderName] = timeline
    }

    override fun readOriginal(folderName: String): TimelineOriginal? = originals[folderName]

    override fun editedExists(folderName: String): Boolean = editeds.containsKey(folderName)

    /** F-027: how many times the edited timeline was written / deleted, to prove Discard touches nothing. */
    var writeEditedCount = 0
        private set
    var deleteEditedCount = 0
        private set

    override fun writeEdited(folderName: String, timeline: TimelineEdited) {
        writeEditedCount++
        editeds[folderName] = timeline
    }

    override fun readEdited(folderName: String): TimelineEdited? = editeds[folderName]

    override fun deleteEdited(folderName: String) {
        deleteEditedCount++
        editeds.remove(folderName)
    }
}
