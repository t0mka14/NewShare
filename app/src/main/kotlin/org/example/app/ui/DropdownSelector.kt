package org.example.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

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
            Icon(if (expanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown, contentDescription = null)
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

/** Radio-button alternative to [DropdownSelector]: same item/label/tag contract, but all options
 * are always visible. [selected] may be null (nothing checked yet). Each row carries [itemTag]. */
@Composable
fun <T> RadioSelector(
    selected: T?,
    items: List<T>,
    itemLabel: (T) -> String,
    itemTag: (T) -> String,
    onSelected: (T) -> Unit,
    itemEnabled: (T) -> Boolean = { true },
    modifier: Modifier = Modifier,
) {
    // Options sit side by side, left-aligned, and wrap onto a new line when the width runs out.
    FlowRow(modifier = modifier.selectableGroup()) {
        items.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .selectable(
                        selected = item == selected,
                        enabled = itemEnabled(item),
                        role = Role.RadioButton,
                        onClick = { onSelected(item) },
                    )
                    .testTag(itemTag(item)),
            ) {
                RadioButton(selected = item == selected, onClick = null, enabled = itemEnabled(item))
                Text(itemLabel(item), modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp))
            }
        }
    }
}
