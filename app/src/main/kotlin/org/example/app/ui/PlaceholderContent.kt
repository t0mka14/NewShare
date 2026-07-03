package org.example.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Stand-in for the upload screen (§8.9) and session browser (§8.11) — out of scope for this
 * chunk (main menu / settings / patient info / calibration / task / questionnaire / info /
 * session-end stub only). Wired so `MainMenuComponent.onUpload()`/`onSessionBrowser()` have a
 * real destination instead of a dead click; a later chunk replaces this with the real screens.
 */
@Composable
fun PlaceholderContent(localization: UiLocalization, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(localization.resolve("placeholder.title"), style = MaterialTheme.typography.h4)
            Spacer(modifier = Modifier.height(16.dp))
            Text(localization.resolve("placeholder.message"), style = MaterialTheme.typography.body1)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onBack, modifier = Modifier.testTag(TestTags.Placeholder.BACK_BUTTON)) {
                Text(localization.resolve("action.back"))
            }
        }
    }
}
