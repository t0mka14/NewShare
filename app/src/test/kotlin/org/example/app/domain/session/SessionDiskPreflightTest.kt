package org.example.app.domain.session

import org.example.app.domain.audio.CaptureFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionDiskPreflightTest {

    @Test
    fun `no-master protocols only need the small fixed allowance`() {
        assertEquals(SessionDiskPreflight.NO_MASTER_MINIMUM_BYTES, SessionDiskPreflight.requiredBytes(null))
    }

    @Test
    fun `target format required bytes is at least the 1 GiB floor`() {
        // 8kHz/8-bit/mono: 30 min * 8000 bytes/s * 2 (margin) = 28,800,000 bytes, well under 1 GiB.
        val tiny = CaptureFormat(sampleRate = 8_000, bits = 8, channels = 1)
        assertEquals(SessionDiskPreflight.MINIMUM_BYTES_AT_TARGET_FORMAT, SessionDiskPreflight.requiredBytes(tiny))
    }

    @Test
    fun `preferred format (48kHz-16bit-mono) stays at the 1 GiB floor — 30 min is only ~165 MiB`() {
        // 48000 * 2 bytes/frame * 1800s = 172,800,000 bytes (~165 MiB) master; even at the 2x
        // margin that is still under 1 GiB, so every realistic single-channel session is
        // floor-bound — this is a deliberate, spec-derived invariant, not an oversight.
        val required = SessionDiskPreflight.requiredBytes(CaptureFormat.PREFERRED)
        assertEquals(SessionDiskPreflight.MINIMUM_BYTES_AT_TARGET_FORMAT, required)
    }

    @Test
    fun `a high-bandwidth format scales with the margin multiplier above the floor`() {
        // 192kHz/32-bit/stereo: frameSize 8 bytes, well above the floor even before margin.
        val highBandwidth = CaptureFormat(sampleRate = 192_000, bits = 32, channels = 2)
        val expectedMasterBytes = 192_000L * 8 * (30 * 60)
        val required = SessionDiskPreflight.requiredBytes(highBandwidth)
        assertTrue(required > SessionDiskPreflight.MINIMUM_BYTES_AT_TARGET_FORMAT)
        assertEquals(expectedMasterBytes * 2, required)
    }

    @Test
    fun `higher sample rate requires proportionally more bytes once above the floor`() {
        val format192k = CaptureFormat(sampleRate = 192_000, bits = 32, channels = 2)
        val format96k = CaptureFormat(sampleRate = 96_000, bits = 32, channels = 2)
        assertEquals(SessionDiskPreflight.requiredBytes(format96k) * 2, SessionDiskPreflight.requiredBytes(format192k))
    }
}
