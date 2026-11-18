package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * SafeBand Guardian Design System — Color Tokens (v3.6.0)
 *
 * Implements a modern, calm, human-centered child safety color system:
 * - Serene Slate neutrals (calm, trustworthy, professional)
 * - Safety Blue & Indigo primary brand accents
 * - Emerald green for verified connected & protected states
 * - Warm Amber for stale or advisory warnings
 * - Controlled Rose Red for confirmed emergencies
 */
object SafeBandColor {
    // Brand Core
    val PrimaryBrand = Color(0xFF2563EB)         // Royal Safety Blue
    val PrimaryBrandDark = Color(0xFF1D4ED8)
    val PrimaryBrandLight = Color(0xFF60A5FA)
    val SecondaryBrand = Color(0xFF4F46E5)       // Deep Indigo Accent

    // Aliases for intuitive API usage
    val Primary = PrimaryBrand
    val PrimaryDark = PrimaryBrandDark
    val PrimaryLight = Color(0xFFEFF6FF)         // Soft 50-tint
    val Secondary = SecondaryBrand
    val SecondaryDark = Color(0xFF3730A3)
    val SecondaryLight = Color(0xFFEEF2FF)       // Soft indigo 50-tint

    val White = Color.White
    val Black = Color.Black

    // Semantic States: Connected / Normal / Protected
    val Success = Color(0xFF10B981)              // Vibrant Emerald
    val SuccessDark = Color(0xFF059669)
    val SuccessLight = Color(0xFFECFDF5)         // Soft 50-tint emerald
    val SuccessSurfaceLight = Color(0xFFECFDF5)  // Soft green tint
    val SuccessSurfaceDark = Color(0x1F10B981)
    val SuccessBorder = Color(0x4D10B981)

    // Semantic States: Stale / Warning / Maintenance
    val Warning = Color(0xFFF59E0B)              // Warm Amber
    val WarningDark = Color(0xFFD97706)
    val WarningLight = Color(0xFFFFFBEB)         // Soft 50-tint amber
    val WarningSurfaceLight = Color(0xFFFFFBEB)  // Soft amber tint
    val WarningSurfaceDark = Color(0x1FF59E0B)
    val WarningBorder = Color(0x4DF59E0B)

    // Semantic States: SOS / Tamper / Emergency
    val Emergency = Color(0xFFEF4444)            // Controlled Rose Red
    val EmergencyDark = Color(0xFFDC2626)
    val EmergencyLight = Color(0xFFFEF2F2)       // Soft 50-tint rose red
    val EmergencySurfaceLight = Color(0xFFFEF2F2)
    val EmergencySurfaceDark = Color(0x28EF4444)
    val EmergencyBorder = Color(0x66EF4444)

    // Slate Neutrals Scale
    val Slate50 = Color(0xFFF8FAFC)
    val Slate100 = Color(0xFFF1F5F9)
    val Slate200 = Color(0xFFE2E8F0)
    val Slate300 = Color(0xFFCBD5E1)
    val Slate400 = Color(0xFF94A3B8)
    val Slate500 = Color(0xFF64748B)
    val Slate600 = Color(0xFF475569)
    val Slate700 = Color(0xFF334155)
    val Slate800 = Color(0xFF1E293B)
    val Slate900 = Color(0xFF0F172A)

    // Neutral Surfaces & Backgrounds — Light Theme
    val BgLight = Slate50
    val SurfaceLight = Color(0xFFFFFFFF)
    val SurfaceElevatedLight = Color(0xFFFFFFFF)
    val SurfaceSubtleLight = Slate100
    val TextPrimaryLight = Slate900
    val TextSecondaryLight = Slate600
    val TextMutedLight = Slate400
    val BorderLight = Slate200
    val BorderSubtleLight = Slate100

    // Neutral Surfaces & Backgrounds — Dark Theme (Deep Navy/Slate)
    val BgDark = Color(0xFF0A0F1D)               // Deep midnight slate
    val SurfaceDark = Color(0xFF111827)          // Gray 900
    val SurfaceElevatedDark = Color(0xFF1F2937)  // Gray 800
    val SurfaceSubtleDark = Color(0xFF1E293B)    // Slate 800
    val TextPrimaryDark = Slate50
    val TextSecondaryDark = Slate400
    val TextMutedDark = Slate500
    val BorderDark = Slate700
    val BorderSubtleDark = Slate800

    // Frosted Glass Layer Overlays
    val GlassOverlayLight = Color(0xB3FFFFFF)    // 70% white blur
    val GlassOverlayDark = Color(0x99111827)     // 60% dark slate blur
    val GlassStrokeLight = Color(0x40FFFFFF)
    val GlassStrokeDark = Color(0x3394A3B8)
}
