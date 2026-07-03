package org.example.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Blocking "configuration required" screen (§6.1 pt 4): no active config (no cache, or the
 * server rejected the installation ID). Rendered entirely from bundled fallback strings (§7) —
 * `localization.config` is `null` here by construction. The only way out is Settings, to enter/
 * fix the installation ID and refresh.
 */
@Composable
fun BlockingConfigurationRequiredContent(localization: UiLocalization, onOpenSettings: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(localization.resolve("app.title"), style = MaterialTheme.typography.h4)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                localization.resolve("error.config.required"),
                style = MaterialTheme.typography.body1,
                modifier = Modifier.fillMaxWidth(0.7f).testTag(TestTags.Blocking.CONFIGURATION_REQUIRED_MESSAGE),
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onOpenSettings, modifier = Modifier.testTag(TestTags.Blocking.OPEN_SETTINGS_BUTTON)) {
                Text(localization.resolve("mainMenu.settingsButton"))
            }
        }
    }
}
