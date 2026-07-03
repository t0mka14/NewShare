package org.example.app.domain.upload

/**
 * Persists per-session `metadata/upload_status.json` (the single authority, §5.4) and the
 * app-wide derived `upload_queue/queue.json` index. Both are atomic tmp+rename writes.
 */
interface UploadStatusRepository {
    fun read(folderName: String): UploadStatus?
    fun write(folderName: String, status: UploadStatus)

    fun readQueueIndex(): UploadQueue?
    fun writeQueueIndex(queue: UploadQueue)
}
