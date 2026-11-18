package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.RelayForwardingRecord
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassHazard
import com.example.ui.theme.GlassHazardBorder
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.MilledSilver
import com.example.ui.theme.PhosphorWhite

/**
 * RelayEmergencyAlertSheet
 *
 * Privacy-Preserving Alert Sheet for Nearby Participating Relay Devices (Feature 2).
 *
 * IMPORTANT PRIVACY CONTRACT:
 * - NEVER displays child's full name, medical details, or parent phone number.
 * - Displays broadcast ephemeral ID and relay coordinates.
 * - Confirms that location fix was securely forwarded to the guardian network.
 */
@Composable
fun RelayEmergencyAlertSheet(
    record: RelayForwardingRecord,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        color = GlassHazard,
        border = BorderStroke(1.5.dp, GlassHazardBorder),
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFDC2626), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Alert",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "NEARBY EMERGENCY DETECTED",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = PhosphorWhite
                    )
                    Text(
                        text = "SafeBand Community Relay Active",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MilledSilver,
                        fontSize = 11.sp
                    )
                }
            }

            // Event Details Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = GlassSurfaceElevated,
                border = BorderStroke(0.75.dp, GlassBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "EVENT TYPE",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MilledSilver
                        )
                        Text(
                            text = record.eventType,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF5252)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "BEACON ID",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MilledSilver
                        )
                        Text(
                            text = record.deviceId,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = PhosphorWhite
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "FORWARDING STATUS",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MilledSilver
                        )
                        Text(
                            text = record.forwardStatus,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (record.forwardStatus == "DELIVERED_DIRECT" || record.forwardStatus == "DISPATCHED") Color(0xFF10B981) else Color(0xFFF59E0B)
                        )
                    }
                }
            }

            // Location Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = GlassSurfaceElevated,
                border = BorderStroke(0.75.dp, GlassBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location",
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "RELAY LOCATION CAPTURED",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MilledSilver
                        )
                        Text(
                            text = "%.5f, %.5f (±%.0fm)".format(
                                record.relayLatitude ?: 0.0,
                                record.relayLongitude ?: 0.0,
                                record.relayAccuracy ?: 15.0f
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = PhosphorWhite
                        )
                    }
                }
            }

            // Privacy Notice
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Privacy Shield",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Zero PII Exposure: Child identity, medical details, and guardian contacts remain strictly private to protect child safety.",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MilledSilver
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF374151),
                        contentColor = Color.White
                    )
                ) {
                    Text("Acknowledge & Close", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        }
    }
}
