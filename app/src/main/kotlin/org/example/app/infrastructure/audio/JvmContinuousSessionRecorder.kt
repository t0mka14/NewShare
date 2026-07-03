package org.example.app.infrastructure.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.oshai.kotlinlogging.KotlinLogging
import org.example.app.domain.CoroutineDispatchers
import org.example.app.domain.audio.AudioError
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.audio.CaptureFormat
import org.example.app.domain.audio.ContinuousSessionRecorder
import org.example.app.domain.audio.InterruptionReason
import org.example.app.domain.audio.RecorderState
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.TargetDataLine

private val logger = KotlinLogging.logger {}

/**
 * Production [ContinuousSessionRecorder] over `javax.sound.sampled` (§5.3.1, §8.4, §8.5).
 *
 * Threading model: a single dedicated `java.lang.Thread` owns the blocking
 * `TargetDataLine.read()` loop for the lifetime of one open line (one per
 * Monitoring/Writing episode, replaced on `resume`). It never runs on an
 * injected [CoroutineDispatchers] dispatcher. A watchdog *coroutine* (launched
 * on [dispatchers]' default dispatcher) polls the last-successful-read
 * timestamp for starvation detection; suspend functions that open/close lines
 * hop onto [CoroutineDispatchers.io].
 *
 * One instance is meant to live for the app's lifetime and be reused across
 * sessions (constructor-injected via `AppContainer`, no singletons) — every
 * state-resetting entry point (`startMonitoring`, `stop`) clears counters and
 * smoothing state so no session leaks into the next.
 */
class JvmContinuousSessionRecorder(
    private val dispatchers: CoroutineDispatchers,
) : ContinuousSessionRecorder {

    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    override val state: StateFlow<RecorderState> = _state.asStateFlow()

    private val _writtenSamples = MutableStateFlow(0L)
    override val writtenSamples: StateFlow<Long> = _writtenSamples.asStateFlow()

    private val _levels = MutableStateFlow(0f)
    override val levels: Flow<Float> = _levels.asStateFlow()

    private val _captureFormat = MutableStateFlow<CaptureFormat?>(null)
    override val captureFormat: StateFlow<CaptureFormat?> = _captureFormat.asStateFlow()

    /**
     * Non-blocking ">5s all-zero samples while writing" warning (§8.5). Not part
     * of the frozen domain contract (the recorder has no notion of "a take is
     * open" — that lives in the domain/UI layer) so this is a best-effort
     * approximation scoped to "currently Writing" rather than "take open"; see
     * the audio-engineer's task report for the exact caveat. UI wiring is a
     * later task.
     */
    val silenceWarning: StateFlow<Boolean> get() = _silenceWarning.asStateFlow()
    private val _silenceWarning = MutableStateFlow(false)

    private val watchdogScope = CoroutineScope(SupervisorJob() + dispatchers.default)

    private val levelMeter = LevelMeter()
    private val globalSampleCounter = GlobalSampleCounter()

    @Volatile private var currentLine: TargetDataLine? = null
    @Volatile private var currentWriter: WavFileWriter? = null
    @Volatile private var captureThread: Thread? = null

    private val lastSuccessfulReadAtMillis = AtomicLong(0L)
    private val interruptionHandled = AtomicBoolean(true) // no episode in flight until a line is open
    private var lastHeaderPatchAtMillis = 0L
    private var silenceAccumMs = 0.0

    init {
        watchdogScope.launch { watchdogLoop() }
    }

    // region public API

    override suspend fun startMonitoring(device: AudioInputDevice) = withContext(dispatchers.io) {
        check(canStartFresh(_state.value)) {
            "startMonitoring is only valid from Idle/Stopped/Failed, was ${_state.value}"
        }
        try {
            val (line, format) = openLine(device)
            resetSessionState()
            currentLine = line
            _captureFormat.value = format
            interruptionHandled.set(false)
            lastSuccessfulReadAtMillis.set(System.currentTimeMillis())
            _state.value = RecorderState.Monitoring
            startCaptureThread(line, format)
        } catch (e: AudioException) {
            logger.warn(e) { "startMonitoring failed for device=${device.id}" }
            _state.value = RecorderState.Failed(e.error)
        }
    }

    override suspend fun startWriting(masterFile: Path) = withContext(dispatchers.io) {
        check(_state.value == RecorderState.Monitoring) {
            "startWriting is only valid from Monitoring, was ${_state.value}"
        }
        val format = _captureFormat.value
            ?: error("captureFormat missing while Monitoring — openLine should have set it")
        try {
            val writer = WavFileWriter.create(masterFile, format)
            globalSampleCounter.startNewPart()
            lastHeaderPatchAtMillis = System.currentTimeMillis()
            silenceAccumMs = 0.0
            currentWriter = writer
            _state.value = RecorderState.Writing
        } catch (e: Exception) {
            logger.warn(e) { "startWriting failed for masterFile=$masterFile" }
            _state.value = RecorderState.Failed(AudioError.RecordingStartFailed(e.message ?: "failed to open master file"))
        }
    }

    override suspend fun resume(device: AudioInputDevice, partFile: Path) = withContext(dispatchers.io) {
        check(_state.value is RecorderState.Interrupted) {
            "resume is only valid from Interrupted, was ${_state.value}"
        }
        try {
            val (line, format) = openLine(device)
            val writer = WavFileWriter.create(partFile, format)
            currentLine = line
            currentWriter = writer
            _captureFormat.value = format
            globalSampleCounter.startNewPart()
            levelMeter.reset()
            lastHeaderPatchAtMillis = System.currentTimeMillis()
            silenceAccumMs = 0.0
            interruptionHandled.set(false)
            lastSuccessfulReadAtMillis.set(System.currentTimeMillis())
            _state.value = RecorderState.Writing
            startCaptureThread(line, format)
        } catch (e: AudioException) {
            logger.warn(e) { "resume failed for device=${device.id}" }
            _state.value = RecorderState.Failed(e.error)
        }
    }

    override suspend fun stop() = withContext(dispatchers.io) {
        stopCaptureThreadAndLine()

        val writer = currentWriter
        currentWriter = null
        if (writer != null) {
            try {
                writer.finalizeAndRename()
            } catch (e: Exception) {
                logger.error(e) { "failed to finalize master file on stop" }
                resetSessionState()
                _state.value = RecorderState.Failed(AudioError.DiskWriteFailed(e.message ?: "finalize failed"))
                return@withContext
            }
        }
        resetSessionState()
        _state.value = RecorderState.Stopped
    }

    // endregion

    // region capture thread

    private fun startCaptureThread(line: TargetDataLine, format: CaptureFormat) {
        val chunkBytes = format.bytesForMillis(CHUNK_MS).coerceAtLeast(format.frameSize())
        val thread = Thread({ captureLoop(line, format, chunkBytes) }, "audio-capture")
        thread.isDaemon = true
        thread.priority = Thread.MAX_PRIORITY
        captureThread = thread
        thread.start()
    }

    private fun captureLoop(line: TargetDataLine, format: CaptureFormat, chunkBytes: Int) {
        val buffer = ByteArray(chunkBytes)
        try {
            while (!Thread.currentThread().isInterrupted) {
                val n = try {
                    line.read(buffer, 0, buffer.size)
                } catch (e: Exception) {
                    logger.warn(e) { "capture line read failed" }
                    onLineFailure(InterruptionReason.DEVICE_LOST)
                    return
                }
                if (n < 0) {
                    onLineFailure(InterruptionReason.DEVICE_LOST)
                    return
                }
                if (n == 0) continue

                lastSuccessfulReadAtMillis.set(System.currentTimeMillis())
                _levels.value = levelMeter.process(buffer, 0, n, format)
                updateSilenceHeuristic(LevelMeter.isAllZero(buffer, 0, n), n, format)

                val writer = currentWriter
                if (writer != null && _state.value is RecorderState.Writing) {
                    writer.write(buffer, 0, n)
                    val globalTotal = globalSampleCounter.update(format.framesIn(writer.bytesWritten))
                    _writtenSamples.value = globalTotal
                    maybePatchHeader(writer)
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "capture loop terminated unexpectedly" }
            onLineFailure(InterruptionReason.DEVICE_LOST)
        }
    }

    private fun maybePatchHeader(writer: WavFileWriter) {
        val now = System.currentTimeMillis()
        if (now - lastHeaderPatchAtMillis >= HEADER_PATCH_INTERVAL_MS) {
            try {
                writer.patchHeader()
                lastHeaderPatchAtMillis = now
            } catch (e: Exception) {
                logger.error(e) { "header patch failed" }
                onWriteFailure(e)
            }
        }
    }

    private fun updateSilenceHeuristic(allZero: Boolean, chunkBytesLen: Int, format: CaptureFormat) {
        if (_state.value !is RecorderState.Writing) {
            silenceAccumMs = 0.0
            if (_silenceWarning.value) _silenceWarning.value = false
            return
        }
        if (allZero) {
            silenceAccumMs += format.millisIn(chunkBytesLen)
            if (silenceAccumMs >= SILENCE_WARNING_MS && !_silenceWarning.value) {
                _silenceWarning.value = true
            }
        } else {
            silenceAccumMs = 0.0
            if (_silenceWarning.value) _silenceWarning.value = false
        }
    }

    // endregion

    // region interruption / watchdog

    private suspend fun watchdogLoop() {
        while (watchdogScope.isActive) {
            delay(WATCHDOG_POLL_MS)
            if (_state.value !is RecorderState.Writing) continue
            val last = lastSuccessfulReadAtMillis.get()
            if (last != 0L && System.currentTimeMillis() - last > WATCHDOG_STARVATION_MS) {
                onLineFailure(InterruptionReason.READ_STARVATION)
            }
        }
    }

    /** Handles a detected device loss / read starvation exactly once per episode
     * (capture thread and watchdog may both observe the same failure). */
    private fun onLineFailure(reason: InterruptionReason) {
        if (!interruptionHandled.compareAndSet(false, true)) return

        val atSample = _writtenSamples.value
        logger.warn { "recorder interrupted at sample=$atSample reason=$reason" }

        val line = currentLine
        currentLine = null
        try {
            line?.close()
        } catch (e: Exception) {
            logger.warn(e) { "error closing line after interruption" }
        }

        val writer = currentWriter
        currentWriter = null
        if (writer != null) {
            try {
                writer.finalizeAndRename()
            } catch (e: Exception) {
                logger.error(e) { "failed to finalize part file after interruption" }
            }
        }

        _state.value = RecorderState.Interrupted(atSample, reason)
    }

    private fun onWriteFailure(cause: Exception) {
        if (!interruptionHandled.compareAndSet(false, true)) return
        val line = currentLine
        currentLine = null
        try {
            line?.close()
        } catch (_: Exception) {
            // best-effort
        }
        currentWriter = null
        _state.value = RecorderState.Failed(AudioError.DiskWriteFailed(cause.message ?: "disk write failed"))
    }

    // endregion

    // region line lifecycle helpers

    private fun canStartFresh(state: RecorderState): Boolean =
        state == RecorderState.Idle || state == RecorderState.Stopped || state is RecorderState.Failed

    private fun resetSessionState() {
        captureThread = null
        currentLine = null
        currentWriter = null
        globalSampleCounter.reset()
        _writtenSamples.value = 0
        _captureFormat.value = null
        _levels.value = 0f
        levelMeter.reset()
        silenceAccumMs = 0.0
        _silenceWarning.value = false
        lastSuccessfulReadAtMillis.set(0L)
        interruptionHandled.set(true)
    }

    private fun stopCaptureThreadAndLine() {
        // Mark "no interruption episode in flight" *before* closing the line: closing
        // it deliberately here unblocks the capture thread's blocked read() the same
        // way a real device loss would, and its error path would otherwise race this
        // shutdown and misreport a clean stop() as an Interrupted(DEVICE_LOST).
        interruptionHandled.set(true)

        val thread = captureThread
        captureThread = null
        val line = currentLine
        currentLine = null
        try {
            line?.stop()
            line?.close()
        } catch (e: Exception) {
            logger.warn(e) { "error closing line on stop" }
        }
        thread?.interrupt()
        try {
            thread?.join(THREAD_JOIN_TIMEOUT_MS)
        } catch (_: InterruptedException) {
            // best-effort join; the daemon thread will exit once the (now-closed) line surfaces the failure
        }
    }

    private fun openLine(device: AudioInputDevice): Pair<TargetDataLine, CaptureFormat> {
        val mixerInfo = MixerIds.findByStableId(device.id)
            ?: throw AudioException(AudioError.DeviceUnavailable(device.id))
        val mixer = try {
            AudioSystem.getMixer(mixerInfo)
        } catch (e: Exception) {
            throw AudioException(AudioError.DeviceUnavailable(device.id))
        }

        val negotiated = CaptureFormatNegotiator.negotiate(isSupported = { candidate ->
            try {
                mixer.isLineSupported(DataLine.Info(TargetDataLine::class.java, candidate.toJavaAudioFormat()))
            } catch (e: Exception) {
                false
            }
        }) ?: throw AudioException(AudioError.NoSupportedPcmFormat(device.id))

        val javaFormat = negotiated.toJavaAudioFormat()
        val info = DataLine.Info(TargetDataLine::class.java, javaFormat)
        val line = try {
            (mixer.getLine(info) as TargetDataLine).apply {
                open(javaFormat, negotiated.bytesForMillis(LINE_BUFFER_MS))
                start()
            }
        } catch (e: LineUnavailableException) {
            throw AudioException(AudioError.DeviceUnavailable(device.id))
        } catch (e: Exception) {
            throw AudioException(AudioError.RecordingStartFailed(e.message ?: "failed to open line"))
        }
        return line to negotiated
    }

    // endregion

    private class AudioException(val error: AudioError) : Exception()

    private companion object {
        const val CHUNK_MS = 80
        const val LINE_BUFFER_MS = 100
        const val WATCHDOG_POLL_MS = 200L
        const val WATCHDOG_STARVATION_MS = 1000L
        const val HEADER_PATCH_INTERVAL_MS = 5000L
        const val SILENCE_WARNING_MS = 5000.0
        const val THREAD_JOIN_TIMEOUT_MS = 2000L
    }
}
