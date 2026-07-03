package org.example.app.infrastructure.audio

import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.audio.AudioInputDeviceProvider
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.Mixer
import javax.sound.sampled.TargetDataLine

/**
 * Enumerates capture-capable mixers via `javax.sound.sampled` (§5.3, §5.3.1).
 * Devices exposing no `TargetDataLine` at all are skipped entirely; devices
 * that expose one but support no usable PCM format are still listed, marked
 * `eligible = false`, so Settings can show and grey them out.
 *
 * Thin by design (hardware-facing) — the actual negotiation logic lives in
 * the hardware-independent [CaptureFormatNegotiator].
 */
class JvmAudioInputDeviceProvider : AudioInputDeviceProvider {
    override fun availableDevices(): List<AudioInputDevice> =
        AudioSystem.getMixerInfo().mapNotNull { info -> describe(info) }

    private fun describe(info: Mixer.Info): AudioInputDevice? {
        val mixer = try {
            AudioSystem.getMixer(info)
        } catch (e: Exception) {
            return null
        }

        val hasTargetDataLine = try {
            mixer.targetLineInfo.any { TargetDataLine::class.java.isAssignableFrom(it.lineClass) }
        } catch (e: Exception) {
            false
        }
        if (!hasTargetDataLine) return null

        val eligible = try {
            CaptureFormatNegotiator.negotiate(isSupported = { candidate ->
                mixer.isLineSupported(DataLine.Info(TargetDataLine::class.java, candidate.toJavaAudioFormat()))
            }) != null
        } catch (e: Exception) {
            false
        }

        return AudioInputDevice(id = MixerIds.stableId(info), name = info.name, eligible = eligible)
    }
}
