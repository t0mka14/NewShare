package org.example.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.example.app.navigation.SessionBrowserComponent

/** §8.11 session browser — lists every recorded session with per-session actions. Draws on the
 * same visual language as the upload screen (bordered list, big action buttons, §13 decision 36). */
@Composable
fun SessionBrowserContent(component: SessionBrowserComponent, localization: UiLocalization) {
    val state by component.state.subscribeAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(localization.resolve("sessionBrowser.title"), style = MaterialTheme.typography.headlineLarge)

        if (state.rows.isEmpty()) {
            Text(localization.resolve("sessionBrowser.noSessions"), style = MaterialTheme.typography.bodyLarge)
        } else {
            LazyColumn(
                modifier = Modifier
                    .border(width = 2.dp, color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp))
                    .fillMaxWidth(0.85f)
                    .height(360.dp),
            ) {
                items(state.rows, key = { it.sessionId }) { row ->
                    Column(
                        modifier = Modifier.fillMaxWidth().testTag(TestTags.SessionBrowser.sessionRow(row.sessionId)).padding(12.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text("${row.patientCode} — ${row.startedAt}", style = MaterialTheme.typography.bodyLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        localization.resolve("sessionBrowser.processingStatus.${(row.processingStatus ?: org.example.app.domain.session.ProcessingStatus.NotProcessed).name}"),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    row.uploadStatus?.let { status ->
                                        Text(localization.resolve("sessionBrowser.uploadStatus.${status.name}"), style = MaterialTheme.typography.bodyMedium)
                                    }
                                    if (row.recovered) {
                                        Text(
                                            localization.resolve("sessionBrowser.recoveredLabel"),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { component.onOpenEditor(row.folderName) },
                                    modifier = Modifier.testTag(TestTags.SessionBrowser.openEditorButton(row.sessionId)),
                                ) {
                                    Text(localization.resolve("sessionBrowser.openEditorButton"))
                                }
                                Button(
                                    onClick = { component.onReprocess(row.folderName) },
                                    modifier = Modifier.testTag(TestTags.SessionBrowser.reprocessButton(row.sessionId)),
                                ) {
                                    Text(localization.resolve("sessionBrowser.reprocessButton"))
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = component::onGoToUpload,
                modifier = Modifier.testTag(TestTags.SessionBrowser.GO_TO_UPLOAD_BUTTON),
            ) {
                Text(localization.resolve("sessionBrowser.goToUploadButton"))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = component::onBack,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.testTag(TestTags.SessionBrowser.BACK_BUTTON),
        ) {
            Text(localization.resolve("action.back"))
        }
    }
}
