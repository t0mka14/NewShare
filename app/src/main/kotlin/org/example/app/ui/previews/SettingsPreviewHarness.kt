package org.example.app.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.window.singleWindowApplication
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.localization.LocalizedStringProvider
import org.example.app.navigation.SettingsComponent
import org.example.app.ui.SettingsContent
import org.example.app.ui.UiLocalization
import org.example.app.ui.theme.ShareTheme

/**
 * Runs the Settings screen in a real window with an inert component:
 * `./gradlew :app:previewSettings`, or under Compose Hot Reload
 * `./gradlew :app:hotRun --mainClass=org.example.app.ui.previews.SettingsPreviewHarnessKt --auto`.
 * Covers the microphone level slider's states (§13 decision 44): level read from the device,
 * a local override next to a config default (reset button visible), and a device whose level
 * cannot be read (slider disabled). Nothing here touches `AppContainer`, the sound subsystem or
 * the filesystem.
 */
fun main() = singleWindowApplication(title = "Settings — preview harness") {
    PreviewSwitcher(
        "Level read" to { SettingsPreview(previewState(micGain = 70, controllable = true)) },
        "Override + config default" to {
            SettingsPreview(previewState(micGain = 40, override = 40, configGain = 63, controllable = true))
        },
        "Level unreadable" to { SettingsPreview(previewState(micGain = null, controllable = false)) },
    )
}

private val previewDevices = listOf(
    AudioInputDevice(id = "usb", name = "Microphone (Sennheiser USB head", eligible = true),
    AudioInputDevice(id = "builtin", name = "Built-in Microphone", eligible = true),
    AudioInputDevice(id = "hdmi", name = "HDMI Audio", eligible = false),
)

private fun previewState(micGain: Int?, controllable: Boolean, override: Int? = null, configGain: Int? = null) =
    SettingsComponent.State(
        availableDevices = previewDevices,
        selectedDeviceId = "usb",
        installationId = "DEMO-001",
        availableLanguages = listOf("cs", "en"),
        selectedLanguage = "en",
        micGain = micGain,
        micGainOverride = override,
        configMicGain = configGain,
        micGainControllable = controllable,
    )

/** Inert [SettingsComponent]: edits update the state so the slider and reset button react. */
private class PreviewSettingsComponent(initial: SettingsComponent.State) : SettingsComponent {
    private val _state = MutableValue(initial)
    override val state: Value<SettingsComponent.State> = _state

    override fun onDeviceSelected(deviceId: String) {
        _state.value = _state.value.copy(selectedDeviceId = deviceId)
    }

    override fun onInstallationIdChanged(value: String) {
        _state.value = _state.value.copy(installationId = value)
    }

    override fun onLanguageSelected(language: String) {
        _state.value = _state.value.copy(selectedLanguage = language)
    }

    override fun onRefreshClicked() = Unit

    override fun onMicGainChanged(value: Int) {
        _state.value = _state.value.copy(micGain = value)
    }

    override fun onMicGainChangeFinished() {
        _state.value = _state.value.copy(micGainOverride = _state.value.micGain)
    }

    override fun onMicGainReset() {
        _state.value = _state.value.copy(micGainOverride = null, micGain = _state.value.configMicGain)
    }
}

@Composable
private fun SettingsPreview(initial: SettingsComponent.State) {
    val component = remember(initial) { PreviewSettingsComponent(initial) }
    ShareTheme {
        SettingsContent(
            component = component,
            localization = UiLocalization(LocalizedStringProvider(), "en", null),
            onBack = {},
        )
    }
}
