package org.example.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UploadContent(component: UploadComponent, localization: UiLocalization) {
    val state by component.state.subscribeAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(localization.resolve("upload.title"), style = MaterialTheme.typography.headlineLarge)

        Card {
            Text(
                localization.resolve("upload.instructions"),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Text(
            if (state.rows.isEmpty() && !state.uploading) {
                localization.resolve("upload.noSessions")
            } else {
                localization.resolve("upload.readyCount", mapOf("count" to state.rows.size.toString()))
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag(TestTags.Upload.READY_COUNT_TEXT),
        )

        LazyColumn(
            modifier = Modifier
                .border(width = 2.dp, color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp))
                .contentWidth(1300.dp)
                .height(220.dp),
        ) {
            items(state.rows, key = { it.sessionId }) { row ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.Upload.sessionRow(row.sessionId))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            Text(row.patientCode, style = MaterialTheme.typography.bodyLarge)
                        }
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(row.date, style = MaterialTheme.typography.bodyLarge)
                        }
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            TooltipArea(
                                tooltip = {
                                    Surface(shape = RoundedCornerShape(4.dp), shadowElevation = 4.dp) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            row.identifiers.forEach { (key, value) -> Text("$key: $value") }
                                        }
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Outlined.Info,
                                    contentDescription = null,
                                    modifier = Modifier.testTag(TestTags.Upload.sessionInfo(row.sessionId)),
                                )
                            }
                        }
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                            Text(
                                localization.resolve("sessionBrowser.uploadStatus.${row.status.name}"),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    row.failureReasonKey?.let { key ->
                        Text(
                            localization.resolve("upload.failureMessage", mapOf("reason" to localization.resolvePlain(key))),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag(TestTags.Upload.sessionErrorText(row.sessionId)),
                        )
                    }
                }
            }
        }

        LinearProgressIndicator(
            progress = { state.overallProgress },
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.contentWidth(1300.dp).testTag(TestTags.Upload.PROGRESS_BAR),
        )

        state.batchResultKey?.let { key ->
            Text(
                localization.resolve(key),
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(TestTags.Upload.SUCCESS_MESSAGE),
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
