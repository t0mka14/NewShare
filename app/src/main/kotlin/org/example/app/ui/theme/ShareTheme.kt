package org.example.app.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Keeps the original shareapp's look (§4 "keep... look and layout, fix its bugs") on top of the
 * rewrite's own Material2 (`androidx.compose.material`) stack — the original used a Material3
 * seed color (`materials/Color.kt`: primary `#00668A`) and oversized, clinic-legible type
 * (`materials/Typography.kt`). This is a fresh implementation of that look, not a copy of the
 * original's Material3 `ColorScheme`/`Typography` objects (§4 "do not copy its architecture").
 */
private val SharePrimary = Color(0xFF00668A)
private val SharePrimaryVariant = Color(0xFF004C69)
private val ShareSecondary = Color(0xFF006E2A)
private val ShareError = Color(0xFFBA1A1A)
private val ShareBackground = Color(0xFFFBFCFF)
private val ShareSurface = Color(0xFFFFFFFF)
private val ShareOnPrimary = Color(0xFFFFFFFF)

private val ShareDarkPrimary = Color(0xFF7BD0FF)
private val ShareDarkSecondary = Color(0xFF64DF7A)
private val ShareDarkBackground = Color(0xFF191C1E)
private val ShareDarkSurface = Color(0xFF242729)

private val LightColors = lightColors(
    primary = SharePrimary,
    primaryVariant = SharePrimaryVariant,
    secondary = ShareSecondary,
    background = ShareBackground,
    surface = ShareSurface,
    error = ShareError,
    onPrimary = ShareOnPrimary,
    onSecondary = Color.White,
    onBackground = Color(0xFF191C1E),
    onSurface = Color(0xFF191C1E),
    onError = Color.White,
)

private val DarkColors = darkColors(
    primary = ShareDarkPrimary,
    secondary = ShareDarkSecondary,
    background = ShareDarkBackground,
    surface = ShareDarkSurface,
    error = Color(0xFFFFB4AB),
    onPrimary = Color(0xFF003549),
    onSecondary = Color(0xFF00391F),
    onBackground = Color(0xFFE1E2E5),
    onSurface = Color(0xFFE1E2E5),
)

/** Large, clinic-legible type scale (mirrors the weighting of the original's headline/body
 * split — big titles, big buttons, readable instruction text at arm's length). */
private val ShareTypography = Typography(
    h4 = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold),
    h5 = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    h6 = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    subtitle1 = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    body1 = TextStyle(fontSize = 18.sp),
    body2 = TextStyle(fontSize = 16.sp),
    button = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    caption = TextStyle(fontSize = 13.sp),
)

@Composable
fun ShareTheme(useDarkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colors = if (useDarkTheme) DarkColors else LightColors,
        typography = ShareTypography,
        content = content,
    )
}
