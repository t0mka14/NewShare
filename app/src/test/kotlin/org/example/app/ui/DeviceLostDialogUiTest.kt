package org.example.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.localization.LocalizedStringProvider
import org.example.app.fakes.FakeAudioInputDeviceProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** A mic hot-plug re-emits the device list while the dialog is open; it must not reset the examiner's pick. */
class DeviceLostDialogUiTest {
    private val localization = UiLocalization(LocalizedStringProvider(builtins = emptyMap()), "en", null)
    private val first = FakeAudioInputDeviceProvider.DEFAULT_DEVICE
    private val second = FakeAudioInputDeviceProvider.SECONDARY_DEVICE
    private val plugged = AudioInputDevice(id = "usb-new", name = "Hot-plugged Mic", eligible = true)

    @OptIn(ExperimentalTestApi::class)
    private fun run(
        block: androidx.compose.ui.test.DesktopComposeUiTest.(setDevices: (List<AudioInputDevice>) -> Unit, resumed: MutableList<AudioInputDevice>) -> Unit,
    ) = runWithDefault { setDevices, resumed, _ -> block(setDevices, resumed) }

    @OptIn(ExperimentalTestApi::class)
    private fun runWithDefault(
        block: androidx.compose.ui.test.DesktopComposeUiTest.(setDevices: (List<AudioInputDevice>) -> Unit, resumed: MutableList<AudioInputDevice>, setDefault: (AudioInputDevice) -> Unit) -> Unit,
    ) =
        runDesktopComposeUiTest {
            var devices by mutableStateOf(listOf(first, second))
            var default by mutableStateOf(first)
            val resumed = mutableListOf<AudioInputDevice>()
            setContent {
                DeviceLostDialog(
                    localization = localization,
                    availableDevices = devices,
                    defaultDevice = default,
                    requiresExplicitResume = true,
                    messageTag = "msg",
                    onDeviceAction = { resumed += it },
                )
            }
            waitForIdle()
            block({ devices = it }, resumed, { default = it })
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the examiner pick survives a list update that keeps the device`() = run { setDevices, resumed ->
        onNodeWithTag(TestTags.DeviceLostDialog.DEVICE_SELECT).performClick()
        onNodeWithTag("${TestTags.DeviceLostDialog.DEVICE_SELECT}.${second.id}").performClick()
        waitForIdle()

        setDevices(listOf(first, second, plugged))
        waitForIdle()
        onNodeWithTag(TestTags.DeviceLostDialog.RESUME_BUTTON).performClick()
        assertEquals(listOf(second), resumed)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the pick falls back to the default device when it vanishes from the list`() = run { setDevices, resumed ->
        onNodeWithTag(TestTags.DeviceLostDialog.DEVICE_SELECT).performClick()
        onNodeWithTag("${TestTags.DeviceLostDialog.DEVICE_SELECT}.${second.id}").performClick()
        waitForIdle()

        // Pick is gone, default is still listed: the default wins over the other present devices.
        setDevices(listOf(plugged, first))
        waitForIdle()
        onNodeWithTag(TestTags.DeviceLostDialog.RESUME_BUTTON).performClick()
        assertEquals(listOf(first), resumed)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a default device absent from the list falls back to a present device`() = run { setDevices, resumed ->
        // `first` (the lost mic) is gone from the list; the dialog must not offer it.
        setDevices(listOf(second, plugged))
        waitForIdle()
        onNodeWithTag(TestTags.DeviceLostDialog.RESUME_BUTTON).performClick()
        assertEquals(listOf(second), resumed)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the pick survives the default device being replaced by an equal-id object`() = runWithDefault { _, resumed, setDefault ->
        onNodeWithTag(TestTags.DeviceLostDialog.DEVICE_SELECT).performClick()
        onNodeWithTag("${TestTags.DeviceLostDialog.DEVICE_SELECT}.${second.id}").performClick()
        waitForIdle()

        setDefault(first.copy(eligible = false, name = "Renamed"))
        waitForIdle()
        onNodeWithTag(TestTags.DeviceLostDialog.RESUME_BUTTON).performClick()
        assertEquals(listOf(second), resumed)
    }
}
