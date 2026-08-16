package org.example.app.ui.previews

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.singleWindowApplication

/**
 * Runs the [CalibrationPreviews] states in a real window: `./gradlew :app:previewCalibration`
 * (or the gutter icon next to `main`). Unlike a static preview this animates, accepts clicks and
 * resizes, which is what the legacy-copied screens actually need checking for (§13 decision 36 —
 * fixed pixel sizes that break scaling are bugs, not a look to preserve).
 *
 * Nothing here touches `AppContainer`, the recorder or the filesystem; the screen is driven by the
 * inert preview component, so no session, microphone or configuration is involved.
 */
fun main() = singleWindowApplication(title = "Calibration — preview harness") {
    PreviewSwitcher(
        "In range" to { CalibrationInRangePreview() },
        "Too quiet" to { CalibrationTooQuietPreview() },
        "Too loud" to { CalibrationTooLoudPreview() },
        "Device lost" to { CalibrationDeviceLostPreview() },
    )
}

/** Chip row that swaps between the preview states, so one window covers all of them. */
@Composable
private fun PreviewSwitcher(vararg previews: Pair<String, @Composable () -> Unit>) {
    var selected by remember { mutableStateOf(0) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            previews.forEachIndexed { index, (label, _) ->
                FilterChip(
                    selected = index == selected,
                    onClick = { selected = index },
                    label = { Text(label) },
                )
            }
        }
        previews[selected].second()
    }
}
