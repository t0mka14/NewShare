package org.example.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.domain.audio.MicGainApplier
import org.example.app.domain.config.RefreshConfigurationUseCase
import org.example.app.domain.localization.LocalizedStringProvider
import org.example.app.domain.settings.InstallationIdProvider
import org.example.app.fakes.FakeAppSettingsRepository
import org.example.app.fakes.FakeAudioInputDeviceProvider
import org.example.app.fakes.FakeAudioInputGainControl
import org.example.app.fakes.FakeConfigApi
import org.example.app.fakes.FakeConfigurationRepository
import org.example.app.fakes.TestCoroutineDispatchers
import org.example.app.navigation.DefaultSettingsComponent
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** F-020: every Settings control can be scrolled to at the old 800x600 default window. */
@OptIn(ExperimentalTestApi::class)
class SettingsLayoutUiTest {

    @Test
    fun `every settings control is reachable by scrolling at 800x600`() = runDesktopComposeUiTest(800, 600) {
        val settingsRepository = FakeAppSettingsRepository()
        val configurationRepository = FakeConfigurationRepository()
        val dispatchers = TestCoroutineDispatchers()
        val component = DefaultSettingsComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            deviceProvider = FakeAudioInputDeviceProvider(
                listOf(FakeAudioInputDeviceProvider.DEFAULT_DEVICE, FakeAudioInputDeviceProvider.SECONDARY_DEVICE),
            ),
            settingsRepository = settingsRepository,
            configurationRepository = configurationRepository,
            refreshConfigurationUseCase = RefreshConfigurationUseCase(settingsRepository, FakeConfigApi(), configurationRepository),
            micGainApplier = MicGainApplier(FakeAudioInputGainControl(), settingsRepository, dispatchers),
            installationIdProvider = InstallationIdProvider(settingsRepository) { "generated-pc-id" },
            dispatchers = dispatchers,
        )
        setContent { SettingsContent(component, UiLocalization(LocalizedStringProvider(), "en", null)) {} }
        waitForIdle()

        listOf(
            TestTags.Settings.REFRESH_CONFIG_BUTTON,
            TestTags.Settings.INSTALLATION_ID_FIELD,
            TestTags.Settings.BACK_BUTTON,
        ).forEach { tag ->
            onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            val bounds = onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue(bounds.height > 10.dp, "$tag has height ${bounds.height}")
        }
    }
}
