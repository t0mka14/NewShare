package org.example.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import org.example.app.domain.config.InfoTask
import org.example.app.domain.config.Protocol
import org.example.app.domain.config.QuestionnaireTask
import org.example.app.domain.config.Task
import org.example.app.domain.config.VideoTask
import org.example.app.domain.config.VocalTask
import org.example.app.domain.config.capturesVideo
import org.example.app.navigation.ProtocolPickerComponent

/** §3 follow-up: shown on the main menu only when the config defines more than one protocol. */
@Composable
fun ProtocolPickerContent(component: ProtocolPickerComponent, localization: UiLocalization) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.contentWidth(900.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(localization.resolve("protocolPicker.title"), style = MaterialTheme.typography.headlineLarge)
            Text(localization.resolve("protocolPicker.instructions"), style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))

            // Grouped by project (config alignment row 3), projects in first-appearance order;
            // protocols without a project (older configs) get no heading.
            Column(
                modifier = Modifier.weight(1f, fill = false)
                    .fillMaxWidth(0.70f)
                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                component.protocols.groupBy { it.project }.forEach { (project, protocols) ->
                    if (project != null) {
                        Text(
                            project,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp).testTag(TestTags.ProtocolPicker.projectHeader(project)),
                        )
                    }
                    protocols.forEach { protocol ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Button(
                                onClick = { component.onProtocolSelected(protocol) },
                                modifier = Modifier.fillMaxWidth(0.6f)
                                    .testTag(TestTags.ProtocolPicker.protocolButton(protocol.name, protocol.project)),
                            ) {
                                Text(protocol.name)
                            }
                            Text(
                                protocolSummary(protocol, localization),
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
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

/** "No. tasks: 5, VOCAL: 3x, INFO: 2x. Requires camera. Version: 1." — types with 0 tasks are omitted. */
internal fun protocolSummary(protocol: Protocol, localization: UiLocalization): String {
    fun s(key: String, vararg placeholders: Pair<String, Any>) =
        placeholders.fold(localization.resolvePlain("protocolPicker.summary.$key")) { raw, (name, value) ->
            raw.replace("{$name}", value.toString())
        }
    val counts = listOf(s("tasks", "count" to protocol.tasks.size)) +
        protocol.tasks.groupingBy { it.typeName }.eachCount()
            .map { (type, count) -> s("taskType", "type" to type, "count" to count) }
    val sentences = listOfNotNull(
        counts.joinToString(", "),
        if (protocol.tasks.any { it.capturesVideo }) s("camera") else null,
        s("version", "version" to (protocol.version ?: "-")),
    )
    return sentences.joinToString(". ", postfix = ".")
}

private val Task.typeName: String
    get() = when (this) {
        is VocalTask -> "VOCAL"
        is QuestionnaireTask -> "QUESTIONNAIRE"
        is InfoTask -> "INFO"
        is VideoTask -> "VIDEO"
    }
