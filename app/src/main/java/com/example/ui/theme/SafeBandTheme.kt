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
    primary = SafeBandColor.PrimaryBrand,
    onPrimary = Color.White,
    primaryContainer = SafeBandColor.PrimaryBrandDark,
    onPrimaryContainer = Color.White,
    secondary = SafeBandColor.SecondaryBrand,
    onSecondary = Color.White,
    tertiary = SafeBandColor.Success,
    onTertiary = Color.White,
    background = SafeBandColor.BgDark,
    onBackground = SafeBandColor.TextPrimaryDark,
    surface = SafeBandColor.SurfaceDark,
    onSurface = SafeBandColor.TextPrimaryDark,
    surfaceVariant = SafeBandColor.SurfaceSubtleDark,
    onSurfaceVariant = SafeBandColor.TextSecondaryDark,
    outline = SafeBandColor.BorderDark,
    outlineVariant = SafeBandColor.BorderSubtleDark,
    error = SafeBandColor.Emergency,
    onError = Color.White,
    errorContainer = SafeBandColor.EmergencySurfaceDark,
    onErrorContainer = SafeBandColor.EmergencyLight
)

private val LightColorScheme = lightColorScheme(
    primary = SafeBandColor.PrimaryBrand,
    onPrimary = Color.White,
    primaryContainer = SafeBandColor.PrimaryBrandLight,
    onPrimaryContainer = SafeBandColor.PrimaryBrandDark,
    secondary = SafeBandColor.SecondaryBrand,
    onSecondary = Color.White,
    tertiary = SafeBandColor.SuccessDark,
    onTertiary = Color.White,
    background = SafeBandColor.BgLight,
    onBackground = SafeBandColor.TextPrimaryLight,
    surface = SafeBandColor.SurfaceLight,
    onSurface = SafeBandColor.TextPrimaryLight,
    surfaceVariant = SafeBandColor.SurfaceSubtleLight,
    onSurfaceVariant = SafeBandColor.TextSecondaryLight,
    outline = SafeBandColor.BorderLight,
    outlineVariant = SafeBandColor.BorderSubtleLight,
    error = SafeBandColor.EmergencyDark,
    onError = Color.White,
    errorContainer = SafeBandColor.EmergencySurfaceLight,
    onErrorContainer = SafeBandColor.EmergencyDark
)

/**
 * SafeBand Guardian Design System — Master Theme Provider (v3.6.0)
 */
@Composable
fun SafeBandTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SafeBandTypography,
        shapes = MaterialShapes,
        content = content
    )
}
