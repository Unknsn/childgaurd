package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SafeZone

/**
 * Custom-drawn Canvas showing the Safe Zone as coordinates plus
 * a circular radius indicator with radar grid lines, cardinal directions,
 * and child device position indicator (without relying on external Maps SDK).
 */
@Composable
fun SafeZoneCanvas(
    safeZone: SafeZone,
    isChildOutside: Boolean = false,
    distanceMeters: Float? = null,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(24.dp)),
        color = Color(0xFF0F172A), // Dark slate canvas
        tonalElevation = 6.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = (minOf(size.width, size.height) / 2f) * 0.82f

                // 1. Radar Grid Lines
                val gridColor = Color(0xFF1E293B)
                val lineColor = Color(0xFF334155)

                // Concentric circles
                val ringCount = 4
                for (i in 1..ringCount) {
                    val r = (maxRadius / ringCount) * i
                    drawCircle(
                        color = gridColor,
                        radius = r,
                        center = center,
                        style = Stroke(
                            width = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    )
                }

                // Crosshair axis lines
                drawLine(
                    color = lineColor,
                    start = Offset(center.x, center.y - maxRadius - 15f),
                    end = Offset(center.x, center.y + maxRadius + 15f),
                    strokeWidth = 1.5f
                )
                drawLine(
                    color = lineColor,
                    start = Offset(center.x - maxRadius - 15f, center.y),
                    end = Offset(center.x + maxRadius + 15f, center.y),
                    strokeWidth = 1.5f
                )

                // 2. Safe Zone Perimeter Circle (proportional radius representation)
                val safeZoneDrawRadius = maxRadius * 0.65f * pulseScale
                val safeZoneColor = if (isChildOutside) Color(0xFFEF4444) else Color(0xFF10B981)

                // Translucent fill
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            safeZoneColor.copy(alpha = 0.22f),
                            safeZoneColor.copy(alpha = 0.05f)
                        ),
                        center = center,
                        radius = safeZoneDrawRadius
                    ),
                    radius = safeZoneDrawRadius,
                    center = center
                )

                // Perimeter border
                drawCircle(
                    color = safeZoneColor,
                    radius = safeZoneDrawRadius,
                    center = center,
                    style = Stroke(
                        width = 3.5f,
                        pathEffect = if (isChildOutside) PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f) else null
                    )
                )

                // Center Home Anchor
                drawCircle(
                    color = Color(0xFF38BDF8),
                    radius = 8f,
                    center = center
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.5f,
                    center = center
                )

                // 3. Child Device Indicator
                val childOffset = if (isChildOutside) {
                    // Outside boundary (top right quadrant)
                    Offset(center.x + safeZoneDrawRadius * 1.25f, center.y - safeZoneDrawRadius * 0.95f)
                } else {
                    // Safely inside boundary
                    Offset(center.x + safeZoneDrawRadius * 0.35f, center.y - safeZoneDrawRadius * 0.25f)
                }

                val childColor = if (isChildOutside) Color(0xFFF87171) else Color(0xFF34D399)

                // Child pulse ripple
                drawCircle(
                    color = childColor.copy(alpha = 0.3f),
                    radius = 18f * pulseScale,
                    center = childOffset
                )
                // Child marker
                drawCircle(
                    color = childColor,
                    radius = 9f,
                    center = childOffset
                )
                drawCircle(
                    color = Color.White,
                    radius = 4f,
                    center = childOffset
                )
            }

            // Top Status Bar Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0x991E293B),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (isChildOutside) Color(0xFFF87171) else Color(0xFF34D399),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isChildOutside) "GEOFENCE BREACH" else "INSIDE SAFE ZONE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isChildOutside) Color(0xFFF87171) else Color(0xFF34D399)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Radius indicator pill
                Surface(
                    color = Color(0x991E293B),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Radius: ${safeZone.radiusMeters.toInt()}m",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Bottom Coordinates & Distance Bar
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xDD0B132B))
                        )
                    )
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Center: %.4f, %.4f".format(safeZone.latitude, safeZone.longitude),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFCBD5E1)
                    )
                }

                if (distanceMeters != null) {
                    Text(
                        text = "Child Distance: ${distanceMeters.toInt()}m from center",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isChildOutside) Color(0xFFFCA5A5) else Color(0xFF6EE7B7),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
