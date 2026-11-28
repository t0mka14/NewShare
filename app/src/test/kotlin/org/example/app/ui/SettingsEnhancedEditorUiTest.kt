package org.example.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.domain.audio.MicGainApplier
import org.example.app.domain.config.RefreshConfigurationUseCase
import org.example.app.domain.localization.LocalizedStringProvider
import org.example.app.domain.settings.AppSettings
import org.example.app.domain.settings.InstallationIdProvider
import org.example.app.domain.settings.enhancedEditorEnabled
import org.example.app.fakes.FakeAppSettingsRepository
import org.example.app.fakes.FakeAudioInputDeviceProvider
import org.example.app.fakes.FakeAudioInputGainControl
import org.example.app.fakes.FakeConfigApi
import org.example.app.fakes.FakeConfigurationRepository
import org.example.app.fakes.FakeVideoInputDeviceProvider
import org.example.app.fakes.TestCoroutineDispatchers
import org.example.app.navigation.DefaultSettingsComponent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class SettingsEnhancedEditorUiTest {

    @Test
    fun `toggling the enhanced editor switch persists and reflects the setting`() = runDesktopComposeUiTest(800, 600) {
        val settingsRepository = FakeAppSettingsRepository()
        val configurationRepository = FakeConfigurationRepository()
        val dispatchers = TestCoroutineDispatchers()
        val component = DefaultSettingsComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            videoDeviceProvider = FakeVideoInputDeviceProvider(),
            deviceProvider = FakeAudioInputDeviceProvider(listOf(FakeAudioInputDeviceProvider.DEFAULT_DEVICE)),
            settingsRepository = settingsRepository,
            configurationRepository = configurationRepository,
            refreshConfigurationUseCase = RefreshConfigurationUseCase(settingsRepository, FakeConfigApi(), configurationRepository),
            micGainApplier = MicGainApplier(FakeAudioInputGainControl(), settingsRepository, dispatchers),
            installationIdProvider = InstallationIdProvider(settingsRepository) { "generated-pc-id" },
            dispatchers = dispatchers,
        )
        setContent { SettingsContent(component, UiLocalization(LocalizedStringProvider(), "en", null)) {} }
        waitForIdle()

        onNodeWithTag(TestTags.Settings.ENHANCED_EDITOR_SWITCH).performScrollTo().assertIsOff()
        assertFalse(settingsRepository.read().enhancedEditorEnabled())

        onNodeWithTag(TestTags.Settings.ENHANCED_EDITOR_SWITCH).performClick()
        waitForIdle()
        onNodeWithTag(TestTags.Settings.ENHANCED_EDITOR_SWITCH).assertIsOn()
        assertTrue(settingsRepository.read().enhancedEditorEnabled())

        onNodeWithTag(TestTags.Settings.ENHANCED_EDITOR_SWITCH).performClick()
        waitForIdle()
        onNodeWithTag(TestTags.Settings.ENHANCED_EDITOR_SWITCH).assertIsOff()
        assertFalse(settingsRepository.read().enhancedEditorEnabled())
    }

    @Test
    fun `a saved enabled setting shows the switch on`() = runDesktopComposeUiTest(800, 600) {
        val settingsRepository = FakeAppSettingsRepository().apply { write(AppSettings(enhancedEditor = true)) }
        val configurationRepository = FakeConfigurationRepository()
        val dispatchers = TestCoroutineDispatchers()
        val component = DefaultSettingsComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            videoDeviceProvider = FakeVideoInputDeviceProvider(),
            deviceProvider = FakeAudioInputDeviceProvider(listOf(FakeAudioInputDeviceProvider.DEFAULT_DEVICE)),
            settingsRepository = settingsRepository,
            configurationRepository = configurationRepository,
            refreshConfigurationUseCase = RefreshConfigurationUseCase(settingsRepository, FakeConfigApi(), configurationRepository),
            micGainApplier = MicGainApplier(FakeAudioInputGainControl(), settingsRepository, dispatchers),
            installationIdProvider = InstallationIdProvider(settingsRepository) { "generated-pc-id" },
            dispatchers = dispatchers,
        )
        setContent { SettingsContent(component, UiLocalization(LocalizedStringProvider(), "en", null)) {} }
        waitForIdle()
        onNodeWithTag(TestTags.Settings.ENHANCED_EDITOR_SWITCH).performScrollTo().assertIsOn()
    }
}
