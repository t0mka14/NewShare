package org.example.app.domain.timeline

/**
 * Persistence for `timeline.events.jsonl`, `timeline_original.json`, and
 * `timeline_edited.json` (§8.3, §8.7, §8.10). Callers pass a session's folder name; the
 * implementation resolves paths via `AppDirectories`.
 *
 * [appendEvent] appends one line and `force()`s the channel per call (§8.3 — event rate is
 * low enough that per-event fsync cost is negligible). `timeline_original.json` is written
 * once, on clean stop or by `RecoverSessionsUseCase`, and is immutable afterward (§8.1);
 * `timeline_edited.json` is written only if the editor actually moved a boundary (§8.7).
 */
interface TimelineRepository {
    fun appendEvent(folderName: String, event: TimelineEvent)
    fun eventLogExists(folderName: String): Boolean

    /** Reads and decodes the full `timeline.events.jsonl`, tolerating a torn last line
     * (§8.4). Returns an empty, non-torn result if the log doesn't exist. */
    fun readEventLog(folderName: String): TimelineParseResult

    fun originalExists(folderName: String): Boolean
    fun writeOriginal(folderName: String, timeline: TimelineOriginal)
    fun readOriginal(folderName: String): TimelineOriginal?

    fun editedExists(folderName: String): Boolean
    fun writeEdited(folderName: String, timeline: TimelineEdited)
    fun readEdited(folderName: String): TimelineEdited?
}
