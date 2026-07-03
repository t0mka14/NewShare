package org.example.app.infrastructure.audio

import org.example.app.domain.audio.CaptureFormat
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random
import org.junit.jupiter.api.Test

class WavFileWriterTest {

    private val format = CaptureFormat(sampleRate = 8_000, bits = 16, channels = 1)

    @Test
    fun `partial path derivation strips wav suffix`(@TempDir dir: Path) {
        val target = dir.resolve("session_master.wav")
        val partial = WavFileWriter.partialPathFor(target)
        assertEquals("session_master.partial.wav", partial.fileName.toString())

        val part2 = dir.resolve("session_master.part2.wav")
        assertEquals("session_master.part2.partial.wav", WavFileWriter.partialPathFor(part2).fileName.toString())
    }

    @Test
    fun `create writes a placeholder header immediately`(@TempDir dir: Path) {
        val target = dir.resolve("session_master.wav")
        val writer = WavFileWriter.create(target, format)
        try {
            val partialBytes = Files.readAllBytes(writer.partialPath)
            assertEquals(WavHeader.HEADER_SIZE, partialBytes.size)
            val parsed = WavHeader.parse(partialBytes)
            assertEquals(0L, parsed.dataSize)
            assertEquals(format, parsed.format)
        } finally {
            writer.closeWithoutRename()
        }
    }

    @Test
    fun `patched header matches exact bytes written so far — simulated crash mid-write`(@TempDir dir: Path) {
        val target = dir.resolve("session_master.wav")
        val writer = WavFileWriter.create(target, format)

        val chunk = Random(42).nextBytes(1600) // 100ms @ 8kHz/16bit/mono
        var expectedBytes = 0L
        repeat(5) {
            writer.write(chunk, 0, chunk.size)
            expectedBytes += chunk.size
        }
        writer.patchHeader()

        // Simulate a crash: write more unpatched data, then abandon the writer
        // without a further patch or finalize — as if the process died here.
        writer.write(chunk, 0, chunk.size)
        val bytesAtCrash = expectedBytes // header should reflect the *last patch*, not this trailing write
        writer.closeWithoutRename()

        // A fresh reader (as recovery would do) must see a valid, parseable header
        // whose dataSize matches the last successful patch, not the true file length.
        val raf = java.io.RandomAccessFile(writer.partialPath.toFile(), "r")
        val headerBytes = ByteArray(WavHeader.HEADER_SIZE)
        raf.readFully(headerBytes)
        val trueFileLength = raf.length()
        raf.close()

        val parsed = WavHeader.parse(headerBytes)
        assertEquals(bytesAtCrash, parsed.dataSize, "header must match the last patch, not the true length")
        assertEquals(format, parsed.format)
        assertTrue(
            trueFileLength > WavHeader.HEADER_SIZE + bytesAtCrash,
            "the unpatched trailing write should be present on disk even though the header doesn't (yet) claim it",
        )
    }

    @Test
    fun `data bytes read back exactly what was written`(@TempDir dir: Path) {
        val target = dir.resolve("session_master.wav")
        val writer = WavFileWriter.create(target, format)
        val part1 = Random(1).nextBytes(2000)
        val part2 = Random(2).nextBytes(3000)
        writer.write(part1, 0, part1.size)
        writer.write(part2, 0, part2.size)
        writer.patchHeader()
        val finalPath = writer.finalizeAndRename()

        val allBytes = Files.readAllBytes(finalPath)
        val parsed = WavHeader.parse(allBytes)
        assertEquals((part1.size + part2.size).toLong(), parsed.dataSize)

        val dataBytes = allBytes.copyOfRange(WavHeader.HEADER_SIZE, allBytes.size)
        assertArrayEquals(part1 + part2, dataBytes)
    }

    @Test
    fun `finalizeAndRename atomically produces the final path and removes the partial name`(@TempDir dir: Path) {
        val target = dir.resolve("session_master.wav")
        val writer = WavFileWriter.create(target, format)
        val chunk = Random(7).nextBytes(800)
        writer.write(chunk, 0, chunk.size)

        assertTrue(Files.exists(writer.partialPath))
        assertFalse(Files.exists(target))

        val finalPath = writer.finalizeAndRename()

        assertEquals(target, finalPath)
        assertTrue(Files.exists(target))
        assertFalse(Files.exists(writer.partialPath))

        val parsed = WavHeader.parse(Files.readAllBytes(target))
        assertEquals(chunk.size.toLong(), parsed.dataSize)
    }

    @Test
    fun `create truncates a pre-existing partial file from a previous crash`(@TempDir dir: Path) {
        val target = dir.resolve("session_master.wav")
        val partialPath = WavFileWriter.partialPathFor(target)
        Files.createDirectories(dir)
        Files.write(partialPath, ByteArray(10_000) { 0x7F })

        val writer = WavFileWriter.create(target, format)
        try {
            val bytes = Files.readAllBytes(writer.partialPath)
            assertEquals(WavHeader.HEADER_SIZE, bytes.size)
        } finally {
            writer.closeWithoutRename()
        }
    }
}
