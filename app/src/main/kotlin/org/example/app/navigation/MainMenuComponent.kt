package org.example.app.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.app.domain.CoroutineDispatchers
import org.example.app.domain.config.ConfigurationRepository
import org.example.app.domain.config.RemoteConfig
import java.awt.Desktop
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

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

    /**
     * One protocol that declares a `protocolInstructionsPdfUrl`. [project] is set when the entry
     * belongs to exactly one project; the same protocol listed under several projects shares one
     * entry (same name and URL) with no project.
     */
    data class ProtocolPdf(val protocolName: String, val url: String, val project: String? = null)

    /**
     * `startEnabled` mirrors `ConfigurationRepository.activeConfig != null` (§8.6/§12: start
     * a new protocol is only possible with an active config). `protocolPdfs` lists the
     * protocols whose instruction-manual URL is declared and answered when checked — empty hides
     * the PDF button, one opens directly, several make the button offer a picker.
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
    /** Blocking; called on `dispatchers.io` for each declared PDF URL. */
    private val isUrlReachable: (String) -> Boolean = ::isHttpUrlReachable,
) : MainMenuComponent, ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    private val _state = MutableValue(MainMenuComponent.State(startEnabled = configurationRepository.activeConfig.value != null))
    override val state: Value<MainMenuComponent.State> = _state

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        scope.launch(dispatchers.main) {
            // collectLatest: a new config cancels the previous config's still-running URL checks.
            configurationRepository.activeConfig.collectLatest { config ->
                _state.value = MainMenuComponent.State(startEnabled = config != null)
                val reachable = withContext(dispatchers.io) {
                    pdfsOf(config).map { pdf -> async { pdf.takeIf { isUrlReachable(pdf.url) } } }.awaitAll().filterNotNull()
                }
                _state.value = _state.value.copy(protocolPdfs = reachable)
            }
        }
    }

    private fun pdfsOf(config: RemoteConfig?) = config?.protocols.orEmpty()
        .filter { !it.protocolInstructionsPdfUrl.isNullOrBlank() }
        .groupBy { it.name to it.protocolInstructionsPdfUrl!! }
        .map { (key, protocols) ->
            MainMenuComponent.ProtocolPdf(
                protocolName = key.first,
                url = key.second,
                project = protocols.mapTo(mutableSetOf()) { it.project }.singleOrNull(),
            )
        }

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

private val pdfHttpClient = HttpClient.newBuilder()
    .followRedirects(HttpClient.Redirect.NORMAL)
    .connectTimeout(Duration.ofSeconds(5))
    .build()

/** Unreachable = no HTTP answer at all (bad URL, non-http scheme, DNS, connect/timeout) or an
 *  answer saying the document doesn't exist (404/410). Anything else counts as reachable — bot
 *  walls (Akamai, Cloudflare) answer non-browser clients with 403/503 while a browser gets
 *  through. The body is never read. */
private fun isHttpUrlReachable(url: String): Boolean {
    val status = runCatching {
        val request = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(10)).GET().build()
        pdfHttpClient.send(request, HttpResponse.BodyHandlers.ofInputStream()).also { it.body().close() }.statusCode()
    }.onFailure { logger.warn { "protocol PDF unreachable, hiding it: $url (${it.javaClass.simpleName})" } }
        .getOrNull() ?: return false
    if (status == 404 || status == 410) {
        logger.warn { "protocol PDF not found (HTTP $status), hiding it: $url" }
        return false
    }
    return true
}
