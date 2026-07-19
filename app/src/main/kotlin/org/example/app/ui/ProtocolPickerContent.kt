package org.example.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.example.app.navigation.ProtocolPickerComponent

/** §3 follow-up: shown on the main menu only when the config defines more than one protocol. */
@Composable
fun ProtocolPickerContent(component: ProtocolPickerComponent, localization: UiLocalization) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth(0.5f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(localization.resolve("protocolPicker.title"), style = MaterialTheme.typography.headlineLarge)
            Text(localization.resolve("protocolPicker.instructions"), style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))

            component.protocols.forEach { protocol ->
                Button(
                    onClick = { component.onProtocolSelected(protocol) },
                    modifier = Modifier.fillMaxWidth(0.6f).testTag(TestTags.ProtocolPicker.protocolButton(protocol.name)),
                ) {
                    Text(protocol.name)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = component::onBack,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.testTag(TestTags.ProtocolPicker.BACK_BUTTON),
            ) {
                Text(localization.resolve("action.back"))
            }
        }
    }
}
