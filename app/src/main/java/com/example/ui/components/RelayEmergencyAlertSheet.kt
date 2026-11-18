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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.RelayForwardingRecord
import com.example.ui.theme.SafeBandColor
import com.example.ui.theme.SafeBandShapes
import com.example.ui.theme.SafeBandSpacing

/**
 * RelayEmergencyAlertSheet (v3.6.0 Design Overhaul)
 * Privacy-Preserving Alert Sheet for Nearby Participating Relay Devices.
 *
 * STRICT PRIVACY CONTRACT:
 * - ZERO child PII (no names, medical records, or parent phone numbers).
 * - Ephemeral beacon ID and honest relay GPS coordinates.
 * - Reassuring feedback that the bystander phone helped relay the alert.
 */
@Composable
fun RelayEmergencyAlertSheet(
    record: RelayForwardingRecord,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    SafeBandCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(SafeBandSpacing.lg),
        shape = SafeBandShapes.ExtraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        borderColor = SafeBandColor.Emergency.copy(alpha = 0.5f),
        borderWidth = 1.5.dp,
        elevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(SafeBandSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(SafeBandSpacing.md)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = SafeBandColor.EmergencySurfaceLight,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Alert",
                            tint = SafeBandColor.EmergencyDark,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(SafeBandSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Nearby Safety Alert Detected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "SafeBand Community Relay Mesh Active",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Event Details Box
            SafeBandCard(
                modifier = Modifier.fillMaxWidth(),
                shape = SafeBandShapes.Large,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                elevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SafeBandSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(SafeBandSpacing.sm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Event Type",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SafeBandStatusBadge(
                            style = if (record.eventType == "TAMPER") StatusBadgeStyle.TAMPER else StatusBadgeStyle.SOS,
                            label = record.eventType
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Beacon Identifier",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = record.deviceId,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Relay Status",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val isDelivered = record.forwardStatus.startsWith("DELIVERED") || record.forwardStatus == "DISPATCHED"
                        SafeBandStatusBadge(
                            style = if (isDelivered) StatusBadgeStyle.CONNECTED else StatusBadgeStyle.STALE,
                            label = if (isDelivered) "Relayed to Guardian" else "Queued for Forwarding"
                        )
                    }
                }
            }

            // Location Box
            SafeBandCard(
                modifier = Modifier.fillMaxWidth(),
                shape = SafeBandShapes.Medium,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                elevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SafeBandSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location",
                        tint = SafeBandColor.PrimaryBrand,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(SafeBandSpacing.sm))
                    Column {
                        Text(
                            text = "Relaying Device Location",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val locText = if (record.relayLatitude != null && record.relayLongitude != null) {
                            "%.5f, %.5f (±%.0fm)".format(
                                record.relayLatitude,
                                record.relayLongitude,
                                record.relayAccuracy ?: 15.0f
                            )
                        } else {
                            "Location Unavailable (No GPS Fix)"
                        }
                        Text(
                            text = locText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Privacy Notice
            SafeBandCard(
                modifier = Modifier.fillMaxWidth(),
                shape = SafeBandShapes.Medium,
                containerColor = SafeBandColor.SuccessSurfaceLight,
                borderColor = SafeBandColor.SuccessBorder,
                elevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SafeBandSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Privacy Shield",
                        tint = SafeBandColor.SuccessDark,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(SafeBandSpacing.sm))
                    Text(
                        text = "Zero Child PII Exposure: Child identity, medical details, and family contacts remain strictly confidential to safeguard privacy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SafeBandColor.SuccessDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(SafeBandSpacing.xs))

            // Action Button
            SafeBandPrimaryButton(
                text = "Acknowledge & Close",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
