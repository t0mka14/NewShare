package org.example.app.fakes

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.example.app.domain.audio.AudioError
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.audio.CaptureFormat
import org.example.app.domain.audio.ContinuousSessionRecorder
import org.example.app.domain.audio.InterruptionReason
import org.example.app.domain.audio.RecorderState
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * Deterministic [ContinuousSessionRecorder] for tests (§10.3). Synthesizes PCM
 * time rather than any real audio:
 *
 * - `writtenSamples` advances only while [state] is [RecorderState.Writing], and
 *   only in response to the injected [FakeClock] moving forward — this fake
 *   registers a [FakeClock.onAdvance] listener at construction, so a single
 *   `clock.advance(duration)` call in a test is the one source of truth for both
 *   domain time and the sample counter (`samples = elapsedTime * sampleRate`,
 *   §5.3.1). This is also how UI-test scenario helpers keep the Compose
 *   `mainClock` and this counter in lockstep (§10.3): advance both clocks
 *   together and the recorder's `writtenSamples` reflects it automatically.
 * - [levels] is test-controlled via [emitLevel]; nothing is emitted on its own.
 * - [simulateInterruption] / [simulateFailure] drive workflow scenario 6
 *   (device loss mid-take → dialog → resume on a new device).
 *
 * This fake is deliberately permissive about call ordering (it does not enforce
 * the state machine documented on [ContinuousSessionRecorder]) — component/UI
 * tests are expected to exercise realistic sequences; strict-ordering bugs in
 * production code belong to a real-recorder integration test, not this fake.
 */
class FakeContinuousSessionRecorder(
    private val clock: FakeClock,
    initialNegotiatedFormat: CaptureFormat = CaptureFormat.PREFERRED,
) : ContinuousSessionRecorder {

    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    override val state: StateFlow<RecorderState> = _state.asStateFlow()

    private val _writtenSamples = MutableStateFlow(0L)
    override val writtenSamples: StateFlow<Long> = _writtenSamples.asStateFlow()

    private val _levels = MutableSharedFlow<Float>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val levels: Flow<Float> = _levels.asSharedFlow()

    private val _captureFormat = MutableStateFlow<CaptureFormat?>(null)
    override val captureFormat: StateFlow<CaptureFormat?> = _captureFormat.asStateFlow()

    /** Format negotiated on the *next* startMonitoring/resume call (§5.3.1 fallback tests). */
    var negotiatedFormat: CaptureFormat = initialNegotiatedFormat

    // ---- test-observable call records ----
    val monitoringStarts = mutableListOf<AudioInputDevice>()
    val writingStarts = mutableListOf<Path>()
    val resumeCalls = mutableListOf<ResumeCall>()
    var stopCallCount = 0
        private set

    private var lastAccountedInstant: Instant = clock.now()

    init {
        clock.onAdvance { accrueSamples() }
    }

    override suspend fun startMonitoring(device: AudioInputDevice) {
        monitoringStarts += device
        lastAccountedInstant = clock.now()
        _captureFormat.value = negotiatedFormat
        _state.value = RecorderState.Monitoring
    }

    override suspend fun startWriting(masterFile: Path) {
        // NB: java.nio.file.Path implements Iterable<Path> (its path segments), which
        // makes `list += path` ambiguous between plusAssign(T) and plusAssign(Iterable<T>)
        // — use .add(...) explicitly to avoid it.
        writingStarts.add(masterFile)
        lastAccountedInstant = clock.now()
        _writtenSamples.value = 0L
        _state.value = RecorderState.Writing
    }

    override suspend fun resume(device: AudioInputDevice, partFile: Path) {
        resumeCalls += ResumeCall(device, partFile)
        lastAccountedInstant = clock.now()
        _captureFormat.value = negotiatedFormat
        _state.value = RecorderState.Writing
    }

    override suspend fun stop() {
        accrueSamples()
        stopCallCount++
        _state.value = RecorderState.Stopped
    }

    /** Accrue `elapsed * sampleRate` samples since the last accounted instant, while Writing. */
    private fun accrueSamples() {
        if (_state.value !is RecorderState.Writing) {
            lastAccountedInstant = clock.now()
            return
        }
        val now = clock.now()
        val elapsedNanos = Duration.between(lastAccountedInstant, now).toNanos()
        if (elapsedNanos <= 0) return
        val sampleRate = (_captureFormat.value ?: negotiatedFormat).sampleRate
        val deltaSamples = elapsedNanos * sampleRate / 1_000_000_000L
        if (deltaSamples > 0) {
            _writtenSamples.value += deltaSamples
            // Only advance the accounted instant by the time actually converted to
            // samples, so sub-sample remainders aren't lost across many small ticks.
            lastAccountedInstant = lastAccountedInstant.plusNanos(deltaSamples * 1_000_000_000L / sampleRate)
        }
    }

    // ---- test hooks ----

    /** Push one reading to [levels] subscribers (linear RMS, 0.0-1.0, §5.3.1). */
    fun emitLevel(level: Float) {
        require(level in 0f..1f) { "level must be normalized 0.0-1.0, was $level" }
        _levels.tryEmit(level)
    }

    /**
     * Simulate device loss / read starvation mid-session (§8.5): state moves to
     * [RecorderState.Interrupted] at the current `writtenSamples` offset. The
     * component under test is expected to react by auto-rejecting the current
     * take and showing the reconnect dialog — this fake only flips recorder
     * state, it does not touch the timeline itself.
     */
    fun simulateInterruption(reason: InterruptionReason = InterruptionReason.DEVICE_LOST) {
        accrueSamples()
        _state.value = RecorderState.Interrupted(atSample = _writtenSamples.value, reason = reason)
    }

    /** Simulate a hard recorder failure (§11 AudioError taxonomy). */
    fun simulateFailure(error: AudioError) {
        _state.value = RecorderState.Failed(error)
    }

    data class ResumeCall(val device: AudioInputDevice, val partFile: Path)
}
