package org.example.app.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.example.app.domain.CoroutineDispatchers
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.audio.AudioInputDeviceProvider
import org.example.app.domain.audio.GainApplyResult
import org.example.app.domain.audio.MIC_GAIN_RANGE
import org.example.app.domain.audio.MicGainApplier
import org.example.app.domain.audio.MicNames
import org.example.app.domain.config.ConfigError
import org.example.app.domain.config.ConfigurationRepository
import org.example.app.domain.config.RefreshConfigurationUseCase
import org.example.app.domain.config.RemoteConfig
import org.example.app.domain.settings.AppSettings
import org.example.app.domain.settings.AppSettingsRepository

interface SettingsComponent {
    val state: Value<State>

    fun onDeviceSelected(deviceId: String)
    fun onInstallationIdChanged(value: String)
    fun onLanguageSelected(language: String)
    fun onRefreshClicked()

    /** Slider dragged (live); nothing is persisted or applied until [onMicGainChangeFinished]. */
    fun onMicGainChanged(value: Int)

    /** Slider released: the value becomes the local override and is set on the selected device. */
    fun onMicGainChangeFinished()

    /** Drop the local override; the config's default (if any for this device) is re-applied. */
    fun onMicGainReset()

    data class State(
        val availableDevices: List<AudioInputDevice> = emptyList(),
        val selectedDeviceId: String? = null,
        val installationId: String = "",
        val availableLanguages: List<String> = emptyList(),
        val selectedLanguage: String? = null,
        val refreshInProgress: Boolean = false,
        /** Localized-string key for the last refresh outcome (`settings.refresh.*` /
         * `error.config.*`), `null` before any refresh has been attempted. */
        val lastRefreshResultKey: String? = null,
        /**
         * Slider position (§13 decision 44): the level read from the selected device every time
         * the screen opens — so a change made in the OS sound panel shows up here — falling back
         * to the local override, else the config default, only while the device's level is not
         * readable; null until any of those is known.
         */
        val micGain: Int? = null,
        /** The persisted local override (`AppSettings.micGain`). */
        val micGainOverride: Int? = null,
        /** The config's `defaultMicGain`, only when the selected device matches `defaultMicName`. */
        val configMicGain: Int? = null,
        /** The selected device's level could be read through the OS — the slider is enabled. */
        val micGainControllable: Boolean = false,
    )
}

/**
 * §3 Settings screen: mic device, microphone level, installation ID, language — persisted
 * immediately (write-through) via [AppSettingsRepository], never `Preferences` (§12). Refresh
 * delegates to [RefreshConfigurationUseCase] and maps its result to a localized string key (§7)
 * the UI resolves via `LocalizedStringProvider` — this component never holds display text.
 *
 * The level slider follows the legacy Settings screen: every time the screen opens it reads the
 * selected device's current level (asynchronously — the slider stays disabled until the read
 * completes, as the legacy `micSliderEnabled`) and shows *that*, so a level changed in the OS
 * sound panel is reflected; a released drag persists the value as the local override and sets it
 * on the device at once through [MicGainApplier]. Which value a session applies is decided in
 * the applier (override, else config), independent of what the device reads at the moment.
 */
class DefaultSettingsComponent(
    componentContext: ComponentContext,
    private val deviceProvider: AudioInputDeviceProvider,
    private val settingsRepository: AppSettingsRepository,
    private val configurationRepository: ConfigurationRepository,
    private val refreshConfigurationUseCase: RefreshConfigurationUseCase,
    private val micGainApplier: MicGainApplier,
    private val dispatchers: CoroutineDispatchers,
) : SettingsComponent, ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    /** Last level read from (or successfully written to) the selected device. */
    private var deviceLevel: Int? = null

    private val _state = MutableValue(initialState())
    override val state: Value<SettingsComponent.State> = _state

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        scope.launch(dispatchers.main) {
            configurationRepository.activeConfig.collect { config ->
                _state.value = _state.value.copy(
                    availableLanguages = config?.languages.orEmpty(),
                    configMicGain = configGainFor(config, selectedDevice()),
                ).withSliderPosition()
            }
        }
        readDeviceLevel()
    }

    private fun initialState(): SettingsComponent.State {
        val saved = settingsRepository.read()
        val devices = deviceProvider.availableDevices()
        val config = configurationRepository.activeConfig.value
        val selected = devices.firstOrNull { it.id == saved?.micDeviceId }
        return SettingsComponent.State(
            availableDevices = devices,
            selectedDeviceId = saved?.micDeviceId,
            installationId = saved?.installationId.orEmpty(),
            availableLanguages = config?.languages.orEmpty(),
            selectedLanguage = saved?.language,
            micGainOverride = saved?.micGain?.coerceIn(MIC_GAIN_RANGE),
            configMicGain = configGainFor(config, selected),
        ).withSliderPosition()
    }

    override fun onDeviceSelected(deviceId: String) {
        val device = _state.value.availableDevices.firstOrNull { it.id == deviceId }
        _state.value = _state.value.copy(
            selectedDeviceId = deviceId,
            configMicGain = configGainFor(configurationRepository.activeConfig.value, device),
        )
        persist()
        readDeviceLevel()
    }

    override fun onInstallationIdChanged(value: String) {
        _state.value = _state.value.copy(installationId = value)
        persist()
    }

    override fun onLanguageSelected(language: String) {
        _state.value = _state.value.copy(selectedLanguage = language)
        persist()
    }

    override fun onRefreshClicked() {
        _state.value = _state.value.copy(refreshInProgress = true, lastRefreshResultKey = null)
        scope.launch(dispatchers.main) {
            val resultKey = when (val result = refreshConfigurationUseCase.refresh()) {
                is RefreshConfigurationUseCase.Result.Success -> "settings.refresh.success"
                is RefreshConfigurationUseCase.Result.OfflineUsingCache -> "settings.refresh.success"
                is RefreshConfigurationUseCase.Result.Failed -> configErrorKey(result.error)
            }
            _state.value = _state.value.copy(refreshInProgress = false, lastRefreshResultKey = resultKey)
        }
    }

    override fun onMicGainChanged(value: Int) {
        _state.value = _state.value.copy(micGain = value.coerceIn(MIC_GAIN_RANGE))
    }

    override fun onMicGainChangeFinished() {
        val value = _state.value.micGain ?: return
        _state.value = _state.value.copy(micGainOverride = value)
        persist()
        val device = selectedDevice() ?: return
        scope.launch(dispatchers.main) {
            if (micGainApplier.set(device, value) is GainApplyResult.Applied) deviceLevel = value
        }
    }

    override fun onMicGainReset() {
        _state.value = _state.value.copy(micGainOverride = null).withSliderPosition()
        persist()
        val device = selectedDevice() ?: return
        val configured = _state.value.configMicGain ?: return
        scope.launch(dispatchers.main) {
            if (micGainApplier.set(device, configured) is GainApplyResult.Applied) {
                deviceLevel = configured
                _state.value = _state.value.withSliderPosition()
            }
        }
    }

    private fun selectedDevice(): AudioInputDevice? =
        _state.value.availableDevices.firstOrNull { it.id == _state.value.selectedDeviceId }

    /** The config's level, but only for the device it names (the applier's rule, §13/44). */
    private fun configGainFor(config: RemoteConfig?, device: AudioInputDevice?): Int? {
        val gain = config?.defaultMicGain ?: return null
        if (device == null || !MicNames.matches(config.defaultMicName, device)) return null
        return gain.coerceIn(MIC_GAIN_RANGE)
    }

    private fun SettingsComponent.State.withSliderPosition(): SettingsComponent.State =
        copy(micGain = deviceLevel ?: micGainOverride ?: configMicGain)

    /** Legacy `findMicGain`: disable the slider, read the device's level off the UI thread, re-enable. */
    private fun readDeviceLevel() {
        deviceLevel = null
        _state.value = _state.value.copy(micGainControllable = false).withSliderPosition()
        val device = selectedDevice() ?: return
        scope.launch(dispatchers.main) {
            val level = micGainApplier.read(device)
            if (selectedDevice()?.id != device.id) return@launch // the examiner moved on
            deviceLevel = level
            _state.value = _state.value.copy(micGainControllable = level != null).withSliderPosition()
        }
    }

    private fun persist() {
        val current = _state.value
        // Merge onto the saved settings: `cameraDeviceId` is owned by the camera flow, not this screen.
        val saved = settingsRepository.read() ?: AppSettings()
        settingsRepository.write(
            saved.copy(
                micDeviceId = current.selectedDeviceId,
                installationId = current.installationId.ifBlank { null },
                language = current.selectedLanguage,
                micGain = current.micGainOverride,
            ),
        )
    }

    private fun configErrorKey(error: ConfigError): String = when (error) {
        ConfigError.InstallationIdMissing -> "error.config.installationIdMissing"
        ConfigError.InstallationIdRejected -> "error.config.installationIdRejected"
        ConfigError.NetworkUnavailableNoCache -> "error.config.networkUnavailable"
        is ConfigError.SchemaUnsupported -> "error.config.schemaUnsupported"
        is ConfigError.ValidationFailed -> "error.config.validationFailed"
        is ConfigError.Malformed -> "error.config.malformed"
        is ConfigError.ServerError -> "settings.refresh.failed"
    }
}
