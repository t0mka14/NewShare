package org.example.app.infrastructure.persistence

import org.example.app.domain.audio.CaptureFormat
import org.example.app.domain.session.Examination
import org.example.app.domain.session.ParticipantRecord
import org.example.app.domain.session.ProcessingInfo
import org.example.app.domain.session.ProcessingStatus
import org.example.app.domain.session.SessionFolderNaming
import org.example.app.fakes.TestAppDirectories
import org.example.app.infrastructure.audio.WavFileWriter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.random.Random

class JsonSessionRepositoryTest {

    private val format = CaptureFormat(sampleRate = 8_000, bits = 16, channels = 1)

    private fun repo(tempDir: Path) = JsonSessionRepository(TestAppDirectories(tempDir))

    @Test
    fun `createSessionDirectory creates the full §8_2 subtree`(@TempDir tempDir: Path) {
        val repository = repo(tempDir)
        val dir = repository.createSessionDirectory("2026-07-03_HC001_s1")

        assertTrue(Files.isDirectory(dir))
        assertTrue(Files.isDirectory(dir.resolve("master")))
        assertTrue(Files.isDirectory(dir.resolve("clips")))
        assertTrue(Files.isDirectory(dir.resolve("metadata")))
        assertTrue(Files.isDirectory(dir.resolve("waveform_cache")))
    }

    @Test
    fun `participant round-trips and is missing before it's written`(@TempDir tempDir: Path) {
        val repository = repo(tempDir)
        repository.createSessionDirectory("s1")
        assertNull(repository.readParticipant("s1"))

        val participant = ParticipantRecord(fields = mapOf("code" to "HC001", "sex" to "F"), createdAt = "2026-07-03T09:00:00Z")
        repository.writeParticipant("s1", participant)

        assertEquals(participant, repository.readParticipant("s1"))
    }

    @Test
    fun `examination round-trips including nested captureFormat and tasks`(@TempDir tempDir: Path) {
        val repository = repo(tempDir)
        repository.createSessionDirectory("s1")

        val examination = Examination(
            sessionId = "s1",
            installationId = "install-1",
            protocolName = "Share",
            configVersion = "2026-07-01.1",
            startedAt = "2026-07-03T09:00:00Z",
            captureFormat = format,
            processing = ProcessingInfo(status = ProcessingStatus.Done, processedAt = "2026-07-03T09:30:00Z", timelineUsed = "original"),
        )
        repository.writeExamination("s1", examination)

        assertEquals(examination, repository.readExamination("s1"))
    }

    @Test
    fun `examination write is atomic — no tmp file survives`(@TempDir tempDir: Path) {
        val repository = repo(tempDir)
        repository.createSessionDirectory("s1")
        repository.writeExamination(
            "s1",
            Examination(sessionId = "s1", installationId = "i", protocolName = "p", configVersion = "1", startedAt = "t"),
        )
        val tmp = repository.sessionDir("s1").resolve("examination.json.tmp")
        assertFalse(Files.exists(tmp))
    }

    @Test
    fun `config snapshot is persisted byte-for-byte, not re-encoded`(@TempDir tempDir: Path) {
        val repository = repo(tempDir)
        repository.createSessionDirectory("s1")
        val raw = """{"schemaVersion":1,   "weird"  :"spacing"}"""
        repository.writeConfigSnapshot("s1", raw)
        assertEquals(raw, repository.readConfigSnapshot("s1"))
    }

    @Test
    fun `listSessions derives patientCode from the folder name and skips corrupt sessions`(@TempDir tempDir: Path) {
        val repository = repo(tempDir)

        val folder1 = SessionFolderNaming.build(LocalDate.of(2026, 7, 3), "HC001", "s1")
        repository.createSessionDirectory(folder1)
        repository.writeExamination(
            folder1,
            Examination(sessionId = "s1", installationId = "i", protocolName = "p", configVersion = "1", startedAt = "t"),
        )

        // Corrupt session: directory exists but examination.json was never written.
        repository.createSessionDirectory("2026-07-03_HC002_s2")

        val summaries = repository.listSessions()

        assertEquals(1, summaries.size)
        assertEquals("s1", summaries[0].sessionId)
        assertEquals("HC001", summaries[0].patientCode)
        assertFalse(summaries[0].recovered)
    }

    @Test
    fun `findPartialMasterFiles is empty when there is no crash residue`(@TempDir tempDir: Path) {
        val repository = repo(tempDir)
        repository.createSessionDirectory("s1")
        assertTrue(repository.findPartialMasterFiles("s1").isEmpty())
    }

    @Test
    fun `finalizePartialMasterFile rebuilds the header from actual length and drops the partial suffix`(@TempDir tempDir: Path) {
        val repository = repo(tempDir)
        repository.createSessionDirectory("s1")
        val masterFile = repository.defaultMasterFile("s1")

        // Simulate a live recording: write a real partial file via the same writer the
        // recorder uses, then abandon it without finalizing (as a crash would).
        val writer = WavFileWriter.create(masterFile, format)
        val chunk = Random(1).nextBytes(1000)
        writer.write(chunk, 0, chunk.size)
        // Deliberately do NOT call patchHeader() — the on-disk header still claims 0 bytes,
        // simulating a crash before the first periodic patch (§8.4: never trust it).
        writer.closeWithoutRename()

        val found = repository.findPartialMasterFiles("s1")
        assertEquals(1, found.size)
        assertEquals(writer.partialPath, found[0])

        val recovered = repository.finalizePartialMasterFile(found[0], format)

        assertEquals(masterFile, recovered.path)
        assertEquals(500L, recovered.frameCount) // 16-bit mono: frameSize 2 bytes, 1000 bytes / 2 = 500 frames
        assertFalse(Files.exists(writer.partialPath))
        assertTrue(Files.exists(masterFile))

        val bytes = Files.readAllBytes(masterFile)
        val parsed = org.example.app.infrastructure.audio.WavHeader.parse(bytes)
        assertEquals(1000L, parsed.dataSize)
        assertEquals(format, parsed.format)
    }

    @Test
    fun `latestFinalizedMasterPartFrames reads the highest-numbered clean master part`(@TempDir tempDir: Path) {
        val repository = repo(tempDir)
        repository.createSessionDirectory("s1")

        assertNull(repository.latestFinalizedMasterPartFrames("s1", format))

        val part1 = repository.defaultMasterFile("s1")
        val writer1 = WavFileWriter.create(part1, format)
        writer1.write(ByteArray(400), 0, 400)
        writer1.finalizeAndRename()

        val part2 = repository.masterDir("s1").resolve("session_master.part2.wav")
        val writer2 = WavFileWriter.create(part2, format)
        writer2.write(ByteArray(800), 0, 800)
        writer2.finalizeAndRename()

        val frames = repository.latestFinalizedMasterPartFrames("s1", format)
        assertEquals(400L, frames) // part2's 800 bytes / frameSize(2) = 400 frames
    }
}
