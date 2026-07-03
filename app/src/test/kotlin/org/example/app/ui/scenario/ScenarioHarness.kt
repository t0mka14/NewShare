package org.example.app.ui.scenario

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.AppContainer
import org.example.app.domain.audio.ContinuousSessionRecorder
import org.example.app.domain.config.CalibrationTask
import org.example.app.domain.timeline.TaskInstanceExpander
import org.example.app.fakes.FakeAudioInputDeviceProvider
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeContinuousSessionRecorder
import org.example.app.fakes.FakeIdGenerator
import org.example.app.fakes.TestAppDirectories
import org.example.app.fakes.TestCoroutineDispatchers
import org.example.app.navigation.DefaultMainMenuComponent
import org.example.app.navigation.DefaultPatientInfoComponent
import org.example.app.navigation.DefaultSessionComponent
import org.example.app.ui.MainMenuContent
import org.example.app.ui.PatientInfoContent
import org.example.app.ui.SessionContent
import org.example.app.ui.SessionSummaryContent
import org.example.app.ui.UiLocalization
import java.nio.file.Path
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

/**
 * Shared plumbing for the §10.3 workflow scenarios that reach into [org.example.app.navigation.SessionComponent]
 * (scenarios 1, 2, 6, 7) — a real fake-driven `AppContainer` + a hand-assembled Menu -> PatientInfo
 * -> Session -> Summary composition, in place of driving `DefaultRootComponent`/`RootContent`
 * directly.
 *
 * **Why not `DefaultRootComponent`:** `AppContainer` only exposes `directories`/`clock`/
 * `idGenerator`/`dispatchers` as constructor parameters — `configApi`, `audioInputDeviceProvider`,
 * and `sessionRecorderFactory` are hardcoded to the production Ktor/JVM implementations with no
 * injection point (see `app/src/main/kotlin/org/example/app/AppContainer.kt`). Driving
 * `DefaultRootComponent.buildSessionChild` end-to-end would therefore enumerate real audio mixers
 * and try to open a real `TargetDataLine` on whatever machine runs the tests — nondeterministic
 * and liable to hang under headless CI (§10.3 explicitly forbids real audio in tests). This is a
 * main-source gap against the §10.3 contract ("AppContainer is constructable with all fakes");
 * reported to the tech lead rather than fixed here (out of QA's boundary, §10 mandate).
 *
 * The workaround: reuse the real `AppContainer` wiring for everything that never touches audio
 * hardware — `JsonConfigurationRepository`, `JsonSessionRepository`, `JsonTimelineRepository`,
 * `StartSessionUseCase`, `ValidateParticipantInfoUseCase`, all backed by a temp-dir
 * `TestAppDirectories` — and hand-assemble the `Session` leg with [FakeContinuousSessionRecorder]/
 * [FakeAudioInputDeviceProvider], mirroring exactly what `DefaultRootComponent.startSession`/
 * `buildSessionChild` do. Scenarios that never reach `Session` (3, 5) use the real
 * `DefaultRootComponent`/`RootContent` directly instead — see `ValidationAndOfflineConfigScenarioTest`.
 */
class ScenarioHarness(tempDir: Path) {
    val clock = FakeClock(Instant.parse("2026-07-03T09:00:00Z"))
    val dispatchers = TestCoroutineDispatchers()
    val deviceProvider = FakeAudioInputDeviceProvider(
        listOf(FakeAudioInputDeviceProvider.DEFAULT_DEVICE, FakeAudioInputDeviceProvider.SECONDARY_DEVICE),
    )

    val container = AppContainer(
        directories = TestAppDirectories(tempDir),
        clock = clock,
        idGenerator = FakeIdGenerator(),
        dispatchers = dispatchers,
    )

    /** Set by [recorderFactory] the moment `SessionComponent` creates one (null for no-master
     * protocols, §6.2 — mirrors the assertion pattern already used by `SessionComponentTest`). */
    var recorder: FakeContinuousSessionRecorder? = null
        private set

    val recorderFactory: () -> ContinuousSessionRecorder = {
        FakeContinuousSessionRecorder(clock).also { recorder = it }
    }

    /** Seeds the raw config cache and activates it, exercising the real `JsonConfigurationRepository`
     * decode/validate path (per the task brief's "cheapest path that exercises the real repository
     * where practical"). */
    fun loadConfig(rawJson: String) {
        container.rawConfigCache.write(rawJson)
        container.configurationRepository.loadCached()
    }

    fun sessionFolderName(): String = container.sessionRepository.listSessionFolderNames().single()
}

/** One examination's on-disk starting values, forwarded from `PatientInfo.onValidated` to the
 * hand-assembled `Session` leg — mirrors `DefaultRootComponent`'s private `pendingParticipantValues`. */
private sealed interface ScenarioScreen {
    data object Menu : ScenarioScreen
    data object PatientInfo : ScenarioScreen
    data class Session(val participantValues: Map<String, String>) : ScenarioScreen
    data object Summary : ScenarioScreen
}

/**
 * Renders Menu -> PatientInfo -> Session -> Summary using the real `Default*Component`/`*Content`
 * pairs, wired by hand from [harness] (see its doc comment for why). Functionally the same
 * screen sequence `RootContent`/`DefaultRootComponent` would drive for a happy-path/repeat/
 * device-loss/questionnaire-only protocol (§10.3 scenarios 1, 2, 6, 7).
 */
@Composable
fun ScenarioApp(harness: ScenarioHarness) {
    var screen by remember { mutableStateOf<ScenarioScreen>(ScenarioScreen.Menu) }
    val config = harness.container.configurationRepository.activeConfig.value
    val localization = UiLocalization(harness.container.localizedStringProvider, "en", config)

    when (val current = screen) {
        ScenarioScreen.Menu -> {
            val menu = remember {
                DefaultMainMenuComponent(
                    componentContext = DefaultComponentContext(LifecycleRegistry()),
                    configurationRepository = harness.container.configurationRepository,
                    dispatchers = harness.container.dispatchers,
                    onStartProtocolClicked = { screen = ScenarioScreen.PatientInfo },
                    onUploadClicked = {},
                    onSettingsClicked = {},
                    onSessionBrowserClicked = {},
                )
            }
            MainMenuContent(menu, localization)
        }

        ScenarioScreen.PatientInfo -> {
            val activeConfig = requireNotNull(config)
            val patientInfo = remember {
                DefaultPatientInfoComponent(
                    componentContext = DefaultComponentContext(LifecycleRegistry()),
                    fields = activeConfig.patientFields,
                    validateParticipantInfoUseCase = harness.container.validateParticipantInfoUseCase,
                    onValidated = { values -> screen = ScenarioScreen.Session(values) },
                )
            }
            PatientInfoContent(patientInfo, localization, onBack = { screen = ScenarioScreen.Menu })
        }

        is ScenarioScreen.Session -> {
            val activeConfig = requireNotNull(config)
            val protocol = activeConfig.protocols.first()
            val devices = harness.deviceProvider.availableDevices()
            val sessionComponent = remember(current) {
                DefaultSessionComponent(
                    componentContext = DefaultComponentContext(LifecycleRegistry()),
                    installationId = "install-1",
                    protocol = protocol,
                    configVersion = activeConfig.configVersion,
                    rawConfigJson = harness.container.rawConfigCache.read() ?: "{}",
                    patientFields = activeConfig.patientFields,
                    participantFieldValues = current.participantValues,
                    initialDevice = devices.first(),
                    availableDevices = devices,
                    recorderFactory = harness.recorderFactory,
                    startSessionUseCase = harness.container.startSessionUseCase,
                    sessionRepository = harness.container.sessionRepository,
                    timelineRepository = harness.container.timelineRepository,
                    clock = harness.clock,
                    dispatchers = harness.container.dispatchers,
                    onSessionEnded = { screen = ScenarioScreen.Summary },
                )
            }
            val navigableTasks = remember(protocol) {
                TaskInstanceExpander.expand(protocol.tasks).instances.filterNot { it.task is CalibrationTask }
            }
            SessionContent(
                component = sessionComponent,
                navigableTasks = navigableTasks,
                availableDevices = devices,
                initialDevice = devices.firstOrNull(),
                localization = localization,
                onBackToMenu = { screen = ScenarioScreen.Menu },
            )
        }

        ScenarioScreen.Summary -> SessionSummaryContent(localization, onDone = { screen = ScenarioScreen.Menu })
    }
}

/**
 * Advances the Compose test `mainClock` and the injected [FakeClock] together (§10.3 "two clocks
 * to coordinate") — [FakeContinuousSessionRecorder] listens to [FakeClock.onAdvance] to accrue
 * `writtenSamples`, so this is the one call scenario tests need to simulate "N ms of recording."
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.advanceBoth(clock: FakeClock, millis: Long) {
    mainClock.advanceTimeBy(millis)
    clock.advance(millis.milliseconds)
}
