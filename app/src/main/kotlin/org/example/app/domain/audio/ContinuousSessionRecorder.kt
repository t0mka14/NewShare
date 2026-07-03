package org.example.app.domain.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Path

/**
 * The single source of truth for audio time (§5.3.1). One continuous master WAV
 * per session; every timeline event offset is read from [writtenSamples].
 *
 * Contract:
 * - [startMonitoring] opens the input line and emits [levels] without writing to disk
 *   (calibration screen).
 * - [startWriting] atomically switches monitoring → writing on the same open line,
 *   without dropping or duplicating samples. Writes go to `<file>.partial.wav`-style
 *   staging with a periodically patched valid header (§8.4); atomic rename on [stop].
 * - [stop] flushes, finalizes the WAV header, closes the line.
 * - Capture runs on a dedicated thread reading ≤100 ms chunks; line buffer + chunk
 *   stay ≤200 ms. A watchdog detects read starvation (§8.5).
 */
interface ContinuousSessionRecorder {
    val state: StateFlow<RecorderState>

    /**
     * Samples handed to the file writer so far. Primary reference for all timeline
     * event offsets (§8.3). 0 while not writing.
     */
    val writtenSamples: StateFlow<Long>

    /**
     * RMS level, linear, normalized 0.0–1.0 full scale, smoothed over ~300 ms.
     * Emitted in both Monitoring and Writing states. One formula everywhere.
     */
    val levels: Flow<Float>

    /**
     * The format actually negotiated for the current device (§5.3.1), available from
     * Monitoring onward; null while Idle. Persisted to examination.json at session start.
     */
    val captureFormat: StateFlow<CaptureFormat?>

    /** Open the device line and start level monitoring. State: Idle → Monitoring. */
    suspend fun startMonitoring(device: AudioInputDevice)

    /** Switch to writing the master file. State: Monitoring → Writing. */
    suspend fun startWriting(masterFile: Path)

    /**
     * Resume after an interruption (§8.5): open [device] and continue into a new
     * master part file in that device's negotiated format. State: Interrupted → Writing.
     */
    suspend fun resume(device: AudioInputDevice, partFile: Path)

    /** Flush, finalize header, close the line. State: any → Stopped. */
    suspend fun stop()
}

sealed interface RecorderState {
    data object Idle : RecorderState
    data object Monitoring : RecorderState
    data object Writing : RecorderState

    /** Device lost mid-session (§8.5); [atSample] is the offset of the interruption. */
    data class Interrupted(val atSample: Long, val reason: InterruptionReason) : RecorderState
    data object Stopped : RecorderState
    data class Failed(val error: AudioError) : RecorderState
}

enum class InterruptionReason { DEVICE_LOST, READ_STARVATION }

/** Audio error taxonomy for the recorder (§11). */
sealed interface AudioError {
    data class DeviceUnavailable(val deviceId: String) : AudioError
    data class NoSupportedPcmFormat(val deviceId: String) : AudioError
    data class RecordingStartFailed(val detail: String) : AudioError
    data class DiskWriteFailed(val detail: String) : AudioError
}
