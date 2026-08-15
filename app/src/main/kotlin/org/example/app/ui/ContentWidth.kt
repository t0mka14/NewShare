package org.example.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/** Fills available width in narrow windows but caps at [max] so the fullscreen
 * layout keeps its centered-column look. Order matters: widthIn before fillMaxWidth. */
internal fun Modifier.contentWidth(max: Dp): Modifier = widthIn(max = max).fillMaxWidth()
