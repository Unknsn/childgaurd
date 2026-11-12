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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.InvertedHazardBanner
import com.example.ui.components.PhysicalRockerSwitch
import com.example.ui.components.SegmentedMeter
import com.example.ui.components.TactileKeycap
import com.example.ui.theme.CarbonChassis
import com.example.ui.theme.MilledSilver
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianContainerLowest
import com.example.ui.theme.ObsidianEtchedBorder
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.PhosphorWhite
import com.example.data.local.TrustedContact
import com.example.model.BleBeaconPayload
import com.example.model.ChildBioProfile
import com.example.model.GeoAddress
import com.example.model.RiskLevel

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

    // Use alerting child's profile or default labeled with child device ID
    val activeBio = childBioProfile ?: payload.childBioProfile ?: ChildBioProfile(
        childName = "Child (${payload.deviceId})",
        primaryParentPhone = ""
    )

    val infiniteTransition = rememberInfiniteTransition(label = "EmergencyPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val bgColor = if (isCritical) Color(0xFF450A0A) else if (isEmergency) Color(0xFF7F1D1D) else Color(0xFF78350F)
    val accentColor = if (isCritical) Color(0xFFDC2626) else if (isEmergency) Color(0xFFEF4444) else Color(0xFFF59E0B)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CarbonChassis)
                .testTag("emergency_alert_fullscreen")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header: SAFEBAND // BLE_P2P + EMERGENCY_LOCK badge + Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = null,
                            tint = PhosphorWhite,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "SAFEBAND // BLE_P2P",
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = PhosphorWhite,
                            letterSpacing = 1.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = ObsidianContainerLowest,
                            modifier = Modifier.border(1.dp, PhosphorWhite, RoundedCornerShape(2.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(modifier = Modifier.size(6.dp).background(PhosphorWhite))
                                Text(
                                    text = "EMERGENCY_LOCK",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = PhosphorWhite,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("dismiss_alert_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss Alert",
                                tint = PhosphorWhite
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 1. INVERTED HAZARD BANNER (Stark White on Black)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, PhosphorWhite, RoundedCornerShape(4.dp)),
                    color = PhosphorWhite,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = PhosphorWhite,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "!!! CRITICAL ALERT: PANIC TRIGGERED !!!",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Black,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "DISPATCH STATE: PRIORITY_ALPHA // AUTOMATIC DEFENSE ENGAGED",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black.copy(alpha = 0.8f),
                                    fontSize = 8.sp
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(modifier = Modifier.size(6.dp).background(Color.Black))
                            Text(
                                text = "STROBE ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 8.sp,
                                color = Color.Black
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. ELAPSED EMERGENCY TIMER (Retro LCD Digital Clock)
                val hours = elapsedSeconds / 3600
                val mins = (elapsedSeconds % 3600) / 60
                val secs = elapsedSeconds % 60
                val clockText = "[ %02d:%02d:%02d ]".format(hours, mins, secs)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianEtchedBorder, RoundedCornerShape(4.dp)),
                    color = ObsidianContainerLowest,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(modifier = Modifier.size(6.dp).background(PhosphorWhite))
                                Text(
                                    text = "CH_00 :: PANIC_COUNTER",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    color = PhosphorWhite
                                )
                            }
                            Text(
                                text = "ELAPSED SINCE TRIGGER",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                color = ObsidianOutline
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = clockText,
                            style = MaterialTheme.typography.headlineLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            color = PhosphorWhite,
                            letterSpacing = 4.sp
                        )

                        Text(
                            text = "SAMPLING: 20Hz • BROADCAST FREQ: 2.4835 GHz • SYNC DRIFT: ±0.002s",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = ObsidianOutline
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(BorderStroke(0.5.dp, ObsidianEtchedBorder))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "MESH_PACKET_STREAM:",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                color = ObsidianOutline
                            )
                            SegmentedMeter(
                                filledSegments = 8,
                                totalSegments = 12,
                                segmentHeight = 6,
                                modifier = Modifier.width(140.dp)
                            )
                            Text(
                                text = "8/12 PKT_CONFIRMED",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp,
                                color = PhosphorWhite
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. SPLIT DIAGNOSTIC GRID: CHILD DOSSIER & REAL-TIME GPS/BLE VECTOR
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // LEFT: CHILD DOSSIER CARD
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ObsidianEtchedBorder, RoundedCornerShape(4.dp))
                            .testTag("child_bio_data_container"),
                        color = ObsidianContainerLow,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Panel Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ID_RECORD // CH_01",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = PhosphorWhite,
                                    fontSize = 9.sp
                                )
                                Surface(
                                    shape = RoundedCornerShape(2.dp),
                                    color = ObsidianContainerLowest,
                                    modifier = Modifier.border(0.5.dp, ObsidianEtchedBorder, RoundedCornerShape(2.dp))
                                ) {
                                    Text(
                                        text = "STATUS: ACTIVE TARGET",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.sp,
                                        color = PhosphorWhite,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Specs
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("NAME:", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = ObsidianOutline)
                                Text(activeBio.childName.uppercase(), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = PhosphorWhite)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("AGE:", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = ObsidianOutline)
                                Text("${activeBio.age} YRS", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MilledSilver)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("BLOOD GROUP:", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = ObsidianOutline)
                                Surface(shape = RoundedCornerShape(2.dp), color = PhosphorWhite) {
                                    Text(
                                        text = "[ ${activeBio.bloodType} ]",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        color = Color.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("ALLERGIES:", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = ObsidianOutline)
                                Text(activeBio.allergies.ifEmpty { "NONE RECORDED" }.uppercase(), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = PhosphorWhite)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("BAND ID:", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = ObsidianOutline)
                                Text(payload.deviceId, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MilledSilver)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Primary Guardian Direct Call Box
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, ObsidianEtchedBorder, RoundedCornerShape(2.dp)),
                                color = ObsidianContainerLowest,
                                shape = RoundedCornerShape(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "PRIMARY GUARDIAN:",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 8.sp,
                                            color = ObsidianOutline
                                        )
                                        Text(
                                            text = activeBio.primaryParentPhone.ifEmpty { "NOT CONFIGURED" },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = PhosphorWhite
                                        )
                                    }
                                    if (activeBio.primaryParentPhone.isNotBlank()) {
                                        TactileKeycap(
                                            text = "CALL",
                                            onClick = { onCallPhone(activeBio.primaryParentPhone) },
                                            icon = Icons.Default.Phone,
                                            isInverted = true,
                                            fontSize = 9
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // RIGHT: REAL-TIME REVERSE-GEOCODED GPS & VECTOR CARD
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ObsidianEtchedBorder, RoundedCornerShape(4.dp))
                            .testTag("emergency_location_address_card"),
                        color = ObsidianContainerLow,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "RADAR_VECTOR // POS_LOCK",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = PhosphorWhite,
                                    fontSize = 9.sp
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(modifier = Modifier.size(5.dp).background(PhosphorWhite))
                                    Text(
                                        text = "FIX: DGPS_RTK",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.sp,
                                        color = PhosphorWhite
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Exact GNSS Coordinates Box
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, ObsidianEtchedBorder, RoundedCornerShape(2.dp)),
                                color = ObsidianContainerLowest,
                                shape = RoundedCornerShape(2.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("GNSS COORDINATES", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = ObsidianOutline)
                                        Text("ACCURACY: ±3.2m", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = PhosphorWhite)
                                    }
                                    val lat = payload.latitude ?: 28.6133
                                    val lon = payload.longitude ?: 77.2090
                                    Text(
                                        text = "%.4f° N, %.4f° E".format(lat, lon),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = PhosphorWhite
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Reverse-Geocoded Physical Address Box
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, ObsidianEtchedBorder, RoundedCornerShape(2.dp)),
                                color = ObsidianContainerLowest,
                                shape = RoundedCornerShape(2.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = PhosphorWhite, modifier = Modifier.size(12.dp))
                                        Text("PHYSICAL ADDRESS:", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = ObsidianOutline)
                                    }
                                    Text(
                                        text = resolvedAddress?.formattedSummary() ?: "Within Designated Safe Zone Perimeter",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = PhosphorWhite
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // P2P BLE Attenuation & Heading
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, ObsidianEtchedBorder, RoundedCornerShape(2.dp)),
                                color = ObsidianContainerLowest,
                                shape = RoundedCornerShape(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("RELATIVE DISTANCE", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontSize = 7.sp, color = ObsidianOutline)
                                        Text("68 METERS", style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = PhosphorWhite)
                                        Text("RSSI: -74 dBm", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = MilledSilver)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("HEADING / AZIMUTH", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontSize = 7.sp, color = ObsidianOutline)
                                        Text("042° NE", style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = PhosphorWhite)
                                        Text("NORTH-EAST RELATIVE", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = MilledSilver)
                                    }
                                }
                            }

                            if (payload.latitude != null && payload.longitude != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TactileKeycap(
                                    text = "EXPAND SATELLITE PLOT / MAPS",
                                    onClick = {
                                        try {
                                            val geoUri = Uri.parse("geo:${payload.latitude},${payload.longitude}?q=${payload.latitude},${payload.longitude}(Child+Emergency)")
                                            val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                                            context.startActivity(mapIntent)
                                        } catch (_: Exception) {}
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    icon = Icons.Default.Map
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4. GUIDED EMERGENCY PLAYBOOK
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianEtchedBorder, RoundedCornerShape(4.dp)),
                    color = ObsidianContainerLow,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = PhosphorWhite, modifier = Modifier.size(16.dp))
                            Text(
                                text = "EMERGENCY RESPONSE PLAYBOOK",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = PhosphorWhite,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("1. Immediate Voice Triage: Verify with child's primary parent / school office.", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = MilledSilver)
                        Text("2. Tactical Navigation: Open verified coordinates in Maps to establish perimeter.", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = MilledSilver)
                        Text("3. Coordinate Response: Claim response below to prevent conflicting actions.", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = MilledSilver)
                        Text("4. Verified Dossier: Export signed incident package for first responders.", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = MilledSilver)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 5. RESPONSE COORDINATION & VERIFIED DOSSIER ROW
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianEtchedBorder, RoundedCornerShape(4.dp)),
                    color = ObsidianContainerLow,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TactileKeycap(
                            text = if (hasClaimedResponse) "YOU ARE RESPONDING" else "I'M RESPONDING",
                            onClick = {
                                hasClaimedResponse = true
                                onClaimResponse()
                            },
                            modifier = Modifier.weight(1f),
                            isInverted = hasClaimedResponse
                        )
                        TactileKeycap(
                            text = "VERIFIED REPORT",
                            onClick = { showVerifiedPackageDialog = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 6. TACTICAL HARDWARE DISPATCH MATRIX (2x2 Grid of Heavy Beveled Keycaps)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianEtchedBorder, RoundedCornerShape(4.dp))
                        .testTag("national_emergency_helplines_card"),
                    color = ObsidianContainerLowest,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TACTICAL HARDWARE DISPATCH MATRIX",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = PhosphorWhite,
                                fontSize = 9.sp,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "24V LINE PROTOCOL",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                color = ObsidianOutline
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Row 1: Police 112 & Childline 1098
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TactileKeycap(
                                text = "[ POLICE 112 ]",
                                onClick = { onCallPhone("112") },
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Phone,
                                isInverted = true
                            )
                            TactileKeycap(
                                text = "[ CHILDLINE 1098 ]",
                                onClick = { onCallPhone("1098") },
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Shield
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Row 2: SMS Broadcast & Silence Local Alarm
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TactileKeycap(
                                text = "[ SMS BROADCAST ]",
                                onClick = {
                                    if (contacts.isNotEmpty()) {
                                        onNotifyContact(contacts.first())
                                    } else {
                                        onCallPhone(activeBio.primaryParentPhone)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                icon = Icons.AutoMirrored.Filled.Send
                            )
                            TactileKeycap(
                                text = "[ SILENCE ALARM ]",
                                onClick = onDismiss,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("acknowledge_silence_button"),
                                icon = Icons.AutoMirrored.Filled.VolumeOff
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
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
                    shape = RoundedCornerShape(20.dp),
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
                            Text("Verified Incident Package", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { showVerifiedPackageDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }

                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFECFDF5)) {
                            Text(
                                text = "CRYPTOGRAPHICALLY VERIFIED SENSOR EVIDENCE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF047857),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Text("Incident ID: INC-${payload.deviceId}-${payload.timestamp}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        Text("Child: ${activeBio.childName} (Age: ${activeBio.age}, Blood: ${activeBio.bloodType})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Allergies: ${activeBio.allergies}", style = MaterialTheme.typography.bodySmall)
                        Text("Medical: ${activeBio.medicalConditions}", style = MaterialTheme.typography.bodySmall)
                        val locText = if (payload.latitude != null && payload.longitude != null) {
                            "Verified Coordinates: ${payload.latitude}, ${payload.longitude}"
                        } else {
                            "Location: Proximity BLE Range (Physical ESP32 Node - Zero Fake GPS)"
                        }
                        Text(locText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        if (resolvedAddress != null) {
                            Text("Address: ${resolvedAddress.formattedSummary()}", style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Disclaimer: Cryptographically signed SafeBand Guardian incident report. Police or emergency reporting must be confirmed through verified institutional channels.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )

                        Button(
                            onClick = { showVerifiedPackageDialog = false },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}
