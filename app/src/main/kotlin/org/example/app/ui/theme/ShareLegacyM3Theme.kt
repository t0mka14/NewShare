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
 * Material3 theme copied verbatim from the legacy shareapp's `materials/{Color,Typography,
 * Shapes,Theme}.kt` (§13 decision 36: the Task and Calibration screens are 1:1 copies of the
 * legacy composables, which are written against Material3). Applied *inside those two screens
 * only* — every other screen keeps the app-wide Material2 [ShareTheme]. The only deliberate
 * departures from the legacy files are packaging fixes: Roboto is classpath-loaded (the
 * original used working-directory `java.io.File` paths) and is set as the typography's font
 * family (the original bundled Roboto but its `Typography` never referenced it).
 */
private val LegacyLightColors = lightColorScheme(
    primary = Color(0xFF00668A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC4E7FF),
    onPrimaryContainer = Color(0xFF001E2C),
    secondary = Color(0xFF00658B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC4E7FF),
    onSecondaryContainer = Color(0xFF001E2D),
    tertiary = Color(0xFF006E2A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF81FC93),
    onTertiaryContainer = Color(0xFF002108),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBFCFF),
    onBackground = Color(0xFF191C1E),
    surface = Color(0xFFFBFCFF),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDCE3E9),
    onSurfaceVariant = Color(0xFF41484D),
    outline = Color(0xFF71787D),
    inverseOnSurface = Color(0xFFF0F1F3),
    inverseSurface = Color(0xFF2E3133),
    inversePrimary = Color(0xFF7BD0FF),
    surfaceTint = Color(0xFF00668A),
    outlineVariant = Color(0xFFC0C7CD),
    scrim = Color(0xFF000000),
)

private val LegacyDarkColors = darkColorScheme(
    primary = Color(0xFF7BD0FF),
    onPrimary = Color(0xFF003549),
    primaryContainer = Color(0xFF004C69),
    onPrimaryContainer = Color(0xFFC4E7FF),
    secondary = Color(0xFF7ED0FF),
    onSecondary = Color(0xFF00344A),
    secondaryContainer = Color(0xFF004C6A),
    onSecondaryContainer = Color(0xFFC4E7FF),
    tertiary = Color(0xFF64DF7A),
    onTertiary = Color(0xFF003912),
    tertiaryContainer = Color(0xFF00531E),
    onTertiaryContainer = Color(0xFF81FC93),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF191C1E),
    onBackground = Color(0xFFE1E2E5),
    surface = Color(0xFF191C1E),
    onSurface = Color(0xFFE1E2E5),
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC0C7CD),
    outline = Color(0xFF8B9297),
    inverseOnSurface = Color(0xFF191C1E),
    inverseSurface = Color(0xFFE1E2E5),
    inversePrimary = Color(0xFF00668A),
    surfaceTint = Color(0xFF7BD0FF),
    outlineVariant = Color(0xFF41484D),
    scrim = Color(0xFF000000),
)

private val LegacyRoboto = FontFamily(
    Font(resource = "fonts/roboto/Roboto-Regular.ttf", weight = FontWeight.Normal),
    Font(resource = "fonts/roboto/Roboto-Bold.ttf", weight = FontWeight.Bold),
    Font(resource = "fonts/roboto/Roboto-Italic.ttf", style = FontStyle.Italic),
    Font(resource = "fonts/roboto/Roboto-BoldItalic.ttf", weight = FontWeight.Bold, style = FontStyle.Italic),
)

/** Legacy `materials/Typography.kt` sizes verbatim (comments carried over). */
private val LegacyTypography = Typography(
    headlineLarge = TextStyle(fontFamily = LegacyRoboto, fontSize = 45.sp), // nadpisy
    bodyLarge = TextStyle(fontFamily = LegacyRoboto, fontSize = 16.sp), // male texty
    bodySmall = TextStyle(fontFamily = LegacyRoboto, fontSize = 18.sp),
    bodyMedium = TextStyle(fontFamily = LegacyRoboto, fontSize = 20.sp), // instrukcni texty
    labelSmall = TextStyle(fontFamily = LegacyRoboto, fontSize = 20.sp), // standardni protokol text tlacitko nahore
    labelMedium = TextStyle(fontFamily = LegacyRoboto, fontSize = 30.sp), // Buttons
    displayMedium = TextStyle(fontFamily = LegacyRoboto, fontSize = 35.sp), // Tasks title
    displaySmall = TextStyle(fontFamily = LegacyRoboto, fontSize = 12.sp), // Help in patient info
)

private val LegacyShapes = Shapes(
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(250.dp),
)

@Composable
fun ShareLegacyM3Theme(useDarkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) LegacyDarkColors else LegacyLightColors,
        typography = LegacyTypography,
        shapes = LegacyShapes,
        content = content,
    )
}
