package org.example.app.domain.audio

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext
import org.example.app.domain.CoroutineDispatchers
import org.example.app.domain.config.RemoteConfig
import org.example.app.domain.settings.AppSettingsRepository

private val logger = KotlinLogging.logger {}

/** Valid range of a microphone level, in the Windows sound panel's units. */
val MIC_GAIN_RANGE: IntRange = 0..100

/**
 * The one place that knows which microphone level to apply (§13 decision 44):
 *
 * 1. a level set on the Settings slider (`AppSettings.micGain`) wins — "a key locally modified
 *    in Settings wins" — and applies to whichever device is being opened;
 * 2. otherwise the config's `defaultMicGain`, but only for the device whose name matches
 *    `defaultMicName` ([MicNames.matches]);
 * 3. otherwise nothing: the OS level is left alone.
 *
 * Applied by [MicGainReapplyingRecorder] right before a device is opened, and by the Settings
 * screen when the examiner moves the slider. Never throws — a level that cannot be set is
 * logged and the recording proceeds at whatever level the device has.
 */
class MicGainApplier(
    private val gainControl: AudioInputGainControl,
    private val settingsRepository: AppSettingsRepository,
    private val dispatchers: CoroutineDispatchers,
) {
    fun effectiveGain(device: AudioInputDevice, config: RemoteConfig?): Int? {
        settingsRepository.read()?.micGain?.let { return it.coerceIn(MIC_GAIN_RANGE) }
        val configured = config?.defaultMicGain ?: return null
        return configured.coerceIn(MIC_GAIN_RANGE).takeIf { MicNames.matches(config.defaultMicName, device) }
    }

    /** Apply [effectiveGain] to [device], if there is one. */
    suspend fun applyBeforeOpen(device: AudioInputDevice, config: RemoteConfig?) {
        val gain = effectiveGain(device, config) ?: return
        set(device, gain)
    }

    suspend fun set(device: AudioInputDevice, percent: Int): GainApplyResult {
        val level = percent.coerceIn(MIC_GAIN_RANGE)
        val result = withContext(dispatchers.io) {
            try {
                gainControl.setInputGain(device.name, level)
            } catch (e: Exception) {
                GainApplyResult.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
        when (result) {
            is GainApplyResult.Applied ->
                logger.info { "mic gain applied: device='${device.name}' level=$level mixer='${result.mixerName}' controls=${result.controlsSet}" }
            GainApplyResult.NoMatchingDevice ->
                logger.warn { "mic gain not applied: no port mixer matches device='${device.name}'" }
            is GainApplyResult.NoVolumeControl ->
                logger.warn { "mic gain not applied: mixer='${result.mixerName}' has no input volume control" }
            is GainApplyResult.Failed ->
                logger.warn { "mic gain not applied for device='${device.name}': ${result.detail}" }
        }
        return result
    }

    suspend fun read(device: AudioInputDevice): Int? = withContext(dispatchers.io) {
        try {
            gainControl.readInputGain(device.name)
        } catch (e: Exception) {
            logger.warn(e) { "could not read mic gain for device='${device.name}'" }
            null
        }
    }
}
