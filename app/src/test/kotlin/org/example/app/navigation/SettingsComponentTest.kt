package org.example.app.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.domain.config.ConfigApplyResult
import org.example.app.domain.config.ConfigFetchResult
import org.example.app.domain.config.RefreshConfigurationUseCase
import org.example.app.domain.audio.MicGainApplier
import org.example.app.domain.config.RemoteConfig
import org.example.app.domain.settings.AppSettings
import org.example.app.domain.settings.InstallationIdProvider
import org.example.app.fakes.FakeAppSettingsRepository
import org.example.app.fakes.FakeAudioInputDeviceProvider
import org.example.app.fakes.FakeAudioInputGainControl
import org.example.app.fakes.FakeConfigApi
import org.example.app.fakes.FakeConfigurationRepository
import org.example.app.fakes.TestCoroutineDispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SettingsComponentTest {

    private fun sampleConfig(defaultMicName: String? = null, defaultMicGain: Int? = null) = RemoteConfig(
        schemaVersion = 1,
        configVersion = "v1",
        defaultLanguage = "en",
        languages = listOf("en", "cs"),
        defaultMicName = defaultMicName,
        defaultMicGain = defaultMicGain,
    )

    /** A config whose `defaultMicName` is the fake secondary device ("USB Headset Mic"), level 63. */
    private fun configWithMicGain() = sampleConfig(
        defaultMicName = FakeAudioInputDeviceProvider.SECONDARY_DEVICE.name,
        defaultMicGain = 63,
    )

    private class Harness(
        val settingsRepository: FakeAppSettingsRepository = FakeAppSettingsRepository(),
        val configApi: FakeConfigApi = FakeConfigApi(),
        val configurationRepository: FakeConfigurationRepository = FakeConfigurationRepository(),
        val deviceProvider: FakeAudioInputDeviceProvider = FakeAudioInputDeviceProvider(
            listOf(FakeAudioInputDeviceProvider.DEFAULT_DEVICE, FakeAudioInputDeviceProvider.SECONDARY_DEVICE),
        ),
        val gainControl: FakeAudioInputGainControl = FakeAudioInputGainControl(),
        refreshUseCase: RefreshConfigurationUseCase? = null,
    ) {
        val dispatchers = TestCoroutineDispatchers()

        val component: SettingsComponent = DefaultSettingsComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            deviceProvider = deviceProvider,
            settingsRepository = settingsRepository,
            configurationRepository = configurationRepository,
            refreshConfigurationUseCase = refreshUseCase
                ?: RefreshConfigurationUseCase(settingsRepository, configApi, configurationRepository),
            micGainApplier = MicGainApplier(gainControl, settingsRepository, dispatchers),
            installationIdProvider = InstallationIdProvider(settingsRepository) { "generated-pc-id" },
            dispatchers = dispatchers,
        )
    }

    private val secondaryName = FakeAudioInputDeviceProvider.SECONDARY_DEVICE.name

    @Test
    fun `initial state reflects persisted settings and available devices`() {
        val settingsRepository = FakeAppSettingsRepository().apply {
            write(AppSettings(micDeviceId = "secondary", installationId = "inst-1", siteToken = "token-1", language = "cs"))
        }
        val h = Harness(settingsRepository = settingsRepository)

        val state = h.component.state.value
        assertEquals("secondary", state.selectedDeviceId)
        assertEquals("inst-1", state.installationId)
        assertEquals("token-1", state.siteToken)
        assertEquals("cs", state.selectedLanguage)
        assertEquals(2, state.availableDevices.size)
    }

    @Test
    fun `the language dropdown shows the config's default when the saved language is not offered`() {
        val h = Harness(
            settingsRepository = FakeAppSettingsRepository().apply { write(AppSettings(language = "en")) },
            configurationRepository = FakeConfigurationRepository(
                initialConfig = sampleConfig().copy(defaultLanguage = "cs", languages = listOf("cs")),
            ),
        )

        assertEquals("cs", h.component.state.value.selectedLanguage)
    }

    @Test
    fun `changing a field persists immediately via AppSettingsRepository`() {
        val h = Harness()

        h.component.onDeviceSelected("secondary")
        h.component.onSiteTokenChanged("  token-42 ")
        h.component.onLanguageSelected("cs")

        val saved = h.settingsRepository.read()!!
        assertEquals("secondary", saved.micDeviceId)
        assertEquals("token-42", saved.siteToken)
        assertEquals("cs", saved.language)
    }

    @Test
    fun `refreshing devices re-enumerates the dropdown items`() {
        val h = Harness()
        h.deviceProvider.setDevices(listOf(FakeAudioInputDeviceProvider.INELIGIBLE_DEVICE))

        h.component.onRefreshDevicesClicked()

        assertEquals(listOf(FakeAudioInputDeviceProvider.INELIGIBLE_DEVICE), h.component.state.value.availableDevices)
    }

    @Test
    fun `refresh success maps to the localized success key`() {
        val h = Harness()
        h.settingsRepository.write(AppSettings(siteToken = "token-1"))
        h.configApi.enqueueSuccess("""{"schemaVersion":1}""")
        h.configurationRepository.enqueueApplyResult(ConfigApplyResult.Applied(sampleConfig()))

        h.component.onRefreshClicked()
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals("settings.refresh.success", h.component.state.value.lastRefreshResultKey)
        assertEquals(listOf("en", "cs"), h.component.state.value.availableLanguages)
    }

    @Test
    fun `refresh with missing site token maps to the siteTokenMissing key`() {
        val h = Harness()
        // No site token saved.

        h.component.onRefreshClicked()
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals("error.config.siteTokenMissing", h.component.state.value.lastRefreshResultKey)
    }

    @Test
    fun `refresh with an unknown site token maps to the siteTokenRejected key`() {
        val h = Harness()
        h.settingsRepository.write(AppSettings(siteToken = "token-1"))
        h.configApi.enqueueSiteTokenUnknown()

        h.component.onRefreshClicked()
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals("error.config.siteTokenRejected", h.component.state.value.lastRefreshResultKey)
    }

    @Test
    fun `refresh of a deactivated site maps to the siteDeactivated key`() {
        val h = Harness()
        h.settingsRepository.write(AppSettings(siteToken = "token-1"))
        h.configApi.enqueueSiteDeactivated()

        h.component.onRefreshClicked()
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals("error.config.siteDeactivated", h.component.state.value.lastRefreshResultKey)
    }

    @Test
    fun `a missing installation id is generated, shown and kept across token edits`() {
        val h = Harness()

        assertEquals("generated-pc-id", h.component.state.value.installationId)
        h.component.onSiteTokenChanged("token-1")

        val saved = h.settingsRepository.read()!!
        assertEquals("generated-pc-id", saved.installationId)
        assertEquals("token-1", saved.siteToken)
    }

    @Test
    fun `offline with cached config still active says the saved configuration is used`() {
        val h = Harness(configurationRepository = FakeConfigurationRepository(initialConfig = sampleConfig()))
        h.settingsRepository.write(AppSettings(siteToken = "token-1"))
        h.configApi.enqueueNetworkUnavailable()

        h.component.onRefreshClicked()
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals("settings.refresh.offline", h.component.state.value.lastRefreshResultKey)
        assertEquals(false, h.component.state.value.refreshInProgress)
    }

    @Test
    fun `an offline fallback from an earlier refresh is shown when Settings opens`() {
        val settingsRepository = FakeAppSettingsRepository().apply { write(AppSettings(siteToken = "token-1")) }
        val configApi = FakeConfigApi().apply { enqueueNetworkUnavailable() }
        val configurationRepository = FakeConfigurationRepository(initialConfig = sampleConfig())
        val useCase = RefreshConfigurationUseCase(settingsRepository, configApi, configurationRepository)
        kotlinx.coroutines.runBlocking { useCase.refresh() } // the startup refresh, before Settings exists

        val h = Harness(settingsRepository = settingsRepository, configurationRepository = configurationRepository, refreshUseCase = useCase)

        assertEquals("settings.refresh.offline", h.component.state.value.lastRefreshResultKey)
    }

    @Test
    fun `refresh result is null before any refresh is attempted`() {
        val h = Harness()
        assertNull(h.component.state.value.lastRefreshResultKey)
    }

    // ---- microphone level slider (§13 decision 44)

    @Test
    fun `the slider shows the selected device's level once it has been read`() {
        val settingsRepository = FakeAppSettingsRepository().apply { write(AppSettings(micDeviceId = "secondary")) }
        val gainControl = FakeAudioInputGainControl(levels = mutableMapOf(secondaryName to 70))
        val h = Harness(settingsRepository = settingsRepository, gainControl = gainControl)

        assertFalse(h.component.state.value.micGainControllable)
        assertNull(h.component.state.value.micGain)

        h.dispatchers.scheduler.advanceUntilIdle()

        val state = h.component.state.value
        assertTrue(state.micGainControllable)
        assertEquals(70, state.micGain)
        assertNull(state.micGainOverride)
        assertEquals(listOf(secondaryName), gainControl.readCalls)
    }

    @Test
    fun `the slider is disabled when the device's level cannot be read`() {
        val settingsRepository = FakeAppSettingsRepository().apply { write(AppSettings(micDeviceId = "secondary")) }
        val h = Harness(settingsRepository = settingsRepository)

        h.dispatchers.scheduler.advanceUntilIdle()

        assertFalse(h.component.state.value.micGainControllable)
        assertNull(h.component.state.value.micGain)
    }

    @Test
    fun `the device's current level wins the slider over a saved override and the config default`() {
        val settingsRepository = FakeAppSettingsRepository().apply { write(AppSettings(micDeviceId = "secondary", micGain = 40)) }
        val gainControl = FakeAudioInputGainControl(levels = mutableMapOf(secondaryName to 70))
        val h = Harness(
            settingsRepository = settingsRepository,
            configurationRepository = FakeConfigurationRepository(initialConfig = configWithMicGain()),
            gainControl = gainControl,
        )
        assertEquals(40, h.component.state.value.micGain) // until the read completes

        h.dispatchers.scheduler.advanceUntilIdle()

        val state = h.component.state.value
        assertEquals(70, state.micGain) // what the OS sound panel currently says
        assertEquals(40, state.micGainOverride)
        assertEquals(63, state.configMicGain)
    }

    @Test
    fun `a saved override is the slider value only while the device's level is unreadable`() {
        val settingsRepository = FakeAppSettingsRepository().apply { write(AppSettings(micDeviceId = "secondary", micGain = 40)) }
        val h = Harness(
            settingsRepository = settingsRepository,
            configurationRepository = FakeConfigurationRepository(initialConfig = configWithMicGain()),
        )
        h.dispatchers.scheduler.advanceUntilIdle()

        val state = h.component.state.value
        assertEquals(40, state.micGain)
        assertFalse(state.micGainControllable)
    }

    @Test
    fun `the config default shows only for the device it names`() {
        val gainControl = FakeAudioInputGainControl(levels = mutableMapOf(secondaryName to 70, "Default Microphone" to 30))
        val h = Harness(
            settingsRepository = FakeAppSettingsRepository().apply { write(AppSettings(micDeviceId = "secondary")) },
            configurationRepository = FakeConfigurationRepository(initialConfig = configWithMicGain()),
            gainControl = gainControl,
        )
        h.dispatchers.scheduler.advanceUntilIdle()
        assertEquals(63, h.component.state.value.configMicGain)
        assertEquals(70, h.component.state.value.micGain) // the device's level, not the config's

        h.component.onDeviceSelected("default")
        h.dispatchers.scheduler.advanceUntilIdle()

        val state = h.component.state.value
        assertNull(state.configMicGain)
        assertEquals(30, state.micGain) // the device's own level, re-read after the switch
        assertEquals(listOf(secondaryName, "Default Microphone"), gainControl.readCalls)
    }

    @Test
    fun `releasing the slider persists the override and sets the device level`() {
        val gainControl = FakeAudioInputGainControl(levels = mutableMapOf(secondaryName to 70))
        val h = Harness(
            settingsRepository = FakeAppSettingsRepository().apply { write(AppSettings(micDeviceId = "secondary")) },
            gainControl = gainControl,
        )
        h.dispatchers.scheduler.advanceUntilIdle()

        h.component.onMicGainChanged(55)
        assertEquals(55, h.component.state.value.micGain)
        assertTrue(gainControl.setCalls.isEmpty()) // dragging alone touches nothing
        h.component.onMicGainChangeFinished()
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals(55, h.settingsRepository.read()?.micGain)
        assertEquals(55, h.component.state.value.micGainOverride)
        assertEquals(listOf(secondaryName to 55), gainControl.setCalls)
    }

    @Test
    fun `reset clears the override and re-applies the config default`() {
        val gainControl = FakeAudioInputGainControl(levels = mutableMapOf(secondaryName to 70))
        val h = Harness(
            settingsRepository = FakeAppSettingsRepository().apply { write(AppSettings(micDeviceId = "secondary", micGain = 40)) },
            configurationRepository = FakeConfigurationRepository(initialConfig = configWithMicGain()),
            gainControl = gainControl,
        )
        h.dispatchers.scheduler.advanceUntilIdle()

        h.component.onMicGainReset()
        h.dispatchers.scheduler.advanceUntilIdle()

        assertNull(h.settingsRepository.read()?.micGain)
        assertNull(h.component.state.value.micGainOverride)
        assertEquals(63, h.component.state.value.micGain)
        assertEquals(listOf(secondaryName to 63), gainControl.setCalls)
    }

    @Test
    fun `persisting a settings edit keeps the camera device and the level override`() {
        val settingsRepository = FakeAppSettingsRepository().apply {
            write(AppSettings(micDeviceId = "secondary", cameraDeviceId = "cam-1", micGain = 40))
        }
        val h = Harness(settingsRepository = settingsRepository)

        h.component.onSiteTokenChanged("token-9")

        val saved = h.settingsRepository.read()!!
        assertEquals("cam-1", saved.cameraDeviceId)
        assertEquals(40, saved.micGain)
        assertEquals("token-9", saved.siteToken)
    }
}
