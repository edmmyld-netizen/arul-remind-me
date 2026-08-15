package com.arulsundaresan.arulremindme.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = ArulRed,
    onPrimary = Color.White,
    primaryContainer = SurfaceTint,
    onPrimaryContainer = ArulRedDark,
    secondary = Accent,
    onSecondary = Color.White,
    tertiary = Success,
    onTertiary = Color.White,
    background = Surface,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = InkMuted,
    outline = Outline,
    outlineVariant = Outline,
    error = ArulRedDark,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = ArulRedLight,
    onPrimary = Color(0xFF3B0507),
    primaryContainer = ArulRedDark,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF4FD8DE),
    onSecondary = Color(0xFF00363A),
    tertiary = Color(0xFF6FDD8B),
    onTertiary = Color(0xFF00391A),
    background = SurfaceDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = SurfaceElevatedDark,
    onSurfaceVariant = InkMutedDark,
    outline = OutlineDark,
    outlineVariant = OutlineDark,
    error = ArulRedLight,
    onError = Color(0xFF3B0507)
)

/**
 * Brand colours are fixed rather than dynamic-colour derived — the Arul mark has to look
 * the same on every device.
 */
@Composable
fun ArulRemindMeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ArulTypography,
        shapes = ArulShapes,
        content = content
    )
}
