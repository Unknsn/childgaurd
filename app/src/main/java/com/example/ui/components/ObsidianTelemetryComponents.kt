package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.example.ui.theme.CarbonChassis
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderStrong
import com.example.ui.theme.GlassHazard
import com.example.ui.theme.GlassHazardBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.MilledSilver
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.ObsidianContainerLowest
import com.example.ui.theme.ObsidianEtchedBorder
import com.example.ui.theme.PhosphorWhite

/**
 * Obsidian Telemetry: Stepped Segmented LED/LCD Bar Meter
 * Displays discrete rectangular cells separated by 2dp gaps (Teenage Engineering flight-deck style).
 */
@Composable
fun SegmentedMeter(
    filledSegments: Int,
    totalSegments: Int = 10,
    modifier: Modifier = Modifier,
    activeColor: Color = PhosphorWhite,
    inactiveColor: Color = ObsidianEtchedBorder.copy(alpha = 0.5f),
    segmentHeight: Int = 10
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until totalSegments) {
            val isFilled = i < filledSegments
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(segmentHeight.dp)
                    .background(
                        color = if (isFilled) activeColor else inactiveColor,
                        shape = RoundedCornerShape(1.dp)
                    )
                    .border(0.5.dp, if (isFilled) Color.White.copy(alpha = 0.3f) else Color.Black, RoundedCornerShape(1.dp))
            )
        }
    }
}

/**
 * Obsidian Telemetry: Tactile Spring Keycap Button
 * Beveled mechanical keycap with etched perimeter and high-contrast tactile feedback.
 */
@Composable
fun TactileKeycap(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isInverted: Boolean = false,
    fontSize: Int = 11
) {
    val bgColor = if (isInverted) PhosphorWhite else ObsidianContainer
    val textColor = if (isInverted) Color(0xFF0A0A0A) else PhosphorWhite
    val borderColor = if (isInverted) Color.White else ObsidianEtchedBorder

    Surface(
        onClick = onClick,
        modifier = modifier
            .border(1.dp, borderColor, RoundedCornerShape(6.dp)),
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        shadowElevation = if (isInverted) 6.dp else 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size((fontSize + 3).dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

/**
 * Obsidian Telemetry: Physical Rocker Switch
 * Dual-state toggle switch with solid and hollow indicator pips.
 */
@Composable
fun PhysicalRockerSwitch(
    label: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onLabel: String = "ON",
    offLabel: String = "OFF"
) {
    Row(
        modifier = modifier
            .background(ObsidianContainerLowest, RoundedCornerShape(6.dp))
            .border(1.dp, ObsidianEtchedBorder, RoundedCornerShape(6.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MilledSilver,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        // ON Side
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (isChecked) PhosphorWhite else Color.Transparent)
                .clickable { onCheckedChange(true) }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isChecked) "■ $onLabel" else "□ $onLabel",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = if (isChecked) Color.Black else MilledSilver
            )
        }
        // OFF Side
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (!isChecked) ObsidianEtchedBorder else Color.Transparent)
                .clickable { onCheckedChange(false) }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (!isChecked) "■ $offLabel" else "□ $offLabel",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = if (!isChecked) PhosphorWhite else ObsidianEtchedBorder
            )
        }
    }
}

/**
 * Obsidian Telemetry: Inverted Critical Hazard Banner
 * High-contrast black-on-white chassis well for emergencies.
 */
@Composable
fun InvertedHazardBanner(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, PhosphorWhite, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        color = PhosphorWhite
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = Color.Black,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color.Black.copy(alpha = 0.85f)
            )
        }
    }
}

/**
 * Frosted Glassmorphic Panel (v3.1.0)
 * Translucent dark acrylic surface with smooth 16dp radius and razor-sharp etched glass border.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = GlassSurface,
    borderColor: Color = GlassBorder,
    borderWidth: Dp = 0.75.dp,
    padding: Dp = 14.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.border(borderWidth, borderColor, shape),
        shape = shape,
        color = backgroundColor,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(padding),
            content = content
        )
    }
}

/**
 * Frosted Glass Button (v3.1.0)
 * Tactile glass action button with subtle highlight and high-contrast retro monospace text.
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isDestructive: Boolean = false,
    fontSize: Int = 11
) {
    val bg = if (isDestructive) GlassHazard else GlassSurfaceElevated
    val border = if (isDestructive) GlassHazardBorder else GlassBorder
    val textCol = if (isDestructive) Color(0xFFFF6B6B) else PhosphorWhite

    Surface(
        onClick = onClick,
        modifier = modifier.border(0.75.dp, border, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textCol,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize.sp,
                color = textCol,
                letterSpacing = 0.5.sp,
                maxLines = 1
            )
        }
    }
}

