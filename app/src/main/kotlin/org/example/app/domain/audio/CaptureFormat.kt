package org.example.app.domain.audio

import kotlinx.serialization.Serializable

/**
 * Negotiated PCM capture format (§5.3.1). Preferred: 48 kHz / 16-bit / mono.
 * Persisted in examination.json at session start (§8.10) so crash recovery can
 * reconstruct the WAV header (§8.4).
 */
@Serializable
data class CaptureFormat(
    val sampleRate: Int,
    val bits: Int,
    val channels: Int,
) {
    companion object {
        val PREFERRED = CaptureFormat(sampleRate = 48_000, bits = 16, channels = 1)
    }
}
