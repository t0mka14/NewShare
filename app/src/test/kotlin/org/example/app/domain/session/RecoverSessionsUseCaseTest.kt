package org.example.app.domain.session

import org.example.app.domain.audio.CaptureFormat
import org.example.app.domain.timeline.TimelineEvent
import org.example.app.domain.timeline.TimelineEventType
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeSessionRepository
import org.example.app.fakes.FakeTimelineRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Paths
import java.time.Instant

class RecoverSessionsUseCaseTest {

    private val format = CaptureFormat.PREFERRED

    private fun examination(sessionId: String, recovered: Boolean = false, captureFormat: CaptureFormat? = format) = Examination(
        sessionId = sessionId,
        installationId = "install-1",
        protocolName = "Share",
        configVersion = "1",
        startedAt = "2026-07-03T09:00:00Z",
        captureFormat = captureFormat,
        recovered = recovered,
    )

    private fun event(type: TimelineEventType, sampleOffset: Long?, taskIndex: Int? = 0, take: Int? = 1) = TimelineEvent(
        type = type, sampleOffset = sampleOffset, wallClock = "2026-07-03T09:0${take ?: 0}:00Z", taskIndex = taskIndex, repetition = 1, take = take,
    )

    private fun newUseCase(
        sessionRepository: FakeSessionRepository,
        timelineRepository: FakeTimelineRepository,
        clock: FakeClock = FakeClock(Instant.parse("2026-07-03T10:00:00Z")),
    ) = RecoverSessionsUseCase(sessionRepository, timelineRepository, clock)

    @Test
    fun `a cleanly compacted session with no partial file needs no recovery`() {
        val sessionRepository = FakeSessionRepository()
        val timelineRepository = FakeTimelineRepository()
        sessionRepository.createSessionDirectory("s1")
        sessionRepository.writeExamination("s1", examination("s1"))
        timelineRepository.writeOriginal(
            "s1",
            org.example.app.domain.timeline.TimelineOriginal(sessionId = "s1", sampleRate = 48_000, events = listOf(event(TimelineEventType.SESSION_RECORDING_STOPPED, 1000))),
        )

        val outcome = newUseCase(sessionRepository, timelineRepository).recoverOne("s1")

        assertTrue(outcome is RecoveryOutcome.NotRecovered)
        // writeExamination should not have been called again beyond the initial setup write.
        assertEquals(1, sessionRepository.examinationWrites.size)
    }

    @Test
    fun `crash with a partial master file and no clean stop compacts with a synthetic STOPPED`() {
        val sessionRepository = FakeSessionRepository()
        val timelineRepository = FakeTimelineRepository()
        sessionRepository.createSessionDirectory("s1")
        sessionRepository.writeExamination("s1", examination("s1"))
        timelineRepository.appendEvent("s1", event(TimelineEventType.SESSION_RECORDING_STARTED, 0, taskIndex = null, take = null))
        timelineRepository.appendEvent("s1", event(TimelineEventType.START_BUTTON_PRESSED, 500))
        val partialFile = Paths.get("fake-sessions-root/s1/master/session_master.partial.wav")
        sessionRepository.seedPartialMasterFile("s1", partialFile, frameCount = 2000L)

        val outcome = newUseCase(sessionRepository, timelineRepository).recoverOne("s1")

        assertTrue(outcome is RecoveryOutcome.Recovered)
        outcome as RecoveryOutcome.Recovered
        assertEquals("s1", outcome.sessionId)
        assertEquals(2000L, outcome.lastWrittenSample) // no interruption -> base offset 0 + 2000 frames

        val original = timelineRepository.readOriginal("s1")!!
        assertEquals(TimelineEventType.SESSION_RECORDING_STOPPED, original.events.last().type)
        assertEquals(2000L, original.events.last().sampleOffset)

        val updatedExamination = sessionRepository.readExamination("s1")!!
        assertTrue(updatedExamination.recovered)
    }

    @Test
    fun `base offset for the synthetic STOPPED accounts for a prior interruption`() {
        val sessionRepository = FakeSessionRepository()
        val timelineRepository = FakeTimelineRepository()
        sessionRepository.createSessionDirectory("s1")
        sessionRepository.writeExamination("s1", examination("s1"))
        timelineRepository.appendEvent("s1", event(TimelineEventType.RECORDING_INTERRUPTED, 10_000, taskIndex = null, take = null))
        timelineRepository.appendEvent("s1", event(TimelineEventType.RECORDING_RESUMED, 10_000, taskIndex = null, take = null))
        val partialFile = Paths.get("fake-sessions-root/s1/master/session_master.part2.partial.wav")
        sessionRepository.seedPartialMasterFile("s1", partialFile, frameCount = 500L)

        val outcome = newUseCase(sessionRepository, timelineRepository).recoverOne("s1") as RecoveryOutcome.Recovered

        assertEquals(10_500L, outcome.lastWrittenSample) // base 10_000 (from RESUMED) + 500 tail frames
    }

    @Test
    fun `a crashed part-2 partial file is reconstructed with the interruption's own format, not the session's top-level one`() {
        val sessionRepository = FakeSessionRepository()
        val timelineRepository = FakeTimelineRepository()
        sessionRepository.createSessionDirectory("s1")

        // Session started at 48kHz/16-bit/mono; device swap on interruption resumed at 44.1kHz.
        val part2Format = CaptureFormat(sampleRate = 44_100, bits = 16, channels = 1)
        sessionRepository.writeExamination(
            "s1",
            examination("s1").copy(
                interruptions = listOf(
                    Interruption(
                        sampleOffset = 10_000,
                        start = "2026-07-03T09:05:00Z",
                        end = "2026-07-03T09:05:10Z",
                        oldDevice = "old-mic",
                        newDevice = "new-mic",
                        partFile = "session_master.part2.wav",
                        captureFormat = part2Format,
                    ),
                ),
            ),
        )
        timelineRepository.appendEvent("s1", event(TimelineEventType.RECORDING_INTERRUPTED, 10_000, taskIndex = null, take = null))
        timelineRepository.appendEvent("s1", event(TimelineEventType.RECORDING_RESUMED, 10_000, taskIndex = null, take = null))
        val partialFile = Paths.get("fake-sessions-root/s1/master/session_master.part2.partial.wav")
        sessionRepository.seedPartialMasterFile("s1", partialFile, frameCount = 300L)

        newUseCase(sessionRepository, timelineRepository).recoverOne("s1")

        assertEquals(part2Format, sessionRepository.finalizeCalls[partialFile])
    }

    @Test
    fun `uncompacted log with no partial file uses the latest finalized master part's frame count`() {
        val sessionRepository = FakeSessionRepository()
        val timelineRepository = FakeTimelineRepository()
        sessionRepository.createSessionDirectory("s1")
        sessionRepository.writeExamination("s1", examination("s1"))
        timelineRepository.appendEvent("s1", event(TimelineEventType.START_BUTTON_PRESSED, 100))
        sessionRepository.seedFinalizedMasterPartFrames("s1", 3000L)

        val outcome = newUseCase(sessionRepository, timelineRepository).recoverOne("s1") as RecoveryOutcome.Recovered

        assertEquals(3000L, outcome.lastWrittenSample)
    }

    @Test
    fun `an uncompacted log that already ends with a clean STOPPED event compacts without a synthetic one`() {
        val sessionRepository = FakeSessionRepository()
        val timelineRepository = FakeTimelineRepository()
        sessionRepository.createSessionDirectory("s1")
        sessionRepository.writeExamination("s1", examination("s1"))
        timelineRepository.appendEvent("s1", event(TimelineEventType.SESSION_RECORDING_STOPPED, 777, taskIndex = null, take = null))

        val outcome = newUseCase(sessionRepository, timelineRepository).recoverOne("s1") as RecoveryOutcome.Recovered

        assertEquals(777L, outcome.lastWrittenSample)
        val updatedExamination = sessionRepository.readExamination("s1")!!
        assertTrue(updatedExamination.recovered)
    }

    @Test
    fun `missing examination_json is reported as corrupt metadata, never thrown`() {
        val sessionRepository = FakeSessionRepository()
        val timelineRepository = FakeTimelineRepository()
        sessionRepository.createSessionDirectory("s1")
        timelineRepository.appendEvent("s1", event(TimelineEventType.START_BUTTON_PRESSED, 100))

        val outcome = newUseCase(sessionRepository, timelineRepository).recoverOne("s1")

        assertTrue(outcome is RecoveryOutcome.Failed)
        assertTrue((outcome as RecoveryOutcome.Failed).error is StorageError.CorruptSessionMetadata)
    }

    @Test
    fun `recoverAll processes every known session folder`() {
        val sessionRepository = FakeSessionRepository()
        val timelineRepository = FakeTimelineRepository()
        sessionRepository.createSessionDirectory("s1")
        sessionRepository.writeExamination("s1", examination("s1"))
        timelineRepository.writeOriginal("s1", org.example.app.domain.timeline.TimelineOriginal(sessionId = "s1", sampleRate = 48_000, events = emptyList()))

        sessionRepository.createSessionDirectory("s2")
        sessionRepository.writeExamination("s2", examination("s2"))
        timelineRepository.appendEvent("s2", event(TimelineEventType.SESSION_RECORDING_STOPPED, 42, taskIndex = null, take = null))

        val outcomes = newUseCase(sessionRepository, timelineRepository).recoverAll()

        assertEquals(2, outcomes.size)
        assertTrue(outcomes.any { it is RecoveryOutcome.NotRecovered })
        assertTrue(outcomes.any { it is RecoveryOutcome.Recovered })
    }
}
