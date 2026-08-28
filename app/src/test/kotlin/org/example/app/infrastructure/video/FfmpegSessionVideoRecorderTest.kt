package org.example.app.infrastructure.video

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import org.example.app.domain.CoroutineDispatchers
import org.example.app.domain.video.VideoCaptureFormat
import org.example.app.domain.video.VideoError
import org.example.app.domain.video.VideoInputDevice
import org.example.app.domain.video.VideoRecorderState
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Drives the real capture pipeline — process spawn, pipe, reader thread, frame splitter, file
 * sink and teardown — from ffmpeg's synthetic `testsrc` instead of a camera, so it runs on any
 * machine and in CI. Only the platform input arguments are substituted; everything downstream
 * is the production code path.
 */
class FfmpegSessionVideoRecorderTest {

    private val dispatchers = object : CoroutineDispatchers {
        override val main = Dispatchers.Unconfined
        override val default = Dispatchers.Default
        override val io = Dispatchers.IO
    }

    /**
     * `testsrc` produces raw frames, so passthrough rungs always fail and the encoding rungs
     * always succeed — which is exactly the shape of a webcam with no MJPEG mode.
     *
     * [rejectFormats] additionally makes the source refuse specific requested modes, standing
     * in for a camera that cannot produce the resolution being asked for.
     */
    private class SyntheticCaptureInput(
        private val fps: Int,
        private val rejectFormats: Set<Pair<Int, Int>> = emptySet(),
        /** Modes the "camera" advertises; empty means it reports nothing probeable. */
        private val advertisedModes: List<CameraMode> = emptyList(),
        /** Defaults to the DirectShow answer, since that is the backend the copy path exists for. */
        override val supportsPassthrough: Boolean = true,
    ) : CaptureInput {
        val attempts = mutableListOf<CaptureAttempt>()

        override val isSupported = true

        override fun args(device: VideoInputDevice, attempt: CaptureAttempt): List<String> {
            attempts += attempt
            val format = attempt.format
            if (format != null && (format.width to format.height) in rejectFormats) {
                // An input ffmpeg cannot open, mirroring a driver refusing an unsupported mode.
                return listOf("-f", "lavfi", "-i", "no_such_filter_source")
            }
            val size = format?.let { "${it.width}x${it.height}" } ?: "320x240"
            return listOf("-f", "lavfi", "-i", "testsrc=size=$size:rate=$fps")
        }

        // A trivially successful command; its output is ignored in favour of parseModes.
        override fun probeArgs(device: VideoInputDevice): List<String>? =
            if (advertisedModes.isEmpty()) null else listOf("-hide_banner", "-version")

        override fun parseModes(output: String): List<CameraMode> = advertisedModes
    }

    private val device = VideoInputDevice(id = "synthetic", name = "synthetic", nameOrdinal = 0, platformIndex = 0, eligible = true)

    private fun recorder(
        fps: Int = 15,
        input: CaptureInput = SyntheticCaptureInput(fps),
        requested: VideoCaptureFormat = VideoCaptureFormat(320, 240, fps),
    ) = FfmpegSessionVideoRecorder(
        dispatchers = dispatchers,
        locator = FfmpegBinaryLocator(),
        hostOs = org.example.app.infrastructure.HostOs.current,
        requestedFormat = requested,
        captureInput = input,
    )

    private fun assumeFfmpegAvailable() {
        assumeTrue(FfmpegBinaryLocator().locate() != null, "no ffmpeg binary; run :app:unpackVideoNatives")
    }

    @Test
    fun `preview streams frames without touching disk`() = runBlocking {
        assumeFfmpegAvailable()
        val recorder = recorder()
        try {
            recorder.startPreview(device)

            assertEquals(VideoRecorderState.Previewing, recorder.state.value)
            assertNotNull(recorder.previewFrames.value, "a preview frame should have arrived")
            assertTrue(isJpeg(recorder.previewFrames.value!!), "preview frames are whole JPEGs")
            assertEquals(0L, recorder.framesWritten.value)
        } finally {
            recorder.stop()
        }
    }

    @Test
    fun `records whole frames between start and stop`(@TempDir tempDir: Path) = runBlocking {
        assumeFfmpegAvailable()
        val recorder = recorder(fps = 15)
        val file = tempDir.resolve("take").resolve("video.mjpeg")
        try {
            recorder.startPreview(device)
            recorder.startRecording(file)
            assertEquals(VideoRecorderState.Recording, recorder.state.value)

            withTimeout(15_000) {
                while (recorder.framesWritten.value < 10) delay(50)
            }
            recorder.stopRecording()

            assertEquals(VideoRecorderState.Previewing, recorder.state.value, "preview survives stopping a take")
            val written = recorder.framesWritten.value
            val bytes = Files.readAllBytes(file)

            assertTrue(bytes.isNotEmpty())
            assertTrue(isJpeg(bytes), "the file is an MJPEG elementary stream, so it opens with SOI")
            // Every byte handed to the file is a whole frame: counting SOI markers must agree
            // with the recorder's own tally, and the stream must end on an EOI.
            assertEquals(written.toInt(), countFrames(bytes), "no partial frame reached the file")
            assertEquals(0xFF, bytes[bytes.size - 2].toInt() and 0xFF)
            assertEquals(0xD9, bytes[bytes.size - 1].toInt() and 0xFF)
        } finally {
            recorder.stop()
        }
    }

    @Test
    fun `stop releases everything and clears the preview`(@TempDir tempDir: Path) = runBlocking {
        assumeFfmpegAvailable()
        val recorder = recorder()
        recorder.startPreview(device)
        recorder.startRecording(tempDir.resolve("video.mjpeg"))

        recorder.stop()

        assertEquals(VideoRecorderState.Stopped, recorder.state.value)
        assertEquals(null, recorder.previewFrames.value)
        assertEquals(0L, recorder.framesWritten.value)
    }

    @Test
    fun `reports a missing capture backend rather than throwing`() = runBlocking {
        val recorder = FfmpegSessionVideoRecorder(
            dispatchers = dispatchers,
            locator = FfmpegBinaryLocator(installRoot = Path.of("/nonexistent-install-root")),
            hostOs = org.example.app.infrastructure.HostOs.current,
            requestedFormat = VideoCaptureFormat.PREFERRED,
            captureInput = SyntheticCaptureInput(15),
        )

        // Only meaningful when PATH has no ffmpeg either; otherwise the locator legitimately
        // finds one and there is nothing to assert.
        assumeTrue(FfmpegBinaryLocator(installRoot = Path.of("/nonexistent-install-root")).locate() == null)

        recorder.startPreview(device)

        assertEquals(
            VideoRecorderState.Failed(VideoError.CaptureBackendUnavailable),
            recorder.state.value,
        )
    }

    /**
     * The reason this ladder exists: an ordinary webcam that cannot deliver the requested mode
     * used to fail outright, because the only fallback dropped the MJPEG request and never
     * relaxed the resolution.
     */
    @Test
    fun `falls back to a mode the camera can actually produce`() = runBlocking {
        assumeFfmpegAvailable()
        val input = SyntheticCaptureInput(fps = 15, rejectFormats = setOf(320 to 240))
        val recorder = recorder(input = input)
        try {
            recorder.startPreview(device)

            assertEquals(VideoRecorderState.Previewing, recorder.state.value)
            assertNotNull(recorder.previewFrames.value)
            assertTrue(
                input.attempts.any { it.format == null || (it.format.width to it.format.height) != (320 to 240) },
                "should have moved past the rejected mode; tried ${input.attempts.map { it.describe }}",
            )
        } finally {
            recorder.stop()
        }
    }

    /** Passthrough is always tried before encoding, so a byte copy is never given up early. */
    @Test
    fun `prefers MJPEG passthrough before asking ffmpeg to encode`() = runBlocking {
        assumeFfmpegAvailable()
        val input = SyntheticCaptureInput(fps = 15)
        val recorder = recorder(input = input)
        try {
            recorder.startPreview(device)

            assertTrue(input.attempts.first().passthrough, "the first rung must be passthrough")
            assertEquals(VideoRecorderState.Previewing, recorder.state.value)
        } finally {
            recorder.stop()
        }
    }

    /**
     * On AVFoundation the copy path cannot work — `-vcodec mjpeg` is accepted but the frames stay
     * raw — and trying it anyway pushed ~100 MB/s of non-JPEG bytes through the reader and the
     * frame splitter for the whole first-frame timeout, once per resolution in the ladder.
     */
    @Test
    fun `skips passthrough where the platform cannot deliver MJPEG`() = runBlocking {
        assumeFfmpegAvailable()
        val input = SyntheticCaptureInput(fps = 15, supportsPassthrough = false)
        val recorder = recorder(input = input)
        try {
            recorder.startPreview(device)

            assertEquals(VideoRecorderState.Previewing, recorder.state.value)
            assertTrue(
                input.attempts.none { it.passthrough },
                "no rung may ask for passthrough; tried ${input.attempts.map { it.describe }}",
            )
        } finally {
            recorder.stop()
        }
    }

    /**
     * The freeze this ladder caused on macOS. AVFoundation declares `1000k tbr` for a camera whose
     * rate it could not estimate, and with no output rate ffmpeg duplicates frames to fill it:
     * 1080p30 arrived as ~1100 fps and ~20 MB/s, ffmpeg alone taking nine of twelve cores, and the
     * VIDEO screen stopped responding without logging anything.
     *
     * A 1000 fps source stands in for that declaration. The delivered rate must follow the
     * requested one, not the source's.
     */
    @Test
    fun `caps the delivered frame rate when the source declares a much higher one`() = runBlocking {
        assumeFfmpegAvailable()
        val input = SyntheticCaptureInput(fps = 1_000, supportsPassthrough = false)
        val recorder = recorder(input = input, requested = VideoCaptureFormat(320, 240, 15))
        try {
            recorder.startPreview(device)
            assertEquals(VideoRecorderState.Previewing, recorder.state.value)

            var frames = 0L
            val collector = launch { recorder.previewFrames.collect { if (it != null) frames++ } }
            delay(2_000)
            collector.cancel()

            // Two seconds at the requested 15 fps is ~30 frames; uncapped it was two thousand.
            // The ceiling is deliberately loose — this is about the order of magnitude.
            assertTrue(frames in 5..300, "expected roughly 30 frames in 2s, got $frames")
        } finally {
            recorder.stop()
        }
    }

    /**
     * The guard that abandons a rung once it has read a lot of bytes without producing a frame
     * must not mistake a genuinely fast camera for a stream that is not MJPEG.
     */
    @Test
    fun `does not abandon a high-bitrate stream that really is MJPEG`() = runBlocking {
        assumeFfmpegAvailable()
        val input = SyntheticCaptureInput(fps = 120, supportsPassthrough = false)
        val recorder = recorder(input = input, requested = VideoCaptureFormat(1280, 720, 120))
        try {
            recorder.startPreview(device)

            assertEquals(VideoRecorderState.Previewing, recorder.state.value)
            assertNotNull(recorder.previewFrames.value)
        } finally {
            recorder.stop()
        }
    }

    /**
     * A bare MJPEG stream has no timestamps, so the remux takes its frame rate from here.
     * Recording what was *requested* rather than what was negotiated would speed up or slow
     * down footage from any camera that did not honour the request.
     */
    @Test
    fun `reports the format actually negotiated, not the one requested`() = runBlocking {
        assumeFfmpegAvailable()
        val input = SyntheticCaptureInput(fps = 15, rejectFormats = setOf(320 to 240))
        val recorder = recorder(input = input)
        try {
            recorder.startPreview(device)

            val negotiated = recorder.captureFormat.value
            assertNotNull(negotiated)
            assertNotEquals(
                VideoCaptureFormat(320, 240, 15),
                negotiated,
                "the requested mode was rejected, so it must not be reported as the capture format",
            )
            // The preview frames must genuinely be that size.
            assertEquals(negotiated!!.width, jpegWidth(recorder.previewFrames.value!!))
        } finally {
            recorder.stop()
        }
    }

    /**
     * The camera's own best mode is used, even when it is not one of the fixed fallback rungs.
     * A blind ladder would have dropped this camera to 640x480.
     */
    @Test
    fun `uses a mode the camera advertises rather than a fixed rung`() = runBlocking {
        assumeFfmpegAvailable()
        val input = SyntheticCaptureInput(
            fps = 15,
            advertisedModes = listOf(
                CameraMode(640, 480, 15, mjpeg = true),
                CameraMode(1600, 1200, 15, mjpeg = true),
            ),
        )
        // The production target is 1080p; 1600x1200 is the advertised mode closest to it.
        val recorder = recorder(input = input, requested = VideoCaptureFormat(1920, 1080, 15))
        try {
            recorder.startPreview(device)

            assertEquals(VideoRecorderState.Previewing, recorder.state.value)
            assertEquals(
                1600 to 1200,
                input.attempts.first().format?.let { it.width to it.height },
                "the closest advertised mode to the target must be tried first; " +
                    "tried ${input.attempts.map { it.describe }}",
            )
            assertEquals(1600, recorder.captureFormat.value?.width)
            assertEquals(1600, jpegWidth(recorder.previewFrames.value!!))
        } finally {
            recorder.stop()
        }
    }

    /** An advertised mode that turns out not to work must not strand the session. */
    @Test
    fun `still starts when the advertised best mode does not actually open`() = runBlocking {
        assumeFfmpegAvailable()
        val input = SyntheticCaptureInput(
            fps = 15,
            rejectFormats = setOf(1600 to 1200),
            advertisedModes = listOf(CameraMode(1600, 1200, 15, mjpeg = true)),
        )
        val recorder = recorder(input = input, requested = VideoCaptureFormat(1920, 1080, 15))
        try {
            recorder.startPreview(device)

            assertEquals(VideoRecorderState.Previewing, recorder.state.value)
            assertNotNull(recorder.previewFrames.value)
        } finally {
            recorder.stop()
        }
    }

    /** Reads the frame width out of the JPEG SOF0 marker, to check the reported size is real. */
    private fun jpegWidth(jpeg: ByteArray): Int {
        var i = 2
        while (i + 9 < jpeg.size) {
            if ((jpeg[i].toInt() and 0xFF) != 0xFF) { i++; continue }
            val marker = jpeg[i + 1].toInt() and 0xFF
            if (marker in 0xC0..0xC3) {
                return ((jpeg[i + 7].toInt() and 0xFF) shl 8) or (jpeg[i + 8].toInt() and 0xFF)
            }
            val length = ((jpeg[i + 2].toInt() and 0xFF) shl 8) or (jpeg[i + 3].toInt() and 0xFF)
            i += 2 + length
        }
        error("no SOF marker in frame")
    }

    private fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size > 3 && (bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xFF) == 0xD8

    private fun countFrames(bytes: ByteArray): Int {
        var count = 0
        val collector = MjpegFrameSplitter { _, _, _ -> count++ }
        collector.append(bytes, 0, bytes.size)
        return count
    }
}
