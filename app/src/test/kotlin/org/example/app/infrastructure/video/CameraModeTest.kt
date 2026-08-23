package org.example.app.infrastructure.video

import org.example.app.domain.video.VideoCaptureFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Fixtures are verbatim ffmpeg output; that is the only input these parsers ever see. */
class CameraModeTest {

    /** ffmpeg 6/7 dshow listing for a camera offering both MJPEG and raw YUY2 pins. */
    private val dshowListing = """
        [dshow @ 000001c8] DirectShow video device options (from video devices)
        [dshow @ 000001c8]  Pin "Capture" (alternative pin name "Capture")
        [dshow @ 000001c8]   vcodec=mjpeg  min s=640x480 fps=5 max s=640x480 fps=30
        [dshow @ 000001c8]   vcodec=mjpeg  min s=1280x720 fps=5 max s=1280x720 fps=30
        [dshow @ 000001c8]   vcodec=mjpeg  min s=1920x1080 fps=5 max s=1920x1080 fps=30
        [dshow @ 000001c8]   pixel_format=yuyv422  min s=640x480 fps=5 max s=640x480 fps=30
        [dshow @ 000001c8]   pixel_format=yuyv422  min s=1280x720 fps=5 max s=1280x720 fps=10
        video=Logitech PTZ Pro 2: Immediate exit requested
    """.trimIndent()

    /** The older spelling, which puts the format in trailing parentheses instead. */
    private val legacyDshowListing = """
        [dshow @ 0000019c]   min s=640x480 fps=5 max s=640x480 fps=30 (pixel_format=yuyv422)
        [dshow @ 0000019c]   min s=1600x1200 fps=5 max s=1600x1200 fps=15 (vcodec=mjpeg)
    """.trimIndent()

    private val avfoundationListing = """
        [AVFoundation indev @ 0x7fb] Selected video size (1x1) is not supported by the device.
        [AVFoundation indev @ 0x7fb] Supported modes:
        [AVFoundation indev @ 0x7fb]   320x240@[1.000000 30.000000]fps
        [AVFoundation indev @ 0x7fb]   640x480@[1.000000 30.000000]fps
        [AVFoundation indev @ 0x7fb]   1280x720@[1.000000 60.000000]fps
    """.trimIndent()

    @Test
    fun `reads dshow modes with their codec and ceiling`() {
        val modes = parseDshowModes(dshowListing)

        assertEquals(5, modes.size)
        assertTrue(CameraMode(1920, 1080, 30, mjpeg = true) in modes)
        assertTrue(CameraMode(1280, 720, 10, mjpeg = false) in modes, "the raw pin's lower fps ceiling is kept")
        assertEquals(3, modes.count { it.mjpeg })
    }

    @Test
    fun `reads the legacy dshow spelling with a trailing format`() {
        val modes = parseDshowModes(legacyDshowListing)

        assertEquals(
            listOf(CameraMode(640, 480, 30, mjpeg = false), CameraMode(1600, 1200, 15, mjpeg = true)),
            modes,
        )
    }

    @Test
    fun `reads avfoundation modes`() {
        val modes = parseAvFoundationModes(avfoundationListing)

        assertEquals(
            listOf(
                CameraMode(320, 240, 30, mjpeg = false),
                CameraMode(640, 480, 30, mjpeg = false),
                CameraMode(1280, 720, 60, mjpeg = false),
            ),
            modes,
        )
    }

    @Test
    fun `unparseable output yields no modes rather than nonsense`() {
        assertEquals(emptyList<CameraMode>(), parseDshowModes("Could not find video device"))
        assertEquals(emptyList<CameraMode>(), parseAvFoundationModes(""))
    }

    // region ranking

    @Test
    fun `an exact match for the target wins`() {
        val ranked = rankModes(parseDshowModes(dshowListing), VideoCaptureFormat(1920, 1080, 30))

        assertEquals(CameraMode(1920, 1080, 30, mjpeg = true), ranked.first())
    }

    /**
     * The case the blind ladder got wrong: a camera whose best mode is 1600x1200 should be
     * used at 1600x1200, not dropped to 480p because no rung happened to match.
     */
    @Test
    fun `an unusual best mode is chosen over a smaller standard one`() {
        val modes = listOf(
            CameraMode(640, 480, 30, mjpeg = true),
            CameraMode(1600, 1200, 15, mjpeg = true),
        )

        val ranked = rankModes(modes, VideoCaptureFormat(1920, 1080, 30))

        assertEquals(CameraMode(1600, 1200, 15, mjpeg = true), ranked.first())
    }

    /** A byte copy beats a closer resolution that would have to be re-encoded. */
    @Test
    fun `MJPEG modes outrank raw ones`() {
        val modes = listOf(
            CameraMode(1920, 1080, 30, mjpeg = false),
            CameraMode(1280, 720, 30, mjpeg = true),
        )

        val ranked = rankModes(modes, VideoCaptureFormat(1920, 1080, 30))

        assertTrue(ranked.first().mjpeg)
    }

    @Test
    fun `a mode never promises more frames than it can deliver`() {
        assertEquals(VideoCaptureFormat(1600, 1200, 15), CameraMode(1600, 1200, 15, true).toFormat(30))
        assertEquals(VideoCaptureFormat(1280, 720, 30), CameraMode(1280, 720, 60, true).toFormat(30))
    }

    // endregion
}
