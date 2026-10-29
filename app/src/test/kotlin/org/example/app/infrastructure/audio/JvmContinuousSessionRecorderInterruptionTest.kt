package org.example.app.infrastructure.audio

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.example.app.domain.CoroutineDispatchers
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.audio.CaptureFormat
import org.example.app.domain.audio.InterruptionReason
import org.example.app.domain.audio.RecorderState
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * F-129 (the cut is recorded at exactly the frames the finished part holds) and F-122 (a slow disk or a
 * paused process is not a lost microphone), against the real [JvmContinuousSessionRecorder].
 */
class JvmContinuousSessionRecorderInterruptionTest {

    private val device = AudioInputDevice(id = "dev", name = "Fake mic", eligible = true)
    private val format = CaptureFormat.PREFERRED

    private val dispatchers = object : CoroutineDispatchers {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val io: CoroutineDispatcher = Dispatchers.IO
    }

    /** Real time plus a controllable jump, to simulate a process pause. */
    private val clockOffset = AtomicLong(0)
    private val clock: () -> Long = { System.currentTimeMillis() + clockOffset.get() }

    /** The spec thresholds scaled down ~10x so the tests stay fast: gap 150 ms, stall 600 ms. */
    private val fastTiming = JvmContinuousSessionRecorder.WatchdogTiming(
        pollMs = 20, deviceCheckGapMs = 150, stallMs = 600, latePollFactor = 2, wedgedCaptureThreadMs = 300,
    )

    private val opener = ScriptedCaptureOpener()
    private val recorders = mutableListOf<JvmContinuousSessionRecorder>()

    @AfterEach
    fun tearDown() = runBlocking {
        opener.lines.forEach { it.ignoreClose = false; it.stalled = false }
        recorders.forEach { it.stop() }
    }

    private fun recorder(timing: JvmContinuousSessionRecorder.WatchdogTiming? = null) =
        (if (timing == null) JvmContinuousSessionRecorder(dispatchers, opener)
        else JvmContinuousSessionRecorder(dispatchers, opener, clock, timing)).also { recorders += it }

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what" }
            Thread.sleep(5)
        }
    }

    private fun startWriting(recorder: JvmContinuousSessionRecorder, file: Path): ScriptedCaptureLine = runBlocking {
        recorder.startMonitoring(device)
        recorder.startWriting(file)
        awaitUntil("first samples") { recorder.writtenSamples.value > 0 }
        opener.lines.last()
    }

    private fun framesInFinishedPart(file: Path): Long {
        val parsed = WavHeader.parse(Files.readAllBytes(file))
        return parsed.dataSize / format.frameSize()
    }

    @Test
    fun `F-129 interrupted atSample equals the frames of the finished part and no partial chunk is written after the cut`(
        @TempDir dir: Path,
    ) {
        val master = dir.resolve("session_master.wav")
        val recorder = recorder() // default (production) timing
        val line = startWriting(recorder, master)
        line.partialBytesOnClose = 1500 * format.frameSize() // a driver releasing read() with a partial chunk
        opener.deviceGone = true
        line.stalled = true

        awaitUntil("interruption") { recorder.state.value is RecorderState.Interrupted }

        val interrupted = recorder.state.value as RecorderState.Interrupted
        val partFrames = framesInFinishedPart(master)
        assertEquals(partFrames, interrupted.atSample, "Interrupted.atSample vs frames in the finished part")
        assertEquals(partFrames, recorder.writtenSamples.value, "writtenSamples vs frames in the finished part")
        assertEquals(0L, partFrames % 2400, "only whole 50 ms chunks were written")
    }

    @Test
    fun `F-122 a stall with the device present shorter than the limit does not interrupt`(@TempDir dir: Path) {
        val recorder = recorder(fastTiming)
        val line = startWriting(recorder, dir.resolve("session_master.wav"))

        line.stalled = true
        Thread.sleep(300) // beyond the device-check gap (150), below the stall limit (600)
        line.stalled = false
        val before = recorder.writtenSamples.value
        awaitUntil("capture to resume") { recorder.writtenSamples.value > before }

        assertEquals(RecorderState.Writing, recorder.state.value)
    }

    @Test
    fun `F-122 a stall of 400 ms with the real thresholds loses no samples and does not interrupt`(@TempDir dir: Path) {
        val master = dir.resolve("session_master.wav")
        val recorder = recorder()
        val line = startWriting(recorder, master)

        // Bytes delivered before writing began (monitoring) are not written, so compare the offset instead.
        fun unwrittenFrames(): Long {
            line.stalled = true // freeze so the delivered/written comparison is stable
            Thread.sleep(50)
            return line.fullChunkBytes / format.frameSize() - recorder.writtenSamples.value
        }
        val unwrittenBefore = unwrittenFrames()
        Thread.sleep(400)
        line.stalled = false
        val before = recorder.writtenSamples.value
        awaitUntil("capture to resume") { recorder.writtenSamples.value > before }
        assertEquals(RecorderState.Writing, recorder.state.value)

        assertEquals(unwrittenBefore, unwrittenFrames(), "every frame delivered after the stall was written")
    }

    @Test
    fun `F-122 a stall with the device gone interrupts promptly as READ_STARVATION`(@TempDir dir: Path) {
        val recorder = recorder(fastTiming)
        val line = startWriting(recorder, dir.resolve("session_master.wav"))

        opener.deviceGone = true
        line.stalled = true

        awaitUntil("interruption", timeoutMs = 2_000) { recorder.state.value is RecorderState.Interrupted }
        assertEquals(InterruptionReason.READ_STARVATION, (recorder.state.value as RecorderState.Interrupted).reason)
    }

    @Test
    fun `F-122 a stall beyond the limit with the device present interrupts as READ_STARVATION`(@TempDir dir: Path) {
        val recorder = recorder(fastTiming)
        val line = startWriting(recorder, dir.resolve("session_master.wav"))

        line.stalled = true

        awaitUntil("interruption", timeoutMs = 3_000) { recorder.state.value is RecorderState.Interrupted }
        assertEquals(InterruptionReason.READ_STARVATION, (recorder.state.value as RecorderState.Interrupted).reason)
    }

    @Test
    fun `F-122 a late watchdog tick resets the read baseline instead of firing`(@TempDir dir: Path) {
        val recorder = recorder(fastTiming)
        val line = startWriting(recorder, dir.resolve("session_master.wav"))

        line.stalled = true
        Thread.sleep(40)
        clockOffset.addAndGet(10_000) // a process pause: the wall clock leaps past the stall limit
        Thread.sleep(100)
        line.stalled = false
        val before = recorder.writtenSamples.value
        awaitUntil("capture to resume") { recorder.writtenSamples.value > before }

        assertEquals(RecorderState.Writing, recorder.state.value)
    }

    @Test
    fun `a capture thread that never returns from read is still reported interrupted by the watchdog`(@TempDir dir: Path) {
        val recorder = recorder(fastTiming)
        val line = startWriting(recorder, dir.resolve("session_master.wav"))

        line.ignoreClose = true
        line.stalled = true
        opener.deviceGone = true

        awaitUntil("interruption", timeoutMs = 3_000) { recorder.state.value is RecorderState.Interrupted }
        assertTrue(line.isClosed)
    }

    @Test
    fun `F-122 sustained late watchdog ticks with the device gone still interrupt`(@TempDir dir: Path) {
        val recorder = recorder(fastTiming)
        val line = startWriting(recorder, dir.resolve("session_master.wav"))
        val running = AtomicBoolean(true)
        // Every watchdog tick (20 ms) sees the clock 120 ms further: all ticks are "late" (> 2 x 20 ms).
        val jitter = thread(isDaemon = true) { while (running.get()) { clockOffset.addAndGet(100); Thread.sleep(20) } }
        try {
            line.stalled = true
            opener.deviceGone = true

            awaitUntil("interruption", timeoutMs = 3_000) { recorder.state.value is RecorderState.Interrupted }
            assertEquals(InterruptionReason.READ_STARVATION, (recorder.state.value as RecorderState.Interrupted).reason)
        } finally {
            running.set(false)
            jitter.join()
        }
    }

    @Test
    fun `F-122 sustained late watchdog ticks cannot hide a stall longer than the limit with the device present`(
        @TempDir dir: Path,
    ) {
        val recorder = recorder(fastTiming)
        val line = startWriting(recorder, dir.resolve("session_master.wav"))
        val running = AtomicBoolean(true)
        val jitter = thread(isDaemon = true) { while (running.get()) { clockOffset.addAndGet(100); Thread.sleep(20) } }
        try {
            line.stalled = true

            awaitUntil("interruption", timeoutMs = 3_000) { recorder.state.value is RecorderState.Interrupted }
            assertEquals(InterruptionReason.READ_STARVATION, (recorder.state.value as RecorderState.Interrupted).reason)
        } finally {
            running.set(false)
            jitter.join()
        }
    }

    @Test
    fun `an old capture thread released after the watchdog finalized its wedged episode writes nothing into the resumed part`(
        @TempDir dir: Path,
    ) {
        val recorder = recorder(fastTiming)
        val master = dir.resolve("session_master.wav")
        val part2 = dir.resolve("session_master.part2.wav")
        val oldLine = startWriting(recorder, master)

        oldLine.ignoreClose = true
        oldLine.stalled = true
        opener.deviceGone = true
        awaitUntil("wedged interruption", timeoutMs = 3_000) { recorder.state.value is RecorderState.Interrupted }
        val cut = (recorder.state.value as RecorderState.Interrupted).atSample
        opener.deviceGone = false

        runBlocking { recorder.resume(device, part2) }
        val newLine = opener.lines.last()
        awaitUntil("new part to receive samples") { recorder.writtenSamples.value > cut }

        // The old driver finally wakes with data, and its next read finds the line closed.
        oldLine.releaseWithBytes = 4800
        oldLine.ignoreClose = false
        Thread.sleep(200)

        newLine.stalled = true // freeze, then compare what the new line delivered with what the recorder counted
        Thread.sleep(100)
        assertEquals(RecorderState.Writing, recorder.state.value, "no false Interrupted for the healthy new episode")
        assertEquals(cut + newLine.fullChunkBytes / format.frameSize(), recorder.writtenSamples.value, "old thread's chunk was counted")
        runBlocking { recorder.stop() }
        assertEquals(newLine.fullChunkBytes / format.frameSize(), framesInFinishedPart(part2), "old thread's chunk was written into part 2")
        assertEquals(cut, framesInFinishedPart(master), "part 1 keeps exactly the frames up to the cut")
    }

    @Test
    fun `stop racing the watchdog's wedged finalize never ends in Failed`(@TempDir dir: Path) {
        repeat(12) { attempt ->
            val recorder = recorder(fastTiming)
            val master = dir.resolve("m$attempt/session_master.wav").also { Files.createDirectories(it.parent) }
            val line = startWriting(recorder, master)
            line.ignoreClose = true
            line.stalled = true
            opener.deviceGone = true
            awaitUntil("claimed failure") { line.isClosed }
            Thread.sleep(260L + attempt * 6) // sweep across the 300 ms wedged boundary

            runBlocking { recorder.stop() }

            assertEquals(RecorderState.Stopped, recorder.state.value, "attempt $attempt")
            assertTrue(Files.exists(master), "attempt $attempt: finished master exists")
            opener.deviceGone = false
        }
    }

    @Test
    fun `periodic header patch does not force to disk but finalize does`(@TempDir dir: Path) {
        val format = CaptureFormat.PREFERRED
        val writer = WavFileWriter.create(dir.resolve("m.wav"), format)
        val afterCreate = writer.syncCount
        val chunk = ByteArray(4800) { 1 }

        writer.write(chunk, 0, chunk.size)
        writer.patchHeader()
        writer.write(chunk, 0, chunk.size)
        writer.patchHeader()

        assertEquals(afterCreate, writer.syncCount, "patchHeader must not fsync on the capture thread")
        // the patched header is what crash recovery reads from the partial file
        val header = WavHeader.parse(Files.readAllBytes(writer.partialPath))
        assertEquals(chunk.size * 2L, header.dataSize)

        writer.finalizeAndRename()
        assertEquals(afterCreate + 1, writer.syncCount)
    }
}
