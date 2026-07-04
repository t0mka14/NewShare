package org.example.app.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.example.app.AppContainer
import org.example.app.domain.config.CalibrationTask
import org.example.app.domain.config.Protocol
import org.example.app.domain.config.RemoteConfig
import org.example.app.domain.timeline.TaskInstanceExpander
import org.example.app.ui.UiLocalization

interface RootComponent {
    val stack: Value<ChildStack<*, Child>>

    /** Current language + active config bundle for resolving display text (§7, §12) —
     * combines `AppSettingsRepository`'s language selection with `ConfigurationRepository`'s
     * `activeConfig`; screens take it as a plain parameter (§5.2, no `CompositionLocal`). */
    val localization: Value<UiLocalization>

    // Root-level navigation events not owned by any frozen chunk-1 component (they have no
    // `onBack`/equivalent of their own) — RootContent wires these directly from screen chrome.
    fun onSettingsBack()
    fun onPatientInfoBack()
    fun onOpenSettingsFromBlocking()
    fun onSessionFailedBackToMenu()
    fun onSessionSummaryDone()

    sealed class Child {
        /** No active config and no cache (§6.1 pt 4) — rendered from bundled fallback strings. */
        data object Blocking : Child()

        class MainMenu(val component: MainMenuComponent) : Child()
        class Settings(val component: SettingsComponent) : Child()

        /** §3 follow-up: only reachable when the active config defines more than one protocol. */
        class ProtocolPicker(val component: ProtocolPickerComponent) : Child()
        class PatientInfo(val component: PatientInfoComponent) : Child()

        /**
         * Owns the examination in progress. `TaskComponent.State` carries everything the task
         * screen needs directly (position/total, device list, task-definition fields, §8.6
         * follow-up) — the UI layer no longer re-expands the protocol itself.
         */
        class Session(val component: SessionComponent) : Child()

        /** §8.7 waveform editor, shown after the protocol only when `enableEditor` is true. */
        class Editor(val component: EditorComponent) : Child()

        /** §8.8 processing progress — blocks navigation while the session is processed. */
        class Processing(val component: ProcessingComponent) : Child()

        /** Minimal post-protocol confirmation (§8.6). */
        data object SessionSummary : Child()

        /** §8.9 upload screen, reached via the main-screen Upload button. */
        class Upload(val component: UploadComponent) : Child()

        /** §8.11 session browser. */
        class SessionBrowser(val component: SessionBrowserComponent) : Child()
    }
}

/**
 * §5.2 root navigation: blocking config-required screen ↔ main menu → (protocol picker) →
 * patient info → session flow → editor (if `enableEditor`) → processing → summary → back to
 * menu; Upload/session-browser are reachable from the main menu at any time. Constructs every
 * screen component from [container].
 */
@OptIn(DelicateDecomposeApi::class)
class DefaultRootComponent(
    componentContext: ComponentContext,
    private val container: AppContainer,
) : RootComponent, ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + container.dispatchers.default)
    private val navigation = StackNavigation<Config>()

    // Set immediately before pushing Config.PatientInfo (mirrors SessionComponent's own
    // private-var-read-in-createChild pattern for its `TaskScreen(index)` config).
    private var pendingProtocol: Protocol? = null
    private var pendingParticipantValues: Map<String, String> = emptyMap()

    private val _localization = MutableValue(buildLocalization())
    override val localization: Value<UiLocalization> = _localization

    override val stack: Value<ChildStack<*, RootComponent.Child>> =
        childStack(
            source = navigation,
            serializer = Config.serializer(),
            initialConfiguration = initialConfig(),
            handleBackButton = true,
            childFactory = ::createChild,
        )

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        scope.launch(container.dispatchers.main) {
            container.configurationRepository.activeConfig.collect { config ->
                _localization.value = _localization.value.copy(config = config)
                val active = stack.value.active.instance
                if (config != null && active is RootComponent.Child.Blocking) {
                    navigation.replaceAll(Config.MainMenu)
                }
            }
        }
    }

    private fun initialConfig(): Config =
        if (container.configurationRepository.activeConfig.value != null) Config.MainMenu else Config.Blocking

    private fun buildLocalization(): UiLocalization {
        val config = container.configurationRepository.activeConfig.value
        return UiLocalization(container.localizedStringProvider, resolveLanguage(config), config)
    }

    private fun resolveLanguage(config: RemoteConfig?): String =
        container.appSettingsRepository.read()?.language ?: config?.defaultLanguage ?: "en"

    private fun createChild(config: Config, childContext: ComponentContext): RootComponent.Child =
        when (config) {
            Config.Blocking -> RootComponent.Child.Blocking

            Config.MainMenu -> RootComponent.Child.MainMenu(
                DefaultMainMenuComponent(
                    componentContext = childContext,
                    configurationRepository = container.configurationRepository,
                    dispatchers = container.dispatchers,
                    onStartProtocolClicked = ::onStartProtocolClicked,
                    onUploadClicked = { navigation.push(Config.Upload) },
                    onSettingsClicked = { navigation.push(Config.Settings) },
                    onSessionBrowserClicked = { navigation.push(Config.SessionBrowser) },
                ),
            )

            Config.Settings -> buildSettingsChild(childContext)

            Config.ProtocolPicker -> RootComponent.Child.ProtocolPicker(
                DefaultProtocolPickerComponent(
                    componentContext = childContext,
                    protocols = container.configurationRepository.activeConfig.value?.protocols.orEmpty(),
                    onProtocolSelectedClicked = { protocol ->
                        pendingProtocol = protocol
                        navigation.push(Config.PatientInfo)
                    },
                    onBackClicked = { navigation.pop() },
                ),
            )

            Config.PatientInfo -> RootComponent.Child.PatientInfo(
                DefaultPatientInfoComponent(
                    componentContext = childContext,
                    fields = container.configurationRepository.activeConfig.value?.patientFields.orEmpty(),
                    validateParticipantInfoUseCase = container.validateParticipantInfoUseCase,
                    onValidated = ::startSession,
                ),
            )

            Config.Session -> buildSessionChild(childContext)

            is Config.Editor -> buildEditorChild(childContext, config)
            is Config.Processing -> buildProcessingChild(childContext, config)

            Config.SessionSummary -> RootComponent.Child.SessionSummary
            Config.Upload -> buildUploadChild(childContext)
            Config.SessionBrowser -> buildSessionBrowserChild(childContext)
        }

    private fun onStartProtocolClicked() {
        val protocols = container.configurationRepository.activeConfig.value?.protocols.orEmpty()
        if (protocols.size > 1) {
            navigation.push(Config.ProtocolPicker)
        } else {
            pendingProtocol = protocols.firstOrNull()
            navigation.push(Config.PatientInfo)
        }
    }

    /**
     * Settings has no way to notify a caller of a language change (frozen interface, §12 "build
     * UI on top, don't redesign them") and `AppSettingsRepository` exposes no change flow — so
     * [localization] tracks live language edits by subscribing to the already-public
     * `SettingsComponent.state` instead, cancelled with the child (§5.2 lifecycle-bound scopes).
     */
    private fun buildSettingsChild(childContext: ComponentContext): RootComponent.Child {
        val settingsComponent = DefaultSettingsComponent(
            componentContext = childContext,
            deviceProvider = container.audioInputDeviceProvider,
            settingsRepository = container.appSettingsRepository,
            configurationRepository = container.configurationRepository,
            refreshConfigurationUseCase = container.refreshConfigurationUseCase,
            dispatchers = container.dispatchers,
        )
        val subscription = settingsComponent.state.subscribe { state ->
            val language = state.selectedLanguage ?: return@subscribe
            if (_localization.value.language != language) {
                _localization.value = _localization.value.copy(language = language)
            }
        }
        childContext.lifecycle.doOnDestroy { subscription.cancel() }
        return RootComponent.Child.Settings(settingsComponent)
    }

    private fun startSession(participantValues: Map<String, String>) {
        pendingParticipantValues = participantValues
        if (pendingProtocol == null) {
            pendingProtocol = container.configurationRepository.activeConfig.value?.protocols?.firstOrNull()
        }
        navigation.push(Config.Session)
    }

    private fun buildSessionChild(childContext: ComponentContext): RootComponent.Child {
        val activeConfig = container.configurationRepository.activeConfig.value
        val protocol = pendingProtocol ?: activeConfig?.protocols?.firstOrNull()
        // Defensive only: PatientInfo (the sole path into Config.Session) is unreachable without
        // an active config, since MainMenu's start button is disabled without one.
        if (protocol == null || activeConfig == null) return RootComponent.Child.Blocking

        val savedSettings = container.appSettingsRepository.read()
        val devices = container.audioInputDeviceProvider.availableDevices()
        val initialDevice = devices.firstOrNull { it.id == savedSettings?.micDeviceId }
            ?: devices.firstOrNull { it.eligible }
            ?: devices.firstOrNull()

        val sessionComponent = DefaultSessionComponent(
            componentContext = childContext,
            installationId = savedSettings?.installationId.orEmpty(),
            protocol = protocol,
            configVersion = activeConfig.configVersion,
            rawConfigJson = container.rawConfigCache.read() ?: "{}",
            patientFields = activeConfig.patientFields,
            participantFieldValues = pendingParticipantValues,
            // DefaultSessionComponent requires a non-null device even for no-master (VIDEO-free
            // questionnaire/info-only) protocols, where it is never actually opened; a zero-device
            // machine is an unsupported/edge deployment, not exercised by the fakes used in tests.
            initialDevice = initialDevice ?: org.example.app.domain.audio.AudioInputDevice(id = "none", name = "No microphone", eligible = false),
            availableDevices = devices,
            recorderFactory = container.sessionRecorderFactory,
            startSessionUseCase = container.startSessionUseCase,
            sessionRepository = container.sessionRepository,
            timelineRepository = container.timelineRepository,
            clock = container.clock,
            dispatchers = container.dispatchers,
            directories = container.directories,
            audioPlaybackService = container.audioPlaybackService,
            onSessionEnded = { folderName ->
                val enableEditor = container.configurationRepository.activeConfig.value?.enableEditor ?: false
                if (enableEditor) {
                    navigation.replaceAll(Config.Editor(folderName, returnToBrowser = false))
                } else {
                    navigation.replaceAll(Config.Processing(folderName, returnToBrowser = false))
                }
            },
        )

        return RootComponent.Child.Session(sessionComponent)
    }

    private fun buildEditorChild(childContext: ComponentContext, config: Config.Editor): RootComponent.Child =
        RootComponent.Child.Editor(
            DefaultEditorComponent(
                componentContext = childContext,
                folderName = config.folderName,
                sessionRepository = container.sessionRepository,
                timelineRepository = container.timelineRepository,
                waveformService = container.waveformService,
                audioPlaybackService = container.audioPlaybackService,
                dispatchers = container.dispatchers,
                onDone = { folderName ->
                    if (config.returnToBrowser) {
                        navigation.pop()
                    } else {
                        navigation.replaceAll(Config.Processing(folderName, returnToBrowser = false))
                    }
                },
            ),
        )

    private fun buildProcessingChild(childContext: ComponentContext, config: Config.Processing): RootComponent.Child =
        RootComponent.Child.Processing(
            DefaultProcessingComponent(
                componentContext = childContext,
                folderName = config.folderName,
                processSessionUseCase = container.processSessionUseCase,
                dispatchers = container.dispatchers,
                onDone = {
                    if (config.returnToBrowser) navigation.pop() else navigation.replaceAll(Config.SessionSummary)
                },
                onBackClicked = {
                    if (config.returnToBrowser) navigation.pop() else navigation.replaceAll(Config.MainMenu)
                },
            ),
        )

    private fun buildUploadChild(childContext: ComponentContext): RootComponent.Child =
        RootComponent.Child.Upload(
            DefaultUploadComponent(
                componentContext = childContext,
                eligibleUploadsQuery = container.eligibleUploadsQuery,
                uploadSessionUseCase = container.uploadSessionUseCase,
                dispatchers = container.dispatchers,
                onBackClicked = { navigation.pop() },
            ),
        )

    private fun buildSessionBrowserChild(childContext: ComponentContext): RootComponent.Child =
        RootComponent.Child.SessionBrowser(
            DefaultSessionBrowserComponent(
                componentContext = childContext,
                sessionRepository = container.sessionRepository,
                uploadStatusRepository = container.uploadStatusRepository,
                onOpenEditorClicked = { folderName -> navigation.push(Config.Editor(folderName, returnToBrowser = true)) },
                onReprocessClicked = { folderName -> navigation.push(Config.Processing(folderName, returnToBrowser = true)) },
                onGoToUploadClicked = { navigation.push(Config.Upload) },
                onBackClicked = { navigation.pop() },
            ),
        )

    override fun onSettingsBack() = navigation.pop()
    override fun onPatientInfoBack() = navigation.pop()
    override fun onOpenSettingsFromBlocking() = navigation.push(Config.Settings)
    override fun onSessionFailedBackToMenu() = navigation.replaceAll(Config.MainMenu)
    override fun onSessionSummaryDone() = navigation.replaceAll(Config.MainMenu)

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Blocking : Config

        @Serializable
        data object MainMenu : Config

        @Serializable
        data object Settings : Config

        @Serializable
        data object ProtocolPicker : Config

        @Serializable
        data object PatientInfo : Config

        @Serializable
        data object Session : Config

        @Serializable
        data class Editor(val folderName: String, val returnToBrowser: Boolean) : Config

        @Serializable
        data class Processing(val folderName: String, val returnToBrowser: Boolean) : Config

        @Serializable
        data object SessionSummary : Config

        @Serializable
        data object Upload : Config

        @Serializable
        data object SessionBrowser : Config
    }
}
