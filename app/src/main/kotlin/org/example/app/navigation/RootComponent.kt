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
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.config.CalibrationTask
import org.example.app.domain.config.Protocol
import org.example.app.domain.config.RemoteConfig
import org.example.app.domain.timeline.TaskInstance
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
    fun onPlaceholderBack()

    sealed class Child {
        /** No active config and no cache (§6.1 pt 4) — rendered from bundled fallback strings. */
        data object Blocking : Child()

        class MainMenu(val component: MainMenuComponent) : Child()
        class Settings(val component: SettingsComponent) : Child()
        class PatientInfo(val component: PatientInfoComponent) : Child()

        /**
         * [navigableTasks]/[availableDevices]/[initialDevice] are computed here (mirroring what
         * [SessionComponent] itself does internally via [TaskInstanceExpander]) purely so the UI
         * layer can look up each task's full definition (title/instructions already come via
         * `TaskComponent.State`, but VOCAL's `showIndicator`/`length`/`audioExamplePath` and every
         * QUESTIONNAIRE's `questions[]` do not) and the device-loss dialog's device list — neither
         * is exposed by the frozen `TaskComponent`/`SessionComponent` public state. See the
         * chunk-2 report to the tech lead for the recommended follow-up.
         */
        class Session(
            val component: SessionComponent,
            val navigableTasks: List<TaskInstance>,
            val availableDevices: List<AudioInputDevice>,
            val initialDevice: AudioInputDevice?,
        ) : Child()

        /** Minimal post-protocol confirmation (§8.6) — the real summary lands with processing/upload. */
        data object SessionSummary : Child()

        /** §8.9/§8.11 land in a later chunk; these keep the main-menu buttons wired to a real
         * (placeholder) destination instead of a dead click. */
        data object Upload : Child()
        data object SessionBrowser : Child()
    }
}

/**
 * §5.2 root navigation: blocking config-required screen ↔ main menu → settings / patient info →
 * session flow → summary → back to menu. Constructs every screen component from [container],
 * matching the frozen chunk-1 component constructors exactly (they are not modified here).
 */
@OptIn(DelicateDecomposeApi::class)
class DefaultRootComponent(
    componentContext: ComponentContext,
    private val container: AppContainer,
) : RootComponent, ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + container.dispatchers.default)
    private val navigation = StackNavigation<Config>()

    // Set immediately before pushing Config.Session (mirrors SessionComponent's own
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
                    onStartProtocolClicked = { navigation.push(Config.PatientInfo) },
                    onUploadClicked = { navigation.push(Config.Upload) },
                    onSettingsClicked = { navigation.push(Config.Settings) },
                    onSessionBrowserClicked = { navigation.push(Config.SessionBrowser) },
                ),
            )

            Config.Settings -> buildSettingsChild(childContext)

            Config.PatientInfo -> RootComponent.Child.PatientInfo(
                DefaultPatientInfoComponent(
                    componentContext = childContext,
                    fields = container.configurationRepository.activeConfig.value?.patientFields.orEmpty(),
                    validateParticipantInfoUseCase = container.validateParticipantInfoUseCase,
                    onValidated = ::startSession,
                ),
            )

            Config.Session -> buildSessionChild(childContext)

            Config.SessionSummary -> RootComponent.Child.SessionSummary
            Config.Upload -> RootComponent.Child.Upload
            Config.SessionBrowser -> RootComponent.Child.SessionBrowser
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
        pendingProtocol = container.configurationRepository.activeConfig.value?.protocols?.firstOrNull()
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

        val navigableTasks = TaskInstanceExpander.expand(protocol.tasks).instances
            .filterNot { it.task is CalibrationTask }

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
            initialDevice = initialDevice ?: AudioInputDevice(id = "none", name = "No microphone", eligible = false),
            availableDevices = devices,
            recorderFactory = container.sessionRecorderFactory,
            startSessionUseCase = container.startSessionUseCase,
            sessionRepository = container.sessionRepository,
            timelineRepository = container.timelineRepository,
            clock = container.clock,
            dispatchers = container.dispatchers,
            onSessionEnded = { navigation.replaceAll(Config.SessionSummary) },
        )

        return RootComponent.Child.Session(sessionComponent, navigableTasks, devices, initialDevice)
    }

    override fun onSettingsBack() = navigation.pop()
    override fun onPatientInfoBack() = navigation.pop()
    override fun onOpenSettingsFromBlocking() = navigation.push(Config.Settings)
    override fun onSessionFailedBackToMenu() = navigation.replaceAll(Config.MainMenu)
    override fun onSessionSummaryDone() = navigation.replaceAll(Config.MainMenu)
    override fun onPlaceholderBack() = navigation.pop()

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Blocking : Config

        @Serializable
        data object MainMenu : Config

        @Serializable
        data object Settings : Config

        @Serializable
        data object PatientInfo : Config

        @Serializable
        data object Session : Config

        @Serializable
        data object SessionSummary : Config

        @Serializable
        data object Upload : Config

        @Serializable
        data object SessionBrowser : Config
    }
}
