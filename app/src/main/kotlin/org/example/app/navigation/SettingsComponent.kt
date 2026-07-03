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
import org.example.app.domain.config.ConfigError
import org.example.app.domain.config.ConfigurationRepository
import org.example.app.domain.config.RefreshConfigurationUseCase
import org.example.app.domain.settings.AppSettings
import org.example.app.domain.settings.AppSettingsRepository

interface SettingsComponent {
    val state: Value<State>

    fun onDeviceSelected(deviceId: String)
    fun onInstallationIdChanged(value: String)
    fun onLanguageSelected(language: String)
    fun onRefreshClicked()

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
    )
}

/**
 * §3 Settings screen: mic device, installation ID, language — persisted immediately
 * (write-through) via [AppSettingsRepository], never `Preferences` (§12). Refresh delegates
 * to [RefreshConfigurationUseCase] and maps its result to a localized string key (§7) the UI
 * resolves via `LocalizedStringProvider` — this component never holds display text.
 */
class DefaultSettingsComponent(
    componentContext: ComponentContext,
    private val deviceProvider: AudioInputDeviceProvider,
    private val settingsRepository: AppSettingsRepository,
    private val configurationRepository: ConfigurationRepository,
    private val refreshConfigurationUseCase: RefreshConfigurationUseCase,
    private val dispatchers: CoroutineDispatchers,
) : SettingsComponent, ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    private val _state = MutableValue(initialState())
    override val state: Value<SettingsComponent.State> = _state

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        scope.launch(dispatchers.main) {
            configurationRepository.activeConfig.collect { config ->
                _state.value = _state.value.copy(availableLanguages = config?.languages.orEmpty())
            }
        }
    }

    private fun initialState(): SettingsComponent.State {
        val saved = settingsRepository.read()
        val devices = deviceProvider.availableDevices()
        return SettingsComponent.State(
            availableDevices = devices,
            selectedDeviceId = saved?.micDeviceId,
            installationId = saved?.installationId.orEmpty(),
            availableLanguages = configurationRepository.activeConfig.value?.languages.orEmpty(),
            selectedLanguage = saved?.language,
        )
    }

    override fun onDeviceSelected(deviceId: String) {
        _state.value = _state.value.copy(selectedDeviceId = deviceId)
        persist()
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

    private fun persist() {
        val current = _state.value
        settingsRepository.write(
            AppSettings(
                micDeviceId = current.selectedDeviceId,
                installationId = current.installationId.ifBlank { null },
                language = current.selectedLanguage,
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
