package org.example.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** Small reusable "click to open a dropdown of options" control, used by Settings (mic device,
 * language) and the calibration/device-lost screens (mic device). Purely presentational — the
 * open/closed flag is local Compose UI state, not component state (§5.2). */
@Composable
fun <T> DropdownSelector(
    triggerTag: String,
    selectedLabel: String,
    items: List<T>,
    itemLabel: (T) -> String,
    itemTag: (T) -> String,
    onSelected: (T) -> Unit,
    itemEnabled: (T) -> Boolean = { true },
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.testTag(triggerTag)) {
            Text(selectedLabel)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(itemLabel(item)) },
                    onClick = {
                        expanded = false
                        onSelected(item)
                    },
                    enabled = itemEnabled(item),
                    modifier = Modifier.testTag(itemTag(item)),
                )
            }
        }
    }
}
