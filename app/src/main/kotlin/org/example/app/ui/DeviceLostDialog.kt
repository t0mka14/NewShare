package org.example.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.example.app.domain.audio.AudioInputDevice

/**
 * Blocking device-lost reconnect dialog (§8.5), rendered inside the main window's Compose
 * scene (§5.2) — never a separate OS window. Shared by the calibration screen (single-step:
 * picking a device applies it immediately, mirroring [CalibrationComponent.onDeviceSelected])
 * and the task screen (two-step: pick, then an explicit resume, mirroring
 * [TaskComponent.onDeviceReselected]) — [requiresExplicitResume] switches between the two.
 */
@Composable
fun DeviceLostDialog(
    localization: UiLocalization,
    availableDevices: List<AudioInputDevice>,
    defaultDevice: AudioInputDevice?,
    requiresExplicitResume: Boolean,
    messageTag: String,
    onDeviceAction: (AudioInputDevice) -> Unit,
) {
    var selected by remember(availableDevices, defaultDevice) {
        mutableStateOf(defaultDevice ?: availableDevices.firstOrNull())
    }

    AlertDialog(
        onDismissRequest = { /* blocking: dismissable only via reconnect/resume (§8.5) */ },
        title = { Text(localization.resolve("error.dialog.title")) },
        text = {
            Column {
                Text(localization.resolve("error.audio.deviceLost"), modifier = Modifier.testTag(messageTag))
                Spacer(modifier = Modifier.height(12.dp))
                Text(localization.resolve("settings.device.label"))
                DropdownSelector(
                    triggerTag = TestTags.DeviceLostDialog.DEVICE_SELECT,
                    selectedLabel = selected?.name.orEmpty(),
                    items = availableDevices,
                    itemLabel = { it.name },
                    itemEnabled = { it.eligible },
                    itemTag = { "${TestTags.DeviceLostDialog.DEVICE_SELECT}.${it.id}" },
                    onSelected = { device ->
                        selected = device
                        if (!requiresExplicitResume) onDeviceAction(device)
                    },
                )
            }
        },
        confirmButton = {
            Row {
                Button(
                    onClick = { defaultDevice?.let(onDeviceAction) },
                    modifier = Modifier.testTag(TestTags.DeviceLostDialog.RECONNECT_BUTTON),
                    enabled = defaultDevice != null,
                ) {
                    Text(localization.resolve("action.reconnect"))
                }
                if (requiresExplicitResume) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { selected?.let(onDeviceAction) },
                        modifier = Modifier.testTag(TestTags.DeviceLostDialog.RESUME_BUTTON),
                        enabled = selected != null,
                    ) {
                        Text(localization.resolve("action.resume"))
                    }
                }
            }
        },
    )
}
