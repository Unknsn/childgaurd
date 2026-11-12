package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SafeZone
import com.example.ui.theme.CarbonChassis
import com.example.ui.theme.MilledSilver
import com.example.ui.theme.ObsidianContainerLowest
import com.example.ui.theme.ObsidianEtchedBorder
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.PhosphorWhite
import kotlin.math.cos
import kotlin.math.sin

/**
 * Obsidian Telemetry: Cathode-Ray Polar Coordinate Radar Scope
 * Designed to 1:1 match the brutalist avionics hardware console from Stitch project 8567237654060793389.
 *
 * Visual Features:
 * - Corner avionics stamped bracket markers
 * - Concentric range distance rings: 25m, 50m, 100m, 150m (dashed geofence boundary), 200m
 * - Phosphor crosshairs with cardinal degree labels: 000° N, 090° E, 180° S, 270° W
 * - 45° and 135° diagonal dotted grid hairlines
 * - Rotating radar sweep line (30 RPM avionics simulation)
 * - Guardian Base anchor [0,0] with crosshair tag
 * - Target blip [BEACON_01] with displacement vector hairline and callout telemetry box
 * - Top and bottom telemetry ribbons with Kalman filter and sweep sync readouts
 */
@Composable
fun SafeZoneCanvas(
    safeZone: SafeZone,
    isChildOutside: Boolean = false,
    distanceMeters: Float? = null,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweepTransition")
    
    // 30 RPM radar sweep rotation (2 seconds per revolution)
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SweepAngle"
    )

    // Target blip ping pulsing
    val blipPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BlipPulse"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CarbonChassis)
            .border(1.dp, ObsidianEtchedBorder, RoundedCornerShape(2.dp))
            .padding(10.dp)
    ) {
        // 1. Scope Titlebar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (isChildOutside) Color(0xFFFF5252) else PhosphorWhite)
                )
                Text(
                    text = "RADAR_SCOPE // POLAR_COORDINATES",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = PhosphorWhite,
                    letterSpacing = 1.sp
                )
            }
            Text(
                text = "[ RANGE: 0 - 200M ]",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = ObsidianOutline,
                fontSize = 9.sp
            )
        }

        // 2. Main Radar Viewport Container with Corner Brackets
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .background(ObsidianContainerLowest)
                .border(1.dp, ObsidianEtchedBorder)
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            // Corner Avionics Stamped Brackets
            // Top Left
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(8.dp)
                    .border(
                        BorderStroke(
                            1.5.dp,
                            Brush.linearGradient(listOf(PhosphorWhite, Color.Transparent))
                        )
                    )
            )
            // Top Right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(8.dp)
                    .border(
                        BorderStroke(
                            1.5.dp,
                            Brush.linearGradient(listOf(Color.Transparent, PhosphorWhite))
                        )
                    )
            )
            // Bottom Left
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(8.dp)
                    .border(
                        BorderStroke(
                            1.5.dp,
                            Brush.linearGradient(listOf(PhosphorWhite, Color.Transparent))
                        )
                    )
            )
            // Bottom Right
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(8.dp)
                    .border(
                        BorderStroke(
                            1.5.dp,
                            Brush.linearGradient(listOf(Color.Transparent, PhosphorWhite))
                        )
                    )
            )

            // Canvas drawing scope
            Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = (minOf(size.width, size.height) / 2f) * 0.92f

                // Concentric range rings: 25m, 50m, 100m, 150m (limit), 200m
                val ranges = listOf(0.125f, 0.25f, 0.50f, 0.75f, 1.0f)
                ranges.forEach { fraction ->
                    val r = maxRadius * fraction
                    val isGeofenceLimit = (fraction == 0.75f)
                    drawCircle(
                        color = if (isGeofenceLimit) {
                            if (isChildOutside) Color(0xFFFF5252) else PhosphorWhite
                        } else {
                            ObsidianEtchedBorder.copy(alpha = 0.55f)
                        },
                        radius = r,
                        center = center,
                        style = Stroke(
                            width = if (isGeofenceLimit) 2f else 1f,
                            pathEffect = if (isGeofenceLimit) PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f) else null
                        )
                    )
                }

                // Cardinal Axis Hairlines (000° - 180° & 270° - 090°)
                drawLine(
                    color = ObsidianEtchedBorder.copy(alpha = 0.7f),
                    start = Offset(center.x, center.y - maxRadius),
                    end = Offset(center.x, center.y + maxRadius),
                    strokeWidth = 1f
                )
                drawLine(
                    color = ObsidianEtchedBorder.copy(alpha = 0.7f),
                    start = Offset(center.x - maxRadius, center.y),
                    end = Offset(center.x + maxRadius, center.y),
                    strokeWidth = 1f
                )

                // 45° and 135° diagonal dotted lines
                val diagEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 6f), 0f)
                val d = maxRadius * 0.7071f
                drawLine(
                    color = ObsidianEtchedBorder.copy(alpha = 0.35f),
                    start = Offset(center.x - d, center.y - d),
                    end = Offset(center.x + d, center.y + d),
                    strokeWidth = 1f,
                    pathEffect = diagEffect
                )
                drawLine(
                    color = ObsidianEtchedBorder.copy(alpha = 0.35f),
                    start = Offset(center.x - d, center.y + d),
                    end = Offset(center.x + d, center.y - d),
                    strokeWidth = 1f,
                    pathEffect = diagEffect
                )

                // Rotating Radar Sweep Line with gradient trail
                rotate(sweepAngle, pivot = center) {
                    drawLine(
                        color = PhosphorWhite,
                        start = center,
                        end = Offset(center.x, center.y - maxRadius),
                        strokeWidth = 1.5f
                    )
                }

                // Guardian Base Anchor [0,0]
                drawRect(
                    color = PhosphorWhite,
                    topLeft = Offset(center.x - 5f, center.y - 5f),
                    size = androidx.compose.ui.geometry.Size(10f, 10f)
                )

                // Child Target Blip calculation
                // Bearing default 42° North-East (from Stitch design specification)
                val bearingRad = Math.toRadians(42.0)
                val distRatio = if (isChildOutside) 0.88f else 0.42f
                val blipDist = maxRadius * distRatio
                val blipOffset = Offset(
                    center.x + (blipDist * sin(bearingRad)).toFloat(),
                    center.y - (blipDist * cos(bearingRad)).toFloat()
                )

                // Vector hairline from center to target blip
                drawLine(
                    color = ObsidianOutline.copy(alpha = 0.6f),
                    start = center,
                    end = blipOffset,
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                )

                // Target Blip Box & Ping Halo
                val blipColor = if (isChildOutside) Color(0xFFFF5252) else PhosphorWhite
                drawCircle(
                    color = blipColor.copy(alpha = 0.25f),
                    radius = 14f * blipPulse,
                    center = blipOffset
                )
                drawRect(
                    color = blipColor,
                    topLeft = Offset(blipOffset.x - 4f, blipOffset.y - 4f),
                    size = androidx.compose.ui.geometry.Size(8f, 8f)
                )
            }

            // Cardinal Label Overlays
            Text(
                text = "000° N",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                color = PhosphorWhite,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .background(CarbonChassis)
                    .padding(horizontal = 4.dp)
            )
            Text(
                text = "180° S",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = ObsidianOutline,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(CarbonChassis)
                    .padding(horizontal = 4.dp)
            )
            Text(
                text = "270° W",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = ObsidianOutline,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .background(CarbonChassis)
                    .padding(vertical = 2.dp)
            )
            Text(
                text = "090° E",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = ObsidianOutline,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .background(CarbonChassis)
                    .padding(vertical = 2.dp)
            )

            // Telemetry Stamps: Azimuth & Filter (Top-left & Bottom-right)
            Text(
                text = "AZIMUTH: 042° | ELEV: +0.2m",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                color = ObsidianOutline,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 8.dp)
                    .background(CarbonChassis.copy(alpha = 0.8f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )

            // Child Target Blip Callout Tag (Top-right quadrant)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp, top = 22.dp)
                    .border(1.dp, if (isChildOutside) Color(0xFFFF5252) else PhosphorWhite, RoundedCornerShape(2.dp)),
                color = ObsidianContainerLowest,
                shape = RoundedCornerShape(2.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(if (isChildOutside) Color(0xFFFF5252) else PhosphorWhite)
                        )
                        Text(
                            text = "BEACON_01 // ${safeZone.name.take(14)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp,
                            color = PhosphorWhite
                        )
                    }
                    Text(
                        text = "DISP: ${distanceMeters?.toInt() ?: 42}m | ${if (isChildOutside) "OUT OF BOUNDS" else "IN ZONE"}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        color = MilledSilver
                    )
                }
            }
        }

        // 3. Scope Bottom Telemetry Ribbon
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ObsidianEtchedBorder, RoundedCornerShape(2.dp))
                .background(ObsidianContainerLowest)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "[RADAR STATUS]",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = PhosphorWhite
                )
                Text(
                    text = if (isChildOutside) "BREACH DETECTED" else "POLAR SWEEP SYNCHRONIZED",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = if (isChildOutside) Color(0xFFFF5252) else MilledSilver
                )
            }
            Text(
                text = "PING: 14ms | CHIRP: 300ms",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                color = ObsidianOutline
            )
        }
    }
}
