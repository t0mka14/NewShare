package org.example.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.example.app.domain.config.ConfigError

/**
 * Blocking "configuration required" screen (§6.1 pt 4): no active config (no cache, or the
 * server rejected the site token). Rendered entirely from bundled fallback strings (§7) —
 * `localization.config` is `null` here by construction. The only way out is Settings, to enter/
 * fix the site token and refresh. Shows why the last refresh failed ([error]) when known,
 * else the generic "configuration required" text.
 */
@Composable
fun BlockingConfigurationRequiredContent(
    localization: UiLocalization,
    error: ConfigError?,
    onOpenSettings: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(localization.resolve("app.title"), style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                localization.resolve(error?.messageKey() ?: "error.config.required"),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.contentWidth(1300.dp).testTag(TestTags.Blocking.CONFIGURATION_REQUIRED_MESSAGE),
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onOpenSettings, modifier = Modifier.testTag(TestTags.Blocking.OPEN_SETTINGS_BUTTON)) {
                Text(localization.resolve("mainMenu.settingsButton"))
            }
        }
    }
}
