package org.example.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.example.app.domain.ErrorReporter

/**
 * Renders [ErrorReporter.current] on top of whatever screen is active. Recoverable errors are
 * dismissable and the app keeps running; fatal ones only offer closing the app (the reporter
 * refuses to dismiss them).
 */
@Composable
fun UnexpectedErrorDialog(
    errorReporter: ErrorReporter,
    localization: UiLocalization,
    onExitApp: () -> Unit,
) {
    val error by errorReporter.current.collectAsState()
    val shown = error ?: return

    AlertDialog(
        onDismissRequest = { errorReporter.dismiss() },
        modifier = Modifier.testTag(TestTags.ErrorDialog.DIALOG),
        title = { Text(localization.resolve("error.dialog.title")) },
        text = {
            Text(
                localization.resolvePlain("error.generic.message") + "\n\n" + shown.throwable.toString(),
                modifier = Modifier.testTag(TestTags.ErrorDialog.MESSAGE),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            if (shown.fatal) {
                Button(
                    onClick = onExitApp,
                    modifier = Modifier.testTag(TestTags.ErrorDialog.EXIT_BUTTON),
                ) {
                    Text(localization.resolve("error.dialog.exit"))
                }
            } else {
                Button(
                    onClick = { errorReporter.dismiss() },
                    modifier = Modifier.testTag(TestTags.ErrorDialog.DISMISS_BUTTON),
                ) {
                    Text(localization.resolve("error.dialog.dismiss"))
                }
            }
        },
    )
}
