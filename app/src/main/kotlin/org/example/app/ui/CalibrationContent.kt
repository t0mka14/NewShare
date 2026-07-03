package org.example.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.example.app.navigation.CalibrationComponent

/** §6.2 calibration screen: live input level vs. the configured `optimalLoudness` band, must be
 * confirmed before the master recording starts. */
@Composable
fun CalibrationContent(component: CalibrationComponent, localization: UiLocalization) {
    val state by component.state.subscribeAsState()

    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth(0.7f),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(localization.resolve(state.titleKey), style = MaterialTheme.typography.h4)

            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    state.instructionKeys.forEach { key -> Text(localization.resolve(key)) }
                }
            }

            CalibrationLevelMeter(
                level = state.level,
                minLoudness = state.minLoudness,
                maxLoudness = state.maxLoudness,
                inRange = state.inRange,
                modifier = Modifier.fillMaxWidth().height(80.dp).testTag(TestTags.Calibration.LEVEL_INDICATOR),
            )

            DropdownSelector(
                triggerTag = TestTags.Calibration.DEVICE_SELECT,
                selectedLabel = state.selectedDevice?.name.orEmpty(),
                items = state.availableDevices,
                itemLabel = { it.name },
                itemEnabled = { it.eligible },
                itemTag = { "${TestTags.Calibration.DEVICE_SELECT}.${it.id}" },
                onSelected = component::onDeviceSelected,
            )

            Button(onClick = component::onConfirm, modifier = Modifier.testTag(TestTags.Calibration.CONFIRM_BUTTON)) {
                Text(localization.resolve("action.confirm"))
            }
        }

        if (state.deviceLost) {
            DeviceLostDialog(
                localization = localization,
                availableDevices = state.availableDevices,
                defaultDevice = state.selectedDevice,
                requiresExplicitResume = false,
                messageTag = TestTags.Calibration.DEVICE_LOST_ERROR,
                onDeviceAction = component::onDeviceSelected,
            )
        }
    }
}

/** Horizontal level meter with the `[minLoudness, maxLoudness]` band highlighted (§6.2) —
 * replaces the original's mixed-formula `SoundLevelBar` (§4) with one linear 0.0-1.0 scale. */
@Composable
private fun CalibrationLevelMeter(
    level: Float,
    minLoudness: Double,
    maxLoudness: Double,
    inRange: Boolean,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colors.onSurface.copy(alpha = 0.15f)
    val bandColor = MaterialTheme.colors.secondary.copy(alpha = 0.35f)
    val levelColor = if (inRange) MaterialTheme.colors.secondary else MaterialTheme.colors.error

    Canvas(modifier = modifier) {
        drawRect(color = trackColor, size = size)
        val bandStart = (minLoudness.coerceIn(0.0, 1.0) * size.width).toFloat()
        val bandEnd = (maxLoudness.coerceIn(0.0, 1.0) * size.width).toFloat()
        drawRect(
            color = bandColor,
            topLeft = Offset(bandStart, 0f),
            size = Size(bandEnd - bandStart, size.height),
        )
        val levelX = (level.coerceIn(0f, 1f) * size.width)
        drawRect(color = levelColor, size = Size(4.dp.toPx(), size.height), topLeft = Offset(levelX, 0f))
    }
}
