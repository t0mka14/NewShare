package org.example.app.infrastructure.video

import org.example.app.domain.video.VideoCaptureFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Fixtures are verbatim ffmpeg banner lines, which is the only thing this parser ever sees. */
class NegotiatedFormatTest {

    @Test
    fun `reads the negotiated mode from a dshow MJPEG camera`() {
        val lines = listOf(
            "Input #0, dshow, from 'video=Logitech PTZ Pro 2':",
            "  Duration: N/A, start: 92951.489000, bitrate: N/A",
            "  Stream #0:0: Video: mjpeg (Baseline) (MJPG / 0x47504A4D), yuvj422p(pc, bt470bg/unknown/unknown), " +
                "1920x1080, 30 fps, 30 tbr, 10000k tbn",
        )

        assertEquals(VideoCaptureFormat(1920, 1080, 30), parseNegotiatedFormat(lines))
    }

    /** A camera that opened at something other than what we asked for — the whole point. */
    @Test
    fun `reads a mode that differs from the requested one`() {
        val lines = listOf(
            "  Stream #0:0: Video: rawvideo (YUY2 / 0x32595559), yuyv422, 1280x720, 25 fps, 25 tbr, 10000k tbn",
        )

        assertEquals(VideoCaptureFormat(1280, 720, 25), parseNegotiatedFormat(lines))
    }

    /** NTSC-rate webcams report 29.97; the remux needs a whole number. */
    @Test
    fun `rounds a fractional frame rate`() {
        val lines = listOf("  Stream #0:0: Video: mjpeg, yuvj422p, 640x480, 29.97 fps, 29.97 tbr, 1k tbn")

        assertEquals(VideoCaptureFormat(640, 480, 30), parseNegotiatedFormat(lines))
    }

    /**
     * `[SAR 1:1 DAR 16:9]` and the bitrate both sit near the resolution on this line; matching
     * either one would report a nonsense capture format.
     */
    @Test
    fun `is not confused by aspect ratio or bitrate on the same line`() {
        val lines = listOf(
            "  Stream #0:0: Video: h264 (Main), yuv420p(progressive), 1280x720 [SAR 1:1 DAR 16:9], " +
                "2500 kb/s, 24 fps, 24 tbr, 1200k tbn",
        )

        assertEquals(VideoCaptureFormat(1280, 720, 24), parseNegotiatedFormat(lines))
    }

    @Test
    fun `ignores audio streams`() {
        val lines = listOf(
            "  Stream #0:0: Audio: pcm_s16le, 48000 Hz, stereo, s16, 1536 kb/s",
            "  Stream #0:1: Video: mjpeg, yuvj422p, 800x600, 20 fps, 20 tbr, 10000k tbn",
        )

        assertEquals(VideoCaptureFormat(800, 600, 20), parseNegotiatedFormat(lines))
    }

    @Test
    fun `returns null when the rate is missing, so the caller keeps what it requested`() {
        val lines = listOf("  Stream #0:0: Video: mjpeg, yuvj422p, 1920x1080, 10000k tbn")

        assertNull(parseNegotiatedFormat(lines))
    }

    @Test
    fun `returns null for output that carries no stream banner`() {
        assertNull(parseNegotiatedFormat(listOf("Could not run graph: Input/output error")))
        assertNull(parseNegotiatedFormat(emptyList()))
    }
}
