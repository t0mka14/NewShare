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
import org.example.app.domain.session.StorageError
import org.example.app.navigation.SessionComponent

/** Renders one examination in progress (§8.6): calibration, then each task instance in turn.
 * `TaskComponent.State` now carries everything the task screen needs directly (§8.6 follow-up),
 * so this no longer re-expands the protocol itself. */
@Composable
fun SessionContent(
    component: SessionComponent,
    localization: UiLocalization,
    onBackToMenu: () -> Unit,
) {
    Children(stack = component.stack) { child ->
        when (val instance = child.instance) {
            is SessionComponent.Child.Bootstrapping -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is SessionComponent.Child.Calibration -> CalibrationContent(instance.component, localization)

            is SessionComponent.Child.TaskScreen -> TaskContent(component = instance.component, localization = localization)

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
