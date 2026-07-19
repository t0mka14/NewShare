package org.example.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
 * §13 decision 36 ("UI is 1:1 with the legacy shareapp"): color values are taken from the
 * original app's Material3 seed (`shareapp/src/main/kotlin/materials/Color.kt`), with
 * `secondary` deliberately remapped to the legacy *tertiary* green family — this rewrite's
 * screens use `secondary` as the green accent. The whole app is Material3 (the former
 * Material2 stack was dropped 2026-07-16 at user request); the Task/Calibration screens
 * additionally wrap themselves in [ShareLegacyM3Theme], which carries the legacy theme
 * files verbatim.
 */
private val SharePrimary = Color(0xFF00668A)
private val ShareSecondary = Color(0xFF006E2A)
private val ShareSecondaryContainer = Color(0xFF81FC93)
private val ShareError = Color(0xFFBA1A1A)
private val ShareBackground = Color(0xFFFBFCFF)
private val ShareSurface = Color(0xFFFFFFFF)
private val ShareOnBackground = Color(0xFF191C1E)

private val ShareDarkPrimary = Color(0xFF7BD0FF)
private val ShareDarkSecondary = Color(0xFF64DF7A)
private val ShareDarkBackground = Color(0xFF191C1E)
private val ShareDarkSurface = Color(0xFF242729)
private val ShareDarkOnBackground = Color(0xFFE1E2E5)

/** The original's `Orange`/`light_OrangeContainer` accent (`materials/Color.kt`) — used for the
 * calibration level bar, matching the original `SoundLevelBar`'s accent color family. */
val ShareAccentOrange = Color(0xFFFF9D30)
val ShareAccentOrangeContainer = Color(0xFFFFDCBF)

// Full schemes (not just the handful of slots the screens read) so no M3 slot falls back to
// the library's purple baseline; values follow the legacy seed like ShareLegacyM3Theme's,
// with the secondary/surface overrides described above.
private val LightColors = lightColorScheme(
    primary = SharePrimary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC4E7FF),
    onPrimaryContainer = Color(0xFF001E2C),
    secondary = ShareSecondary,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = ShareSecondaryContainer,
    onSecondaryContainer = Color(0xFF002108),
    tertiary = ShareSecondary,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = ShareSecondaryContainer,
    onTertiaryContainer = Color(0xFF002108),
    error = ShareError,
    errorContainer = Color(0xFFFFDAD6),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF410002),
    background = ShareBackground,
    onBackground = ShareOnBackground,
    surface = ShareSurface,
    onSurface = ShareOnBackground,
    surfaceVariant = Color(0xFFDCE3E9),
    onSurfaceVariant = Color(0xFF41484D),
    outline = Color(0xFF71787D),
    inverseOnSurface = Color(0xFFF0F1F3),
    inverseSurface = Color(0xFF2E3133),
    inversePrimary = ShareDarkPrimary,
    surfaceTint = SharePrimary,
    outlineVariant = Color(0xFFC0C7CD),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = ShareDarkPrimary,
    onPrimary = Color(0xFF003549),
    primaryContainer = Color(0xFF004C69),
    onPrimaryContainer = Color(0xFFC4E7FF),
    secondary = ShareDarkSecondary,
    onSecondary = Color(0xFF00391F),
    secondaryContainer = Color(0xFF00531E),
    onSecondaryContainer = Color(0xFF81FC93),
    tertiary = ShareDarkSecondary,
    onTertiary = Color(0xFF00391F),
    tertiaryContainer = Color(0xFF00531E),
    onTertiaryContainer = Color(0xFF81FC93),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
    background = ShareDarkBackground,
    onBackground = ShareDarkOnBackground,
    surface = ShareDarkSurface,
    onSurface = ShareDarkOnBackground,
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC0C7CD),
    outline = Color(0xFF8B9297),
    inverseOnSurface = Color(0xFF191C1E),
    inverseSurface = Color(0xFFE1E2E5),
    inversePrimary = SharePrimary,
    surfaceTint = ShareDarkPrimary,
    outlineVariant = Color(0xFF41484D),
    scrim = Color(0xFF000000),
)

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

/**
 * Clinic-legible type scale, proportioned like the original's but capped a little below its
 * literal sizes (the cap is a deliberate §13/36 scaling fix for the denser non-legacy
 * screens). The former Material2 slots map onto M3 at identical sizes: h4→[headlineLarge],
 * h5→[headlineMedium], h6→[headlineSmall], subtitle1→[titleMedium], body1→[bodyLarge],
 * body2→[bodyMedium], button→[labelLarge] (M3 buttons render labelLarge), caption→[bodySmall].
 * M3 has no `defaultFontFamily`, so Roboto is set per style — including the slots screens
 * don't reference explicitly but M3 components render internally (titleLarge for dialogs,
 * labelMedium/labelSmall for small controls).
 */
private val ShareTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Roboto, fontSize = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontFamily = Roboto, fontSize = 32.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontFamily = Roboto, fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = Roboto, fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = Roboto, fontSize = 18.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontFamily = Roboto, fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = Roboto, fontSize = 20.sp),
    bodyMedium = TextStyle(fontFamily = Roboto, fontSize = 16.sp),
    bodySmall = TextStyle(fontFamily = Roboto, fontSize = 13.sp),
    labelLarge = TextStyle(fontFamily = Roboto, fontSize = 18.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontFamily = Roboto, fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = Roboto, fontSize = 11.sp, fontWeight = FontWeight.Medium),
    displayLarge = TextStyle(fontFamily = Roboto, fontSize = 57.sp),
    displayMedium = TextStyle(fontFamily = Roboto, fontSize = 45.sp),
    displaySmall = TextStyle(fontFamily = Roboto, fontSize = 36.sp),
)

/** Pill-shaped primary action buttons (`materials/Shapes.kt`'s `large = RoundedCornerShape(200.dp)`,
 * used by the original for Back/Next/Confirm/Upload) and rounded cards/dialogs (`medium` 16dp). */
private val ShareShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(50),
)

@Composable
fun ShareTheme(useDarkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        typography = ShareTypography,
        shapes = ShareShapes,
        content = content,
    )
}
