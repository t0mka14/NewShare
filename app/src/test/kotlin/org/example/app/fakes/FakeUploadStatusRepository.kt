package org.example.app.fakes

import org.example.app.domain.upload.UploadStatus
import org.example.app.domain.upload.UploadStatusRepository

class FakeUploadStatusRepository : UploadStatusRepository {
    private val statuses = mutableMapOf<String, UploadStatus>()

    override fun read(folderName: String): UploadStatus? = statuses[folderName]

    override fun write(folderName: String, status: UploadStatus) {
        statuses[folderName] = status
    }
}
