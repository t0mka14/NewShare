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
import org.example.app.domain.config.ConfigurationRepository

interface MainMenuComponent {
    val state: Value<State>

    fun onStartProtocol()
    fun onUpload()
    fun onSettings()
    fun onSessionBrowser()

    /** `startEnabled` mirrors `ConfigurationRepository.activeConfig != null` (§8.6/§12: start
     * a new protocol is only possible with an active config). */
    data class State(val startEnabled: Boolean = false)
}

class DefaultMainMenuComponent(
    componentContext: ComponentContext,
    configurationRepository: ConfigurationRepository,
    dispatchers: CoroutineDispatchers,
    private val onStartProtocolClicked: () -> Unit,
    private val onUploadClicked: () -> Unit,
    private val onSettingsClicked: () -> Unit,
    private val onSessionBrowserClicked: () -> Unit,
) : MainMenuComponent, ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    private val _state = MutableValue(
        MainMenuComponent.State(startEnabled = configurationRepository.activeConfig.value != null),
    )
    override val state: Value<MainMenuComponent.State> = _state

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        scope.launch(dispatchers.main) {
            configurationRepository.activeConfig.collect { config ->
                _state.value = MainMenuComponent.State(startEnabled = config != null)
            }
        }
    }

    override fun onStartProtocol() {
        if (_state.value.startEnabled) onStartProtocolClicked()
    }

    override fun onUpload() = onUploadClicked()

    override fun onSettings() = onSettingsClicked()

    override fun onSessionBrowser() = onSessionBrowserClicked()
}
