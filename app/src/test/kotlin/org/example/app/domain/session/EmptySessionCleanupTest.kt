package org.example.app.domain.session

import org.example.app.domain.audio.CaptureFormat
import org.example.app.domain.timeline.TimelineEvent
import org.example.app.domain.timeline.TimelineEventType
import org.example.app.domain.timeline.TimelineOriginal
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeSessionRepository
import org.example.app.fakes.FakeTimelineRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Paths

/** F-047: the folder of a session that never recorded goes; anything that might hold data stays. */
class EmptySessionCleanupTest {

    private val sessions = FakeSessionRepository()
    private val timelines = FakeTimelineRepository()
    private val cleanup = EmptySessionCleanup(sessions, timelines)

    private fun examination(sessionId: String) = Examination(
        sessionId = sessionId,
        installationId = "inst",
        protocolName = "p",
        configVersion = "v1",
        startedAt = "2026-07-03T09:00:00Z",
    )

    private fun session(folder: String, exam: Examination = examination("id-$folder")) {
        sessions.createSessionDirectory(folder)
        sessions.writeExamination(folder, exam)
    }

    private fun event(type: TimelineEventType, sampleOffset: Long? = null, taskIndex: Int? = null) = TimelineEvent(
        type = type, sampleOffset = sampleOffset, wallClock = "2026-07-03T09:00:00Z",
        taskIndex = taskIndex, repetition = taskIndex?.let { 1 }, take = taskIndex?.let { 1 },
    )

    private fun partial(folder: String) = Paths.get("fake-sessions-root/$folder/master/session_master.partial.wav")

    private fun assertKept(folder: String) {
        assertEquals(emptyList<String>(), sessions.deletedSessions, "nothing may be deleted")
        assertTrue(folder in sessions.listSessionFolderNames())
    }

    @Test
    fun `a session with no master, no events and no records is deleted`() {
        session("empty")

        assertTrue(cleanup.deleteIfEmpty("empty"))

        assertEquals(listOf("empty"), sessions.deletedSessions)
        assertFalse("empty" in sessions.listSessionFolderNames())
    }

    @Test
    fun `entering a task screen alone does not make a session worth keeping`() {
        session("s")
        timelines.appendEvent("s", event(TimelineEventType.TASK_SCREEN_ENTERED, taskIndex = 0))

        assertTrue(cleanup.deleteIfEmpty("s"))
    }

    @Test
    fun `a header-only master is not audio`() {
        session("s")
        sessions.seedPartialMasterFile("s", partial("s"), frameCount = 0)

        assertTrue(cleanup.deleteIfEmpty("s"))
    }

    @Test
    fun `a session with master audio frames is never deleted`() {
        session("s")
        sessions.seedMasterAudio("s")

        assertFalse(cleanup.deleteIfEmpty("s"))
        assertKept("s")
    }

    @Test
    fun `a session with a leftover partial master holding frames is never deleted`() {
        session("s")
        sessions.seedPartialMasterFile("s", partial("s"), frameCount = 480)

        assertFalse(cleanup.deleteIfEmpty("s"))
        assertKept("s")
    }

    @Test
    fun `a session whose log has SESSION_RECORDING_STARTED is never deleted even without audio`() {
        session("s")
        timelines.appendEvent("s", event(TimelineEventType.SESSION_RECORDING_STARTED, sampleOffset = 0))

        assertFalse(cleanup.deleteIfEmpty("s"))
        assertKept("s")
    }

    @Test
    fun `a session with a take event is never deleted even without audio`() {
        session("s")
        timelines.appendEvent("s", event(TimelineEventType.START_BUTTON_PRESSED, taskIndex = 0))

        assertFalse(cleanup.deleteIfEmpty("s"))
        assertKept("s")
    }

    @Test
    fun `a session with a timeline_original is never deleted`() {
        session("s")
        timelines.writeOriginal("s", TimelineOriginal(sessionId = "id-s", sampleRate = 48_000, events = emptyList()))

        assertFalse(cleanup.deleteIfEmpty("s"))
        assertKept("s")
    }

    @Test
    fun `a torn last log line keeps the session`() {
        session("s")
        timelines.appendEvent("s", event(TimelineEventType.TASK_SCREEN_ENTERED, taskIndex = 0))
        timelines.rawEventLines("s").add("""{"type":"START_BUT""")

        assertFalse(cleanup.deleteIfEmpty("s"))
        assertKept("s")
    }

    @Test
    fun `recovered, finished or archived sessions are never deleted`() {
        session("recovered", examination("a").copy(recovered = true))
        session("ended", examination("b").copy(endedAt = "2026-07-03T09:10:00Z"))
        session("tasks", examination("c").copy(tasks = listOf(TaskRecord(0, "INFO", null, 1, 1))))
        session("archived", examination("d"))
        sessions.seedArchiveExists("archived")

        assertEquals(0, cleanup.sweep(listOf("recovered", "ended", "tasks", "archived")))
        assertEquals(emptyList<String>(), sessions.deletedSessions)
    }

    @Test
    fun `the active session is never deleted`() {
        session("active")

        assertFalse(cleanup.deleteIfEmpty("active", active = "active"))
        assertKept("active")
    }

    @Test
    fun `a session whose examination cannot be read is kept`() {
        sessions.createSessionDirectory("noexam")

        assertFalse(cleanup.deleteIfEmpty("noexam"))
        assertKept("noexam")
    }

    @Test
    fun `a failing check keeps the session and does not throw`() {
        session("s")
        val broken = EmptySessionCleanup(
            object : SessionRepository by sessions {
                override fun hasMasterAudio(folderName: String): Boolean = error("disk gone")
            },
            timelines,
        )

        assertFalse(broken.deleteIfEmpty("s"))
        assertKept("s")
    }

    @Test
    fun `sweep deletes only the empty sessions and counts them`() {
        session("empty1")
        session("empty2")
        session("audio")
        sessions.seedMasterAudio("audio")

        assertEquals(2, cleanup.sweep(listOf("empty1", "empty2", "audio")))
        assertEquals(listOf("audio"), sessions.listSessionFolderNames())
    }

    @Test
    fun `startup recovers a crashed session first and then removes only the empty one`() {
        session("crashed", examination("id-crashed").copy(captureFormat = CaptureFormat.PREFERRED))
        timelines.appendEvent("crashed", event(TimelineEventType.SESSION_RECORDING_STARTED, sampleOffset = 0))
        sessions.seedPartialMasterFile("crashed", partial("crashed"), frameCount = 96_000)
        session("empty")
        val startup = StartupRecovery(RecoverSessionsUseCase(sessions, timelines, FakeClock()), sessions, cleanup)

        startup.run()

        assertEquals(listOf("empty"), sessions.deletedSessions)
        assertEquals(listOf("crashed"), sessions.listSessionFolderNames())
        assertTrue(sessions.readExamination("crashed")!!.recovered)
        assertEquals(1, startup.pending.value!!.recovered.size)
    }

    @Test
    fun `a recovered session is not a candidate even if its master is header-only`() {
        session("crashed", examination("id-crashed").copy(captureFormat = CaptureFormat.PREFERRED))
        timelines.appendEvent("crashed", event(TimelineEventType.SESSION_RECORDING_STARTED, sampleOffset = 0))
        sessions.seedPartialMasterFile("crashed", partial("crashed"), frameCount = 0)
        val startup = StartupRecovery(RecoverSessionsUseCase(sessions, timelines, FakeClock()), sessions, cleanup)

        startup.run()

        assertEquals(emptyList<String>(), sessions.deletedSessions)
        assertEquals(listOf("crashed"), sessions.listSessionFolderNames())
    }
}
