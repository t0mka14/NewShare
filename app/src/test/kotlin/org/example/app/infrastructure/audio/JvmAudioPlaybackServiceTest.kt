package org.example.app.infrastructure.audio

import org.example.app.domain.audio.CaptureFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Exercises [JvmAudioPlaybackService]'s chunking/position-tracking logic
 * against a [FakePlaybackLine] — no real output device, so this runs
 * headless (§10.1). [SystemPlaybackLine] itself (the real `SourceDataLine`
 * wiring) is intentionally untested here: it has no branching logic to
 * verify without real audio hardware.
 */
class JvmAudioPlaybackServiceTest {

    private val format = CaptureFormat(sampleRate = 8_000, bits = 16, channels = 1)
    private val lines = mutableListOf<FakePlaybackLine>()
    private val service = JvmAudioPlaybackService(lineFactory = { FakePlaybackLine().also { lines += it } })

    @Test
    fun `play streams the whole file and reaches the end position`(@TempDir dir: Path) {
        val file = dir.resolve("clip.wav")
        writeTestWav(file, format, monoRamp(400))

        service.play(file)
        awaitIdle()

        val line = lines.single()
        assertEquals(format, line.openedFormats.single())
        assertEquals(400 * 2, line.writes.sumOf { it.size })
        assertTrue(line.drained)
        assertTrue(line.stopped)
        assertTrue(line.closed)
        assertEquals(400L, service.positionSamples.value)
        assertFalse(service.isPlaying.value)
    }

    @Test
    fun `playRange streams only the requested range and starts position at startSample`(@TempDir dir: Path) {
        val file = dir.resolve("clip.wav")
        writeTestWav(file, format, monoRamp(1000))

        service.playRange(file, startSample = 100, stopSample = 300)
        awaitIdle()

        val line = lines.single()
        assertEquals(200 * 2, line.writes.sumOf { it.size })
        assertEquals(300L, service.positionSamples.value)
    }

    @Test
    fun `playRange rejects an inverted or out-of-range request without opening a line`(@TempDir dir: Path) {
        val file = dir.resolve("clip.wav")
        writeTestWav(file, format, monoRamp(100))

        // Fire-and-forget contract: rejected ranges are logged no-ops, never exceptions on the
        // calling (UI) thread.
        service.playRange(file, 50, 50)
        service.playRange(file, 60, 50)
        service.playRange(file, 0, 200)
        assertTrue(lines.isEmpty(), "no line should be opened for a rejected range")
        assertFalse(service.isPlaying.value)
    }

    @Test
    fun `play with a missing or unreadable file is a no-op instead of an exception`(@TempDir dir: Path) {
        val missing = dir.resolve("missing.wav")

        service.play(missing)
        service.playRange(missing, 0, 100)

        assertTrue(lines.isEmpty(), "no line should be opened for an unreadable file")
        assertFalse(service.isPlaying.value)
    }

    @Test
    fun `starting a new playback stops and closes the previous line`(@TempDir dir: Path) {
        val file = dir.resolve("clip.wav")
        writeTestWav(file, format, monoRamp(100_000))

        service.play(file)
        service.play(file) // starts a second playback before/around the first's completion
        awaitIdle()

        assertEquals(2, lines.size)
        assertTrue(lines[0].closed, "the superseded line must end up stopped/closed")
    }

    @Test
    fun `stop halts playback and releases the line`(@TempDir dir: Path) {
        val file = dir.resolve("clip.wav")
        writeTestWav(file, format, monoRamp(100_000))

        service.play(file)
        service.stop()

        assertFalse(service.isPlaying.value)
        assertTrue(lines.single().closed)
    }

    /**
     * The §8.7 position line is drawn straight onto the waveform, so it must follow the audio the
     * device is *rendering*, not the audio handed to the line: writes return immediately until
     * the line's buffer fills, which used to push the reported position a whole buffer (0.25-0.5 s
     * on real hardware) ahead of the sound.
     */
    @Test
    fun `position follows rendered frames, not frames written into the line buffer`(@TempDir dir: Path) {
        val file = dir.resolve("clip.wav")
        writeTestWav(file, format, monoRamp(800))
        val line = BufferingPlaybackLine(bufferFrames = 200)
        val service = JvmAudioPlaybackService(lineFactory = { line })

        service.playRange(file, startSample = 0, stopSample = 800)

        // Buffer full, nothing rendered yet: the line has swallowed frames but none have sounded.
        awaitCondition("line did not fill its buffer") { line.writtenFrames >= 200 }
        assertEquals(0L, service.positionSamples.value)

        line.renderedFrames = 400
        awaitCondition("position did not follow the rendered frames") { service.positionSamples.value >= 400 }
        assertTrue(
            service.positionSamples.value <= line.renderedFrames,
            "position ${service.positionSamples.value} ran ahead of the ${line.renderedFrames} rendered frames",
        )

        line.renderedFrames = 800
        awaitCondition("playback did not finish") { !service.isPlaying.value }
        assertEquals(800L, service.positionSamples.value)
        assertTrue(line.drained)
    }

    private fun awaitCondition(message: String, timeoutMs: Long = 2_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(1)
        }
        assertTrue(condition(), message)
    }

    private fun awaitIdle(timeoutMs: Long = 2_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (service.isPlaying.value && System.currentTimeMillis() < deadline) {
            Thread.sleep(1)
        }
        assertFalse(service.isPlaying.value, "playback did not finish within timeout")
    }
}

/**
 * Models what a real `SourceDataLine` does and [FakePlaybackLine] cannot: accepts writes into a
 * bounded buffer and reports a rendered-frame clock the test drives by hand.
 */
private class BufferingPlaybackLine(private val bufferFrames: Long) : PlaybackLine {
    @Volatile var writtenFrames = 0L
    @Volatile var renderedFrames = 0L
    @Volatile var drained = false
    private var frameSize = 2

    override fun open(format: CaptureFormat) {
        frameSize = format.frameSize()
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int): Int {
        while (writtenFrames - renderedFrames >= bufferFrames) {
            Thread.sleep(1) // throws InterruptedException on stop(), like a real blocking write
        }
        writtenFrames += length / frameSize
        return length
    }

    override fun framePosition(): Long = renderedFrames

    override fun drain() {
        drained = true
    }

    override fun stop() = Unit
    override fun close() = Unit
}

private class FakePlaybackLine : PlaybackLine {
    val openedFormats = mutableListOf<CaptureFormat>()
    val writes = mutableListOf<ByteArray>()
    var drained = false
    var stopped = false
    var closed = false

    override fun open(format: CaptureFormat) {
        openedFormats += format
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int): Int {
        writes += buffer.copyOfRange(offset, offset + length)
        return length
    }

    override fun drain() {
        drained = true
    }

    override fun stop() {
        stopped = true
    }

    override fun close() {
        closed = true
    }
}
