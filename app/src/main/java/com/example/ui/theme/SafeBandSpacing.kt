package com.example.ui.theme

import androidx.compose.ui.unit.dp

/**
 * SafeBand Guardian Design System — Spacing & Layout Tokens (v3.6.0)
 *
 * Strict 4dp-based spacing scale for rhythm and optical alignment:
 * - XXS: 2dp (Micro gaps, border margins)
 * - XS: 4dp (Tight icon-to-label gaps)
 * - SM: 8dp (Inner element padding, chip spacing)
 * - MD: 12dp (Sub-component padding, list item gaps)
 * - LG: 16dp (Standard card padding, horizontal screen margins)
 * - XL: 20dp (Inter-card vertical rhythm)
 * - XXL: 24dp (Major section separation)
 * - XXXL: 32dp (Hero padding, screen header gaps)
 * - Huge: 40dp (Large touch targets, bottom padding)
 */
object SafeBandSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 40.dp

    // Layout Standards
    val ScreenHorizontal = 16.dp
    val ScreenTopBar = 12.dp
    val CardInner = 16.dp
    val CardGap = 12.dp
    val MinTouchTarget = 48.dp
}
