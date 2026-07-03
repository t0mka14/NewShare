package org.example.app.domain.upload

import kotlinx.serialization.Serializable

/** One `upload_queue/queue.json.entries[]` row (§8.10). */
@Serializable
data class UploadQueueEntry(
    val sessionId: String,
    val attemptCount: Int,
    val nextAttemptAt: String? = null,
)

/** `upload_queue/queue.json` (§5.4, §8.10) — a derived index, rebuilt from every session's
 * `upload_status.json` at startup; if the two disagree, session metadata wins. */
@Serializable
data class UploadQueue(
    val version: Int = 1,
    val entries: List<UploadQueueEntry> = emptyList(),
)

/** One session's upload state, as needed to decide queue membership (§5.4). Backoff/
 * `nextAttemptAt` scheduling itself is the `UploadWorker`'s job (Phase 3); this only rebuilds
 * the on-disk index from already-known state. */
data class SessionUploadState(
    val sessionId: String,
    val status: UploadStatusValue,
    val attemptCount: Int,
    val nextAttemptAt: String? = null,
)

/**
 * Pure rebuild of `queue.json`'s content from each session's authoritative
 * `upload_status.json` (§5.4: "rebuilt from session metadata at startup"). A session is
 * queue-eligible while its status is `NotUploaded` or `Failed`; `Uploaded` (terminal) and
 * `Uploading` (a stale in-flight marker left by a crash — the next startup always restarts
 * from a clean scan, so it is treated the same as `Failed` for requeue purposes) are handled
 * as follows: `Uploading` is included too, since a crash mid-upload must not silently drop
 * the session from the retry queue.
 */
object UploadQueueIndexBuilder {
    fun build(sessions: List<SessionUploadState>): UploadQueue =
        UploadQueue(
            entries = sessions
                .filter { it.status != UploadStatusValue.Uploaded }
                .map { UploadQueueEntry(it.sessionId, it.attemptCount, it.nextAttemptAt) },
        )
}
