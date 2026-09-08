package org.example.app.infrastructure.audio

import io.github.oshai.kotlinlogging.KotlinLogging
import org.example.app.domain.audio.AudioInputGainControl
import org.example.app.domain.audio.GainApplyResult
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.CompoundControl
import javax.sound.sampled.Control
import javax.sound.sampled.FloatControl
import javax.sound.sampled.Mixer
import javax.sound.sampled.Port
import kotlin.math.roundToInt

private val logger = KotlinLogging.logger {}

/**
 * Input level via Java Sound's *port* mixers (§13 decision 44).
 *
 * The `TargetDataLine` the recorder captures from has no volume control on Windows
 * (DirectSound). What does carry one is the separate mixer Java Sound creates per sound
 * device, named `"Port <device name>"` — winmm mixer API on Windows (endpoint volume),
 * CoreAudio on macOS, ALSA on Linux. Its input ports (`Port.Info.isSource`) hold a
 * `CompoundControl` with a linear `Volume` `FloatControl` (0.0–1.0), which is the same 0–100
 * level the Windows sound panel shows. The port mixer of the device is found with
 * [PortMixers.belongsTo] — the same endpoint name on Windows/macOS, the same ALSA card on Linux.
 *
 * Every volume control found on every input port of the matching mixer is written — on
 * Windows a capture endpoint typically exposes one, ALSA may expose several — **except boost
 * ports** (`Mic Boost`, `Microphone Boost`): a boost is a preamp stage the sound panels show
 * separately from the level, and it is usually coarse (ALSA's has three steps), so it is left
 * alone. Output ports are never touched. Reading returns the finest-grained remaining control
 * (smallest `precision`), which on a multi-control card is the real level rather than a
 * stepped one. Thin and hardware-facing, like [JvmAudioInputDeviceProvider].
 */
class JvmAudioInputGainControl : AudioInputGainControl {

    override fun setInputGain(deviceName: String, percent: Int): GainApplyResult {
        val fraction = percent.coerceIn(0, 100) / 100f
        return try {
            val mixers = matchingPortMixers(deviceName)
            if (mixers.isEmpty()) return GainApplyResult.NoMatchingDevice
            var controlsSet = 0
            var mixerName = ""
            for ((info, mixer) in mixers) {
                mixerName = info.name
                forEachInputVolumeControl(mixer) { control ->
                    control.value = control.minimum + (control.maximum - control.minimum) * fraction
                    controlsSet++
                }
            }
            if (controlsSet > 0) GainApplyResult.Applied(mixerName, controlsSet) else GainApplyResult.NoVolumeControl(mixerName)
        } catch (e: Exception) {
            GainApplyResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    override fun readInputGain(deviceName: String): Int? = try {
        var best: Pair<Float, Int>? = null // precision → level
        for ((_, mixer) in matchingPortMixers(deviceName)) {
            forEachInputVolumeControl(mixer) { control ->
                val span = control.maximum - control.minimum
                if (span > 0f) {
                    val level = ((control.value - control.minimum) / span * 100f).roundToInt().coerceIn(0, 100)
                    val precision = if (control.precision > 0f) control.precision else Float.MAX_VALUE
                    if (best == null || precision < best!!.first) best = precision to level
                }
            }
        }
        best?.second
    } catch (e: Exception) {
        logger.warn(e) { "reading mic gain failed for device='$deviceName'" }
        null
    }

    private fun matchingPortMixers(deviceName: String): List<Pair<Mixer.Info, Mixer>> =
        AudioSystem.getMixerInfo()
            .filter { info -> PortMixers.belongsTo(info.name, deviceName) }
            .mapNotNull { info ->
                try {
                    info to AudioSystem.getMixer(info)
                } catch (e: Exception) {
                    logger.warn(e) { "port mixer '${info.name}' could not be opened" }
                    null
                }
            }

    private fun forEachInputVolumeControl(mixer: Mixer, action: (FloatControl) -> Unit) {
        val inputPorts = mixer.sourceLineInfo.filterIsInstance<Port.Info>()
            .filter { it.isSource && !it.name.contains(BOOST_MARKER, ignoreCase = true) }
        for (portInfo in inputPorts) {
            val port = try {
                mixer.getLine(portInfo) as Port
            } catch (e: Exception) {
                logger.warn(e) { "port '${portInfo.name}' on '${mixer.mixerInfo.name}' unavailable" }
                continue
            }
            try {
                port.open()
                port.controls.forEach { control -> walkVolumeControls(control, action) }
            } catch (e: Exception) {
                logger.warn(e) { "port '${portInfo.name}' on '${mixer.mixerInfo.name}' failed" }
            } finally {
                try {
                    port.close()
                } catch (_: Exception) {
                    // best effort
                }
            }
        }
    }

    private fun walkVolumeControls(control: Control, action: (FloatControl) -> Unit) {
        when (control) {
            is CompoundControl -> control.memberControls.forEach { walkVolumeControls(it, action) }
            is FloatControl -> if (control.type == FloatControl.Type.VOLUME) action(control)
            else -> Unit
        }
    }

    companion object {
        /** Ports named like `Mic Boost` are a preamp, not the level — skipped. */
        const val BOOST_MARKER = "boost"
    }
}
