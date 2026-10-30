package org.example.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.example.app.navigation.CalibrationComponent
import org.example.app.ui.theme.ShareAccentOrange
import org.example.app.ui.theme.ShareAccentOrangeContainer

/**
 * §6.2 calibration screen — a 1:1 copy of the legacy `CalibrationScreen` (§13 decision 36,
 * `shareapp/src/main/kotlin/screens/CalibrationScreen.kt`): title, instructions card, the
 * mic-position photo next to the vertical [SoundLevelBar], and the pill Back / Continue
 * buttons. Legacy bugs fixed: the image is classpath-loaded (`painterResource`) instead of a
 * working-directory `File`, the between-section `Spacer`s use height (the legacy used width
 * inside a Column), and the level bar's green target band comes from the local
 * `optimalLoudness` setting instead of the legacy hardcoded zone. The device dropdown has no
 * legacy counterpart (the legacy picked the mic in settings) and is kept discreetly below the
 * bar row — device selection must stay available here (§8.5).
 */
@Composable
fun CalibrationContent(component: CalibrationComponent, localization: UiLocalization, onBack: () -> Unit) {
    val state by component.state.subscribeAsState()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // F-127: 90% of the window, but the column is capped (like Settings) so a full-screen window keeps it readable
        Box(modifier = Modifier.fillMaxSize(0.9f), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxHeight().contentWidth(1100.dp).testTag(TestTags.Calibration.CONTENT),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                localization.resolve(state.titleKey),
                style = screenTitleTextStyle(),
                modifier = Modifier.padding(bottom = 20.dp),
            )
            Card {
                Column(modifier = Modifier.padding(all = 16.dp)) {
                    state.instructionKeys.forEach { key ->
                        Text(localization.resolve(key), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            if (state.micFailed) {
                Card {
                    Row(
                        modifier = Modifier.padding(all = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                localization.resolve("calibration.micError"),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag(TestTags.Calibration.MIC_ERROR),
                            )
                            if (state.micRetryFailed) {
                                Text(
                                    localization.resolve("calibration.micErrorRestart"),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.testTag(TestTags.Calibration.MIC_ERROR_RESTART),
                                )
                            }
                        }
                        Button(
                            shape = MaterialTheme.shapes.large,
                            onClick = component::onRetry,
                            modifier = Modifier.testTag(TestTags.Calibration.RETRY_BUTTON),
                        ) {
                            Text(localization.resolve("action.retry"), style = actionButtonTextStyle())
                        }
                    }
                }
            }
            //Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                // F-127: the level meter sits next to the picture instead of at the far edge
                horizontalArrangement = Arrangement.spacedBy(72.dp, Alignment.CenterHorizontally),
            ) {
                Image(
                    painter = painterResource("drawable/mic_position_trans.png"),
                    contentDescription = "Microphone position",
                    modifier = Modifier.fillMaxHeight(0.5f).testTag(TestTags.Calibration.MIC_IMAGE),
                )
                SoundLevelBar(
                    level = state.level,
                    minLoudness = state.minLoudness,
                    maxLoudness = state.maxLoudness,
                    modifier = Modifier.width(160.dp).fillMaxHeight(0.5f).testTag(TestTags.Calibration.LEVEL_INDICATOR),
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Button(
                    shape = MaterialTheme.shapes.large,
                    onClick = onBack,
                ) {
                    Text(localization.resolve("action.back"), style = actionButtonTextStyle())
                }
                // No legacy counterpart: the input device must be selectable here (§8.5)
                DropdownSelector(
                    triggerTag = TestTags.Calibration.DEVICE_SELECT,
                    selectedLabel = state.selectedDevice?.name.orEmpty(),
                    items = state.availableDevices,
                    itemLabel = { it.name },
                    itemEnabled = { it.eligible },
                    itemTag = { "${TestTags.Calibration.DEVICE_SELECT}.${it.id}" },
                    onSelected = component::onDeviceSelected,
                )
                Button(
                    onClick = component::onConfirm,
                    enabled = state.canConfirm,
                    modifier = Modifier
                        .testTag(TestTags.Calibration.CONFIRM_BUTTON),
                ) {
                    Text(localization.resolve("calibration.continueButton"), style = actionButtonTextStyle())
                }
            }
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

/**
 * The legacy vertical `AudioUtilities.SoundLevelBar`: an outlined bar filling bottom-up with
 * the orange accent as the input level rises, a translucent green target band, and a 0.1–0.9
 * tick scale along the bar's left edge. Drawn bottom-up directly instead of the legacy's
 * `rotate(180°)` trick (identical result); the level is the recorder's normalized 0–1 value
 * (the legacy `level * 10` rescaled its raw units) and the green band spans the configured
 * `[minLoudness, maxLoudness]` instead of the legacy hardcoded zone.
 */
@Composable
private fun SoundLevelBar(
    level: Float,
    minLoudness: Double,
    maxLoudness: Double,
    modifier: Modifier = Modifier,
) {
    val textMeasure = rememberTextMeasurer()
    val colorLevels = ShareAccentOrange
    val colorBar = ShareAccentOrangeContainer
    val colorBox = MaterialTheme.colorScheme.onSurfaceVariant
    val anim: Float by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessVeryLow,
        ),
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val barWidth = size.width / 3
            drawRect(
                color = colorBox,
                size = Size(barWidth, size.height),
                style = Stroke(width = 2.dp.toPx()),
            )
            val fillHeight = size.height * anim
            drawRect(
                color = colorBar,
                size = Size(barWidth - 2.dp.toPx(), fillHeight),
                topLeft = Offset(1.dp.toPx(), size.height - fillHeight),
            )
            val bandHeight = size.height * (maxLoudness - minLoudness).toFloat().coerceIn(0f, 1f)
            drawRect(
                color = Color.Green,
                alpha = 0.3f,
                size = Size(barWidth - 2.dp.toPx(), bandHeight),
                topLeft = Offset(1.dp.toPx(), size.height * (1f - maxLoudness.toFloat().coerceIn(0f, 1f))),
            )
            for (i in 1..9) {
                drawLine(
                    color = colorLevels,
                    start = Offset(-10f, size.height * i / 10),
                    end = Offset(10f, size.height * i / 10),
                    strokeWidth = 5f,
                )
                drawText(
                    textMeasurer = textMeasure,
                    text = "0.${10 - i}",
                    topLeft = Offset(-55f, size.height * i / 10 - 12),
                )
            }
        }
    }
}
