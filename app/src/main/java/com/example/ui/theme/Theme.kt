package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PhosphorWhite,
    secondary = MilledSilver,
    tertiary = MilledSilverLight,
    background = CarbonChassis,
    surface = ObsidianSurface,
    surfaceVariant = ObsidianContainerHighest,
    outline = ObsidianOutline,
    outlineVariant = ObsidianEtchedBorder,
    onPrimary = Color(0xFF131313),
    onSecondary = Color(0xFF131313),
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark,
    error = ErrorBright,
    errorContainer = ErrorContainerDark
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF131313),
    secondary = Color(0xFF444748),
    tertiary = Color(0xFF64748B),
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = Color(0xFFE5E5E5),
    outline = Color(0xFF8E9192),
    outlineVariant = Color(0xFFD4D4D4),
    onPrimary = PhosphorWhite,
    onSecondary = PhosphorWhite,
    onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight,
    error = SafeBandRedLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use intentional theme colors by default
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
