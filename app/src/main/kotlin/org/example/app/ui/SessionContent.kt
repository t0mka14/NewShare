package org.example.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.session.StorageError
import org.example.app.domain.timeline.TaskInstance
import org.example.app.navigation.SessionComponent

/**
 * Renders one examination in progress (§8.6): calibration, then each task instance in turn.
 * [navigableTasks]/[availableDevices]/[initialDevice] are computed once by `RootComponent` at
 * session-start time — see [TaskContent]'s doc comment for why the UI layer needs them
 * alongside [SessionComponent] rather than reading them off it directly.
 */
@Composable
fun SessionContent(
    component: SessionComponent,
    navigableTasks: List<TaskInstance>,
    availableDevices: List<AudioInputDevice>,
    initialDevice: AudioInputDevice?,
    localization: UiLocalization,
    onBackToMenu: () -> Unit,
) {
    Children(stack = component.stack) { child ->
        when (val instance = child.instance) {
            is SessionComponent.Child.Bootstrapping -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is SessionComponent.Child.Calibration -> CalibrationContent(instance.component, localization)

            is SessionComponent.Child.TaskScreen -> {
                val taskState by instance.component.state.subscribeAsState()
                val position = navigableTasks.indexOfFirst { it.taskIndex == taskState.taskIndex }
                val taskInstance = navigableTasks.getOrNull(position)
                if (taskInstance != null) {
                    TaskContent(
                        component = instance.component,
                        localization = localization,
                        taskDefinition = taskInstance.task,
                        positionInProtocol = position + 1,
                        totalNavigableTasks = navigableTasks.size,
                        availableDevices = availableDevices,
                        initialDevice = initialDevice,
                    )
                }
            }

            is SessionComponent.Child.Failed -> {
                val startError by component.startError.subscribeAsState()
                SessionFailedContent(startError.error, localization, onBackToMenu)
            }
        }
    }
}

@Composable
private fun SessionFailedContent(error: StorageError?, localization: UiLocalization, onBackToMenu: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(localization.resolve("session.failedTitle"), style = MaterialTheme.typography.h5)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                localization.resolve(error?.messageKey() ?: "error.generic.message"),
                modifier = Modifier.testTag(TestTags.Session.FAILED_MESSAGE),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onBackToMenu, modifier = Modifier.testTag(TestTags.Session.BACK_TO_MENU_BUTTON)) {
                Text(localization.resolve("action.back"))
            }
        }
    }
}
