package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable Vector Brand Emblem for SafeBand Guardian.
 * Implements the redesigned shield + guardian silhouette.
 */
@Composable
fun SafeBandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val s = w / 108f

            // Outer Shield
            val shieldPath = Path().apply {
                moveTo(54f * s, 24f * s)
                cubicTo(67f * s, 24f * s, 78f * s, 32f * s, 78f * s, 45f * s)
                cubicTo(78f * s, 63f * s, 54f * s, 82f * s, 54f * s, 82f * s)
                cubicTo(54f * s, 82f * s, 30f * s, 63f * s, 30f * s, 45f * s)
                cubicTo(30f * s, 32f * s, 41f * s, 24f * s, 54f * s, 24f * s)
                close()
            }

            drawPath(
                path = shieldPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF00F5D4), Color(0xFF00BBF9), Color(0xFF0077B6)),
                    start = Offset(30f * s, 24f * s),
                    end = Offset(78f * s, 82f * s)
                )
            )

            // Inner Shield
            val innerPath = Path().apply {
                moveTo(54f * s, 29f * s)
                cubicTo(64f * s, 29f * s, 72f * s, 35f * s, 72f * s, 45f * s)
                cubicTo(72f * s, 59f * s, 54f * s, 74f * s, 54f * s, 74f * s)
                cubicTo(54f * s, 74f * s, 36f * s, 59f * s, 36f * s, 45f * s)
                cubicTo(36f * s, 35f * s, 44f * s, 29f * s, 54f * s, 29f * s)
                close()
            }
            drawPath(path = innerPath, color = Color(0xFF09182E))

            // Guardian Arch
            val archPath = Path().apply {
                moveTo(44f * s, 40f * s)
                cubicTo(44f * s, 34f * s, 64f * s, 34f * s, 64f * s, 40f * s)
                cubicTo(64f * s, 48f * s, 54f * s, 52f * s, 54f * s, 52f * s)
                cubicTo(54f * s, 52f * s, 44f * s, 48f * s, 44f * s, 40f * s)
                close()
            }
            drawPath(path = archPath, color = Color(0xFF00BBF9))

            // Protected Child Dot
            drawCircle(
                color = Color(0xFF00F5D4),
                radius = 4f * s,
                center = Offset(54f * s, 43f * s)
            )

            // Pulse line
            val pulsePath = Path().apply {
                moveTo(42f * s, 59f * s)
                lineTo(48f * s, 59f * s)
                lineTo(51f * s, 53f * s)
                lineTo(54f * s, 64f * s)
                lineTo(57f * s, 56f * s)
                lineTo(60f * s, 59f * s)
                lineTo(66f * s, 59f * s)
            }
            drawPath(
                path = pulsePath,
                color = Color(0xFF00F5D4),
                style = Stroke(
                    width = 2.6f * s,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Sparkle
            drawCircle(
                color = Color(0xFFFFD166),
                radius = 2.2f * s,
                center = Offset(54f * s, 33f * s)
            )
        }
    }
}

