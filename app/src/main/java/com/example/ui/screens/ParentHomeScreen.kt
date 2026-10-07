package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import com.example.model.BatteryState
import com.example.model.BleBeaconPayload
import com.example.model.ChildBioProfile
import com.example.model.ConnectivityTier
import com.example.model.GeoAddress
import com.example.model.GeofenceState
import com.example.model.LocationConfidence
import com.example.model.OperatingMode
import com.example.model.RiskLevel
import com.example.model.RouteState
import com.example.model.ScenarioId
import com.example.model.ScenarioStatus
import com.example.model.TrustedRoute
import com.example.model.VerifiedLocation
import com.example.ui.SafeBandViewModel
import com.example.ui.components.EmergencyAlertSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentHomeScreen(
    viewModel: SafeBandViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    val isScanning by viewModel.bleManager.isScanning.collectAsState()
    val lastBeacon by viewModel.incomingAlert.collectAsState()
    val isAlertActive by viewModel.isParentAlertActive.collectAsState()
    val isAlertSilenced by viewModel.isParentAlertSilenced.collectAsState()
    val elapsedSeconds by viewModel.alertDurationSeconds.collectAsState()
    val contacts by viewModel.allContacts.collectAsState()
    val childBioProfile by viewModel.childBioProfile.collectAsState()
    val incomingBio by viewModel.incomingChildProfile.collectAsState()
    val incomingContacts by viewModel.incomingChildContacts.collectAsState()
    val incomingAddress by viewModel.incomingAlertAddress.collectAsState()
    val safeZoneAddress by viewModel.safeZoneAddress.collectAsState()
    val deviceId by viewModel.deviceId.collectAsState()
    val safeZone by viewModel.safeZone.collectAsState()
    val nearbyNodes by viewModel.nearbyNodes.collectAsState()

    // Pulse animation for scanner
    val infiniteTransition = rememberInfiniteTransition(label = "RadarScanner")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SafeBand Guardian",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isScanning) Color(0xFFECFDF5) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isScanning) Color(0xFF10B981) else Color.Gray,
                                        modifier = Modifier.size(6.dp)
                                    ) {}
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isScanning) "BLE ACTIVE" else "STANDBY",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isScanning) Color(0xFF047857) else Color.Gray
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Guarding: ${childBioProfile.childName.ifEmpty { "Child ($deviceId)" }} • ${safeZone.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("parent_settings_button")
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("parent_navigation_bar"),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Security, contentDescription = "Monitor") },
                    label = { Text("Monitor") },
                    modifier = Modifier.testTag("tab_monitor")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Radar, contentDescription = "Safe Zone") },
                    label = { Text("Safe Zone") },
                    modifier = Modifier.testTag("tab_safezone")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History") },
                    modifier = Modifier.testTag("tab_history")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.ContactPhone, contentDescription = "Contacts") },
                    label = { Text("Contacts") },
                    modifier = Modifier.testTag("tab_contacts")
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> ParentMonitorTab(
                    viewModel = viewModel,
                    isScanning = isScanning,
                    pulseAlpha = pulseAlpha,
                    deviceId = deviceId,
                    lastBeacon = lastBeacon,
                    isSilenced = isAlertSilenced,
                    childBio = incomingBio ?: childBioProfile,
                    safeZoneAddress = safeZoneAddress,
                    nearbyNodes = nearbyNodes,
                    onNavigateToSafeZone = { selectedTab = 1 },
                    onNavigateToHistory = { selectedTab = 2 }
                )
                1 -> SafeZoneConfigScreen(viewModel = viewModel)
                2 -> AlertHistoryScreen(viewModel = viewModel)
                3 -> TrustedContactsScreen(viewModel = viewModel)
            }
        }
    }

    // Full-Screen Emergency Alert Sheet on receiving beacon
    if (isAlertActive && lastBeacon != null) {
        val activeBio = incomingBio ?: lastBeacon!!.childBioProfile ?: ChildBioProfile(
            childName = "Child (${lastBeacon!!.deviceId})",
            primaryParentPhone = ""
        )
        val activeContacts = if (incomingContacts.isNotEmpty()) incomingContacts else contacts
        val activeAddress = incomingAddress ?: lastBeacon!!.address

        EmergencyAlertSheet(
            payload = lastBeacon!!,
            elapsedSeconds = elapsedSeconds,
            contacts = activeContacts,
            childBioProfile = activeBio,
            resolvedAddress = activeAddress,
            onDismiss = { viewModel.dismissParentAlert() },
            onNotifyContact = { contact ->
                val intent = viewModel.createEmergencySmsIntent(
                    context = context,
                    contactPhoneNumber = contact.phoneNumber,
                    payload = lastBeacon!!,
                    bioProfile = activeBio,
                    address = activeAddress
                )
                context.startActivity(intent)
            },
            onCallPhone = { phone ->
                try {
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.replace(" ", "")}"))
                    context.startActivity(dialIntent)
                } catch (_: Exception) {}
            },
            onUpdateBioProfile = { updated ->
                viewModel.updateChildBioProfile(updated)
            }
        )
    }
}

@Composable
fun ParentMonitorTab(
    viewModel: SafeBandViewModel,
    isScanning: Boolean,
    pulseAlpha: Float,
    deviceId: String,
    lastBeacon: BleBeaconPayload?,
    isSilenced: Boolean,
    childBio: ChildBioProfile,
    safeZoneAddress: GeoAddress?,
    nearbyNodes: Map<String, BleBeaconPayload>,
    onNavigateToSafeZone: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val context = LocalContext.current
    val isEmergency = lastBeacon != null && (lastBeacon.riskLevel == RiskLevel.CRITICAL || lastBeacon.riskLevel == RiskLevel.HIGH || lastBeacon.riskLevel == RiskLevel.MEDIUM)
    var isDemoExpanded by remember { mutableStateOf(false) }

    val verifiedLocation by viewModel.verifiedLocation.collectAsState()
    val geofenceState by viewModel.geofenceState.collectAsState()
    val distanceToBoundary by viewModel.distanceToBoundary.collectAsState()
    val routeState by viewModel.routeState.collectAsState()
    val trustedRoute by viewModel.trustedRoute.collectAsState()
    val batteryInfo by viewModel.batteryInfo.collectAsState()
    val connectivityStatus by viewModel.connectivityStatus.collectAsState()
    val scenarioStatus by viewModel.scenarioRunner.scenarioStatus.collectAsState()
    var selectedScenario by remember { mutableStateOf(ScenarioId.SAFE_ZONE_EXIT) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. PRIMARY PARENT QUESTION: "Is my child safe?"
        val heroBg = if (isEmergency) Color(0xFFFEF2F2) else Color(0xFFECFDF5)
        val heroBorder = if (isEmergency) Color(0xFFF87171) else Color(0xFF6EE7B7)
        val heroIconTint = if (isEmergency) Color(0xFFDC2626) else Color(0xFF059669)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = heroBg),
            border = BorderStroke(1.5.dp, heroBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = heroIconTint.copy(alpha = 0.15f),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isEmergency) Icons.Default.Warning else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = heroIconTint,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = childBio.childName.ifEmpty { "Child ($deviceId)" },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = if (isEmergency) "ALERT IN PROGRESS • ${lastBeacon?.riskLevel?.title}" else "ALL SECURE • Child is Safe",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = heroIconTint
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Location / Safe Zone Summary
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEmergency) "Last Alert Incident Location:" else "Last Verified Location:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = if (lastBeacon != null && !lastBeacon.isSimulation) {
                                "Location: UNKNOWN (Physical ESP32 node has no GPS hardware)\n• In BLE direct proximity range"
                            } else {
                                (safeZoneAddress?.formattedSummary() ?: "Within designated safe zone perimeter") +
                                    (verifiedLocation?.let { "\n• ${it.getRelativeTimeString()} • ${it.confidence.displayName} (±${it.accuracyMeters.toInt()}m)" } ?: "")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Text(
                            text = if (lastBeacon != null && !lastBeacon.isSimulation) {
                                "• Signal State: Direct BLE Signal Active"
                            } else {
                                "• Perimeter Status: ${geofenceState.displayName}" +
                                    (distanceToBoundary?.let { dist ->
                                        if (dist > 0) " (+%.0f m outside)".format(dist) else " (%.0f m inside)".format(dist)
                                    } ?: "") +
                                    if (trustedRoute.isEnabled) "\n• Route Corridor: ${routeState.displayName}" else ""
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Child Device Telemetry: Battery & Connectivity (Batch F: Phase 15 & 16)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isPhysicalNode = lastBeacon != null && !lastBeacon.isSimulation
                    val batColor = if (isPhysicalNode) Color.Gray else when (batteryInfo.batteryState) {
                        BatteryState.NORMAL -> Color(0xFF10B981)
                        BatteryState.LOW -> Color(0xFFF59E0B)
                        BatteryState.CRITICAL -> Color(0xFFEF4444)
                    }
                    val batBg = if (isPhysicalNode) Color(0xFFF3F4F6) else when (batteryInfo.batteryState) {
                        BatteryState.NORMAL -> Color(0xFFECFDF5)
                        BatteryState.LOW -> Color(0xFFFFFBEB)
                        BatteryState.CRITICAL -> Color(0xFFFEF2F2)
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = batBg,
                        border = BorderStroke(1.dp, batColor.copy(alpha = 0.35f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isPhysicalNode) Icons.Default.BatteryAlert else if (batteryInfo.isCharging) Icons.Default.BatteryChargingFull else if (batteryInfo.batteryState == BatteryState.CRITICAL) Icons.Default.BatteryAlert else Icons.Default.BatteryFull,
                                contentDescription = "Child Band Battery",
                                tint = batColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = if (isPhysicalNode) "N/A" else "${batteryInfo.percentage ?: "--"}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = batColor
                                )
                                Text(
                                    text = if (isPhysicalNode) "NOT AVAILABLE" else if (batteryInfo.operatingMode == OperatingMode.POWER_SAVING) "Power Saving" else batteryInfo.batteryState.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    val connColor = when (connectivityStatus.tier) {
                        ConnectivityTier.ONLINE -> Color(0xFF10B981)
                        ConnectivityTier.NEARBY -> Color(0xFF2563EB)
                        ConnectivityTier.RELAYED -> Color(0xFF9333EA)
                        ConnectivityTier.OFFLINE -> Color(0xFFF59E0B)
                        ConnectivityTier.UNKNOWN -> Color.Gray
                    }
                    val connBg = when (connectivityStatus.tier) {
                        ConnectivityTier.ONLINE -> Color(0xFFECFDF5)
                        ConnectivityTier.NEARBY -> Color(0xFFEFF6FF)
                        ConnectivityTier.RELAYED -> Color(0xFFFAF5FF)
                        ConnectivityTier.OFFLINE -> Color(0xFFFFFBEB)
                        ConnectivityTier.UNKNOWN -> Color(0xFFF3F4F6)
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = connBg,
                        border = BorderStroke(1.dp, connColor.copy(alpha = 0.35f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (connectivityStatus.tier) {
                                    ConnectivityTier.ONLINE -> Icons.Default.CloudDone
                                    ConnectivityTier.NEARBY -> Icons.Default.Radar
                                    ConnectivityTier.RELAYED -> Icons.Default.Wifi
                                    else -> Icons.Default.CloudOff
                                },
                                contentDescription = "Child Band Connectivity",
                                tint = connColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = connectivityStatus.tier.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = connColor
                                )
                                Text(
                                    text = when (connectivityStatus.tier) {
                                        ConnectivityTier.ONLINE -> "Internet OK"
                                        ConnectivityTier.NEARBY -> "Direct BLE"
                                        ConnectivityTier.RELAYED -> "Mesh Relay"
                                        ConnectivityTier.OFFLINE -> "BLE Only"
                                        ConnectivityTier.UNKNOWN -> "Scanning"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (isSilenced && isEmergency) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFEF3C7)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Siren silenced • Alert active",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                            Button(
                                onClick = { viewModel.reopenAlertSheet() },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFD97706),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Open Emergency Sheet", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Call Guardian
                    OutlinedButton(
                        onClick = {
                            val phone = childBio.primaryParentPhone
                            if (phone.isNotBlank()) {
                                try {
                                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.replace(" ", "")}"))
                                    context.startActivity(dialIntent)
                                } catch (_: Exception) {}
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Call",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // View Map
                    OutlinedButton(
                        onClick = onNavigateToSafeZone,
                        modifier = Modifier.weight(1.2f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Safe Zone",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // Emergency 112
                    Button(
                        onClick = {
                            try {
                                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
                                context.startActivity(dialIntent)
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "112 SOS",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 2. BLE Receiver Status Pill Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = if (isScanning) Color(0xFF10B981).copy(alpha = pulseAlpha) else Color.Gray,
                        modifier = Modifier.size(12.dp)
                    ) {}
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isScanning) "BLE GUARDIAN SCANNER ACTIVE" else "SCANNER PAUSED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isScanning) Color(0xFF047857) else Color.Gray
                        )
                        Text(
                            text = "Listening for wearable safety beacons (range ~30m)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = deviceId,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // 3. Nearby Child Nodes (Multi-node and Physical node display)
        if (nearbyNodes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "NEARBY CHILD NODES (${nearbyNodes.size})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    nearbyNodes.values.forEach { node ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (node.isSimulation) "Simulated: ${node.deviceId}" else "Physical: ${node.deviceId} (ESP32-S3)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (!node.isSimulation) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFD1FAE5)
                                    ) {
                                        Text(
                                            text = "HARDWARE",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF065F46),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = node.riskLevel.containerColor
                            ) {
                                Text(
                                    text = node.riskLevel.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = node.riskLevel.primaryColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Recent Safety Timeline
        val recentEvents by viewModel.allEvents.collectAsState()
        if (recentEvents.isNotEmpty()) {
            Spacer(modifier = Modifier.height(18.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RECENT SAFETY TIMELINE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline,
                            letterSpacing = 1.sp
                        )
                        TextButton(onClick = onNavigateToHistory) {
                            Text("View All Log", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    recentEvents.take(3).forEach { ev ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = when (ev.riskLevel) {
                                        "HIGH" -> Color(0xFFDC2626)
                                        "MEDIUM" -> Color(0xFFD97706)
                                        else -> Color(0xFF10B981)
                                    },
                                    modifier = Modifier.size(8.dp)
                                ) {}
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = ev.flagType,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = ev.outcome.replace("_", " "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 5. Prototype Evaluation Controls (Demarcated Demo Mode)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFEF2F2).copy(alpha = 0.7f)
            ),
            border = BorderStroke(1.dp, Color(0xFFFCA5A5))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isDemoExpanded = !isDemoExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DEMO CONTROLS (DEVELOPER / EVALUATION)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF991B1B),
                            letterSpacing = 0.8.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    IconButton(
                        onClick = { isDemoExpanded = !isDemoExpanded },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isDemoExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Toggle Demo",
                            tint = Color(0xFF991B1B)
                        )
                    }
                }

                AnimatedVisibility(visible = isDemoExpanded) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                        ) {
                            Text(
                                text = "⚠️ EVALUATION MODE: Deterministic simulation for developer/evaluator audit. Isolated from live emergency services, external sirens, calls, and SMS.",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF991B1B),
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Scenario Execution Status Banner
                        val statusBg = when {
                            scenarioStatus.isRunning -> Color(0xFFFEF3C7)
                            scenarioStatus.isCompleted -> Color(0xFFECFDF5)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }
                        val statusTextColor = when {
                            scenarioStatus.isRunning -> Color(0xFFB45309)
                            scenarioStatus.isCompleted -> Color(0xFF047857)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = statusBg
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when {
                                        scenarioStatus.isRunning -> "RUNNING: ${scenarioStatus.activeScenarioId?.displayName ?: ""} • ${scenarioStatus.currentStepDescription}"
                                        scenarioStatus.isCompleted -> "COMPLETED: ${scenarioStatus.activeScenarioId?.displayName ?: ""}"
                                        else -> "IDLE (Baseline Clean State)"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = statusTextColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "SELECT DETERMINISTIC SCENARIO (PHASE 17):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7F1D1D),
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Scenario Selector Chips
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ScenarioId.values().forEach { id ->
                                val isSelected = selectedScenario == id
                                val expectedRisk = when (id) {
                                    ScenarioId.NORMAL_DAY -> RiskLevel.NORMAL
                                    ScenarioId.SAFE_ZONE_EXIT -> RiskLevel.HIGH
                                    ScenarioId.MOTION_ANOMALY -> RiskLevel.HIGH
                                    ScenarioId.TAMPER_MOVEMENT -> RiskLevel.HIGH
                                    ScenarioId.OFFLINE_EMERGENCY -> RiskLevel.HIGH
                                    ScenarioId.MULTI_RELAY_EMERGENCY -> RiskLevel.HIGH
                                    ScenarioId.MANUAL_SOS -> RiskLevel.CRITICAL
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) Color(0xFFFEE2E2) else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        if (isSelected) 1.5.dp else 1.dp,
                                        if (isSelected) Color(0xFFDC2626) else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedScenario = id }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = id.displayName,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color(0xFF991B1B) else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = id.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = expectedRisk.containerColor
                                        ) {
                                            Text(
                                                text = expectedRisk.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = expectedRisk.primaryColor,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Scenario Run & Reset Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.runScenario(selectedScenario) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("run_scenario_button"),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFDC2626),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("RUN SCENARIO", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { viewModel.resetScenario() },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("reset_scenario_button"),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF7F1D1D)
                                ),
                                border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("RESET", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Instant Manual Beacon Test Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.simulateIncomingEmergency(RiskLevel.MEDIUM) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("simulate_warning_beacon_button"),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Quick Warning", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = { viewModel.simulateIncomingEmergency(RiskLevel.HIGH) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("simulate_emergency_beacon_button"),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Quick Emergency", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}
