package org.example.app.ui.previews

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.config.RemoteConfig
import org.example.app.domain.localization.LocalizedStringProvider
import org.example.app.navigation.CalibrationComponent
import org.example.app.ui.CalibrationContent
import org.example.app.ui.UiLocalization
import org.example.app.ui.theme.ShareTheme

/**
 * Design-time previews of [CalibrationContent] (§13 decision 36 — the legacy-copied screens are
 * the ones worth eyeballing). A preview function must take no parameters, so the screen's
 * [CalibrationComponent] and [UiLocalization] are supplied as inert stand-ins below.
 *
 * These live in the main source set because the IDE's preview scanner only looks there; they add
 * a few KB to the distribution and are referenced by nothing in the app itself. `PreviewHarness`
 * renders the same functions in a real window (`./gradlew :app:previewCalibration`), which is the
 * better tool for anything animated or clickable — the level bar springs from 0, and the device
 * dropdown and device-lost dialog need input a static preview cannot give them.
 */

/** Renders whatever state it is constructed with and ignores every interaction. */
private class PreviewCalibrationComponent(state: CalibrationComponent.State) : CalibrationComponent {
    override val state: Value<CalibrationComponent.State> = MutableValue(state)
    override fun onDeviceSelected(device: AudioInputDevice) = Unit
    override fun onConfirm() = Unit
}

private val previewMic = AudioInputDevice(id = "mic-1", name = "USB microphone", eligible = true)
private val previewMicUnusable = AudioInputDevice(id = "mic-2", name = "Built-in input (no PCM)", eligible = false)

/**
 * With `config = null` the title/instruction keys would render as the keys themselves (§7's last
 * fallback step), which tells you nothing about the layout — so the preview carries a minimal
 * strings table. `action.back`/`action.next` resolve from `BuiltinStrings.en`.
 */
private val previewLocalization = UiLocalization(
    provider = LocalizedStringProvider(),
    language = "en",
    config = RemoteConfig(
        schemaVersion = 1,
        configVersion = "preview",
        defaultLanguage = "en",
        strings = mapOf(
            "en" to mapOf(
                "calibration_title" to "Calibration",
                "calibration_instructions" to
                    "Sit about 30 cm from the microphone and speak at a normal volume. " +
                    "Keep the level inside the <bold>green band</bold>.",
            ),
        ),
    ),
)

private fun previewState(
    level: Float = 0.35f,
    deviceLost: Boolean = false,
) = CalibrationComponent.State(
    titleKey = "calibration_title",
    instructionKeys = listOf("calibration_instructions"),
    level = level,
    minLoudness = 0.2,
    maxLoudness = 0.5,
    inRange = level >= 0.2f && level <= 0.5f,
    availableDevices = listOf(previewMic, previewMicUnusable),
    selectedDevice = previewMic,
    deviceLost = deviceLost,
)

/**
 * [ShareTheme] is applied here rather than by the screen: `CalibrationContent` is themed by
 * `Main.kt` in production (like every other screen), and neither `PreviewHarness` nor the IDE
 * preview pane wraps its content — without this the preview would render on M3's purple
 * baseline. No `Surface` either, so the preview matches what `Main.kt` actually renders.
 */
@Composable
private fun CalibrationPreview(state: CalibrationComponent.State) {
    ShareTheme {
        CalibrationContent(
            component = PreviewCalibrationComponent(state),
            localization = previewLocalization,
            onBack = {},
        )
    }
}

/** Level inside the configured `optimalLoudness` band. */
@Preview
@Composable
fun CalibrationInRangePreview() = CalibrationPreview(previewState())

/** Level below the band — the orange fill barely rises above the baseline. */
@Preview
@Composable
fun CalibrationTooQuietPreview() = CalibrationPreview(previewState(level = 0.05f))

/** Level above the band. */
@Preview
@Composable
fun CalibrationTooLoudPreview() = CalibrationPreview(previewState(level = 0.85f))

/** §8.5 device loss while monitoring: the blocking dialog over the screen. */
@Preview
@Composable
fun CalibrationDeviceLostPreview() = CalibrationPreview(previewState(deviceLost = true))
