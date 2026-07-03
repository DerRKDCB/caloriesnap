package com.example.caloriestracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = LeafGreenBright,
    onPrimary = Color(0xFF0F2007),
    primaryContainer = ForestGreen,
    onPrimaryContainer = Color(0xFFD7F5C7),
    secondary = Color(0xFFB4D39F),
    onSecondary = Color(0xFF14240B),
    secondaryContainer = Color(0xFF314826),
    onSecondaryContainer = Color(0xFFD5F2C5),
    tertiary = CitrusAccent,
    onTertiary = Color(0xFF371900),
    tertiaryContainer = Color(0xFF5A2D00),
    onTertiaryContainer = Color(0xFFFFDBC2),
    background = GroveSurfaceDark,
    onBackground = OnDarkText,
    surface = GroveSurfaceDark,
    onSurface = OnDarkText,
    surfaceVariant = MossSurfaceVariantDark,
    onSurfaceVariant = Color(0xFFCDD8C5),
    outline = Color(0xFF8B977F)
)

private val LightColorScheme = lightColorScheme(
    primary = LeafGreen,
    onPrimary = Color(0xFF0F2007),
    primaryContainer = Color(0xFFD9F4CB),
    onPrimaryContainer = Color(0xFF0D2006),
    secondary = FernSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3F3D7),
    onSecondaryContainer = Color(0xFF15270D),
    tertiary = CitrusAccent,
    onTertiary = Color(0xFF371900),
    tertiaryContainer = Color(0xFFFFE3C9),
    onTertiaryContainer = Color(0xFF2A1200),
    background = DewSurfaceLight,
    onBackground = OnLightText,
    surface = DewSurfaceLight,
    onSurface = OnLightText,
    surfaceVariant = MistSurfaceVariantLight,
    onSurfaceVariant = Color(0xFF435035),
    outline = Color(0xFF6A7A60)
)

@Composable
fun CaloriesTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme: ColorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
