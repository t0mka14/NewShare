package org.example.app.domain.video

import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Path

/**
 * Camera capture for VIDEO tasks.
 *
 * Deliberately much smaller than [org.example.app.domain.audio.ContinuousSessionRecorder]:
 * audio is one continuous master WAV that owns session time, and every timeline offset is
 * read from its sample counter. Video owns no time. It has no sample clock, no part files
 * and no device-loss resume path — VIDEO start/stop are anchored to the *audio* clock by
 * the timeline events the caller logs around them.
 *
 * Lifecycle: `startPreview` opens the camera and streams [previewFrames] without touching
 * disk; `startRecording`/`stopRecording` toggle file writing on the same open camera, so
 * recording begins and ends on an exact frame boundary with no process restart; `stop`
 * closes everything.
 */
interface SessionVideoRecorder {
    val state: StateFlow<VideoRecorderState>

    /**
     * Latest captured frame as JPEG bytes, conflated — a slow consumer sees the newest
     * frame and misses the ones in between, by design.
     *
     * Frames are dropped for *display* only; every frame still reaches the file while
     * recording. Back-pressuring the capture reader would block the capture process on
     * write, overflow the driver's real-time buffer, and drop frames at the camera itself.
     *
     * Carries encoded bytes rather than an image type because the domain layer must not
     * depend on the UI layer; `ui/VideoSurface.kt` decodes.
     */
    val previewFrames: StateFlow<ByteArray?>

    /** The format capture was started with; null until [startPreview] succeeds. */
    val captureFormat: StateFlow<VideoCaptureFormat?>

    /** Frames written to the current file. Reset by each [startRecording]. */
    val framesWritten: StateFlow<Long>

    /** Open the camera and start streaming [previewFrames]. State: Idle → Previewing. */
    suspend fun startPreview(device: VideoInputDevice)

    /** Begin appending frames to [file]. State: Previewing → Recording. */
    suspend fun startRecording(file: Path)

    /** Stop appending frames; the preview keeps running. State: Recording → Previewing. */
    suspend fun stopRecording()

    /** Close the file if open, terminate capture, release the camera. State: any → Stopped. */
    suspend fun stop()
}

sealed interface VideoRecorderState {
    data object Idle : VideoRecorderState

    /** Camera open, frames flowing to [SessionVideoRecorder.previewFrames], nothing on disk. */
    data object Previewing : VideoRecorderState

    /** Camera open, frames flowing to both the preview and the open file. */
    data object Recording : VideoRecorderState
    data object Stopped : VideoRecorderState
    data class Failed(val error: VideoError) : VideoRecorderState
}

/** Video error taxonomy (§11), mirroring [org.example.app.domain.audio.AudioError]. */
sealed interface VideoError {
    /** No camera matched, or the camera is held by another application. */
    data class DeviceUnavailable(val deviceId: String) : VideoError

    /** The bundled capture binary is missing from the install (§9 `native/` payload). */
    data object CaptureBackendUnavailable : VideoError

    /** The camera was found but capture would not start; [detail] is the backend's message. */
    data class CaptureStartFailed(val detail: String) : VideoError

    /** Capture died mid-recording — the process exited, or the stream ended early. */
    data class CaptureInterrupted(val detail: String) : VideoError
    data class DiskWriteFailed(val detail: String) : VideoError
}
