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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.example.app.domain.config.IndicatorType
import org.example.app.navigation.SettingsComponent
import kotlin.math.roundToInt

/** §3 Settings screen: mic device + level slider (legacy layout, §13/44), installation ID, language, indicator; Server section (site token, refresh) at the bottom. Scrolls when the window is short. */
@Composable
fun SettingsContent(component: SettingsComponent, localization: UiLocalization, onBack: () -> Unit) {
    val state by component.state.subscribeAsState()
    val contentWidth = Modifier.contentWidth(1100.dp)

    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
        Column(modifier = contentWidth.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.Start) {
            Text(
                localization.resolve("settings.title"),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(modifier = Modifier.height(32.dp))

            Text(localization.resolve("settings.device.label"), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                IconButton(
                    onClick = component::onRefreshDevicesClicked,
                    modifier = Modifier.testTag(TestTags.Settings.DEVICE_REFRESH_BUTTON),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = localization.resolvePlain("settings.device.refresh"))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // The legacy microphone row: value label + 0..100 slider, disabled until the selected
            // device's level has been read (§13 decision 44).
            Text(localization.resolve("settings.micGain.label"), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    state.micGain?.toString().orEmpty(),
                    modifier = Modifier.width(40.dp).testTag(TestTags.Settings.MIC_GAIN_VALUE),
                )
                Slider(
                    value = (state.micGain ?: 0).toFloat(),
                    onValueChange = { component.onMicGainChanged(it.roundToInt()) },
                    onValueChangeFinished = component::onMicGainChangeFinished,
                    valueRange = 0f..100f,
                    steps = 99,
                    enabled = state.micGainControllable,
                    modifier = Modifier.weight(1f).testTag(TestTags.Settings.MIC_GAIN_SLIDER),
                )
            }
            if (state.micGainUnavailable) {
                Text(
                    localization.resolve("settings.micGain.unavailable"),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag(TestTags.Settings.MIC_GAIN_UNAVAILABLE),
                )
            }
            if (state.micGainOverride != null && state.configMicGain != null) {
                TextButton(
                    onClick = component::onMicGainReset,
                    modifier = Modifier.testTag(TestTags.Settings.MIC_GAIN_RESET_BUTTON),
                ) {
                    Text(localization.resolve("settings.micGain.reset", mapOf("value" to state.configMicGain.toString())))
                }
            }
            if (localization.resolvePlain("settings.micGain.hint").isNotBlank()) {
                Text(localization.resolve("settings.micGain.hint"), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))

            Text(localization.resolve("settings.language.label"), style = MaterialTheme.typography.titleMedium)
            RadioSelector(
                selected = state.selectedLanguage,
                items = state.availableLanguages,
                itemLabel = { it },
                itemTag = { TestTags.Settings.languageOption(it) },
                onSelected = component::onLanguageSelected,
                modifier = Modifier.testTag(TestTags.Settings.LANGUAGE_SELECT),
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Local override of the config's `indicatorType` (§13 decision 53)
            Text(localization.resolve("settings.indicator.label"), style = MaterialTheme.typography.titleMedium)
            RadioSelector(
                selected = state.indicatorType,
                items = IndicatorType.entries,
                itemLabel = { localization.resolvePlain("settings.indicator.${it.name}") },
                itemTag = { TestTags.Settings.indicatorOption(it.name) },
                onSelected = component::onIndicatorTypeSelected,
                modifier = Modifier.testTag(TestTags.Settings.INDICATOR_SELECT),
            )
            if (state.indicatorTypeOverride != null) {
                TextButton(
                    onClick = component::onIndicatorTypeReset,
                    modifier = Modifier.testTag(TestTags.Settings.INDICATOR_RESET_BUTTON),
                ) {
                    val configured = localization.resolvePlain("settings.indicator.${state.configIndicatorType.name}")
                    Text(localization.resolve("settings.indicator.reset", mapOf("value" to configured)))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))

            // Server section, kept at the bottom: site token, configuration refresh.
            Text(localization.resolve("settings.server.title"), style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(localization.resolve("settings.siteToken.label"), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.siteToken,
                onValueChange = component::onSiteTokenChanged,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(TestTags.Settings.SITE_TOKEN_FIELD),
            )
            Spacer(modifier = Modifier.height(8.dp))

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
                    color = if (isSuccess) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                )
            }
            if (state.registeredToAnotherSite) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    localization.resolve("settings.otherSite"),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(TestTags.Settings.OTHER_SITE_NOTE),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))

            Text(localization.resolve("settings.installationId.label"), style = MaterialTheme.typography.titleMedium)
            SelectionContainer {
                Text(
                    state.installationId,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag(TestTags.Settings.INSTALLATION_ID_FIELD),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterHorizontally).testTag(TestTags.Settings.BACK_BUTTON),
            ) {
                Text(localization.resolve("settings.backButton"))
            }
        }
    }
}
