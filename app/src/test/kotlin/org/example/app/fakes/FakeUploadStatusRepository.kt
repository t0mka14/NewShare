package org.example.app.fakes

import org.example.app.domain.upload.UploadQueue
import org.example.app.domain.upload.UploadStatus
import org.example.app.domain.upload.UploadStatusRepository

class FakeUploadStatusRepository : UploadStatusRepository {
    private val statuses = mutableMapOf<String, UploadStatus>()
    private var queue: UploadQueue? = null

    override fun read(folderName: String): UploadStatus? = statuses[folderName]

    override fun write(folderName: String, status: UploadStatus) {
        statuses[folderName] = status
    }

    override fun readQueueIndex(): UploadQueue? = queue

    override fun writeQueueIndex(queue: UploadQueue) {
        this.queue = queue
    }
}
