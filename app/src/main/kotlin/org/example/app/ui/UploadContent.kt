package org.example.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.example.app.navigation.UploadComponent

/**
 * §8.9 upload screen — modeled on the original app's `UploadScreen` (instruction card, bordered
 * session list, single progress bar, big Send/Upload button, Back) with the eligible-session
 * list, per-session error reasons, and manual-only sequential batch upload of §5.4/decision 34.
 */
@Composable
fun UploadContent(component: UploadComponent, localization: UiLocalization) {
    val state by component.state.subscribeAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(localization.resolve("upload.title"), style = MaterialTheme.typography.h4)

        Card {
            Text(
                localization.resolve("upload.instructions"),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.body1,
            )
        }

        Text(
            if (state.rows.isEmpty() && !state.uploading) {
                localization.resolve("upload.noSessions")
            } else {
                localization.resolve("upload.readyCount", mapOf("count" to state.rows.size.toString()))
            },
            style = MaterialTheme.typography.subtitle1,
            modifier = Modifier.testTag(TestTags.Upload.READY_COUNT_TEXT),
        )

        LazyColumn(
            modifier = Modifier
                .border(width = 2.dp, color = MaterialTheme.colors.primary, shape = RoundedCornerShape(8.dp))
                .fillMaxWidth(0.7f)
                .height(220.dp),
        ) {
            items(state.rows, key = { it.sessionId }) { row ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.Upload.sessionRow(row.sessionId))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("${row.patientCode} — ${row.sessionId}", style = MaterialTheme.typography.body1)
                        Text(localization.resolve("sessionBrowser.uploadStatus.${row.status.name}"), style = MaterialTheme.typography.body2)
                    }
                    row.failureReasonKey?.let { key ->
                        Text(
                            localization.resolve("upload.failureMessage", mapOf("reason" to localization.resolvePlain(key))),
                            color = MaterialTheme.colors.error,
                            style = MaterialTheme.typography.caption,
                            modifier = Modifier.testTag(TestTags.Upload.sessionErrorText(row.sessionId)),
                        )
                    }
                }
            }
        }

        LinearProgressIndicator(
            progress = state.overallProgress,
            modifier = Modifier.fillMaxWidth(0.7f).testTag(TestTags.Upload.PROGRESS_BAR),
        )

        state.batchResultKey?.let { key ->
            Text(
                localization.resolve(key),
                color = MaterialTheme.colors.secondary,
                modifier = Modifier.testTag(TestTags.Upload.SUCCESS_MESSAGE),
            )
        }

        Button(
            onClick = component::onUploadClicked,
            enabled = !state.uploading && state.rows.isNotEmpty(),
            modifier = Modifier.testTag(TestTags.Upload.UPLOAD_BUTTON),
        ) {
            Text(localization.resolve("action.upload"))
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Button(
                onClick = component::onBack,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.testTag(TestTags.Upload.BACK_BUTTON),
            ) {
                Text(localization.resolve("action.back"))
            }
        }
    }
}
