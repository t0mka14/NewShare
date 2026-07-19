package org.example.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.example.app.navigation.MainMenuComponent

/** §3 main menu: New examination / Upload / Settings / Sessions, in the original's centered
 * button-column layout (§4 "keep... look and layout"). */
@Composable
fun MainMenuContent(component: MainMenuComponent, localization: UiLocalization) {
    val state by component.state.subscribeAsState()
    val buttonWidth = Modifier.fillMaxWidth(0.35f)

    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(localization.resolve("mainMenu.title"), style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = component::onStartProtocol,
                enabled = state.startEnabled,
                modifier = buttonWidth.testTag(TestTags.MainMenu.START_PROTOCOL_BUTTON),
            ) {
                Text(localization.resolve("mainMenu.startButton"))
            }
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = component::onUpload,
                modifier = buttonWidth.testTag(TestTags.MainMenu.UPLOAD_BUTTON),
            ) {
                Text(localization.resolve("mainMenu.uploadButton"))
            }
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = component::onSettings,
                modifier = buttonWidth.testTag(TestTags.MainMenu.SETTINGS_BUTTON),
            ) {
                Text(localization.resolve("mainMenu.settingsButton"))
            }
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = component::onSessionBrowser,
                modifier = buttonWidth.testTag(TestTags.MainMenu.SESSION_BROWSER_BUTTON),
            ) {
                Text(localization.resolve("mainMenu.sessionBrowserButton"))
            }
        }
    }
}
