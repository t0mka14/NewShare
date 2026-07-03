package org.example.app.domain.upload

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UploadQueueIndexBuilderTest {

    @Test
    fun `excludes terminal Uploaded sessions`() {
        val sessions = listOf(
            SessionUploadState("s1", UploadStatusValue.Uploaded, attemptCount = 1),
            SessionUploadState("s2", UploadStatusValue.NotUploaded, attemptCount = 0),
        )
        val queue = UploadQueueIndexBuilder.build(sessions)
        assertEquals(listOf("s2"), queue.entries.map { it.sessionId })
    }

    @Test
    fun `includes Failed and Uploading sessions so a crash mid-upload is never dropped`() {
        val sessions = listOf(
            SessionUploadState("s1", UploadStatusValue.Failed, attemptCount = 2, nextAttemptAt = "2026-07-04T00:00:00Z"),
            SessionUploadState("s2", UploadStatusValue.Uploading, attemptCount = 1),
        )
        val queue = UploadQueueIndexBuilder.build(sessions)
        assertEquals(setOf("s1", "s2"), queue.entries.map { it.sessionId }.toSet())
        val s1Entry = queue.entries.first { it.sessionId == "s1" }
        assertEquals(2, s1Entry.attemptCount)
        assertEquals("2026-07-04T00:00:00Z", s1Entry.nextAttemptAt)
    }

    @Test
    fun `empty session list yields an empty queue`() {
        val queue = UploadQueueIndexBuilder.build(emptyList())
        assertTrue(queue.entries.isEmpty())
    }
}
