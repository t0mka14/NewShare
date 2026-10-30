package org.example.app.infrastructure.audio

import org.example.app.domain.video.redacted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
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
import java.util.concurrent.atomic.AtomicReference
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
    private val lineOpener: CaptureLineOpener = SystemCaptureLineOpener,
    private val clock: () -> Long = System::currentTimeMillis,
    private val watchdogTiming: WatchdogTiming = WatchdogTiming(),
) : ContinuousSessionRecorder {

    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    override val state: StateFlow<RecorderState> = _state.asStateFlow()

    private val _writtenSamples = MutableStateFlow(0L)
    override val writtenSamples: StateFlow<Long> = _writtenSamples.asStateFlow()

    private val _levels = MutableStateFlow(0f)
    override val levels: Flow<Float> = _levels.asStateFlow()

    // F-144: a SharedFlow, not a StateFlow — every chunk's value reaches a collector even when the
    // line delivers chunks in back-to-back bursts (a StateFlow conflates and drops equal values).
    private val _fastLevels = MutableSharedFlow<Float>(replay = 1, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        .also { it.tryEmit(0f) }
    override val fastLevels: Flow<Float> = _fastLevels

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

    /** Barely-smoothed meter behind [fastLevels]; window tuned in [LevelMeter.FAST_WINDOW_MS]. */
    private val fastMeter = LevelMeter(windowMs = LevelMeter.FAST_WINDOW_MS)
    private val globalSampleCounter = GlobalSampleCounter()

    @Volatile private var currentLine: TargetDataLine? = null
    @Volatile private var currentWriter: WavFileWriter? = null
    @Volatile private var captureThread: Thread? = null

    private val lastSuccessfulReadAtMillis = AtomicLong(0L)
    private val interruptionHandled = AtomicBoolean(true) // no episode in flight until a line is open
    @Volatile private var currentDeviceId: String? = null

    /**
     * Failure claimed by a non-capture thread (the watchdog). The capture thread owns the writer, so
     * it alone finalizes the part and publishes `Interrupted` once its `read` returns (F-129).
     */
    private val pendingFailure = AtomicReference<InterruptionReason?>(null)
    private val pendingFailureSinceMillis = AtomicLong(0L)

    /**
     * Identity of the current capture episode (bumped by every start/resume/stop). A capture thread only
     * touches shared state under [episodeLock] and only while its own id is still current, so a thread left
     * wedged in `read` by an earlier episode can neither write into a newer part nor report a failure for it.
     * The same lock makes finalizing the writer (stop vs. the watchdog's wedged path) happen exactly once.
     */
    private val episodeLock = Any()
    private val episode = AtomicLong(0L)
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
            synchronized(episodeLock) {
                resetSessionState()
                currentLine = line
                currentDeviceId = device.id
                _captureFormat.value = format
                val id = beginEpisode()
                _state.value = RecorderState.Monitoring
                startCaptureThread(line, format, id)
            }
        } catch (e: AudioException) {
            logger.warn(redacted(e)) { "startMonitoring failed for device=${device.id}" }
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
            synchronized(episodeLock) {
                globalSampleCounter.startNewPart()
                lastHeaderPatchAtMillis = clock()
                silenceAccumMs = 0.0
                currentWriter = writer
                _state.value = RecorderState.Writing
            }
        } catch (e: Exception) {
            logger.warn(redacted(e)) { "startWriting failed for masterFile=${masterFile.fileName}" }
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
            synchronized(episodeLock) {
                currentLine = line
                currentWriter = writer
                _captureFormat.value = format
                globalSampleCounter.startNewPart()
                levelMeter.reset()
                fastMeter.reset()
                lastHeaderPatchAtMillis = clock()
                silenceAccumMs = 0.0
                currentDeviceId = device.id
                val id = beginEpisode()
                _state.value = RecorderState.Writing
                startCaptureThread(line, format, id)
            }
        } catch (e: AudioException) {
            logger.warn(redacted(e)) { "resume failed for device=${device.id}" }
            _state.value = RecorderState.Failed(e.error)
        }
    }

    override suspend fun stop() = withContext(dispatchers.io) {
        val writer = stopCaptureThreadAndLine()
        if (writer != null) {
            try {
                writer.finalizeAndRename()
            } catch (e: Exception) {
                logger.error(redacted(e)) { "failed to finalize master file on stop" }
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

    private fun startCaptureThread(line: TargetDataLine, format: CaptureFormat, episodeId: Long) {
        val chunkBytes = format.bytesForMillis(CHUNK_MS).coerceAtLeast(format.frameSize())
        val thread = Thread({ captureLoop(line, format, chunkBytes, episodeId) }, "audio-capture")
        thread.isDaemon = true
        thread.priority = Thread.MAX_PRIORITY
        captureThread = thread
        thread.start()
    }

    private fun captureLoop(line: TargetDataLine, format: CaptureFormat, chunkBytes: Int, episodeId: Long) {
        val buffer = ByteArray(chunkBytes)
        try {
            while (!Thread.currentThread().isInterrupted) {
                val n = try {
                    line.read(buffer, 0, buffer.size)
                } catch (e: Exception) {
                    logger.warn(redacted(e)) { "capture line read failed" }
                    onCaptureThreadFailure(InterruptionReason.DEVICE_LOST, episodeId)
                    return
                }
                synchronized(episodeLock) {
                    // A newer episode (or a stop) took over while this read was blocked: this thread is
                    // stale and must not write, count, meter or report anything.
                    if (!isCurrent(episodeId)) return
                    // A failure claimed meanwhile (the watchdog closed the line, which may release this read
                    // with a partial chunk): that chunk lies after the cut, so it is dropped, never written (F-129).
                    if (pendingFailure.get() != null || n < 0) {
                        onCaptureThreadFailure(InterruptionReason.DEVICE_LOST, episodeId)
                        return
                    }
                    if (n > 0) processChunk(buffer, n, format)
                }
            }
        } catch (e: Exception) {
            logger.error(redacted(e)) { "capture loop terminated unexpectedly" }
            onCaptureThreadFailure(InterruptionReason.DEVICE_LOST, episodeId)
        }
    }

    /** Runs under [episodeLock] on the capture thread of the current episode. */
    private fun processChunk(buffer: ByteArray, n: Int, format: CaptureFormat) {
        lastSuccessfulReadAtMillis.set(clock())
        _levels.value = levelMeter.process(buffer, 0, n, format)
        _fastLevels.tryEmit(fastMeter.process(buffer, 0, n, format))
        updateSilenceHeuristic(LevelMeter.isAllZero(buffer, 0, n), n, format)

        val writer = currentWriter
        if (writer != null && _state.value is RecorderState.Writing) {
            writer.write(buffer, 0, n)
            val globalTotal = globalSampleCounter.update(format.framesIn(writer.bytesWritten))
            _writtenSamples.value = globalTotal
            maybePatchHeader(writer)
        }
    }

    private fun maybePatchHeader(writer: WavFileWriter) {
        val now = clock()
        if (now - lastHeaderPatchAtMillis >= HEADER_PATCH_INTERVAL_MS) {
            try {
                writer.patchHeader()
                lastHeaderPatchAtMillis = now
            } catch (e: Exception) {
                logger.error(redacted(e)) { "header patch failed" }
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
        var previousTickAt = clock()
        var consecutiveLateTicks = 0
        while (watchdogScope.isActive) {
            delay(watchdogTiming.pollMs)
            val now = clock()
            val tickGap = now - previousTickAt
            previousTickAt = now
            if (_state.value !is RecorderState.Writing) continue
            val episodeId = episode.get()

            // A failure was claimed but the capture thread never came back from its read (a line that
            // ignores close()): publish it from here rather than hanging in Writing forever.
            val pending = pendingFailure.get()
            if (pending != null) {
                if (now - pendingFailureSinceMillis.get() > watchdogTiming.wedgedCaptureThreadMs) finishInterruption(pending, episodeId)
                continue
            }

            val last = lastSuccessfulReadAtMillis.get()
            val gap = now - last
            val deviceCheckDue = last != 0L && gap > watchdogTiming.deviceCheckGapMs

            // Our own late wake-up (GC / process pause) says nothing about the device: restart the baseline.
            // A lost device still shows, and the reset is bounded so sustained load (every tick late)
            // cannot hide a stalled read.
            if (tickGap > watchdogTiming.pollMs * watchdogTiming.latePollFactor) {
                consecutiveLateTicks++
                if (deviceCheckDue && deviceLost()) {
                    requestInterruption(InterruptionReason.READ_STARVATION, episodeId)
                    continue
                }
                if (consecutiveLateTicks <= watchdogTiming.maxConsecutiveLateTicks) {
                    logger.warn { "watchdog tick late by ${tickGap}ms; read baseline reset" }
                    lastSuccessfulReadAtMillis.set(now)
                    continue
                }
            } else {
                consecutiveLateTicks = 0
            }

            if (deviceCheckDue && (gap > watchdogTiming.stallMs || deviceLost())) {
                requestInterruption(InterruptionReason.READ_STARVATION, episodeId)
            }
        }
    }

    /** True when the device has vanished or the line was closed under us (§8.5). */
    private fun deviceLost(): Boolean {
        val line = currentLine ?: return true
        val deviceId = currentDeviceId ?: return false
        return try {
            !line.isOpen || !lineOpener.deviceExists(deviceId)
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Claims an interruption from a thread that does not own the writer: records the reason and closes
     * the line so the blocked `read` returns; the capture thread then finishes the interruption itself.
     */
    private fun requestInterruption(reason: InterruptionReason, episodeId: Long) {
        val line = synchronized(episodeLock) {
            if (!isCurrent(episodeId) || interruptionHandled.get()) return
            if (!pendingFailure.compareAndSet(null, reason)) return
            pendingFailureSinceMillis.set(clock())
            currentLine
        }
        logger.warn { "recorder interruption requested reason=$reason" }
        try {
            line?.close()
        } catch (e: Exception) {
            logger.warn(redacted(e)) { "error closing line after interruption" }
        }
    }

    /** Capture-thread exit on a failure it observed itself, or one the watchdog claimed first. */
    private fun onCaptureThreadFailure(default: InterruptionReason, episodeId: Long) {
        synchronized(episodeLock) {
            if (!isCurrent(episodeId)) return
            if (!interruptionHandled.get()) pendingFailure.compareAndSet(null, default)
            val reason = pendingFailure.get() ?: return // null: a deliberate stop() closed the line
            finishInterruption(reason, episodeId)
        }
    }

    /**
     * Finalizes the part and publishes `Interrupted` exactly once per episode. Normally runs on the capture
     * thread after its last write, so the counter read here is exactly what part N holds and where the
     * next part continues from. Does nothing when the episode was already ended, replaced or stopped.
     */
    private fun finishInterruption(reason: InterruptionReason, episodeId: Long) {
        synchronized(episodeLock) {
            if (!isCurrent(episodeId)) return
            if (!interruptionHandled.compareAndSet(false, true)) return

            val atSample = globalSampleCounter.total
            logger.warn { "recorder interrupted at sample=$atSample reason=$reason" }

            val line = currentLine
            currentLine = null
            try {
                line?.close()
            } catch (e: Exception) {
                logger.warn(redacted(e)) { "error closing line after interruption" }
            }

            val writer = currentWriter
            currentWriter = null
            if (writer != null) {
                try {
                    writer.finalizeAndRename()
                } catch (e: Exception) {
                    logger.error(redacted(e)) { "failed to finalize part file after interruption" }
                }
            }

            _state.value = RecorderState.Interrupted(atSample, reason)
        }
    }

    private fun isCurrent(episodeId: Long) = episode.get() == episodeId

    private fun onWriteFailure(cause: Exception) {
        synchronized(episodeLock) {
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
    }

    // endregion

    // region line lifecycle helpers

    private fun canStartFresh(state: RecorderState): Boolean =
        state == RecorderState.Idle || state == RecorderState.Stopped || state is RecorderState.Failed

    private fun resetSessionState() {
        episode.incrementAndGet()
        captureThread = null
        currentLine = null
        currentWriter = null
        globalSampleCounter.reset()
        _writtenSamples.value = 0
        _captureFormat.value = null
        _levels.value = 0f
        _fastLevels.tryEmit(0f)
        levelMeter.reset()
        fastMeter.reset()
        silenceAccumMs = 0.0
        _silenceWarning.value = false
        lastSuccessfulReadAtMillis.set(0L)
        pendingFailure.set(null)
        currentDeviceId = null
        interruptionHandled.set(true)
    }

    private fun beginEpisode(): Long {
        pendingFailure.set(null)
        lastSuccessfulReadAtMillis.set(clock())
        interruptionHandled.set(false)
        return episode.incrementAndGet()
    }

    /**
     * Ends the current episode and returns the writer still to be finalized (null if none, or if the
     * watchdog's wedged path already finalized it), so exactly one party ever finalizes a part.
     */
    private fun stopCaptureThreadAndLine(): WavFileWriter? {
        val thread: Thread?
        val line: TargetDataLine?
        val writer: WavFileWriter?
        synchronized(episodeLock) {
            // Mark "no interruption episode in flight" *before* closing the line: closing it deliberately
            // here unblocks the capture thread's blocked read() the same way a real device loss would, and
            // its error path would otherwise race this shutdown and misreport a clean stop() as an
            // Interrupted(DEVICE_LOST). Bumping the episode also retires any wedged capture thread.
            episode.incrementAndGet()
            interruptionHandled.set(true)
            thread = captureThread
            captureThread = null
            line = currentLine
            currentLine = null
            writer = currentWriter
            currentWriter = null
        }
        try {
            line?.stop()
            line?.close()
        } catch (e: Exception) {
            logger.warn(redacted(e)) { "error closing line on stop" }
        }
        thread?.interrupt()
        try {
            thread?.join(THREAD_JOIN_TIMEOUT_MS)
        } catch (_: InterruptedException) {
            // best-effort join; the daemon thread will exit once the (now-closed) line surfaces the failure
        }
        return writer
    }

    /**
     * Opens the first negotiated format the device actually accepts (§5.3.1). `isLineSupported`
     * only pre-filters (DirectSound says yes to everything), so each candidate gets exactly one
     * open attempt and a format rejection moves on to the next. A denied microphone also shows up
     * as "unsupported" for every format on Windows, so a full pass ending in DeviceUnavailable is
     * expected there.
     */
    private fun openLine(device: AudioInputDevice): Pair<TargetDataLine, CaptureFormat> {
        if (!lineOpener.deviceExists(device.id)) throw AudioException(AudioError.DeviceUnavailable(device.id))

        var lastRejection: Exception? = null
        for (candidate in CaptureFormatNegotiator.candidates(isSupported = { lineOpener.isLineSupported(device.id, it) })) {
            try {
                val line = lineOpener.open(device.id, candidate, LINE_BUFFER_MS)
                logger.info { "capture format chosen: $candidate" }
                return line to candidate
            } catch (e: Exception) {
                // LineUnavailableException is also "busy"/denied; all candidates failing is DeviceUnavailable.
                if (e !is LineUnavailableException && e !is IllegalArgumentException) {
                    throw AudioException(AudioError.RecordingStartFailed(e.message ?: "failed to open line"), e)
                }
                logger.info { "capture format rejected: $candidate (${e::class.simpleName})" }
                lastRejection = e
            }
        }
        throw lastRejection?.let { AudioException(AudioError.DeviceUnavailable(device.id), it) }
            ?: AudioException(AudioError.NoSupportedPcmFormat(device.id))
    }

    // endregion

    /** Carries the [AudioError] for the state machine; message and cause keep the log useful. */
    private class AudioException(val error: AudioError, cause: Throwable? = null) :
        Exception("${error::class.simpleName}: ${cause?.message ?: error}", cause)

    /**
     * Watchdog thresholds (§8.5). A read gap beyond [deviceCheckGapMs] is a device loss when the device is
     * gone or the line is closed; with the device present it must exceed [stallMs] (a slow fsync or a
     * process pause is not a lost microphone). A tick later than [pollMs] x [latePollFactor] resets the
     * baseline (unless the device is gone), at most [maxConsecutiveLateTicks] times in a row, so sustained
     * load cannot hide a stalled read. [wedgedCaptureThreadMs] bounds how long a claimed failure waits for the capture thread.
     */
    data class WatchdogTiming(
        val pollMs: Long = 200L,
        val deviceCheckGapMs: Long = 1000L,
        val stallMs: Long = 3000L,
        val latePollFactor: Int = 2,
        val wedgedCaptureThreadMs: Long = 2000L,
        val maxConsecutiveLateTicks: Int = 3,
    )

    private companion object {
        const val CHUNK_MS = 50
        const val LINE_BUFFER_MS = 500
        const val HEADER_PATCH_INTERVAL_MS = 5000L
        const val SILENCE_WARNING_MS = 5000.0
        const val THREAD_JOIN_TIMEOUT_MS = 2000L
    }
}
