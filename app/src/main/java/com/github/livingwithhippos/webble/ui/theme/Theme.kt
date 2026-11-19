package com.github.livingwithhippos.webble.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    primaryContainer = Coral,
    onPrimaryContainer = Ink,
    secondary = Periwinkle,
    onSecondary = Color.White,
    secondaryContainer = Periwinkle,
    onSecondaryContainer = Ink,
    tertiary = Mustard,
    onTertiary = Ink,
    tertiaryContainer = Mustard,
    onTertiaryContainer = Ink,
    background = Sage,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = Sand,
    onSurfaceVariant = Color(0xFF5E5E66),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Cream,
    surfaceContainer = Sand,
    surfaceContainerHigh = Sand,
    surfaceContainerHighest = Color(0xFFE2DED3),
    inverseSurface = Ink,
    inverseOnSurface = Color.White,
    outline = Color(0xFFB9B6AD),
    outlineVariant = Color(0xFFD9D5CB),
)

private val DarkColorScheme = darkColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    primaryContainer = Coral,
    onPrimaryContainer = Ink,
    secondary = Periwinkle,
    onSecondary = Color.White,
    secondaryContainer = Periwinkle,
    onSecondaryContainer = Ink,
    tertiary = Mustard,
    onTertiary = Ink,
    tertiaryContainer = Mustard,
    onTertiaryContainer = Ink,
    background = Color(0xFF111113),
    onBackground = Color.White,
    surface = Color(0xFF1A1A1D),
    onSurface = Color.White,
    surfaceVariant = InkRaised,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Color(0xFF111113),
    surfaceContainerLow = Color(0xFF1A1A1D),
    surfaceContainer = InkRaised,
    surfaceContainerHigh = InkRaised,
    surfaceContainerHighest = Color(0xFF34343A),
    inverseSurface = Cream,
    inverseOnSurface = Ink,
    outline = Color(0xFF4A4A52),
    outlineVariant = Color(0xFF34343A),
)

private val WebbleShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun WebbleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = WebbleShapes,
        content = content,
    )
}
