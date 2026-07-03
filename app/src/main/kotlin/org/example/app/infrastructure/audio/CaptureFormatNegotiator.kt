package org.example.app.infrastructure.audio

import org.example.app.domain.audio.CaptureFormat
import kotlin.math.abs

/**
 * Pure format-negotiation logic (§5.3.1): preferred is PCM 48 kHz / 16-bit / mono;
 * otherwise the nearest supported PCM format, sample-rate distance first (ties
 * broken toward the higher rate), then bit depth, then channel count (mono
 * before stereo). No JVM audio-line dependency — [isSupported] is an injected
 * predicate so this is fully unit-testable against synthetic format tables.
 */
object CaptureFormatNegotiator {

    /** Common PCM sample rates to probe when the preferred format is unsupported. */
    val STANDARD_SAMPLE_RATES: List<Int> = listOf(
        48_000, 44_100, 96_000, 32_000, 22_050, 16_000, 11_025, 88_200, 8_000, 192_000, 176_400,
    )

    /** 16-bit preferred; the remaining depths are plausible PCM fallbacks. */
    val STANDARD_BIT_DEPTHS: List<Int> = listOf(16, 24, 32, 8)

    /** Mono preferred; stereo fallback (processing downmixes later, §8.8). */
    val STANDARD_CHANNEL_COUNTS: List<Int> = listOf(1, 2)

    /**
     * Returns the negotiated [CaptureFormat], or `null` if [isSupported] rejects
     * every candidate (device reported ineligible, §5.3.1).
     */
    fun negotiate(
        preferred: CaptureFormat = CaptureFormat.PREFERRED,
        candidateRates: List<Int> = STANDARD_SAMPLE_RATES,
        candidateBits: List<Int> = STANDARD_BIT_DEPTHS,
        candidateChannels: List<Int> = STANDARD_CHANNEL_COUNTS,
        isSupported: (CaptureFormat) -> Boolean,
    ): CaptureFormat? {
        if (isSupported(preferred)) return preferred

        val ratesByDistanceThenHigher = candidateRates.distinct()
            .sortedWith(compareBy({ abs(it - preferred.sampleRate) }, { -it }))

        for (rate in ratesByDistanceThenHigher) {
            for (bits in candidateBits) {
                for (channels in candidateChannels) {
                    val candidate = CaptureFormat(rate, bits, channels)
                    if (candidate == preferred) continue
                    if (isSupported(candidate)) return candidate
                }
            }
        }
        return null
    }
}
