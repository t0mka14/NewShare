package org.example.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.example.app.domain.session.ProcessSessionUseCase
import org.example.app.navigation.ProcessingComponent

/** §8.8 processing progress screen — blocks navigation (no back/close affordance besides the
 * explicit failure-path Back button) while `ProcessSessionUseCase` runs. */
@Composable
fun ProcessingContent(component: ProcessingComponent, localization: UiLocalization) {
    val state by component.state.subscribeAsState()

    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.contentWidth(1100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(localization.resolve("processing.title"), style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(24.dp))

            if (!state.failed) {
                Text(
                    localization.resolve(state.step?.let(::stepKey) ?: "processing.step.selectingTimeline"),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag(TestTags.Processing.STEP_LABEL),
                )
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { state.fraction },
                    modifier = Modifier.fillMaxWidth().testTag(TestTags.Processing.PROGRESS_BAR),
                )
            } else {
                Text(localization.resolve("processing.error.title"), style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    localization.resolve("processing.error.generic"),
                    modifier = Modifier.testTag(TestTags.Processing.ERROR_TEXT),
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row {
                    Button(onClick = component::onBack, modifier = Modifier.testTag(TestTags.Processing.BACK_BUTTON)) {
                        Text(localization.resolve("action.back"))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(onClick = component::onRetry, modifier = Modifier.testTag(TestTags.Processing.RETRY_BUTTON)) {
                        Text(localization.resolve("action.retry"))
                    }
                }
            }
        }
    }
}

private fun stepKey(step: ProcessSessionUseCase.Step): String = when (step) {
    ProcessSessionUseCase.Step.SELECTING_TIMELINE -> "processing.step.selectingTimeline"
    ProcessSessionUseCase.Step.CUTTING_CLIPS -> "processing.step.cuttingClips"
    ProcessSessionUseCase.Step.BUILDING_ARCHIVE -> "processing.step.buildingArchive"
    ProcessSessionUseCase.Step.UPDATING_METADATA -> "processing.step.updatingMetadata"
}
