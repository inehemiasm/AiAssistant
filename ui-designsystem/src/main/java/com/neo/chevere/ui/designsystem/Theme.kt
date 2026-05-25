package com.neo.chevere.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)

enum class AtmosphericTheme {
    CLASSIC_CYAN,
    MATRIX_GREEN,
    CYBERPUNK_GOLD,
    OBSIDIAN_DARK
}

private val MatrixDarkColorScheme = darkColorScheme(
    primary = primaryGreenDark,
    onPrimary = Color(0xFF003813),
    primaryContainer = primaryContainerGreenDark,
    onPrimaryContainer = onPrimaryContainerGreenDark,
    secondary = primaryGreenDark.copy(alpha = 0.8f),
    onSecondary = Color(0xFF003813),
    secondaryContainer = primaryContainerGreenDark,
    onSecondaryContainer = onPrimaryContainerGreenDark,
    tertiary = Color(0xFF5FFB7D),
    onTertiary = Color(0xFF003813),
    background = backgroundGreenDark,
    onBackground = Color(0xFFE0FFE6),
    surface = surfaceGreenDark,
    onSurface = Color(0xFFE0FFE6),
    surfaceVariant = Color(0xFF1B2F21),
    onSurfaceVariant = Color(0xFFBCE6C7),
    outline = outlineGreenDark,
    outlineVariant = Color(0xFF1B2F21),
    surfaceContainerLowest = surfaceContainerLowGreenDark,
    surfaceContainerLow = surfaceContainerLowGreenDark,
    surfaceContainer = surfaceGreenDark,
    surfaceContainerHigh = surfaceContainerHighGreenDark,
    surfaceContainerHighest = surfaceContainerHighestGreenDark,
)

private val MatrixLightColorScheme = lightColorScheme(
    primary = primaryGreenLight,
    onPrimary = Color.White,
    primaryContainer = primaryContainerGreenLight,
    onPrimaryContainer = onPrimaryContainerGreenLight,
    secondary = primaryGreenLight.copy(alpha = 0.8f),
    onSecondary = Color.White,
    secondaryContainer = primaryContainerGreenLight,
    onSecondaryContainer = onPrimaryContainerGreenLight,
    background = backgroundGreenLight,
    onBackground = Color(0xFF003D14),
    surface = surfaceGreenLight,
    onSurface = Color(0xFF003D14),
    surfaceVariant = Color(0xFFD6F5E1),
    onSurfaceVariant = Color(0xFF003D14),
    outline = primaryGreenLight,
    outlineVariant = Color(0xFFD6F5E1),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = backgroundGreenLight,
    surfaceContainer = Color(0xFFE8F8EE),
    surfaceContainerHigh = Color(0xFFD6F5E1),
    surfaceContainerHighest = Color(0xFFC4F2D4),
)

private val CyberpunkDarkColorScheme = darkColorScheme(
    primary = primaryGoldDark,
    onPrimary = Color(0xFF3D1F00),
    primaryContainer = primaryContainerGoldDark,
    onPrimaryContainer = onPrimaryContainerGoldDark,
    secondary = primaryGoldDark.copy(alpha = 0.8f),
    onSecondary = Color(0xFF3D1F00),
    secondaryContainer = primaryContainerGoldDark,
    onSecondaryContainer = onPrimaryContainerGoldDark,
    tertiary = Color(0xFFFFD54F),
    onTertiary = Color(0xFF3D1F00),
    background = backgroundGoldDark,
    onBackground = Color(0xFFFFEAD6),
    surface = surfaceGoldDark,
    onSurface = Color(0xFFFFEAD6),
    surfaceVariant = Color(0xFF321E07),
    onSurfaceVariant = Color(0xFFFFD4A8),
    outline = outlineGoldDark,
    outlineVariant = Color(0xFF321E07),
    surfaceContainerLowest = surfaceContainerLowGoldDark,
    surfaceContainerLow = surfaceContainerLowGoldDark,
    surfaceContainer = surfaceGoldDark,
    surfaceContainerHigh = surfaceContainerHighGoldDark,
    surfaceContainerHighest = surfaceContainerHighestGoldDark,
)

private val CyberpunkLightColorScheme = lightColorScheme(
    primary = primaryGoldLight,
    onPrimary = Color.White,
    primaryContainer = primaryContainerGoldLight,
    onPrimaryContainer = onPrimaryContainerGoldLight,
    secondary = primaryGoldLight.copy(alpha = 0.8f),
    onSecondary = Color.White,
    secondaryContainer = primaryContainerGoldLight,
    onSecondaryContainer = onPrimaryContainerGoldLight,
    background = backgroundGoldLight,
    onBackground = Color(0xFF3D1F00),
    surface = surfaceGoldLight,
    onSurface = Color(0xFF3D1F00),
    surfaceVariant = Color(0xFFFFECCD),
    onSurfaceVariant = Color(0xFF3D1F00),
    outline = primaryGoldLight,
    outlineVariant = Color(0xFFFFECCD),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = backgroundGoldLight,
    surfaceContainer = Color(0xFFFFF7E6),
    surfaceContainerHigh = Color(0xFFFFECCD),
    surfaceContainerHighest = Color(0xFFFFDF9E),
)

private val ObsidianDarkColorScheme = darkColorScheme(
    primary = primaryObsidianDark,
    onPrimary = Color(0xFF2C1454),
    primaryContainer = primaryContainerObsidianDark,
    onPrimaryContainer = onPrimaryContainerObsidianDark,
    secondary = primaryObsidianDark.copy(alpha = 0.8f),
    onSecondary = Color(0xFF2C1454),
    secondaryContainer = primaryContainerObsidianDark,
    onSecondaryContainer = onPrimaryContainerObsidianDark,
    tertiary = Color(0xFFE8DFFF),
    onTertiary = Color(0xFF2C1454),
    background = backgroundObsidianDark,
    onBackground = Color(0xFFECE6FF),
    surface = surfaceObsidianDark,
    onSurface = Color(0xFFECE6FF),
    surfaceVariant = Color(0xFF251B3D),
    onSurfaceVariant = Color(0xFFEBE3FF),
    outline = outlineObsidianDark,
    outlineVariant = Color(0xFF251B3D),
    surfaceContainerLowest = surfaceContainerLowObsidianDark,
    surfaceContainerLow = surfaceContainerLowObsidianDark,
    surfaceContainer = surfaceObsidianDark,
    surfaceContainerHigh = surfaceContainerHighObsidianDark,
    surfaceContainerHighest = surfaceContainerHighestObsidianDark,
)

private val ObsidianLightColorScheme = lightColorScheme(
    primary = primaryObsidianLight,
    onPrimary = Color.White,
    primaryContainer = primaryContainerObsidianLight,
    onPrimaryContainer = onPrimaryContainerObsidianLight,
    secondary = primaryObsidianLight.copy(alpha = 0.8f),
    onSecondary = Color.White,
    secondaryContainer = primaryContainerObsidianLight,
    onSecondaryContainer = onPrimaryContainerObsidianLight,
    background = backgroundObsidianLight,
    onBackground = Color(0xFF251B3D),
    surface = surfaceObsidianLight,
    onSurface = Color(0xFF251B3D),
    surfaceVariant = Color(0xFFEBE3FF),
    onSurfaceVariant = Color(0xFF251B3D),
    outline = primaryObsidianLight,
    outlineVariant = Color(0xFFEBE3FF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = backgroundObsidianLight,
    surfaceContainer = Color(0xFFFAF8FF),
    surfaceContainerHigh = Color(0xFFEBE3FF),
    surfaceContainerHighest = Color(0xFFD4C4FC),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HighTechAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeStyle: AtmosphericTheme = AtmosphericTheme.CLASSIC_CYAN,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeStyle) {
        AtmosphericTheme.CLASSIC_CYAN -> if (darkTheme) DarkColorScheme else LightColorScheme
        AtmosphericTheme.MATRIX_GREEN -> if (darkTheme) MatrixDarkColorScheme else MatrixLightColorScheme
        AtmosphericTheme.CYBERPUNK_GOLD -> if (darkTheme) CyberpunkDarkColorScheme else CyberpunkLightColorScheme
        AtmosphericTheme.OBSIDIAN_DARK -> if (darkTheme) ObsidianDarkColorScheme else ObsidianLightColorScheme
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = MaterialTheme.shapes,
        typography = Typography,
        content = content
    )
}
