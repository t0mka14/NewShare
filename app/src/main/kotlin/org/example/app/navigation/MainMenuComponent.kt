package org.example.app.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.example.app.domain.CoroutineDispatchers
import org.example.app.domain.config.ConfigurationRepository
import org.example.app.domain.config.RemoteConfig
import java.awt.Desktop
import java.net.URI

private val logger = KotlinLogging.logger {}

interface MainMenuComponent {
    val state: Value<State>

    fun onStartProtocol()
    fun onUpload()
    fun onSettings()
    fun onSessionBrowser()

    /** Opens one protocol's instruction manual (§3 "Get protocol PDF"). */
    fun onOpenProtocolPdf(url: String)

    /** Language switch from the top-right flag; persists and re-resolves every screen's text. */
    fun onLanguageSelected(language: String)

    /** One protocol that declares a `protocolInstructionsPdfUrl`. */
    data class ProtocolPdf(val protocolName: String, val url: String)

    /**
     * `startEnabled` mirrors `ConfigurationRepository.activeConfig != null` (§8.6/§12: start
     * a new protocol is only possible with an active config). `protocolPdfs` lists the
     * protocols that declare an instruction-manual URL — empty disables the PDF button, one
     * opens directly, several make the button offer a picker.
     */
    data class State(
        val startEnabled: Boolean = false,
        val protocolPdfs: List<ProtocolPdf> = emptyList(),
    )
}

class DefaultMainMenuComponent(
    componentContext: ComponentContext,
    configurationRepository: ConfigurationRepository,
    dispatchers: CoroutineDispatchers,
    private val onStartProtocolClicked: () -> Unit,
    private val onUploadClicked: () -> Unit,
    private val onSettingsClicked: () -> Unit,
    private val onSessionBrowserClicked: () -> Unit,
    private val onLanguageSelectedClicked: (String) -> Unit,
) : MainMenuComponent, ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    private val _state = MutableValue(stateOf(configurationRepository.activeConfig.value))
    override val state: Value<MainMenuComponent.State> = _state

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        scope.launch(dispatchers.main) {
            configurationRepository.activeConfig.collect { config -> _state.value = stateOf(config) }
        }
    }

    private fun stateOf(config: RemoteConfig?) = MainMenuComponent.State(
        startEnabled = config != null,
        protocolPdfs = config?.protocols.orEmpty().mapNotNull { protocol ->
            protocol.protocolInstructionsPdfUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { MainMenuComponent.ProtocolPdf(protocol.name, it) }
        },
    )

    override fun onStartProtocol() {
        if (_state.value.startEnabled) onStartProtocolClicked()
    }

    override fun onUpload() = onUploadClicked()

    override fun onSettings() = onSettingsClicked()

    override fun onSessionBrowser() = onSessionBrowserClicked()

    /** A malformed URL or a headless desktop must never take the menu down with it. */
    override fun onOpenProtocolPdf(url: String) {
        runCatching {
            check(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                "browsing is not supported on this desktop"
            }
            Desktop.getDesktop().browse(URI(url))
        }.onFailure { logger.warn(it) { "could not open the protocol instructions PDF" } }
    }

    override fun onLanguageSelected(language: String) = onLanguageSelectedClicked(language)
}
