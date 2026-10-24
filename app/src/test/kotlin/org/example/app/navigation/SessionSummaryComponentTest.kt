package org.example.app.navigation

import org.example.app.domain.session.Examination
import org.example.app.domain.session.ProcessingInfo
import org.example.app.domain.session.ProcessingStatus
import org.example.app.domain.session.TaskRecord
import org.example.app.fakes.FakeSessionRepository
import org.example.app.navigation.SessionSummaryComponent.Result
import org.example.app.ui.sessionSummaryProcessedKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SessionSummaryComponentTest {
    private fun task(i: Int, clip: String?) = TaskRecord(taskIndex = i, type = "VOCAL", repetition = 1, clipFile = clip)

    private fun result(status: ProcessingStatus?, tasks: List<TaskRecord>): Result {
        val repo = FakeSessionRepository()
        repo.writeExamination(
            "f",
            Examination(
                sessionId = "s", installationId = "i", protocolName = "p", configVersion = "v", startedAt = "t",
                tasks = tasks, processing = status?.let { ProcessingInfo(status = it) },
            ),
        )
        return DefaultSessionSummaryComponent("f", repo).result
    }

    @Test
    fun `F-026 processed session counts clips`() {
        val r = result(ProcessingStatus.Done, listOf(task(0, "clips/a.wav"), task(1, "clips/b.wav"), task(2, null)))
        assertEquals(Result.ProcessedClips(2), r)
        assertEquals("sessionSummary.processed.other", sessionSummaryProcessedKey(r, "en"))
    }

    @Test
    fun `F-026 singular and Czech plural forms`() {
        val one = Result.ProcessedClips(1)
        assertEquals("sessionSummary.processed.one", sessionSummaryProcessedKey(one, "en"))
        assertEquals("sessionSummary.processed.one", sessionSummaryProcessedKey(one, "cs"))
        assertEquals("sessionSummary.processed.few", sessionSummaryProcessedKey(Result.ProcessedClips(3), "cs"))
        assertEquals("sessionSummary.processed.other", sessionSummaryProcessedKey(Result.ProcessedClips(3), "en"))
        assertEquals("sessionSummary.processed.other", sessionSummaryProcessedKey(Result.ProcessedClips(5), "cs"))
    }

    @Test
    fun `F-026 no-master session shows processed text without a count`() {
        val r = result(ProcessingStatus.Done, listOf(task(0, null)))
        assertEquals(Result.ProcessedNoClips, r)
        assertEquals("sessionSummary.processed", sessionSummaryProcessedKey(r, "en"))
    }

    @Test
    fun `F-026 non-Done session makes no processed claim`() {
        for (status in listOf(null, ProcessingStatus.NotProcessed, ProcessingStatus.Failed)) {
            val r = result(status, listOf(task(0, "clips/a.wav")))
            assertEquals(Result.NotProcessed, r)
            assertNull(sessionSummaryProcessedKey(r, "en"))
        }
    }
}
