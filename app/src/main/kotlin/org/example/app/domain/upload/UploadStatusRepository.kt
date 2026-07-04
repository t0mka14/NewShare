package org.example.app.domain.upload

/**
 * Persists per-session `metadata/upload_status.json` (the single authority, §5.4) — an
 * atomic tmp+rename write. There is no separate queue index: upload is manual-only (§13
 * decision 34) and the upload screen's session list is computed on demand from session
 * metadata (see `EligibleUploadsQuery`).
 */
interface UploadStatusRepository {
    fun read(folderName: String): UploadStatus?
    fun write(folderName: String, status: UploadStatus)
}
