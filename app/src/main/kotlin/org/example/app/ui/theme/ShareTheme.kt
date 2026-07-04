package org.example.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * §13 decision 36 ("UI is 1:1 with the legacy shareapp"): color values are taken verbatim from
 * the original app's Material3 seed (`shareapp/src/main/kotlin/materials/Color.kt`) and its type
 * scale is reproduced proportionally (`materials/Typography.kt`: 45sp headline / 35sp task title
 * / 30sp buttons / 20sp instructions / 12sp help text) — mapped onto this rewrite's own
 * Material2 (`androidx.compose.material`) stack, not a copy of the original's Material3
 * `ColorScheme`/`Typography` objects (§4 "do not copy its architecture"). [ShareShapes] mirrors
 * the original's pill-shaped primary buttons (`materials/Shapes.kt`: `large = RoundedCornerShape(200.dp)`).
 */
private val SharePrimary = Color(0xFF00668A)
private val SharePrimaryVariant = Color(0xFF004C69)
private val ShareSecondary = Color(0xFF006E2A)
private val ShareSecondaryContainer = Color(0xFF81FC93)
private val ShareError = Color(0xFFBA1A1A)
private val ShareBackground = Color(0xFFFBFCFF)
private val ShareSurface = Color(0xFFFFFFFF)
private val ShareOnPrimary = Color(0xFFFFFFFF)
private val ShareOnBackground = Color(0xFF191C1E)

private val ShareDarkPrimary = Color(0xFF7BD0FF)
private val ShareDarkSecondary = Color(0xFF64DF7A)
private val ShareDarkBackground = Color(0xFF191C1E)
private val ShareDarkSurface = Color(0xFF242729)
private val ShareDarkOnBackground = Color(0xFFE1E2E5)

/** The original's `Orange`/`light_OrangeContainer` accent (`materials/Color.kt`) — used for the
 * calibration level meter's "in range" highlight band, matching the original `SoundLevelBar`'s
 * accent color family while fixing its mixed loudness formula (§4, `CalibrationContent`). */
val ShareAccentOrange = Color(0xFFFF9D30)
val ShareAccentOrangeContainer = Color(0xFFFFDCBF)

private val LightColors = lightColors(
    primary = SharePrimary,
    primaryVariant = SharePrimaryVariant,
    secondary = ShareSecondary,
    secondaryVariant = ShareSecondary,
    background = ShareBackground,
    surface = ShareSurface,
    error = ShareError,
    onPrimary = ShareOnPrimary,
    onSecondary = Color.White,
    onBackground = ShareOnBackground,
    onSurface = ShareOnBackground,
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
    onBackground = ShareDarkOnBackground,
    onSurface = ShareDarkOnBackground,
)

/**
 * Clinic-legible type scale, proportioned like the original's (`headlineLarge` 45sp screen
 * titles, `displayMedium` 35sp task titles, `labelMedium` 30sp buttons, `bodyMedium` 20sp
 * instructions, `displaySmall` 12sp help text) but capped a little below those literal sizes —
 * the original only ever laid out 2-3 buttons per row; this rewrite's task screen has five
 * (Start/Stop/Repeat/Skip/Next, §8.6) side by side, so keeping the original's exact 30sp button
 * text would overflow at moderate window widths. Scaling to fit every row without wrapping is a
 * bug fix (§13 decision 36: "prevents proper screen scaling... is a bug to fix, not a look to
 * preserve"), not a look to preserve.
 */
/**
 * The legacy app's Roboto family (§1 kept resources, §13 decision 36), loaded from the
 * classpath — the original loaded the same TTFs via working-directory-relative
 * `java.io.File` paths (`materials/Typography.kt`), which breaks under the §9 install
 * layout; classpath loading is the scaling/packaging bug fix, the fonts are identical.
 */
private val Roboto = FontFamily(
    Font(resource = "fonts/roboto/Roboto-Light.ttf", weight = FontWeight.Light),
    Font(resource = "fonts/roboto/Roboto-Regular.ttf", weight = FontWeight.Normal),
    Font(resource = "fonts/roboto/Roboto-Medium.ttf", weight = FontWeight.Medium),
    Font(resource = "fonts/roboto/Roboto-Bold.ttf", weight = FontWeight.Bold),
    Font(resource = "fonts/roboto/Roboto-Italic.ttf", style = FontStyle.Italic),
    Font(resource = "fonts/roboto/Roboto-BoldItalic.ttf", weight = FontWeight.Bold, style = FontStyle.Italic),
)

private val ShareTypography = Typography(
    defaultFontFamily = Roboto,
    h4 = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold),
    h5 = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
    h6 = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    subtitle1 = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    body1 = TextStyle(fontSize = 20.sp),
    body2 = TextStyle(fontSize = 16.sp),
    button = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
    caption = TextStyle(fontSize = 13.sp),
)

/** Pill-shaped primary action buttons (`materials/Shapes.kt`'s `large = RoundedCornerShape(200.dp)`,
 * used by the original for Back/Next/Confirm/Upload) and rounded cards/dialogs (`medium = 16.dp`). */
private val ShareShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(50),
)

@Composable
fun ShareTheme(useDarkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colors = if (useDarkTheme) DarkColors else LightColors,
        typography = ShareTypography,
        shapes = ShareShapes,
        content = content,
    )
}
