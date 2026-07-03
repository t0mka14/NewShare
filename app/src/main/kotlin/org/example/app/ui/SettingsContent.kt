package org.example.app.ui

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
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.example.app.navigation.SettingsComponent

/** §3 Settings screen: mic device, installation ID, language, refresh — nothing else. */
@Composable
fun SettingsContent(component: SettingsComponent, localization: UiLocalization, onBack: () -> Unit) {
    val state by component.state.subscribeAsState()
    val contentWidth = Modifier.fillMaxWidth(0.6f)

    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
        Column(modifier = contentWidth, horizontalAlignment = Alignment.Start) {
            Text(
                localization.resolve("settings.title"),
                style = MaterialTheme.typography.h4,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(modifier = Modifier.height(32.dp))

            Text(localization.resolve("settings.device.label"), style = MaterialTheme.typography.subtitle1)
            DropdownSelector(
                triggerTag = TestTags.Settings.DEVICE_SELECT,
                selectedLabel = state.availableDevices.firstOrNull { it.id == state.selectedDeviceId }?.name
                    ?: state.selectedDeviceId.orEmpty(),
                items = state.availableDevices,
                itemLabel = { it.name },
                itemEnabled = { it.eligible },
                itemTag = { TestTags.Settings.deviceOption(it.id) },
                onSelected = { component.onDeviceSelected(it.id) },
            )
            Spacer(modifier = Modifier.height(24.dp))

            Text(localization.resolve("settings.installationId.label"), style = MaterialTheme.typography.subtitle1)
            OutlinedTextField(
                value = state.installationId,
                onValueChange = component::onInstallationIdChanged,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(TestTags.Settings.INSTALLATION_ID_FIELD),
            )
            Spacer(modifier = Modifier.height(24.dp))

            Text(localization.resolve("settings.language.label"), style = MaterialTheme.typography.subtitle1)
            DropdownSelector(
                triggerTag = TestTags.Settings.LANGUAGE_SELECT,
                selectedLabel = state.selectedLanguage.orEmpty(),
                items = state.availableLanguages,
                itemLabel = { it },
                itemTag = { TestTags.Settings.languageOption(it) },
                onSelected = component::onLanguageSelected,
            )
            Spacer(modifier = Modifier.height(32.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = component::onRefreshClicked,
                    enabled = !state.refreshInProgress,
                    modifier = Modifier.testTag(TestTags.Settings.REFRESH_CONFIG_BUTTON),
                ) {
                    Text(localization.resolve("settings.refresh.button"))
                }
                if (state.refreshInProgress) {
                    CircularProgressIndicator(modifier = Modifier.height(24.dp))
                }
            }
            state.lastRefreshResultKey?.let { key ->
                Spacer(modifier = Modifier.height(8.dp))
                val isSuccess = key == "settings.refresh.success"
                Text(
                    localization.resolve(key),
                    color = if (isSuccess) MaterialTheme.colors.onSurface else MaterialTheme.colors.error,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onBack, modifier = Modifier.testTag(TestTags.Settings.BACK_BUTTON)) {
                Text(localization.resolve("action.back"))
            }
        }
    }
}
