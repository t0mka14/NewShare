package org.example.app.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.AppContainer
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.fakes.ConfigFixtures
import org.example.app.fakes.FakeAudioInputDeviceProvider
import org.example.app.fakes.FakeAudioInputGainControl
import org.example.app.fakes.FakeContinuousSessionRecorder
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeIdGenerator
import org.example.app.fakes.TestAppDirectories
import org.example.app.domain.settings.AppSettings
import org.example.app.fakes.TestCoroutineDispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * §6.1 routing: no-config → blocking screen; cached/fetched config → main menu. `AppContainer`
 * is the composition root (§5.2) — only `directories`/`clock`/`idGenerator`/`dispatchers` are
 * swappable, so this exercises the real `JsonConfigurationRepository`/`JsonAppSettingsRepository`
 * etc. against a temp directory rather than fakes (chunk-1's per-component tests already cover
 * `MainMenuComponent`/`SettingsComponent`/`PatientInfoComponent` in isolation with fakes; this
 * suite is scoped to the navigation changes RootComponent itself owns).
 */
class RootComponentTest {

    private fun buildContainer(
        tempDir: Path,
        dispatchers: TestCoroutineDispatchers = TestCoroutineDispatchers(),
        clock: FakeClock = FakeClock(),
        gainControl: FakeAudioInputGainControl = FakeAudioInputGainControl(),
        deviceProvider: FakeAudioInputDeviceProvider = FakeAudioInputDeviceProvider(),
        recorder: FakeContinuousSessionRecorder = FakeContinuousSessionRecorder(clock),
    ): AppContainer =
        AppContainer(
            directories = TestAppDirectories(tempDir),
            clock = clock,
            idGenerator = FakeIdGenerator(),
            dispatchers = dispatchers,
            // The mic-level port would otherwise reach the machine's sound subsystem when a session
            // opens its device; the device list and recorder are faked for the same reason.
            audioInputGainControl = gainControl,
            audioInputDeviceProvider = deviceProvider,
            sessionRecorderFactory = { recorder },
        )

    private fun buildRoot(container: AppContainer): RootComponent =
        DefaultRootComponent(DefaultComponentContext(LifecycleRegistry()), container)

    @Test
    fun `no cached config routes to the blocking screen`(@TempDir tempDir: Path) {
        val container = buildContainer(tempDir)
        container.configurationRepository.loadCached()

        val root = buildRoot(container)

        assertTrue(root.stack.value.active.instance is RootComponent.Child.Blocking)
    }

    @Test
    fun `a valid cached config routes straight to the main menu`(@TempDir tempDir: Path) {
        val container = buildContainer(tempDir)
        container.rawConfigCache.write(ConfigFixtures.questionnaireOnly)
        container.configurationRepository.loadCached()

        val root = buildRoot(container)

        assertTrue(root.stack.value.active.instance is RootComponent.Child.MainMenu)
    }

    @Test
    fun `applying a fetched config while blocked navigates to the main menu`(@TempDir tempDir: Path) {
        val dispatchers = TestCoroutineDispatchers()
        val container = buildContainer(tempDir, dispatchers)
        container.configurationRepository.loadCached()
        val root = buildRoot(container)
        assertTrue(root.stack.value.active.instance is RootComponent.Child.Blocking)

        container.configurationRepository.applyFetched(ConfigFixtures.questionnaireOnly)
        dispatchers.scheduler.advanceUntilIdle()

        assertTrue(root.stack.value.active.instance is RootComponent.Child.MainMenu)
    }

    @Test
    fun `settings button then back returns to the main menu`(@TempDir tempDir: Path) {
        val container = buildContainer(tempDir)
        container.rawConfigCache.write(ConfigFixtures.questionnaireOnly)
        container.configurationRepository.loadCached()
        val root = buildRoot(container)

        val menu = (root.stack.value.active.instance as RootComponent.Child.MainMenu).component
        menu.onSettings()
        assertTrue(root.stack.value.active.instance is RootComponent.Child.Settings)

        root.onSettingsBack()
        assertTrue(root.stack.value.active.instance is RootComponent.Child.MainMenu)
    }

    @Test
    fun `start protocol opens patient info and back returns to the main menu`(@TempDir tempDir: Path) {
        val container = buildContainer(tempDir)
        container.rawConfigCache.write(ConfigFixtures.questionnaireOnly)
        container.configurationRepository.loadCached()
        val root = buildRoot(container)

        val menu = (root.stack.value.active.instance as RootComponent.Child.MainMenu).component
        menu.onStartProtocol()
        assertTrue(root.stack.value.active.instance is RootComponent.Child.PatientInfo)

        root.onPatientInfoBack()
        assertTrue(root.stack.value.active.instance is RootComponent.Child.MainMenu)
    }

    /** Validated patient details reach a session child, for a protocol with no VIDEO task. */
    @Test
    fun `validated patient details open the session`(@TempDir tempDir: Path) {
        val dispatchers = TestCoroutineDispatchers()
        val container = buildContainer(tempDir, dispatchers)
        container.rawConfigCache.write(ConfigFixtures.questionnaireOnly)
        container.configurationRepository.loadCached()
        val root = buildRoot(container)

        val menu = (root.stack.value.active.instance as RootComponent.Child.MainMenu).component
        menu.onStartProtocol()
        val patientInfo = (root.stack.value.active.instance as RootComponent.Child.PatientInfo).component
        patientInfo.onFieldChanged("code", "HC001")
        patientInfo.onFieldChanged("visitNumber", "V1")
        patientInfo.onContinue()
        dispatchers.scheduler.advanceUntilIdle()

        assertTrue(
            root.stack.value.active.instance is RootComponent.Child.Session,
            "expected a session, got ${root.stack.value.active.instance}",
        )
    }

    @Test
    fun `start protocol with a multi-protocol config shows the picker before patient info`(@TempDir tempDir: Path) {
        val container = buildContainer(tempDir)
        container.rawConfigCache.write(ConfigFixtures.fullProtocol) // defines "Share" + "QuestionnaireOnly"
        container.configurationRepository.loadCached()
        val root = buildRoot(container)

        val menu = (root.stack.value.active.instance as RootComponent.Child.MainMenu).component
        menu.onStartProtocol()

        val picker = root.stack.value.active.instance
        assertTrue(picker is RootComponent.Child.ProtocolPicker)
        val protocolNames = (picker as RootComponent.Child.ProtocolPicker).component.protocols.map { it.name }
        assertTrue(protocolNames.containsAll(listOf("Share", "QuestionnaireOnly")))

        picker.component.onProtocolSelected(picker.component.protocols.first { it.name == "QuestionnaireOnly" })
        assertTrue(root.stack.value.active.instance is RootComponent.Child.PatientInfo)
    }

    @Test
    fun `protocol picker back returns to the main menu`(@TempDir tempDir: Path) {
        val container = buildContainer(tempDir)
        container.rawConfigCache.write(ConfigFixtures.fullProtocol)
        container.configurationRepository.loadCached()
        val root = buildRoot(container)

        val menu = (root.stack.value.active.instance as RootComponent.Child.MainMenu).component
        menu.onStartProtocol()
        val picker = (root.stack.value.active.instance as RootComponent.Child.ProtocolPicker).component

        picker.onBack()

        assertTrue(root.stack.value.active.instance is RootComponent.Child.MainMenu)
    }

    @Test
    fun `the main menu language flag persists the choice and re-publishes localization`(@TempDir tempDir: Path) {
        val container = buildContainer(tempDir)
        container.rawConfigCache.write(ConfigFixtures.questionnaireOnly)
        container.configurationRepository.loadCached()
        container.appSettingsRepository.write(AppSettings(installationId = "DEMO-002", micDeviceId = "mic-1"))
        val root = buildRoot(container)
        val menu = (root.stack.value.active.instance as RootComponent.Child.MainMenu).component

        menu.onLanguageSelected("cs")

        assertEquals("cs", root.localization.value.language)
        val saved = container.appSettingsRepository.read()
        assertEquals("cs", saved?.language)
        // The flag must not wipe what Settings owns.
        assertEquals("DEMO-002", saved?.installationId)
        assertEquals("mic-1", saved?.micDeviceId)
    }

    @Test
    fun `blocking screen open-settings button navigates to settings`(@TempDir tempDir: Path) {
        val container = buildContainer(tempDir)
        container.configurationRepository.loadCached()
        val root = buildRoot(container)
        assertTrue(root.stack.value.active.instance is RootComponent.Child.Blocking)

        root.onOpenSettingsFromBlocking()

        assertTrue(root.stack.value.active.instance is RootComponent.Child.Settings)
    }

    // ---- microphone selection and level at session start (§6.2, §13 decision 44)

    /** The device `sample_config.json` names in `defaultMicName`, as Java would report it. */
    private val configuredMic = AudioInputDevice(id = "usb", name = "USBAudioDevice", eligible = true)

    /** Click through main menu → picker ("Share", the VOCAL protocol) → patient info → session. */
    private fun openShareSession(root: RootComponent, dispatchers: TestCoroutineDispatchers) {
        val menu = (root.stack.value.active.instance as RootComponent.Child.MainMenu).component
        menu.onStartProtocol()
        val picker = (root.stack.value.active.instance as RootComponent.Child.ProtocolPicker).component
        picker.onProtocolSelected(picker.protocols.first { it.name == "Share" })
        val patientInfo = (root.stack.value.active.instance as RootComponent.Child.PatientInfo).component
        patientInfo.onFieldChanged("code", "HC001")
        patientInfo.onFieldChanged("visitNumber", "V1")
        patientInfo.onContinue()
        dispatchers.scheduler.advanceUntilIdle()
        assertTrue(root.stack.value.active.instance is RootComponent.Child.Session, "expected a session")
    }

    @Test
    fun `the session sets the configured level on the configured mic before opening it`(@TempDir tempDir: Path) {
        val dispatchers = TestCoroutineDispatchers()
        val clock = FakeClock()
        val gainControl = FakeAudioInputGainControl()
        val recorder = FakeContinuousSessionRecorder(clock)
        val container = buildContainer(
            tempDir, dispatchers, clock, gainControl,
            deviceProvider = FakeAudioInputDeviceProvider(listOf(FakeAudioInputDeviceProvider.DEFAULT_DEVICE, configuredMic)),
            recorder = recorder,
        )
        container.rawConfigCache.write(ConfigFixtures.fullProtocol) // defaultMicName USBAudioDevice, defaultMicGain 63
        container.configurationRepository.loadCached()
        val root = buildRoot(container)

        openShareSession(root, dispatchers)

        // No saved mic: the config's defaultMicName is the fallback device, and it gets the level.
        assertEquals(listOf(configuredMic), recorder.monitoringStarts)
        assertEquals(listOf("USBAudioDevice" to 63), gainControl.setCalls)
    }

    @Test
    fun `a saved mic wins over defaultMicName and a saved level wins over defaultMicGain`(@TempDir tempDir: Path) {
        val dispatchers = TestCoroutineDispatchers()
        val clock = FakeClock()
        val gainControl = FakeAudioInputGainControl()
        val recorder = FakeContinuousSessionRecorder(clock)
        val container = buildContainer(
            tempDir, dispatchers, clock, gainControl,
            deviceProvider = FakeAudioInputDeviceProvider(listOf(FakeAudioInputDeviceProvider.DEFAULT_DEVICE, configuredMic)),
            recorder = recorder,
        )
        container.rawConfigCache.write(ConfigFixtures.fullProtocol)
        container.configurationRepository.loadCached()
        container.appSettingsRepository.write(AppSettings(micDeviceId = "default", micGain = 40))
        val root = buildRoot(container)

        openShareSession(root, dispatchers)

        assertEquals(listOf(FakeAudioInputDeviceProvider.DEFAULT_DEVICE), recorder.monitoringStarts)
        assertEquals(listOf("Default Microphone" to 40), gainControl.setCalls)
    }

    @Test
    fun `a mic that is not the configured one opens with its level untouched`(@TempDir tempDir: Path) {
        val dispatchers = TestCoroutineDispatchers()
        val clock = FakeClock()
        val gainControl = FakeAudioInputGainControl()
        val recorder = FakeContinuousSessionRecorder(clock)
        val container = buildContainer(
            tempDir, dispatchers, clock, gainControl,
            deviceProvider = FakeAudioInputDeviceProvider(listOf(FakeAudioInputDeviceProvider.DEFAULT_DEVICE)),
            recorder = recorder,
        )
        container.rawConfigCache.write(ConfigFixtures.fullProtocol)
        container.configurationRepository.loadCached()
        val root = buildRoot(container)

        openShareSession(root, dispatchers)

        assertEquals(listOf(FakeAudioInputDeviceProvider.DEFAULT_DEVICE), recorder.monitoringStarts)
        assertTrue(gainControl.setCalls.isEmpty())
    }
}
