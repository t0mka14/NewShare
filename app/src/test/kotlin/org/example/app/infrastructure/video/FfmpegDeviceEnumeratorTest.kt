package org.example.app.infrastructure.video

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fixtures are real ffmpeg listings. Both platforms print to stderr and then exit non-zero,
 * and both interleave things we must not pick up — audio devices, and dshow's "Alternative
 * name" lines, which look like device entries but are DirectShow monikers.
 */
class FfmpegDeviceEnumeratorTest {

    private val dshowListing = """
        [dshow @ 000001d0] "Logitech PTZ Pro 2" (video)
        [dshow @ 000001d0]   Alternative name "@device_pnp_\\?\usb#vid_046d&pid_0853&mi_00#a&1"
        [dshow @ 000001d0] "Integrated Webcam" (video)
        [dshow @ 000001d0]   Alternative name "@device_pnp_\\?\usb#vid_0bda&pid_5650&mi_00#b&2"
        [dshow @ 000001d0] "Microphone (Logitech PTZ Pro 2)" (audio)
        [dshow @ 000001d0]   Alternative name "@device_cm_{33D9A762}\Microphone"
        dummy: Immediate exit requested
    """.trimIndent()

    private val avfoundationListing = """
        [AVFoundation indev @ 0x7f9] AVFoundation video devices:
        [AVFoundation indev @ 0x7f9] [0] FaceTime HD Camera
        [AVFoundation indev @ 0x7f9] [1] Logitech PTZ Pro 2
        [AVFoundation indev @ 0x7f9] [2] Capture screen 0
        [AVFoundation indev @ 0x7f9] AVFoundation audio devices:
        [AVFoundation indev @ 0x7f9] [0] MacBook Pro Microphone
        : Input/output error
    """.trimIndent()

    @Test
    fun `parses dshow video devices and ignores audio and alternative names`() {
        val devices = FfmpegDeviceEnumerator.parseDshow(dshowListing)

        assertEquals(listOf("Logitech PTZ Pro 2", "Integrated Webcam"), devices.map { it.name })
        assertEquals(listOf(0, 1), devices.map { it.platformIndex })
        assertTrue(devices.all { it.eligible })
    }

    /**
     * Two cameras of the same model report the same FriendlyName. `-video_device_number`
     * counts among devices sharing a name, so the ordinal must restart per name rather than
     * follow the position in the overall listing.
     */
    @Test
    fun `numbers duplicate dshow names per name, not per listing position`() {
        val listing = """
            [dshow @ 1] "Logitech PTZ Pro 2" (video)
            [dshow @ 1] "Integrated Webcam" (video)
            [dshow @ 1] "Logitech PTZ Pro 2" (video)
        """.trimIndent()

        val devices = FfmpegDeviceEnumerator.parseDshow(listing)

        assertEquals(listOf(0, 0, 1), devices.map { it.nameOrdinal })
        assertEquals(listOf(0, 1, 2), devices.map { it.platformIndex })
        assertEquals(3, devices.map { it.id }.toSet().size, "ids stay unique")
    }

    @Test
    fun `parses avfoundation video devices and stops at the audio section`() {
        val devices = FfmpegDeviceEnumerator.parseAvFoundation(avfoundationListing)

        assertEquals(
            listOf("FaceTime HD Camera", "Logitech PTZ Pro 2", "Capture screen 0"),
            devices.map { it.name },
        )
        assertEquals(listOf(0, 1, 2), devices.map { it.platformIndex })
    }

    @Test
    fun `returns nothing for unparseable output`() {
        assertEquals(emptyList<Any>(), FfmpegDeviceEnumerator.parseDshow("ffmpeg: command not found"))
        assertEquals(emptyList<Any>(), FfmpegDeviceEnumerator.parseAvFoundation(""))
    }
}
