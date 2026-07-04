package org.example.app.infrastructure.persistence

import kotlinx.serialization.json.Json
import org.example.app.domain.AppDirectories
import org.example.app.domain.upload.UploadStatus
import org.example.app.domain.upload.UploadStatusRepository
import java.nio.file.Path

/** Production [UploadStatusRepository]: per-session `metadata/upload_status.json` (§8.9).
 * Upload is manual-only (§13 decision 34) — there is no queue index to persist. */
class JsonUploadStatusRepository(
    private val directories: AppDirectories,
) : UploadStatusRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private fun statusPath(folderName: String): Path =
        directories.sessionDir(folderName).resolve("metadata").resolve("upload_status.json")

    override fun read(folderName: String): UploadStatus? =
        AtomicFileWriter.readStringOrNull(statusPath(folderName))
            ?.let { json.decodeFromString(UploadStatus.serializer(), it) }

    override fun write(folderName: String, status: UploadStatus) {
        AtomicFileWriter.writeString(statusPath(folderName), json.encodeToString(UploadStatus.serializer(), status))
    }
}
