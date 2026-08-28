package org.example.app.infrastructure.video

import org.example.app.domain.video.VideoCaptureFormat
import org.example.app.domain.video.VideoInputDevice
import org.example.app.infrastructure.HostOs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The platform capture arguments, checked without a camera. These are pure string building, and
 * they are where a per-platform mistake is cheapest to catch — on the wrong host the same mistake
 * only shows up as an unexplained delay or a frozen screen.
 */
class PlatformCaptureInputTest {

    private val device = VideoInputDevice(
        id = "cam", name = "Some Camera", nameOrdinal = 0, platformIndex = 2, eligible = true,
    )
    private val format = VideoCaptureFormat(1920, 1080, 30)

    /**
     * AVFoundation accepts `-vcodec mjpeg` and then hands over raw `uyvy422` frames labelled
     * mjpeg, so `-c:v copy` copies raw video — measured at ~100 MB/s with not one JPEG marker in
     * it. There is nothing to pass through, so the rung must not be offered.
     */
    @Test
    fun `macOS does not offer MJPEG passthrough`() {
        assertFalse(PlatformCaptureInput(HostOs.MAC).supportsPassthrough)
    }

    /** DirectShow exposes a UVC camera's MJPEG pin, which is the case the copy path exists for. */
    @Test
    fun `Windows offers MJPEG passthrough`() {
        assertTrue(PlatformCaptureInput(HostOs.WINDOWS).supportsPassthrough)
    }

    @Test
    fun `Windows passthrough asks dshow for the camera's mjpeg pin`() {
        val args = PlatformCaptureInput(HostOs.WINDOWS)
            .args(device, CaptureAttempt(format, passthrough = true))

        assertTrue(args.containsInOrder("-vcodec", "mjpeg"), "dshow selects the MJPEG pin: $args")
        assertTrue(args.containsInOrder("-f", "dshow"), args.toString())
        assertTrue(args.containsInOrder("-video_size", "1920x1080"), args.toString())
    }

    @Test
    fun `Windows encoded path does not select a camera codec`() {
        val args = PlatformCaptureInput(HostOs.WINDOWS)
            .args(device, CaptureAttempt(format, passthrough = false))

        assertFalse(args.contains("-vcodec"), args.toString())
    }

    @Test
    fun `macOS addresses the device by index and keeps capture video-only`() {
        val args = PlatformCaptureInput(HostOs.MAC).args(device, CaptureAttempt(format, passthrough = false))

        assertTrue(args.containsInOrder("-f", "avfoundation"), args.toString())
        assertTrue(args.containsInOrder("-i", "2:none"), "video index 2, no audio: $args")
        assertFalse(args.contains("-vcodec"), args.toString())
    }

    /** The last rung constrains nothing, which is the only rung an unknown camera is sure to open. */
    @Test
    fun `a null format requests neither a size nor a rate`() {
        val args = PlatformCaptureInput(HostOs.MAC).args(device, CaptureAttempt(null, passthrough = false))

        assertFalse(args.contains("-video_size"), args.toString())
        assertFalse(args.contains("-framerate"), args.toString())
    }

    @Test
    fun `each platform names its ffmpeg backend`() {
        assertEquals("dshow", PlatformCaptureInput(HostOs.WINDOWS).backendName)
        assertEquals("avfoundation", PlatformCaptureInput(HostOs.MAC).backendName)
        assertEquals("unsupported", PlatformCaptureInput(HostOs.LINUX).backendName)
    }

    /** The name has to be the one actually passed to `-f`, or it describes nothing. */
    @Test
    fun `the backend name is the one passed to ffmpeg`() {
        listOf(HostOs.WINDOWS, HostOs.MAC).forEach { host ->
            val input = PlatformCaptureInput(host)
            val args = input.args(device, CaptureAttempt(format, passthrough = false))
            assertTrue(args.containsInOrder("-f", input.backendName), "$host: $args")
        }
    }

    @Test
    fun `capture is unsupported off Windows and macOS`() {
        assertFalse(PlatformCaptureInput(HostOs.LINUX).isSupported)
        assertFalse(PlatformCaptureInput(HostOs.LINUX).supportsPassthrough)
        assertEquals(null, PlatformCaptureInput(HostOs.LINUX).probeArgs(device))
    }

    private fun List<String>.containsInOrder(first: String, second: String): Boolean {
        val at = indexOf(first)
        return at >= 0 && getOrNull(at + 1) == second
    }
}
