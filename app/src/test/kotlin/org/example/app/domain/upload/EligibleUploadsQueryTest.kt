package org.example.app.domain.upload

import org.example.app.domain.session.Examination
import org.example.app.fakes.FakeSessionRepository
import org.example.app.fakes.FakeUploadStatusRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EligibleUploadsQueryTest {

    private val sessionRepository = FakeSessionRepository()
    private val uploadStatusRepository = FakeUploadStatusRepository()
    private val query = EligibleUploadsQuery(sessionRepository, uploadStatusRepository)

    private fun seedSession(folderName: String, sessionId: String, withArchive: Boolean = true) {
        sessionRepository.createSessionDirectory(folderName)
        sessionRepository.writeExamination(
            folderName,
            Examination(
                sessionId = sessionId,
                installationId = "install1",
                protocolName = "Share",
                configVersion = "1",
                startedAt = "t",
            ),
        )
        if (withArchive) sessionRepository.seedArchiveExists(folderName)
    }

    @Test
    fun `a never-uploaded processed session is eligible with no failure reason`() {
        seedSession("2026-07-01_HC001_s1", "s1")

        val result = query.eligibleSessions()

        assertEquals(1, result.size)
        val row = result.single()
        assertEquals("2026-07-01_HC001_s1", row.folderName)
        assertEquals("s1", row.sessionId)
        assertEquals(UploadStatusValue.NotUploaded, row.status)
        assertEquals(null, row.failureReason)
    }

    @Test
    fun `a session without a processing archive is not eligible`() {
        seedSession("2026-07-01_HC001_s1", "s1", withArchive = false)

        assertTrue(query.eligibleSessions().isEmpty())
    }

    @Test
    fun `an Uploaded session is terminal and never listed`() {
        seedSession("2026-07-01_HC001_s1", "s1")
        uploadStatusRepository.write("2026-07-01_HC001_s1", UploadStatus(status = UploadStatusValue.Uploaded))

        assertTrue(query.eligibleSessions().isEmpty())
    }

    @Test
    fun `an Uploading leftover from a crash is listed as Failed with the interrupted reason — decision 35`() {
        seedSession("2026-07-01_HC001_s1", "s1")
        uploadStatusRepository.write(
            "2026-07-01_HC001_s1",
            UploadStatus(status = UploadStatusValue.Uploading, attemptCount = 1),
        )

        val row = query.eligibleSessions().single()

        assertEquals(UploadStatusValue.Failed, row.status)
        assertEquals(EligibleUploadsQuery.INTERRUPTED_REASON, row.failureReason)
    }

    @Test
    fun `a Failed session is eligible and surfaces the last attempt's outcome as the failure reason`() {
        seedSession("2026-07-01_HC001_s1", "s1")
        uploadStatusRepository.write(
            "2026-07-01_HC001_s1",
            UploadStatus(
                status = UploadStatusValue.Failed,
                attempts = listOf(
                    UploadAttempt(at = "2026-07-01T09:00:00Z", outcome = "NetworkFailure"),
                    UploadAttempt(at = "2026-07-01T09:05:00Z", outcome = "Rejected: unknown installation id"),
                ),
                attemptCount = 2,
            ),
        )

        val row = query.eligibleSessions().single()

        assertEquals(UploadStatusValue.Failed, row.status)
        assertEquals("Rejected: unknown installation id", row.failureReason)
    }

    @Test
    fun `multiple eligible sessions are all returned`() {
        seedSession("2026-07-01_HC001_s1", "s1")
        seedSession("2026-07-02_HC002_s2", "s2")
        uploadStatusRepository.write("2026-07-02_HC002_s2", UploadStatus(status = UploadStatusValue.Failed))

        val folderNames = query.eligibleSessions().map { it.folderName }.toSet()

        assertEquals(setOf("2026-07-01_HC001_s1", "2026-07-02_HC002_s2"), folderNames)
    }
}
