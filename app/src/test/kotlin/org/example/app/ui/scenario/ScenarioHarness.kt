package org.example.app.ui.scenario

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.AppContainer
import org.example.app.domain.audio.ContinuousSessionRecorder
import org.example.app.fakes.FakeAudioClipService
import org.example.app.fakes.FakeAudioInputDeviceProvider
import org.example.app.fakes.FakeAudioPlaybackService
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeConfigApi
import org.example.app.fakes.FakeContinuousSessionRecorder
import org.example.app.fakes.FakeIdGenerator
import org.example.app.fakes.FakeUploadApi
import org.example.app.fakes.FakeWaveformService
import org.example.app.fakes.TestAppDirectories
import org.example.app.fakes.TestCoroutineDispatchers
import org.example.app.navigation.DefaultRootComponent
import org.example.app.navigation.RootComponent
import org.example.app.ui.RootContent
import java.nio.file.Path
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

/**
 * Shared plumbing for the §10.3 workflow scenarios: a fully fake-constructed [AppContainer]
 * driving the real [DefaultRootComponent] / [RootContent] end to end, exactly as the app
 * itself is composed (`Main.kt`).
 *
 * **History:** earlier phases hand-assembled a Menu -> PatientInfo -> Session -> Summary
 * composition here because `AppContainer` hardcoded `configApi`/`audioInputDeviceProvider`/
 * `sessionRecorderFactory` to production Ktor/JVM implementations with no injection point —
 * driving `DefaultRootComponent` would have enumerated real audio mixers and opened a real
 * `TargetDataLine`. Phase 3 closed that gap (every port in [AppContainer]'s constructor is
 * now injectable, including `audioClipService`/`waveformService`), so every scenario can
 * drive the same component tree production code uses.
 *
 * **Construction order matters:** [container] is built eagerly, but [RootComponent] itself
 * is *not* — [ScenarioApp] builds it lazily (via `remember`) inside composition, which runs
 * after the test has already called [loadConfig]. `DefaultRootComponent`'s constructor reads
 * `configurationRepository.activeConfig.value` synchronously to pick its initial screen
 * (`MainMenu` vs the no-config `Blocking` screen, §6.1 pt 4) — building it before the config
 * is loaded would strand every scenario on the blocking screen.
 */
class ScenarioHarness(tempDir: Path) {
    val clock = FakeClock(Instant.parse("2026-07-03T09:00:00Z"))
    val dispatchers = TestCoroutineDispatchers()
    val deviceProvider = FakeAudioInputDeviceProvider(
        listOf(FakeAudioInputDeviceProvider.DEFAULT_DEVICE, FakeAudioInputDeviceProvider.SECONDARY_DEVICE),
    )

    /** Set by [recorderFactory] the moment `SessionComponent` creates one (null for no-master
     * protocols, §6.2 — mirrors the assertion pattern already used by `SessionComponentTest`). */
    var recorder: FakeContinuousSessionRecorder? = null
        private set

    private val recorderFactory: () -> ContinuousSessionRecorder = {
        FakeContinuousSessionRecorder(clock).also { recorder = it }
    }

    val audioClipService = FakeAudioClipService()
    val waveformService = FakeWaveformService()
    val audioPlaybackService = FakeAudioPlaybackService()

    /** Scriptable per-session upload outcomes (§10.3 workflow 8) — see [FakeUploadApi.respondTo]. */
    val uploadApi = FakeUploadApi()

    val container = AppContainer(
        directories = TestAppDirectories(tempDir),
        clock = clock,
        idGenerator = FakeIdGenerator(),
        dispatchers = dispatchers,
        // Never actually called (scenarios seed config via `loadConfig`/the raw cache, not a
        // live fetch) but explicit rather than defaulting to a real `KtorConfigApi` (§10.3: no
        // real network in tests).
        configApi = FakeConfigApi(),
        uploadApi = uploadApi,
        audioInputDeviceProvider = deviceProvider,
        audioClipService = audioClipService,
        waveformService = waveformService,
        audioPlaybackService = audioPlaybackService,
        sessionRecorderFactory = recorderFactory,
    )

    /** Seeds the raw config cache and activates it, exercising the real `JsonConfigurationRepository`
     * decode/validate path. Must be called before `setContent { ScenarioApp(harness) }` — see
     * the class doc for why. */
    fun loadConfig(rawJson: String) {
        container.rawConfigCache.write(rawJson)
        container.configurationRepository.loadCached()
    }

    /** The single session folder created so far — for scenarios that run exactly one protocol. */
    fun sessionFolderName(): String = container.sessionRepository.listSessionFolderNames().single()

    /** Every session folder created so far, oldest first (`FakeIdGenerator` is sequential) — for
     * scenarios that run more than one protocol through the same harness (§10.3 workflow 8). */
    fun sessionFolderNames(): List<String> = container.sessionRepository.listSessionFolderNames()
}

/**
 * Renders the real [DefaultRootComponent] / [RootContent] from [harness] — the same
 * Menu -> (ProtocolPicker) -> PatientInfo -> Session -> (Editor) -> Processing -> Summary /
 * Upload / SessionBrowser navigation graph `Main.kt` composes in production, minus the
 * hardware/network ports (§10.3 full-fake `AppContainer`).
 */
@Composable
fun ScenarioApp(harness: ScenarioHarness) {
    val root = remember(harness) {
        DefaultRootComponent(DefaultComponentContext(LifecycleRegistry()), harness.container)
    }
    RootContent(root)
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
