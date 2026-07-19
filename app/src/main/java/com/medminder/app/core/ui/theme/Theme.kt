package com.medminder.app.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = OceanBlue,
    onPrimary = Color.White,
    primaryContainer = PaleBlue,
    onPrimaryContainer = MidnightBlue,
    secondary = SkyBlue,
    onSecondary = DarkSlate,
    secondaryContainer = PaleBlue,
    onSecondaryContainer = MidnightBlue,
    tertiary = MidnightBlue,
    onTertiary = Color.White,
    tertiaryContainer = SkyBlue.copy(alpha = 0.3f),
    onTertiaryContainer = MidnightBlue,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRed.copy(alpha = 0.1f),
    onErrorContainer = ErrorRed,
    background = AlmostWhite,
    onBackground = DarkSlate,
    surface = White,
    onSurface = DarkSlate,
    surfaceVariant = PaleBlue.copy(alpha = 0.5f),
    onSurfaceVariant = MidnightBlue,
    outline = SoftGrey
)

private val HighContrastColors = lightColorScheme(
    primary = MidnightBlue,
    onPrimary = Color.White,
    primaryContainer = MidnightBlue,
    onPrimaryContainer = Color.White,
    secondary = OceanBlue,
    onSecondary = Color.White,
    secondaryContainer = OceanBlue,
    onSecondaryContainer = Color.White,
    tertiary = DarkSlate,
    onTertiary = Color.White,
    tertiaryContainer = DarkSlate,
    onTertiaryContainer = Color.White,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRed,
    onErrorContainer = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color.White,
    onSurfaceVariant = Color.Black,
    outline = Color.Black
)

private val DarkColors = darkColorScheme(
    primary = SkyBlue,
    onPrimary = MidnightBlue,
    primaryContainer = OceanBlue,
    onPrimaryContainer = PaleBlue,
    secondary = OceanBlue,
    onSecondary = PaleBlue,
    secondaryContainer = MidnightBlue,
    onSecondaryContainer = SkyBlue,
    tertiary = PaleBlue,
    onTertiary = MidnightBlue,
    tertiaryContainer = OceanBlue.copy(alpha = 0.5f),
    onTertiaryContainer = PaleBlue,
    background = MidnightBlue, // Dark blue background
    onBackground = PaleBlue,
    surface = Color(0xFF0A1929), // Very dark blue for surface
    onSurface = PaleBlue,
    surfaceVariant = OceanBlue.copy(alpha = 0.3f), // Slightly lighter than background
    onSurfaceVariant = SkyBlue,
    outline = SkyBlue.copy(alpha = 0.5f),
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRed.copy(alpha = 0.3f),
    onErrorContainer = White
)

@Composable
fun MedMinderTheme(
    darkTheme: Boolean = false,
    highContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = when {
        highContrast -> HighContrastColors
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
