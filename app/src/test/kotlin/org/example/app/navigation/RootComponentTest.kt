package org.example.app.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import org.example.app.AppContainer
import org.example.app.domain.audio.AudioInputDevice
import kotlinx.coroutines.runBlocking
import org.example.app.domain.config.ConfigError
import org.example.app.fakes.ConfigFixtures
import org.example.app.fakes.FakeConfigApi
import org.example.app.fakes.FakeExampleAudioCache
import org.example.app.fakes.FakeAudioInputDeviceProvider
import org.example.app.fakes.FakeAudioInputGainControl
import org.example.app.fakes.FakeContinuousSessionRecorder
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeIdGenerator
import org.example.app.fakes.TestAppDirectories
import org.example.app.domain.config.ConfigDecoder
import org.example.app.domain.settings.AppSettings
import org.example.app.fakes.TestCoroutineDispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
        configApi: FakeConfigApi = FakeConfigApi(),
    ): AppContainer =
        AppContainer(
            directories = TestAppDirectories(tempDir),
            clock = clock,
            idGenerator = FakeIdGenerator(),
            dispatchers = dispatchers,
            exampleAudioCache = FakeExampleAudioCache(), // §10.3: no real downloads in tests
            // The mic-level port would otherwise reach the machine's sound subsystem when a session
            // opens its device; the device list and recorder are faked for the same reason.
            audioInputGainControl = gainControl,
            audioInputDeviceProvider = deviceProvider,
            sessionRecorderFactory = { recorder },
            configApi = configApi,
            deviceCheckinApi = org.example.app.fakes.FakeDeviceCheckinApi(),
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
    fun `recovery dialog Open Sessions routes to the session browser and the menu stays clean on return`(@TempDir tempDir: Path) {
        val container = buildContainer(tempDir)
        container.rawConfigCache.write(ConfigFixtures.questionnaireOnly)
        container.configurationRepository.loadCached()
        container.startupRecovery.publish(listOf(org.example.app.domain.session.RecoveryOutcome.Recovered("2026-10-23_P01_s1", "s1", null)))
        val root = buildRoot(container)

        val menu = (root.stack.value.active.instance as RootComponent.Child.MainMenu).component
        assertTrue(menu.state.value.recovery != null)
        menu.onRecoveryOpenSessions()
        assertTrue(root.stack.value.active.instance is RootComponent.Child.SessionBrowser)

        (root.stack.value.active.instance as RootComponent.Child.SessionBrowser).component.onBack()
        val backMenu = (root.stack.value.active.instance as RootComponent.Child.MainMenu).component
        assertEquals(null, backMenu.state.value.recovery)
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
        patientInfo.onFieldChanged("patient_code", "HC001")
        patientInfo.onFieldChanged("visitNumber", "V1")
        patientInfo.onContinue()
        dispatchers.scheduler.advanceUntilIdle()

        assertTrue(
            root.stack.value.active.instance is RootComponent.Child.Session,
            "expected a session, got ${root.stack.value.active.instance}",
        )
    }

    @Test
    fun `patient info shows the fields of the protocol that was picked`(@TempDir tempDir: Path) {
        val container = buildContainer(tempDir)
        val base = ConfigDecoder.decode(ConfigFixtures.fullProtocol).config
        val onlyCode = base.protocols[0].copy(name = "CodeOnly", patientFields = base.protocols[0].patientFields.take(1))
        container.rawConfigCache.write(ConfigFixtures.fullProtocol)
        container.configurationRepository.loadCached()
        val root = buildRoot(container)

        (root.stack.value.active.instance as RootComponent.Child.MainMenu).component.onStartProtocol()
        val picker = (root.stack.value.active.instance as RootComponent.Child.ProtocolPicker).component
        picker.onProtocolSelected(onlyCode)

        val patientInfo = (root.stack.value.active.instance as RootComponent.Child.PatientInfo).component
        assertEquals(listOf("patient_code"), patientInfo.state.value.fields.map { it.name })
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
    fun `a saved language the config does not offer falls back to the config's default`(@TempDir tempDir: Path) {
        val container = buildContainer(tempDir)
        val czechOnly = ConfigFixtures.questionnaireOnly.replace(Regex(""""languages": \[[^\]]*]"""), """"languages": ["cs"]""")
        container.rawConfigCache.write(czechOnly)
        container.configurationRepository.loadCached()
        container.appSettingsRepository.write(AppSettings(language = "en"))

        assertEquals("cs", buildRoot(container).localization.value.language)
    }

    @Test
    fun `an offered saved language is kept`(@TempDir tempDir: Path) {
        val container = buildContainer(tempDir)
        container.rawConfigCache.write(ConfigFixtures.questionnaireOnly) // cs + en, default cs
        container.configurationRepository.loadCached()
        container.appSettingsRepository.write(AppSettings(language = "en"))

        assertEquals("en", buildRoot(container).localization.value.language)
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

    // ---- blocking screen reason and leaving Settings back to it

    @Test
    fun `a failed refresh gives the blocking screen its reason`(@TempDir tempDir: Path) {
        val dispatchers = TestCoroutineDispatchers()
        val container = buildContainer(tempDir, dispatchers)
        container.configurationRepository.loadCached()
        val root = buildRoot(container)
        assertNull(root.configError.value)

        runBlocking { container.refreshConfigurationUseCase.refresh() } // the startup refresh, no token
        dispatchers.scheduler.advanceUntilIdle()

        assertEquals(ConfigError.SiteTokenMissing, root.configError.value)
    }

    @Test
    fun `a refresh that succeeded in settings lands on the main menu on back, not the blocking screen`(
        @TempDir tempDir: Path,
    ) {
        val dispatchers = TestCoroutineDispatchers()
        val container = buildContainer(tempDir, dispatchers)
        container.configurationRepository.loadCached()
        val root = buildRoot(container)
        root.onOpenSettingsFromBlocking()

        container.configurationRepository.applyFetched(ConfigFixtures.questionnaireOnly)
        dispatchers.scheduler.advanceUntilIdle()
        assertTrue(root.stack.value.active.instance is RootComponent.Child.Settings)

        root.onSettingsBack()

        assertTrue(root.stack.value.active.instance is RootComponent.Child.MainMenu)
        assertEquals(1, root.stack.value.items.size)
    }

    @Test
    fun `back to the blocking screen re-checks an edited token`(@TempDir tempDir: Path) {
        val dispatchers = TestCoroutineDispatchers()
        val api = FakeConfigApi().apply { enqueueSiteTokenUnknown() }
        val container = buildContainer(tempDir, dispatchers, configApi = api)
        container.configurationRepository.loadCached()
        val root = buildRoot(container)
        runBlocking { container.refreshConfigurationUseCase.refresh() }
        dispatchers.scheduler.advanceUntilIdle()
        assertEquals(ConfigError.SiteTokenMissing, root.configError.value)

        root.onOpenSettingsFromBlocking()
        (root.stack.value.active.instance as RootComponent.Child.Settings).component.onSiteTokenChanged("typo")
        root.onSettingsBack() // no Refresh pressed
        dispatchers.scheduler.advanceUntilIdle()

        assertTrue(root.stack.value.active.instance is RootComponent.Child.Blocking)
        assertEquals(listOf("typo"), api.requestedSiteTokens)
        assertEquals(ConfigError.SiteTokenRejected, root.configError.value)
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
        patientInfo.onFieldChanged("patient_code", "HC001")
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

    @Test
    fun `F-062 a mic picked on the calibration screen is saved as the default`(@TempDir tempDir: Path) {
        val dispatchers = TestCoroutineDispatchers()
        val clock = FakeClock()
        val recorder = FakeContinuousSessionRecorder(clock)
        val container = buildContainer(
            tempDir, dispatchers, clock, FakeAudioInputGainControl(),
            deviceProvider = FakeAudioInputDeviceProvider(
                listOf(FakeAudioInputDeviceProvider.DEFAULT_DEVICE, FakeAudioInputDeviceProvider.SECONDARY_DEVICE),
            ),
            recorder = recorder,
        )
        container.rawConfigCache.write(ConfigFixtures.fullProtocol)
        container.configurationRepository.loadCached()
        val lifecycle = LifecycleRegistry()
        val root = DefaultRootComponent(DefaultComponentContext(lifecycle), container)

        openShareSession(root, dispatchers)
        val session = (root.stack.value.active.instance as RootComponent.Child.Session).component
        val calibration = (session.stack.value.active.instance as SessionComponent.Child.Calibration).component
        // Auto-selection at start is not a pick.
        assertNull(container.appSettingsRepository.read()?.micDeviceId)

        calibration.onDeviceSelected(FakeAudioInputDeviceProvider.SECONDARY_DEVICE)
        dispatchers.scheduler.advanceUntilIdle()
        assertEquals(FakeAudioInputDeviceProvider.SECONDARY_DEVICE.id, container.appSettingsRepository.read()?.micDeviceId)
    }

    private fun rootAtSessionEditor(
        container: AppContainer,
        priorEdit: org.example.app.domain.timeline.TimelineEdited?,
        captureFormat: org.example.app.domain.audio.CaptureFormat? = org.example.app.domain.audio.CaptureFormat(sampleRate = 1000, bits = 16, channels = 1),
    ): Pair<RootComponent, EditorComponent> {
        container.rawConfigCache.write(ConfigFixtures.questionnaireOnly)
        container.configurationRepository.loadCached()
        val folder = "2026-10-23_P01_s1"
        container.sessionRepository.createSessionDirectory(folder)
        container.sessionRepository.writeExamination(
            folder,
            org.example.app.domain.session.Examination(
                sessionId = "s1",
                installationId = "i1",
                protocolName = "Share",
                configVersion = "v1",
                startedAt = "2026-10-23T09:00:00Z",
                captureFormat = captureFormat,
            ),
        )
        container.timelineRepository.writeOriginal(
            folder,
            org.example.app.domain.timeline.TimelineOriginal(sessionId = "s1", sampleRate = 1000, events = emptyList()),
        )
        priorEdit?.let { container.timelineRepository.writeEdited(folder, it) }
        val root = buildRoot(container)
        (root.stack.value.active.instance as RootComponent.Child.MainMenu).component.onSessionBrowser()
        val browser = (root.stack.value.active.instance as RootComponent.Child.SessionBrowser).component
        browser.onOpenEditor(folder)
        return root to (root.stack.value.active.instance as RootComponent.Child.Editor).component
    }

    @Test
    fun `accepting an untouched editor opened from Sessions pops back without reprocessing (F-066)`(@TempDir tempDir: Path) {
        val dispatchers = TestCoroutineDispatchers()
        val (root, editor) = rootAtSessionEditor(buildContainer(tempDir, dispatchers), priorEdit = null)
        dispatchers.scheduler.advanceUntilIdle()

        editor.onAccept()

        assertTrue(root.stack.value.active.instance is RootComponent.Child.SessionBrowser)
        // F-104: the examiner is told nothing was reprocessed instead of seeing an unchanged list.
        val browser = (root.stack.value.active.instance as RootComponent.Child.SessionBrowser).component
        assertEquals(SessionBrowserComponent.Message.NoChanges, browser.state.value.message)
    }

    @Test
    fun `accepting a changed edit state from Sessions reprocesses and returns to Sessions (F-066)`(@TempDir tempDir: Path) {
        val dispatchers = TestCoroutineDispatchers()
        // An existing edit that Accept deletes (no VOCAL segments loaded -> nothing moved).
        val prior = org.example.app.domain.timeline.TimelineEdited(sessionId = "s1", sampleRate = 1000, segments = emptyList())
        val (root, editor) = rootAtSessionEditor(buildContainer(tempDir, dispatchers), priorEdit = prior)
        dispatchers.scheduler.advanceUntilIdle()

        editor.onAccept()

        val active = root.stack.value.active.instance
        assertTrue(active is RootComponent.Child.Processing, "expected Processing but was $active")
        // Editor is gone from the stack: back from Processing lands on Sessions, not the editor.
        assertTrue(root.stack.value.backStack.none { it.instance is RootComponent.Child.Editor })
        (active as RootComponent.Child.Processing).component.onBack()
        assertTrue(root.stack.value.active.instance is RootComponent.Child.SessionBrowser)
    }

    @Test
    fun `F-104 accepting a changed edit from Sessions shows the processed message on Sessions`(@TempDir tempDir: Path) {
        val dispatchers = TestCoroutineDispatchers()
        val prior = org.example.app.domain.timeline.TimelineEdited(sessionId = "s1", sampleRate = 1000, segments = emptyList())
        val (root, editor) = rootAtSessionEditor(buildContainer(tempDir, dispatchers), priorEdit = prior, captureFormat = null)
        dispatchers.scheduler.advanceUntilIdle()

        editor.onAccept()
        assertTrue(root.stack.value.active.instance is RootComponent.Child.Processing)
        dispatchers.scheduler.advanceUntilIdle()

        val browser = (root.stack.value.active.instance as RootComponent.Child.SessionBrowser).component
        assertEquals(SessionBrowserComponent.Message.Processed("P01", 0), browser.state.value.message)
    }

    @Test
    fun `F-104 reprocess from Sessions returns to Sessions with the processed message and a Done row`(@TempDir tempDir: Path) {
        val dispatchers = TestCoroutineDispatchers()
        val container = buildContainer(tempDir, dispatchers)
        val (root, editor) = rootAtSessionEditor(container, priorEdit = null, captureFormat = null)
        dispatchers.scheduler.advanceUntilIdle()
        editor.onAccept() // untouched -> back on Sessions
        val browser = (root.stack.value.active.instance as RootComponent.Child.SessionBrowser).component
        assertTrue(browser.state.value.rows.single().canReprocess)

        browser.onReprocess("2026-10-23_P01_s1")
        assertTrue(root.stack.value.active.instance is RootComponent.Child.Processing)
        dispatchers.scheduler.advanceUntilIdle()

        assertTrue(root.stack.value.active.instance is RootComponent.Child.SessionBrowser)
        assertEquals(SessionBrowserComponent.Message.Processed("P01", 0), browser.state.value.message)
        browser.refresh() // the test root's lifecycle is never resumed, so the resume refresh does not run
        val row = browser.state.value.rows.single()
        assertEquals(org.example.app.domain.session.ProcessingStatus.Done, row.processingStatus)
        assertTrue(!row.canReprocess)
    }

    @Test
    fun `F-027 discarding the editor opened from Sessions pops back with no Processing`(@TempDir tempDir: Path) {
        val dispatchers = TestCoroutineDispatchers()
        val prior = org.example.app.domain.timeline.TimelineEdited(sessionId = "s1", sampleRate = 1000, segments = emptyList())
        val container = buildContainer(tempDir, dispatchers)
        val (root, editor) = rootAtSessionEditor(container, priorEdit = prior)
        dispatchers.scheduler.advanceUntilIdle()

        editor.onDiscard()

        assertTrue(root.stack.value.active.instance is RootComponent.Child.SessionBrowser)
        assertTrue(root.stack.value.backStack.none { it.instance is RootComponent.Child.Processing })
        assertTrue(container.timelineRepository.editedExists("2026-10-23_P01_s1"), "discard must leave the edit on disk")
    }
}
