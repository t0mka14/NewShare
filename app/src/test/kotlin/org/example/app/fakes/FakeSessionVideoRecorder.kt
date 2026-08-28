package org.example.app.fakes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.example.app.domain.video.SessionVideoRecorder
import org.example.app.domain.video.VideoCaptureFormat
import org.example.app.domain.video.VideoError
import org.example.app.domain.video.VideoInputDevice
import org.example.app.domain.video.VideoRecorderState
import java.nio.file.Files
import java.nio.file.Path

/**
 * In-memory [SessionVideoRecorder]. Shaped like [FakeContinuousSessionRecorder]: it records
 * what was asked of it and lets a test drive frames and failures by hand.
 *
 * [emitFrame] writes to the recording file when one is open, so a scenario can assert that a
 * take produced bytes without a camera or an ffmpeg process anywhere in sight.
 */
class FakeSessionVideoRecorder : SessionVideoRecorder {

    private val _state = MutableStateFlow<VideoRecorderState>(VideoRecorderState.Idle)
    override val state: StateFlow<VideoRecorderState> = _state.asStateFlow()

    private val _previewFrames = MutableStateFlow<ByteArray?>(null)
    override val previewFrames: StateFlow<ByteArray?> = _previewFrames.asStateFlow()

    private val _captureFormat = MutableStateFlow<VideoCaptureFormat?>(null)
    override val captureFormat: StateFlow<VideoCaptureFormat?> = _captureFormat.asStateFlow()

    private val _framesWritten = MutableStateFlow(0L)
    override val framesWritten: StateFlow<Long> = _framesWritten.asStateFlow()

    val previewStarts = mutableListOf<VideoInputDevice>()
    val recordingStarts = mutableListOf<Path>()
    var stopRecordingCallCount = 0
        private set
    var stopCallCount = 0
        private set

    /** The file currently being recorded, or null between takes. */
    var openFile: Path? = null
        private set

    /** What [startPreview] will report as negotiated; set it to model a camera that is not 1080p30. */
    var negotiatedFormat: VideoCaptureFormat = VideoCaptureFormat.PREFERRED

    override suspend fun startPreview(device: VideoInputDevice) {
        previewStarts += device
        _captureFormat.value = negotiatedFormat
        _state.value = VideoRecorderState.Previewing
    }

    override suspend fun startRecording(file: Path) {
        // .add, not `+=`: java.nio.file.Path is Iterable<Path>, so `+=` resolves to `plus`.
        recordingStarts.add(file)
        Files.createDirectories(file.parent)
        Files.write(file, ByteArray(0))
        openFile = file
        _framesWritten.value = 0L
        _state.value = VideoRecorderState.Recording
    }

    override suspend fun stopRecording() {
        stopRecordingCallCount++
        openFile = null
        if (_state.value == VideoRecorderState.Recording) _state.value = VideoRecorderState.Previewing
    }

    override suspend fun stop() {
        stopCallCount++
        openFile = null
        _previewFrames.value = null
        _framesWritten.value = 0L
        _state.value = VideoRecorderState.Stopped
    }

    /** Delivers one frame: to the preview always, and to the open file while recording. */
    fun emitFrame(bytes: ByteArray = SAMPLE_FRAME) {
        _previewFrames.value = bytes
        openFile?.let { file ->
            Files.newOutputStream(file, java.nio.file.StandardOpenOption.APPEND).use { it.write(bytes) }
            _framesWritten.value += 1
        }
    }

    fun simulateFailure(error: VideoError) {
        openFile = null
        _state.value = VideoRecorderState.Failed(error)
    }

    companion object {
        /** A minimal but structurally valid JPEG: SOI, a quantisation table, SOS, EOI. */
        val SAMPLE_FRAME: ByteArray = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xDB.toByte(), 0x00, 0x04, 0x00, 0x00,
            0xFF.toByte(), 0xDA.toByte(), 0x00, 0x04, 0x00, 0x00,
            0x11, 0x22, 0x33,
            0xFF.toByte(), 0xD9.toByte(),
        )
    }
}
