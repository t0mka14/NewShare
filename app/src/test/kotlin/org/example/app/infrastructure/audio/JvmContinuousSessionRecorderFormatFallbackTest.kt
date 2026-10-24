package org.example.app.infrastructure.audio

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.example.app.domain.CoroutineDispatchers
import org.example.app.domain.audio.AudioError
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.audio.CaptureFormat
import org.example.app.domain.audio.RecorderState
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.TargetDataLine

/**
 * F-002: DirectSound's `isLineSupported` says yes to everything, so a candidate is only
 * confirmed by an actual open; a rejected open falls through to the next candidate (§5.3.1).
 */
class JvmContinuousSessionRecorderFormatFallbackTest {

    private val device = AudioInputDevice(id = "dev", name = "Fake mic", eligible = true)
    private val stereo48k = CaptureFormat(48_000, 16, 2)
    private val mono44k = CaptureFormat(44_100, 16, 1)

    private val dispatchers = object : CoroutineDispatchers {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val io: CoroutineDispatcher = Dispatchers.IO
    }

    private val openedLines = CopyOnWriteArrayList<TargetDataLine>()
    private val recorders = mutableListOf<JvmContinuousSessionRecorder>()

    @AfterEach
    fun tearDown() = runBlocking { recorders.forEach { it.stop() } }

    /** Claims support for every format (like DirectSound); `open` succeeds only for [openable]. */
    private inner class FakeOpener(private val openable: (CaptureFormat) -> Boolean) : CaptureLineOpener {
        val attempts = CopyOnWriteArrayList<CaptureFormat>()
        override fun deviceExists(deviceId: String) = true
        override fun isLineSupported(deviceId: String, format: CaptureFormat) = true
        override fun open(deviceId: String, format: CaptureFormat, bufferMs: Int): TargetDataLine {
            attempts += format
            if (!openable(format)) throw LineUnavailableException("format not supported")
            return fakeLine().also { openedLines += it }
        }
    }

    private fun recorder(opener: CaptureLineOpener) =
        JvmContinuousSessionRecorder(dispatchers, opener).also { recorders += it }

    @Test
    fun `startMonitoring falls back to the next candidate when open rejects the first`() = runBlocking {
        val opener = FakeOpener { it == stereo48k }
        val recorder = recorder(opener)

        recorder.startMonitoring(device)

        assertEquals(RecorderState.Monitoring, recorder.state.value)
        assertEquals(stereo48k, recorder.captureFormat.value)
        assertEquals(listOf(CaptureFormat.PREFERRED, stereo48k), opener.attempts.toList())
    }

    @Test
    fun `startMonitoring fails with DeviceUnavailable after trying every candidate exactly once`() = runBlocking {
        val opener = FakeOpener { false }
        val recorder = recorder(opener)

        recorder.startMonitoring(device)

        assertEquals(RecorderState.Failed(AudioError.DeviceUnavailable(device.id)), recorder.state.value)
        val attempts = opener.attempts.toList()
        assertEquals(CaptureFormat.PREFERRED, attempts.first())
        assertEquals(attempts.size, attempts.toSet().size) // never the same format twice
        assertEquals(true, stereo48k in attempts && mono44k in attempts)
        assertEquals(CaptureFormatNegotiator.STANDARD_SAMPLE_RATES.size * 2, attempts.size)
    }

    @Test
    fun `resume falls back to the next candidate and records that format`(@TempDir dir: Path) = runBlocking {
        var openNumber = 0
        val opener = object : CaptureLineOpener by FakeOpener({ true }) {
            val attempts = CopyOnWriteArrayList<CaptureFormat>()
            override fun open(deviceId: String, format: CaptureFormat, bufferMs: Int): TargetDataLine {
                attempts += format
                openNumber++
                // 1st open (monitoring) takes the preferred format and then dies; on resume only mono 44.1k works.
                if (openNumber > 1 && format != mono44k) throw LineUnavailableException("format not supported")
                return fakeLine(failReads = openNumber == 1).also { openedLines += it }
            }
        }
        val recorder = recorder(opener)
        recorder.startMonitoring(device)
        awaitState(recorder) { it is RecorderState.Interrupted }

        recorder.resume(device, dir.resolve("session_master.part2.wav"))

        assertEquals(RecorderState.Writing, recorder.state.value)
        assertEquals(mono44k, recorder.captureFormat.value)
        val resumeAttempts = opener.attempts.drop(1)
        assertEquals(mono44k, resumeAttempts.last())
        assertEquals(resumeAttempts.size, resumeAttempts.toSet().size)
    }

    private fun awaitState(recorder: JvmContinuousSessionRecorder, predicate: (RecorderState) -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!predicate(recorder.state.value)) {
            check(System.currentTimeMillis() < deadline) { "timed out in ${recorder.state.value}" }
            Thread.sleep(10)
        }
    }

    /** A [TargetDataLine] whose reads yield silence (or end-of-stream when [failReads]). */
    private fun fakeLine(failReads: Boolean = false): TargetDataLine =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(TargetDataLine::class.java)) { _, method, args ->
            when (method.name) {
                "read" -> if (failReads) -1 else { Thread.sleep(5); 0 }
                "available" -> 0
                "isOpen", "isActive", "isRunning" -> true
                "getFramePosition", "getBufferSize" -> 0
                "getLongFramePosition", "getMicrosecondPosition" -> 0L
                "getLevel" -> 0f
                "hashCode" -> System.identityHashCode(method)
                "toString" -> "FakeTargetDataLine"
                else -> if (method.returnType == Boolean::class.javaPrimitiveType) false else null
            }
        } as TargetDataLine
}
