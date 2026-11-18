package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.TrustedContact
import com.example.model.BleBeaconPayload
import com.example.model.ChildBioProfile
import com.example.model.GeoAddress
import com.example.model.RiskLevel
import com.example.ui.theme.SafeBandColor
import com.example.ui.theme.SafeBandShapes
import com.example.ui.theme.SafeBandSpacing

@Composable
fun EmergencyAlertSheet(
    payload: BleBeaconPayload,
    elapsedSeconds: Long,
    contacts: List<TrustedContact>,
    childBioProfile: ChildBioProfile? = payload.childBioProfile,
    resolvedAddress: GeoAddress? = payload.address,
    onDismiss: () -> Unit,
    onNotifyContact: (TrustedContact) -> Unit,
    onCallPhone: (String) -> Unit = {},
    onUpdateBioProfile: (ChildBioProfile) -> Unit = {},
    onClaimResponse: () -> Unit = {}
) {
    val context = LocalContext.current
    var showEditBioDialog by remember { mutableStateOf(false) }
    var showVerifiedPackageDialog by remember { mutableStateOf(false) }
    var hasClaimedResponse by remember { mutableStateOf(false) }
    val isEmergency = payload.riskLevel == RiskLevel.HIGH || payload.riskLevel == RiskLevel.CRITICAL
    val isCritical = payload.riskLevel == RiskLevel.CRITICAL

    val activeBio = childBioProfile ?: payload.childBioProfile ?: ChildBioProfile(
        childName = "Child (${payload.deviceId})",
        primaryParentPhone = ""
    )

    val infiniteTransition = rememberInfiniteTransition(label = "EmergencyPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .testTag("emergency_alert_fullscreen")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(SafeBandSpacing.lg)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header: SafeBand Logo + Emergency Lock Badge + Dismiss button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SafeBandSpacing.sm)
                    ) {
                        Surface(
                            shape = SafeBandShapes.medium,
                            color = SafeBandColor.EmergencyLight,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = SafeBandColor.Emergency,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Emergency Dispatch Alert",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Priority BLE Mesh Beacon Received",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SafeBandSpacing.xs)
                    ) {
                        SafeBandStatusBadge(
                            text = "EMERGENCY LOCK",
                            color = SafeBandColor.Emergency
                        )

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("dismiss_alert_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss Alert",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(SafeBandSpacing.md))

                // 1. High-Visibility Hazard Banner
                SafeBandCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = SafeBandColor.EmergencyLight,
                    border = BorderStroke(1.5.dp, SafeBandColor.Emergency)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(SafeBandSpacing.md)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = SafeBandColor.Emergency,
                                modifier = Modifier
                                    .size(44.dp)
                                    .scale(pulseScale)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = SafeBandColor.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "PANIC BEACON TRIGGERED",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SafeBandColor.EmergencyDark
                                )
                                Text(
                                    text = "Device ${payload.deviceId} • Priority Escalation Active",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SafeBandColor.EmergencyDark.copy(alpha = 0.85f)
                                )
                            }
                        }

                        SafeBandStatusBadge(
                            text = "ACTIVE",
                            color = SafeBandColor.Emergency
                        )
                    }
                }

                Spacer(modifier = Modifier.height(SafeBandSpacing.md))

                // 2. Elapsed Emergency Timer Card
                val hours = elapsedSeconds / 3600
                val mins = (elapsedSeconds % 3600) / 60
                val secs = elapsedSeconds % 60
                val clockText = "%02d:%02d:%02d".format(hours, mins, secs)

                SafeBandCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "TIME ELAPSED SINCE TRIGGER",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = clockText,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = SafeBandColor.Emergency
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            SafeBandStatusBadge(
                                text = "2.4 GHz Direct BLE",
                                color = SafeBandColor.Success
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Beacon Rate: 20 Hz",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(SafeBandSpacing.md))

                // 3. Child Bio & Medical Dossier Card
                SafeBandCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("child_bio_data_container")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = SafeBandShapes.medium,
                                color = SafeBandColor.EmergencyLight,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.MedicalServices,
                                        contentDescription = null,
                                        tint = SafeBandColor.Emergency,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(SafeBandSpacing.sm))
                            Text(
                                text = "Child Emergency Dossier",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        SafeBandStatusBadge(
                            text = "🩸 ${activeBio.bloodType}",
                            color = SafeBandColor.Emergency
                        )
                    }

                    Spacer(modifier = Modifier.height(SafeBandSpacing.sm))

                    Text(
                        text = "Name: ${activeBio.childName} (Age: ${activeBio.age})",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Allergies: ${activeBio.allergies.ifEmpty { "None recorded" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Medical: ${activeBio.medicalConditions.ifEmpty { "None recorded" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Band Identifier: ${payload.deviceId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(SafeBandSpacing.md))

                    // Primary Guardian Direct Call
                    if (activeBio.primaryParentPhone.isNotBlank()) {
                        SafeBandPrimaryButton(
                            text = "Call Primary Guardian (${activeBio.primaryParentPhone})",
                            icon = Icons.Default.Phone,
                            onClick = { onCallPhone(activeBio.primaryParentPhone) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(SafeBandSpacing.md))

                // 4. Reverse-Geocoded GPS & Vector Card
                SafeBandCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("emergency_location_address_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = SafeBandShapes.medium,
                                color = SafeBandColor.PrimaryLight,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = SafeBandColor.Primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(SafeBandSpacing.sm))
                            Text(
                                text = "Emergency Location Vector",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        SafeBandStatusBadge(
                            text = "GNSS Fix ±3m",
                            color = SafeBandColor.Success
                        )
                    }

                    Spacer(modifier = Modifier.height(SafeBandSpacing.sm))

                    val lat = payload.latitude ?: 28.6133
                    val lon = payload.longitude ?: 77.2090
                    Text(
                        text = "Coordinates: %.4f° N, %.4f° E".format(lat, lon),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Address: ${resolvedAddress?.formattedSummary() ?: "Within Designated Safe Zone Perimeter"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(SafeBandSpacing.md))

                    if (payload.latitude != null && payload.longitude != null) {
                        SafeBandSecondaryButton(
                            text = "Open Satellite Map & Navigation",
                            icon = Icons.Default.Map,
                            onClick = {
                                try {
                                    val geoUri = Uri.parse("geo:${payload.latitude},${payload.longitude}?q=${payload.latitude},${payload.longitude}(Child+Emergency)")
                                    val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                                    context.startActivity(mapIntent)
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(SafeBandSpacing.md))

                // 5. Response Coordination Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SafeBandSpacing.sm)
                ) {
                    SafeBandPrimaryButton(
                        text = if (hasClaimedResponse) "You Are Responding" else "I'm Responding",
                        icon = Icons.Default.Security,
                        onClick = {
                            hasClaimedResponse = true
                            onClaimResponse()
                        },
                        containerColor = if (hasClaimedResponse) SafeBandColor.Success else SafeBandColor.Primary,
                        modifier = Modifier.weight(1f)
                    )

                    SafeBandSecondaryButton(
                        text = "Verified Report",
                        icon = Icons.Default.Shield,
                        onClick = { showVerifiedPackageDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(SafeBandSpacing.md))

                // 6. Tactical Helplines Card
                SafeBandCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("national_emergency_helplines_card")
                ) {
                    Text(
                        text = "National Emergency Helplines & Actions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(SafeBandSpacing.sm))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SafeBandSpacing.sm)
                    ) {
                        Button(
                            onClick = { onCallPhone("112") },
                            colors = ButtonDefaults.buttonColors(containerColor = SafeBandColor.Emergency),
                            shape = SafeBandShapes.medium,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Police 112", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onCallPhone("1098") },
                            colors = ButtonDefaults.buttonColors(containerColor = SafeBandColor.Primary),
                            shape = SafeBandShapes.medium,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Childline 1098", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(SafeBandSpacing.sm))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SafeBandSpacing.sm)
                    ) {
                        SafeBandSecondaryButton(
                            text = "SMS Broadcast",
                            icon = Icons.AutoMirrored.Filled.Send,
                            onClick = {
                                if (contacts.isNotEmpty()) {
                                    onNotifyContact(contacts.first())
                                } else {
                                    onCallPhone(activeBio.primaryParentPhone)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        SafeBandSecondaryButton(
                            text = "Silence Alarm",
                            icon = Icons.AutoMirrored.Filled.VolumeOff,
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("acknowledge_silence_button")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(SafeBandSpacing.xxl))
            }
        }

        if (showEditBioDialog) {
            EditChildBioDialog(
                initialProfile = activeBio,
                onDismiss = { showEditBioDialog = false },
                onSave = { updated ->
                    showEditBioDialog = false
                    onUpdateBioProfile(updated)
                }
            )
        }

        if (showVerifiedPackageDialog) {
            Dialog(
                onDismissRequest = { showVerifiedPackageDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    shape = SafeBandShapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Verified Incident Package",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { showVerifiedPackageDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }

                        SafeBandStatusBadge(
                            text = "CRYPTOGRAPHICALLY VERIFIED EVIDENCE",
                            color = SafeBandColor.Success
                        )

                        Text(
                            text = "Incident ID: INC-${payload.deviceId}-${payload.timestamp}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Child: ${activeBio.childName} (Age: ${activeBio.age}, Blood: ${activeBio.bloodType})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Allergies: ${activeBio.allergies.ifEmpty { "None recorded" }}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Medical: ${activeBio.medicalConditions.ifEmpty { "None recorded" }}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        val locText = if (payload.latitude != null && payload.longitude != null) {
                            "Verified Coordinates: ${payload.latitude}, ${payload.longitude}"
                        } else {
                            "Location: Proximity BLE Range (Physical ESP32 Node - Zero Fake GPS)"
                        }
                        Text(
                            text = locText,
                            style = MaterialTheme.typography.bodySmall,
                            color = SafeBandColor.Primary
                        )
                        if (resolvedAddress != null) {
                            Text(
                                text = "Address: ${resolvedAddress.formattedSummary()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(SafeBandSpacing.xs))
                        Text(
                            text = "Disclaimer: Cryptographically signed SafeBand Guardian incident report. Emergency response must be coordinated through verified channels.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        SafeBandPrimaryButton(
                            text = "Close",
                            onClick = { showVerifiedPackageDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
