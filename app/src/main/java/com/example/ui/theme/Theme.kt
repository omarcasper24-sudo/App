package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = Color(0xFF002A32),
    primaryContainer = Color(0xFF004D5A),
    onPrimaryContainer = Color(0xFFB8F8FF),
    secondary = EmeraldDrive,
    onSecondary = Color(0xFF003319),
    secondaryContainer = Color(0xFF00522B),
    onSecondaryContainer = Color(0xFFB3FFD1),
    tertiary = VioletSleep,
    onTertiary = Color(0xFF280659),
    tertiaryContainer = Color(0xFF421C7A),
    onTertiaryContainer = Color(0xFFEBDCFF),
    background = ObsidianBg,
    onBackground = CrispWhiteText,
    surface = MidnightSurface,
    onSurface = CrispWhiteText,
    surfaceVariant = ElevatedNavyCard,
    onSurfaceVariant = SoftSlateText,
    outline = Color(0xFF334776)
)

private val LightColorScheme = lightColorScheme(
    primary = DeepCyanPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC4F3FF),
    onPrimaryContainer = Color(0xFF001F25),
    secondary = DeepEmeraldSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB9F6D8),
    onSecondaryContainer = Color(0xFF002112),
    tertiary = DeepIndigoTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE7DEFF),
    onTertiaryContainer = Color(0xFF1D0061),
    background = DaylightBg,
    onBackground = InkDarkText,
    surface = DaylightSurface,
    onSurface = InkDarkText,
    surfaceVariant = DaylightCard,
    onSurfaceVariant = MutedNavyText,
    outline = Color(0xFFB3C2DC)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
