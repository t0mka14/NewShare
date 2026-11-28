package org.example.app.infrastructure.persistence

import org.example.app.domain.timeline.SampleRange
import org.example.app.domain.timeline.TimelineEdited
import org.example.app.domain.timeline.TimelineEditedSegment
import org.example.app.domain.timeline.TimelineEvent
import org.example.app.domain.timeline.TimelineEventType
import org.example.app.domain.timeline.TimelineOriginal
import org.example.app.fakes.TestAppDirectories
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class JsonTimelineRepositoryTest {

    private fun repo(tempDir: Path) = JsonTimelineRepository(TestAppDirectories(tempDir))

    private fun event(take: Int, type: TimelineEventType = TimelineEventType.START_BUTTON_PRESSED, sampleOffset: Long? = 100L) =
        TimelineEvent(type = type, sampleOffset = sampleOffset, wallClock = "2026-07-03T09:00:0${take}Z", taskIndex = 0, repetition = 1, take = take)

    @Test
    fun `appendEvent persists one JSON object per line, in order`(@TempDir tempDir: Path) {
        val repository = repo(tempDir)
        repository.appendEvent("s1", event(1))
        repository.appendEvent("s1", event(2))

        assertTrue(repository.eventLogExists("s1"))
        val result = repository.readEventLog("s1")
        assertEquals(listOf(event(1), event(2)), result.events)
        assertFalse(result.tornLastLine)
    }

    @Test
    fun `readEventLog on a missing file returns an empty, non-torn result`(@TempDir tempDir: Path) {
        val repository = repo(tempDir)
        assertFalse(repository.eventLogExists("s1"))
        val result = repository.readEventLog("s1")
        assertTrue(result.events.isEmpty())
        assertFalse(result.tornLastLine)
    }

    @Test
    fun `tolerates a torn final line written by a real crash simulation`(@TempDir tempDir: Path) {
        val repository = repo(tempDir)
        repository.appendEvent("s1", event(1))

        // Simulate a crash mid-write of the second line: append a truncated JSON line
        // directly, bypassing appendEvent's atomic-ish full-line write.
        val path = TestAppDirectories(tempDir).sessionDir("s1").resolve("timeline.events.jsonl")
        Files.write(path, "\n{\"type\":\"START_BUTTON_PRESSED\",\"samp".toByteArray(StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.APPEND)

        val result = repository.readEventLog("s1")
        assertEquals(listOf(event(1)), result.events)
        assertTrue(result.tornLastLine)
    }

    @Test
    fun `timeline_original round-trips and is reported present only after writing`(@TempDir tempDir: Path) {
        val repository = repo(tempDir)
        assertFalse(repository.originalExists("s1"))
        assertNull(repository.readOriginal("s1"))

        val original = TimelineOriginal(sessionId = "s1", sampleRate = 48_000, events = listOf(event(1), event(2)))
        repository.writeOriginal("s1", original)

        assertTrue(repository.originalExists("s1"))
        assertEquals(original, repository.readOriginal("s1"))
    }

    @Test
    fun `timeline_edited round-trips and is reported present only after writing`(@TempDir tempDir: Path) {
        val repository = repo(tempDir)
        assertFalse(repository.editedExists("s1"))

        val edited = TimelineEdited(
            sessionId = "s1",
            sampleRate = 48_000,
            segments = listOf(TimelineEditedSegment(taskIndex = 0, repetition = 1, startSample = 1000, stopSample = 5000)),
        )
        repository.writeEdited("s1", edited)

        assertTrue(repository.editedExists("s1"))
        assertEquals(edited, repository.readEdited("s1"))
    }

    private fun editedPath(tempDir: Path) =
        TestAppDirectories(tempDir).sessionDir("s1").resolve("timeline_edited.json")

    private val noCutsJson =
        """{"sessionId":"s1","sampleRate":48000,"segments":[{"taskIndex":0,"repetition":1,"startSample":1000,"stopSample":5000}]}"""

    @Test
    fun `timeline_edited with cuts round-trips and writes the cuts key`(@TempDir tempDir: Path) {
        val repository = repo(tempDir)
        val edited = TimelineEdited(
            sessionId = "s1",
            sampleRate = 48_000,
            segments = listOf(
                TimelineEditedSegment(
                    0, 1, 1000, 90_000,
                    cuts = listOf(SampleRange(2000, 3000), SampleRange(5000, 6000)),
                ),
            ),
        )
        repository.writeEdited("s1", edited)
        assertEquals(edited, repository.readEdited("s1"))
        val text = String(Files.readAllBytes(editedPath(tempDir)), StandardCharsets.UTF_8)
        assertTrue(
            text.contains(""""cuts":[{"startSample":2000,"stopSample":3000},{"startSample":5000,"stopSample":6000}]"""),
            text,
        )
    }

    @Test
    fun `timeline_edited without cuts has no cuts key and is unchanged`(@TempDir tempDir: Path) {
        val repository = repo(tempDir)
        repository.writeEdited(
            "s1",
            TimelineEdited(sessionId = "s1", sampleRate = 48_000, segments = listOf(TimelineEditedSegment(0, 1, 1000, 5000))),
        )
        val text = String(Files.readAllBytes(editedPath(tempDir)), StandardCharsets.UTF_8)
        assertFalse(text.contains("cuts"), text)
        assertEquals(noCutsJson, text.trim())
    }

    @Test
    fun `old timeline_edited json without the cuts field reads as empty cuts`(@TempDir tempDir: Path) {
        val repository = repo(tempDir)
        val path = editedPath(tempDir)
        Files.createDirectories(path.parent)
        Files.write(path, noCutsJson.toByteArray(StandardCharsets.UTF_8))
        val read = repository.readEdited("s1")!!
        assertTrue(read.segments.single().cuts.isEmpty())
    }

    @Test
    fun `null sampleOffset round-trips for no-master sessions`(@TempDir tempDir: Path) {
        val repository = repo(tempDir)
        repository.appendEvent("s1", event(1, sampleOffset = null))
        assertEquals(listOf(event(1, sampleOffset = null)), repository.readEventLog("s1").events)
    }
}
