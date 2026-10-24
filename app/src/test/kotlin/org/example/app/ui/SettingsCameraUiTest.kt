package org.example.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
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
import org.example.app.fakes.FakeVideoInputDeviceProvider
import org.example.app.fakes.TestCoroutineDispatchers
import org.example.app.navigation.DefaultSettingsComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** F-019: the camera dropdown and its entries render, and picking one is saved. */
@OptIn(ExperimentalTestApi::class)
class SettingsCameraUiTest {

    private class Rig(video: FakeVideoInputDeviceProvider) {
        val settingsRepository = FakeAppSettingsRepository()
        val dispatchers = TestCoroutineDispatchers()
        private val configurationRepository = FakeConfigurationRepository()
        val component = DefaultSettingsComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            deviceProvider = FakeAudioInputDeviceProvider(listOf(FakeAudioInputDeviceProvider.DEFAULT_DEVICE)),
            settingsRepository = settingsRepository,
            configurationRepository = configurationRepository,
            refreshConfigurationUseCase = RefreshConfigurationUseCase(settingsRepository, FakeConfigApi(), configurationRepository),
            micGainApplier = MicGainApplier(FakeAudioInputGainControl(), settingsRepository, dispatchers),
            installationIdProvider = InstallationIdProvider(settingsRepository) { "generated-pc-id" },
            dispatchers = dispatchers,
            videoDeviceProvider = video,
        )
    }

    @Test
    fun `F-019 camera dropdown lists Automatic and the cameras and saves the pick`() = runDesktopComposeUiTest(1000, 820) {
        val rig = Rig(FakeVideoInputDeviceProvider())
        setContent { SettingsContent(rig.component, UiLocalization(LocalizedStringProvider(), "en", null)) {} }
        waitForIdle()
        onNodeWithTag(TestTags.Settings.CAMERA_LOADING).assertIsDisplayed()

        rig.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()
        onNodeWithTag(TestTags.Settings.CAMERA_SELECT).assertIsDisplayed().performClick()
        waitForIdle()
        onNodeWithTag(TestTags.Settings.CAMERA_AUTOMATIC_OPTION).assertIsDisplayed()
        onNodeWithTag(TestTags.Settings.cameraOption("fake-camera-1")).assertIsDisplayed().performClick()
        waitForIdle()

        assertEquals("fake-camera-1", rig.settingsRepository.read()!!.cameraDeviceId)
    }

    @Test
    fun `F-019 no camera shows only Automatic and a note`() = runDesktopComposeUiTest(1000, 820) {
        val rig = Rig(FakeVideoInputDeviceProvider(emptyList()))
        setContent { SettingsContent(rig.component, UiLocalization(LocalizedStringProvider(), "en", null)) {} }
        rig.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        onNodeWithTag(TestTags.Settings.CAMERA_NONE_NOTE).assertIsDisplayed()
        onNodeWithTag(TestTags.Settings.CAMERA_SELECT).performClick()
        waitForIdle()
        onNodeWithTag(TestTags.Settings.CAMERA_AUTOMATIC_OPTION).assertIsDisplayed()
        assertNull(rig.settingsRepository.read()?.cameraDeviceId)
    }
}
