package org.example.app.domain.session

import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeSessionRepository
import org.example.app.fakes.FakeTimelineRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StartupRecoveryTest {

    private val sessions = FakeSessionRepository()
    private val recovery = StartupRecovery(RecoverSessionsUseCase(sessions, FakeTimelineRepository(), FakeClock()), sessions)

    private fun examination(sessionId: String, startedAt: String) = Examination(
        sessionId = sessionId,
        installationId = "inst",
        protocolName = "p",
        configVersion = "v1",
        startedAt = startedAt,
    )

    @Test
    fun `NotRecovered and an empty scan produce no summary`() {
        assertNull(StartupRecovery.report(emptyList()) { error("unused") })
        assertNull(StartupRecovery.report(listOf(RecoveryOutcome.NotRecovered("a"))) { error("unused") })
    }

    @Test
    fun `recovered and failed sessions are listed separately with patient code and start time`() {
        sessions.writeExamination("2026-10-23_P01_s1", examination("s1", "2026-10-23T09:15:00Z"))
        recovery.publish(
            listOf(
                RecoveryOutcome.NotRecovered("2026-10-22_P00_s0"),
                RecoveryOutcome.Recovered("2026-10-23_P01_s1", "s1", 1000L),
                RecoveryOutcome.Failed("broken-folder", StorageError.CorruptSessionMetadata("x")),
            ),
        )

        val summary = recovery.pending.value!!
        assertEquals(
            listOf(StartupRecovery.Entry("2026-10-23_P01_s1", "P01", "2026-10-23T09:15:00Z")),
            summary.recovered,
        )
        // Unreadable examination.json falls back to the folder name only.
        assertEquals(listOf(StartupRecovery.Entry("broken-folder", null, null)), summary.failed)
    }

    @Test
    fun `dismiss clears the pending summary`() {
        recovery.publish(listOf(RecoveryOutcome.Recovered("f", "s", null)))
        recovery.dismiss()
        assertNull(recovery.pending.value)
    }

    @Test
    fun `a scan that throws leaves nothing pending and does not propagate`() {
        val broken = StartupRecovery(
            RecoverSessionsUseCase(
                object : SessionRepository by sessions {
                    override fun listSessionFolderNames(): List<String> = error("disk gone")
                },
                FakeTimelineRepository(),
                FakeClock(),
            ),
            sessions,
        )
        broken.run()
        assertNull(broken.pending.value)
    }
}
