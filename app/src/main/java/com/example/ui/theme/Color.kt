package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Obsidian Telemetry Design System Tokens (Stitch 8567237654060793389)
val CarbonChassis = Color(0xFF0A0A0A)
val ObsidianSurface = Color(0xFF131313)
val ObsidianContainerLowest = Color(0xFF0E0E0E)
val ObsidianContainerLow = Color(0xFF1C1B1B)
val ObsidianContainer = Color(0xFF201F1F)
val ObsidianContainerHigh = Color(0xFF2A2A2A)
val ObsidianContainerHighest = Color(0xFF353534)
val ObsidianSurfaceBright = Color(0xFF393939)

// Frosted Glass Monochrome UI Tokens (v3.1.0)
val GlassSurface = Color(0x18FFFFFF)             // ~9.4% white frosted glass acrylic
val GlassSurfaceElevated = Color(0x28FFFFFF)     // ~15.7% white frosted glass
val GlassSurfaceLowest = Color(0x0EFFFFFF)       // ~5.5% white frosted glass
val GlassBorder = Color(0x30FFFFFF)              // ~18.8% white etched glass stroke
val GlassBorderStrong = Color(0x55FFFFFF)        // ~33.3% white prominent glass stroke
val GlassHazard = Color(0x33EF4444)              // translucent frosted emergency red
val GlassHazardBorder = Color(0x80EF4444)
val GlassSuccess = Color(0x3310B981)
val GlassSuccessBorder = Color(0x8010B981)

val ObsidianEtchedBorder = Color(0xFF444748)
val ObsidianOutline = Color(0xFF8E9192)

val PhosphorWhite = Color(0xFFFFFFFF)
val MilledSilver = Color(0xFFC7C6C6)
val MilledSilverLight = Color(0xFFE0E0E0)
val OnSurfaceText = Color(0xFFE5E2E1)
val OnSurfaceVariantText = Color(0xFFC4C7C8)

// Emergency / Inverted Alert Tokens
val InvertedHazardBg = Color(0xFFFFFFFF)
val InvertedHazardText = Color(0xFF000000)
val ErrorContainerDark = Color(0xFF93000A)
val ErrorBright = Color(0xFFFFB4AB)

// Legacy compatibility aliases
val SafeBandCyanLight = PhosphorWhite
val SafeBandTealLight = MilledSilver
val SafeBandAmberLight = MilledSilverLight
val SafeBandRedLight = Color(0xFFDC2626)

val SafeBandCyanDark = PhosphorWhite
val SafeBandTealDark = MilledSilver
val SafeBandAmberDark = MilledSilverLight
val SafeBandRedDark = Color(0xFFFFB4AB)

val BackgroundLight = Color(0xFFF4F4F4)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF131313)

val BackgroundDark = CarbonChassis
val SurfaceDark = ObsidianSurface
val OnSurfaceDark = OnSurfaceText

val Purple80 = SafeBandCyanDark
val PurpleGrey80 = ObsidianOutline
val Pink80 = SafeBandTealDark

val Purple40 = SafeBandCyanLight
val PurpleGrey40 = ObsidianEtchedBorder
val Pink40 = SafeBandTealLight
