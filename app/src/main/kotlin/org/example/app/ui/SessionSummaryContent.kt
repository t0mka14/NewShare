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
 * Minimal post-protocol summary (§8.6 "session-end/summary stub", chunk 2 scope). The full
 * summary — clip listing, processing progress (§8.8) — lands with the processing/upload chunk;
 * this only confirms the protocol finished and returns to the main menu.
 */
@Composable
fun SessionSummaryContent(localization: UiLocalization, onDone: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(localization.resolve("sessionSummary.title"), style = MaterialTheme.typography.h4)
            Spacer(modifier = Modifier.height(16.dp))
            Text(localization.resolve("sessionSummary.message"), style = MaterialTheme.typography.body1)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onDone, modifier = Modifier.testTag(TestTags.SessionSummary.DONE_BUTTON)) {
                Text(localization.resolve("action.done"))
            }
        }
    }
}
