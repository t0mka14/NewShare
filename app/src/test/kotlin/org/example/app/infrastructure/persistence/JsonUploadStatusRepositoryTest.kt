package org.example.app.infrastructure.persistence

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.example.app.domain.upload.UploadAttempt
import org.example.app.domain.upload.UploadQueue
import org.example.app.domain.upload.UploadQueueEntry
import org.example.app.domain.upload.UploadStatus
import org.example.app.domain.upload.UploadStatusValue
import org.example.app.fakes.TestAppDirectories
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class JsonUploadStatusRepositoryTest {

    @Test
    fun `per-session status round-trips including serverResponse and attempts`(@TempDir tempDir: Path) {
        val repository = JsonUploadStatusRepository(TestAppDirectories(tempDir))
        assertNull(repository.read("s1"))

        val status = UploadStatus(
            status = UploadStatusValue.Uploaded,
            zipSha256 = "deadbeef",
            uploadedAt = "2026-07-03T10:00:00Z",
            serverResponse = buildJsonObject { put("ok", JsonPrimitive(true)) },
            attempts = listOf(UploadAttempt(at = "2026-07-03T09:59:00Z", outcome = "success")),
            attemptCount = 1,
        )
        repository.write("s1", status)

        assertEquals(status, repository.read("s1"))
    }

    @Test
    fun `queue index round-trips independently of per-session status`(@TempDir tempDir: Path) {
        val repository = JsonUploadStatusRepository(TestAppDirectories(tempDir))
        assertNull(repository.readQueueIndex())

        val queue = UploadQueue(entries = listOf(UploadQueueEntry("s1", attemptCount = 2, nextAttemptAt = "2026-07-04T00:00:00Z")))
        repository.writeQueueIndex(queue)

        assertEquals(queue, repository.readQueueIndex())
    }
}
