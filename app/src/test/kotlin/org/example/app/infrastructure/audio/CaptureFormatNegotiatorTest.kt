package org.example.app.infrastructure.audio

import org.example.app.domain.audio.CaptureFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CaptureFormatNegotiatorTest {

    @Test
    fun `preferred format is used when the device supports it`() {
        val result = CaptureFormatNegotiator.negotiate(isSupported = { it == CaptureFormat.PREFERRED })
        assertEquals(CaptureFormat.PREFERRED, result)
    }

    @Test
    fun `falls back to nearest supported sample rate at 16-bit mono`() {
        val supported = setOf(CaptureFormat(44_100, 16, 1))
        val result = CaptureFormatNegotiator.negotiate(isSupported = { it in supported })
        assertEquals(CaptureFormat(44_100, 16, 1), result)
    }

    @Test
    fun `higher sample rate wins a tie in distance from the preferred rate`() {
        // 40_000 and 56_000 are both 8_000 away from the preferred 48_000.
        val supported = setOf(
            CaptureFormat(40_000, 16, 1),
            CaptureFormat(56_000, 16, 1),
        )
        val result = CaptureFormatNegotiator.negotiate(
            candidateRates = listOf(40_000, 56_000),
            isSupported = { it in supported },
        )
        assertEquals(CaptureFormat(56_000, 16, 1), result, "higher rate should win the distance tie")
    }

    @Test
    fun `falls back to stereo when mono is unavailable at the nearest rate`() {
        val supported = setOf(CaptureFormat(48_000, 16, 2))
        val result = CaptureFormatNegotiator.negotiate(isSupported = { it in supported })
        assertEquals(CaptureFormat(48_000, 16, 2), result)
    }

    @Test
    fun `mono is preferred over stereo at the same sample rate and bit depth`() {
        val supported = setOf(
            CaptureFormat(44_100, 16, 1),
            CaptureFormat(44_100, 16, 2),
        )
        val result = CaptureFormatNegotiator.negotiate(isSupported = { it in supported })
        assertEquals(CaptureFormat(44_100, 16, 1), result)
    }

    @Test
    fun `non-16-bit formats are never negotiated — decision 32`() {
        // The nearest rate (44_100) offers only 8-bit; 16-bit exists at a farther
        // rate (32_000). 8-bit must never be picked (§13 decision 32: the pipeline
        // is 16-bit end to end), so the farther 16-bit format wins.
        val supported = setOf(
            CaptureFormat(44_100, 8, 2),
            CaptureFormat(32_000, 16, 1),
        )
        val result = CaptureFormatNegotiator.negotiate(isSupported = { it in supported })
        assertEquals(CaptureFormat(32_000, 16, 1), result)
    }

    @Test
    fun `a device with no 16-bit PCM format at all is ineligible — decision 32`() {
        val supported = setOf(
            CaptureFormat(48_000, 24, 1),
            CaptureFormat(48_000, 8, 1),
            CaptureFormat(48_000, 32, 2),
        )
        val result = CaptureFormatNegotiator.negotiate(isSupported = { it in supported })
        assertNull(result, "24/8/32-bit-only devices must be reported ineligible")
    }

    @Test
    fun `returns null when the device offers no usable PCM format — ineligible`() {
        val result = CaptureFormatNegotiator.negotiate(isSupported = { false })
        assertNull(result)
    }

    @Test
    fun `only probes the preferred format plus candidate rates supplied by the caller`() {
        val probedRates = mutableListOf<Int>()
        CaptureFormatNegotiator.negotiate(
            candidateRates = listOf(16_000, 8_000),
            isSupported = { candidate -> probedRates.add(candidate.sampleRate); false },
        )
        // The preferred format is always probed first (short-circuit fast path),
        // then exactly the supplied candidate rates — nothing from the default table.
        assertEquals(setOf(CaptureFormat.PREFERRED.sampleRate, 16_000, 8_000), probedRates.toSet())
    }
}
