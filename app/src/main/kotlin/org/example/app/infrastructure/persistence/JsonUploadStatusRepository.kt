package org.example.app.infrastructure.persistence

import kotlinx.serialization.json.Json
import org.example.app.domain.AppDirectories
import org.example.app.domain.upload.UploadQueue
import org.example.app.domain.upload.UploadStatus
import org.example.app.domain.upload.UploadStatusRepository
import java.nio.file.Path

/** Production [UploadStatusRepository]: per-session `metadata/upload_status.json` (§8.9) and
 * the app-wide `upload_queue/queue.json` derived index (§5.4). */
class JsonUploadStatusRepository(
    private val directories: AppDirectories,
) : UploadStatusRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private fun statusPath(folderName: String): Path =
        directories.sessionDir(folderName).resolve("metadata").resolve("upload_status.json")
    private val queuePath: Path get() = directories.uploadQueueDir.resolve("queue.json")

    override fun read(folderName: String): UploadStatus? =
        AtomicFileWriter.readStringOrNull(statusPath(folderName))
            ?.let { json.decodeFromString(UploadStatus.serializer(), it) }

    override fun write(folderName: String, status: UploadStatus) {
        AtomicFileWriter.writeString(statusPath(folderName), json.encodeToString(UploadStatus.serializer(), status))
    }

    override fun readQueueIndex(): UploadQueue? =
        AtomicFileWriter.readStringOrNull(queuePath)?.let { json.decodeFromString(UploadQueue.serializer(), it) }

    override fun writeQueueIndex(queue: UploadQueue) {
        AtomicFileWriter.writeString(queuePath, json.encodeToString(UploadQueue.serializer(), queue))
    }
}
