package org.example.app.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.domain.config.ConfigApplyResult
import org.example.app.domain.config.ConfigFetchResult
import org.example.app.domain.config.RefreshConfigurationUseCase
import org.example.app.domain.config.RemoteConfig
import org.example.app.domain.settings.AppSettings
import org.example.app.fakes.FakeAppSettingsRepository
import org.example.app.fakes.FakeAudioInputDeviceProvider
import org.example.app.fakes.FakeConfigApi
import org.example.app.fakes.FakeConfigurationRepository
import org.example.app.fakes.TestCoroutineDispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SettingsComponentTest {

    private fun sampleConfig() = RemoteConfig(
        schemaVersion = 1,
        configVersion = "v1",
        defaultLanguage = "en",
        languages = listOf("en", "cs"),
    )

    private class Harness(
        val settingsRepository: FakeAppSettingsRepository = FakeAppSettingsRepository(),
        val configApi: FakeConfigApi = FakeConfigApi(),
        val configurationRepository: FakeConfigurationRepository = FakeConfigurationRepository(),
        val deviceProvider: FakeAudioInputDeviceProvider = FakeAudioInputDeviceProvider(
            listOf(FakeAudioInputDeviceProvider.DEFAULT_DEVICE, FakeAudioInputDeviceProvider.SECONDARY_DEVICE),
        ),
    ) {
        val dispatchers = TestCoroutineDispatchers()

        val component: SettingsComponent = DefaultSettingsComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            deviceProvider = deviceProvider,
            settingsRepository = settingsRepository,
            configurationRepository = configurationRepository,
            refreshConfigurationUseCase = RefreshConfigurationUseCase(settingsRepository, configApi, configurationRepository),
            dispatchers = dispatchers,
        )
    }

    @Test
    fun `initial state reflects persisted settings and available devices`() {
        val settingsRepository = FakeAppSettingsRepository().apply {
            write(AppSettings(micDeviceId = "secondary", installationId = "inst-1", language = "cs"))
        }
        val h = Harness(settingsRepository = settingsRepository)

        val state = h.component.state.value
        assertEquals("secondary", state.selectedDeviceId)
        assertEquals("inst-1", state.installationId)
        assertEquals("cs", state.selectedLanguage)
        assertEquals(2, state.availableDevices.size)
    }

    @Test
    fun `changing a field persists immediately via AppSettingsRepository`() {
        val h = Harness()

        h.component.onDeviceSelected("secondary")
        h.component.onInstallationIdChanged("inst-42")
        h.component.onLanguageSelected("cs")

        val saved = h.settingsRepository.read()!!
        assertEquals("secondary", saved.micDeviceId)
        assertEquals("inst-42", saved.installationId)
        assertEquals("cs", saved.language)
    }

    @Test
    fun `refresh success maps to the localized success key`() {
        val h = Harness()
        h.settingsRepository.write(AppSettings(installationId = "inst-1"))
        h.configApi.enqueueSuccess("""{"schemaVersion":1}""")
        h.configurationRepository.enqueueApplyResult(ConfigApplyResult.Applied(sampleConfig()))

        h.component.onRefreshClicked()
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals("settings.refresh.success", h.component.state.value.lastRefreshResultKey)
        assertEquals(listOf("en", "cs"), h.component.state.value.availableLanguages)
    }

    @Test
    fun `refresh with missing installation id maps to the installationIdMissing key`() {
        val h = Harness()
        // No installation ID saved.

        h.component.onRefreshClicked()
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals("error.config.installationIdMissing", h.component.state.value.lastRefreshResultKey)
    }

    @Test
    fun `refresh rejected installation id maps to the installationIdRejected key`() {
        val h = Harness()
        h.settingsRepository.write(AppSettings(installationId = "inst-1"))
        h.configApi.enqueueInvalidInstallationId()

        h.component.onRefreshClicked()
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals("error.config.installationIdRejected", h.component.state.value.lastRefreshResultKey)
    }

    @Test
    fun `offline with cached config still active counts as success, not a failure`() {
        val h = Harness(configurationRepository = FakeConfigurationRepository(initialConfig = sampleConfig()))
        h.settingsRepository.write(AppSettings(installationId = "inst-1"))
        h.configApi.enqueueNetworkUnavailable()

        h.component.onRefreshClicked()
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals("settings.refresh.success", h.component.state.value.lastRefreshResultKey)
        assertEquals(false, h.component.state.value.refreshInProgress)
    }

    @Test
    fun `refresh result is null before any refresh is attempted`() {
        val h = Harness()
        assertNull(h.component.state.value.lastRefreshResultKey)
    }
}
