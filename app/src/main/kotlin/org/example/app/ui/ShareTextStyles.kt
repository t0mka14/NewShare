package org.example.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * The two type roles the original app's clinician-facing screens (main menu, calibration, task)
 * used and this rewrite's Material3 scale does not have a slot for: a large *regular-weight*
 * screen title and an equally large regular-weight primary-action label.
 *
 * The original's `materials/Typography.kt` never set a font weight on anything, so every screen
 * rendered regular; it also sized its button labels at 30sp (`labelMedium`) rather than the
 * 18sp bold that Material3 buttons get from `labelLarge`. Rather than resize or de-bold the
 * shared scale — which the denser non-legacy screens (settings, upload, editor, session
 * browser) are built on — the three screens that must match the original ask for these styles
 * explicitly.
 */
@Composable
internal fun screenTitleTextStyle(): TextStyle =
    MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Normal)

@Composable
internal fun actionButtonTextStyle(): TextStyle =
    MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal)
