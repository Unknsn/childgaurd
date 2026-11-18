package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * SafeBand Guardian Design System — Geometry & Shapes (v3.6.0)
 *
 * Consistent corner radii scale:
 * - Small / small: 8dp (Chips, small badges, input fields)
 * - Medium / medium: 12dp (Sub-cards, list items, action buttons)
 * - Large / large: 16dp (Standard dashboard cards, panels)
 * - ExtraLarge / extraLarge: 24dp (Prominent hero cards, dialogs, bottom sheets)
 * - Pill / pill: 999dp (Fully rounded badges and floating pills)
 */
object SafeBandShapes {
    @get:JvmName("shapeSmall")
    val Small = RoundedCornerShape(8.dp)
    @get:JvmName("shapeMedium")
    val Medium = RoundedCornerShape(12.dp)
    @get:JvmName("shapeLarge")
    val Large = RoundedCornerShape(16.dp)
    @get:JvmName("shapeExtraLarge")
    val ExtraLarge = RoundedCornerShape(24.dp)
    @get:JvmName("shapePill")
    val Pill = RoundedCornerShape(999.dp)

    val small = Small
    val medium = Medium
    val large = Large
    val extraLarge = ExtraLarge
    val pill = Pill
}

val MaterialShapes = Shapes(
    small = SafeBandShapes.Small,
    medium = SafeBandShapes.Medium,
    large = SafeBandShapes.Large,
    extraLarge = SafeBandShapes.ExtraLarge
)
