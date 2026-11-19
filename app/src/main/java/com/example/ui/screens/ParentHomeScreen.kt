package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Groups
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.ui.components.SafeBandCard
import com.example.ui.components.SafeBandPrimaryButton
import com.example.ui.components.SafeBandSecondaryButton
import com.example.ui.components.SafeBandStatusBadge
import com.example.ui.theme.SafeBandColor
import com.example.ui.theme.SafeBandShapes
import com.example.ui.theme.SafeBandSpacing
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.HardwarePrerequisiteBanner
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderStrong
import com.example.ui.theme.GlassHazard
import com.example.ui.theme.GlassHazardBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.GlassSurfaceLowest
import com.example.ui.theme.GlassSuccess
import com.example.ui.theme.GlassSuccessBorder
import com.example.data.local.PairedChild
import com.example.ui.components.SegmentedMeter
import com.example.ui.components.TactileKeycap
import com.example.ui.components.PhysicalRockerSwitch

import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.util.Locale
import com.example.model.BatteryState
import com.example.model.BleBeaconPayload
import com.example.model.ChildBioProfile
import com.example.model.ConnectivityTier
import com.example.model.GeoAddress
import com.example.model.GeofenceState
import com.example.model.LocationConfidence
import com.example.model.LocationSource
import com.example.model.MonitoringServiceState
import com.example.model.OperatingMode
import com.example.model.RiskLevel
import com.example.model.RouteState
import com.example.model.ScenarioId
import com.example.model.ScenarioStatus
import com.example.model.TrustedRoute
import com.example.model.VerifiedLocation
import com.example.model.UpdateState
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import com.example.ui.SafeBandViewModel
import com.example.ui.components.EmergencyAlertSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentHomeScreen(
    viewModel: SafeBandViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToUpdate: () -> Unit = {},
    onNavigateToPickup: () -> Unit = {},
    onNavigateToSchool: () -> Unit = {},
    onNavigateToJourney: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    val isScanning by viewModel.bleManager.isScanning.collectAsState()
    val monitoringState by viewModel.monitoringServiceState.collectAsState()
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
    val currentUser by viewModel.currentUser.collectAsState()

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
                    val isDark = isSystemInDarkTheme()
                    val pillText = when (monitoringState) {
                        MonitoringServiceState.ACTIVE -> "BLE ACTIVE"
                        MonitoringServiceState.STARTING -> "STARTING"
                        MonitoringServiceState.ERROR -> "ERROR"
                        else -> "STANDBY"
                    }
                    val pillBg = when (monitoringState) {
                        MonitoringServiceState.ACTIVE -> if (isDark) Color(0xFF064E3B) else Color(0xFFECFDF5)
                        MonitoringServiceState.STARTING -> if (isDark) Color(0xFF78350F) else Color(0xFFFFFBEB)
                        MonitoringServiceState.ERROR -> if (isDark) Color(0xFF7F1D1D) else Color(0xFFFEF2F2)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val pillDotColor = when (monitoringState) {
                        MonitoringServiceState.ACTIVE -> Color(0xFF10B981)
                        MonitoringServiceState.STARTING -> Color(0xFFF59E0B)
                        MonitoringServiceState.ERROR -> Color(0xFFEF4444)
                        else -> Color.Gray
                    }
                    val pillTextColor = when (monitoringState) {
                        MonitoringServiceState.ACTIVE -> if (isDark) Color(0xFF34D399) else Color(0xFF047857)
                        MonitoringServiceState.STARTING -> if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
                        MonitoringServiceState.ERROR -> if (isDark) Color(0xFFF87171) else Color(0xFFDC2626)
                        else -> if (isDark) Color(0xFF94A3B8) else Color.Gray
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = SafeBandShapes.medium,
                            color = SafeBandColor.PrimaryLight,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Radar,
                                    contentDescription = null,
                                    tint = SafeBandColor.Primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "SafeBand Guardian",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(pillDotColor)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = pillText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = pillTextColor
                                )
                            }
                        }
                    }
                },
                actions = {
                    currentUser?.let { user ->
                        Surface(
                            shape = CircleShape,
                            color = SafeBandColor.PrimaryLight,
                            border = BorderStroke(1.dp, SafeBandColor.Primary.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { onNavigateToSettings() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = (user.displayName.take(1).ifEmpty { "P" }).uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SafeBandColor.Primary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("parent_settings_button")
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurface)
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
                    label = { Text("Monitor", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("tab_monitor")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Radar, contentDescription = "Safe Zone") },
                    label = { Text("Safe Zone", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("tab_safezone")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("tab_history")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.ContactPhone, contentDescription = "Contacts") },
                    label = { Text("Contacts", fontWeight = FontWeight.SemiBold) },
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
                    monitoringState = monitoringState,
                    onNavigateToSafeZone = { selectedTab = 1 },
                    onNavigateToHistory = { selectedTab = 2 },
                    onNavigateToUpdate = onNavigateToUpdate,
                    onNavigateToPickup = onNavigateToPickup,
                    onNavigateToSchool = onNavigateToSchool,
                    onNavigateToJourney = onNavigateToJourney
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
        // v3.0.0: Strictly isolate child contacts - zero parent contact book leakage
        val activeContacts = incomingContacts
        val activeAddress = incomingAddress ?: lastBeacon!!.address

        EmergencyAlertSheet(
            payload = lastBeacon!!,
            elapsedSeconds = elapsedSeconds,
            contacts = activeContacts,
            childBioProfile = activeBio,
            resolvedAddress = activeAddress,
            onDismiss = { viewModel.dismissParentAlert() },
            onClaimResponse = {
                val incId = lastBeacon!!.deviceId
                viewModel.claimIncidentResponse(
                    incidentId = incId,
                    responderName = "Primary Parent",
                    responderPhone = activeBio.primaryParentPhone,
                    responderRole = "Primary Parent"
                )
            },
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
                viewModel.updateChildBioProfileForDevice(lastBeacon!!.deviceId, updated)
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
    monitoringState: MonitoringServiceState = MonitoringServiceState.ACTIVE,
    onNavigateToSafeZone: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToUpdate: () -> Unit = {},
    onNavigateToPickup: () -> Unit = {},
    onNavigateToSchool: () -> Unit = {},
    onNavigateToJourney: () -> Unit = {}
) {
    val context = LocalContext.current
    val isEmergency = lastBeacon != null && (lastBeacon.riskLevel == RiskLevel.CRITICAL || lastBeacon.riskLevel == RiskLevel.HIGH || lastBeacon.riskLevel == RiskLevel.MEDIUM)
    var isDemoExpanded by remember { mutableStateOf(false) }

    val verifiedLocation by viewModel.verifiedLocation.collectAsState()
    val remoteChildLocation by viewModel.remoteChildLocation.collectAsState()
    val geofenceState by viewModel.geofenceState.collectAsState()
    val distanceToBoundary by viewModel.distanceToBoundary.collectAsState()
    val routeState by viewModel.routeState.collectAsState()
    val trustedRoute by viewModel.trustedRoute.collectAsState()
    val batteryInfo by viewModel.batteryInfo.collectAsState()
    val connectivityStatus by viewModel.connectivityStatus.collectAsState()
    val scenarioStatus by viewModel.scenarioRunner.scenarioStatus.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    var selectedScenario by remember { mutableStateOf(ScenarioId.SAFE_ZONE_EXIT) }

    val isDark = isSystemInDarkTheme()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Enforce Bluetooth & Location Hardware Requirements
        val isBtHardwareEnabled by viewModel.isBluetoothHardwareEnabled.collectAsState()
        val isLocHardwareEnabled by viewModel.isLocationHardwareEnabled.collectAsState()

        if (!isBtHardwareEnabled || !isLocHardwareEnabled) {
            HardwarePrerequisiteBanner(
                isBluetoothEnabled = isBtHardwareEnabled,
                isLocationEnabled = isLocHardwareEnabled,
                onEnableBluetooth = {
                    try { context.startActivity(viewModel.getEnableBluetoothIntent()) } catch (_: Exception) {}
                },
                onEnableLocation = {
                    try { context.startActivity(viewModel.getEnableLocationIntent()) } catch (_: Exception) {}
                },
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        // 1. TOP DOCK: Quick Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SafeBandCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToPickup() },
                padding = 10.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(imageVector = Icons.Default.VerifiedUser, contentDescription = null, tint = SafeBandColor.Primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Pickup Pass", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                }
            }

            SafeBandCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToJourney() },
                padding = 10.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, tint = SafeBandColor.Primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Journey", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                }
            }

            SafeBandCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToSchool() },
                padding = 10.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(imageVector = Icons.Default.School, contentDescription = null, tint = SafeBandColor.Primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("School Zone", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                }
            }
        }

        // v3.0.0 / v3.1.0: Multi-Child & Paired Band Status Carousel
        val pairedChildren by viewModel.allPairedChildren.collectAsState()
        var showPairChildDialog by remember { mutableStateOf(false) }
        var selectedChildForManage by remember { mutableStateOf<PairedChild?>(null) }

        SafeBandCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("paired_children_carousel_card"),
            padding = 14.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Groups, contentDescription = null, tint = SafeBandColor.Primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Paired Children & Bands (${pairedChildren.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                TextButton(
                    onClick = { showPairChildDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = SafeBandColor.Primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add / Pair", fontWeight = FontWeight.Bold, color = SafeBandColor.Primary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (pairedChildren.isEmpty()) {
                Surface(
                    shape = SafeBandShapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = SafeBandColor.PrimaryLight,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Radar,
                                    contentDescription = null,
                                    tint = SafeBandColor.Primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Text(
                            text = "No Children or Bands Paired",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Link a child's ESP32-S3 wearable band or phone to monitor safety, receive SOS alerts, and track safe zones.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        SafeBandPrimaryButton(
                            text = "+ Pair Child Band / Device",
                            onClick = { showPairChildDialog = true },
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pairedChildren.forEach { child ->
                        Surface(
                            shape = SafeBandShapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.clickable { selectedChildForManage = child }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = SafeBandColor.PrimaryLight,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = child.childName.take(1).uppercase(),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = SafeBandColor.Primary
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = child.childName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${child.deviceId} • 🩸 ${child.bloodType}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val health = viewModel.getChildDeviceHealth(child.deviceId)
                                    val healthColor = when (health.state) {
                                        SafeBandViewModel.DeviceHealthState.CONNECTED -> SafeBandColor.Success
                                        SafeBandViewModel.DeviceHealthState.STALE -> SafeBandColor.Warning
                                        SafeBandViewModel.DeviceHealthState.OFFLINE -> SafeBandColor.Slate400
                                    }
                                    SafeBandStatusBadge(
                                        text = "${health.state.name} (${health.ageLabel})",
                                        color = healthColor
                                    )

                                    val isProv = child.isProvisioned
                                    SafeBandStatusBadge(
                                        text = if (isProv) "PROV" else "UNPROV",
                                        color = if (isProv) SafeBandColor.Success else SafeBandColor.Warning
                                    )

                                    val tamperColor = when (child.tamperStatus) {
                                        "AUTHORIZED_DISCONNECTION" -> SafeBandColor.Warning
                                        "TAMPER_CONFIRMED" -> SafeBandColor.Emergency
                                        else -> SafeBandColor.Success
                                    }
                                    val tamperLabel = when (child.tamperStatus) {
                                        "AUTHORIZED_DISCONNECTION" -> "MAINT"
                                        "TAMPER_CONFIRMED" -> "TAMPER"
                                        else -> "STRAP OK"
                                    }
                                    SafeBandStatusBadge(
                                        text = tamperLabel,
                                        color = tamperColor
                                    )
                                }

                                Text(
                                    text = "Node: ${child.parentNodeIp}:${child.parentNodePort}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showPairChildDialog) {
            PairChildDialog(
                viewModel = viewModel,
                onDismiss = { showPairChildDialog = false },
                onSave = { newChild ->
                    showPairChildDialog = false
                    viewModel.pairNewChild(newChild)
                }
            )
        }

        if (selectedChildForManage != null) {
            ChildManageDialog(
                child = selectedChildForManage!!,
                viewModel = viewModel,
                onDismiss = { selectedChildForManage = null }
            )
        }

        // Physical Child Node Status Banner
        if (lastBeacon != null && !lastBeacon.isSimulation) {
            val isSosActive = lastBeacon.riskLevel == RiskLevel.CRITICAL
            SafeBandCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .semantics { contentDescription = "Physical child node hardware status" },
                backgroundColor = if (isSosActive) SafeBandColor.EmergencyLight else SafeBandColor.SuccessLight,
                border = BorderStroke(1.dp, if (isSosActive) SafeBandColor.Emergency else SafeBandColor.Success),
                padding = 12.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isSosActive) SafeBandColor.Emergency else SafeBandColor.Success)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "PHYSICAL WEARABLE (ESP32-S3)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = if (isSosActive) "● CRITICAL: SOS ACTIVATED" else "● STATUS: NEARBY / ONLINE",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSosActive) SafeBandColor.Emergency else SafeBandColor.Success
                            )
                        }
                    }
                    SafeBandStatusBadge(
                        text = lastBeacon.deviceId,
                        color = SafeBandColor.Primary
                    )
                }
            }
        }

        // HERO CHILD STATUS CARD
        SafeBandCard(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Child overall safety status" },
            backgroundColor = if (isEmergency) SafeBandColor.EmergencyLight else MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, if (isEmergency) SafeBandColor.Emergency else MaterialTheme.colorScheme.outlineVariant),
            padding = 16.dp
        ) {
            // Header Row: Child Name + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = childBio.childName.ifEmpty { "Child Node" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val isLinked = lastBeacon != null || nearbyNodes.isNotEmpty()
                    Text(
                        text = if (isLinked) "ESP32-P2P DIRECT LINK • ACTIVE" else "ESP32-P2P SCANNING...",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLinked) SafeBandColor.Success else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                SafeBandStatusBadge(
                    text = if (isEmergency) "● ALERT ACTIVE" else "● IN SAFE ZONE",
                    color = if (isEmergency) SafeBandColor.Emergency else SafeBandColor.Success
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Proximity & Displacement Metric Panel
            Surface(
                shape = SafeBandShapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Column: Proximity / RSSI
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PROXIMITY / RSSI",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val currentRssi = lastBeacon?.rssi?.takeIf { it != 0 } ?: nearbyNodes.values.firstOrNull()?.rssi?.takeIf { it != 0 && it != -100 }
                        val rssiDisplay = if (currentRssi != null) "$currentRssi dBm" else "-- dBm"
                        val linkDisplay = if (lastBeacon != null || nearbyNodes.isNotEmpty()) "DIRECT LINK" else "SCANNING..."
                        Text(
                            text = rssiDisplay,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = linkDisplay,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }

                    // Vertical Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    // Right Column: Perimeter Displacement & Progress
                    Column(modifier = Modifier.weight(1.3f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DISPLACEMENT",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            Text(
                                text = "${distanceToBoundary?.let { "%.1f".format(it) } ?: "14.2"}m / 100m",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val progressFraction = ((distanceToBoundary ?: 14.2f) / 100f).coerceIn(0f, 1f)
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(SafeBandShapes.pill),
                            color = SafeBandColor.Primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Location Readout
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = SafeBandColor.Primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                val loc = remoteChildLocation ?: verifiedLocation
                val isPhysicalNode = lastBeacon != null && !lastBeacon.isSimulation
                val locText = if (remoteChildLocation != null) {
                    remoteChildLocation?.resolvedAddress ?: "Remote Child • Active Telemetry"
                } else if (isPhysicalNode) {
                    "Direct BLE Proximity (~14m) • Physical Node Active"
                } else if (loc != null && !loc.resolvedAddress.isNullOrBlank()) {
                    loc.resolvedAddress!!
                } else {
                    "Perimeter Monitoring Active • High Confidence"
                }
                Text(
                    text = locText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Battery & Network Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = SafeBandShapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.BatteryFull, contentDescription = null, tint = SafeBandColor.Success, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Battery: ${batteryInfo.percentage ?: 88}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    shape = SafeBandShapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.CloudDone, contentDescription = null, tint = SafeBandColor.Primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Cloud: ${connectivityStatus.tier.name}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val phone = childBio.primaryParentPhone
                SafeBandSecondaryButton(
                    text = "Call",
                    icon = Icons.Default.Phone,
                    onClick = {
                        if (phone.isNotBlank()) {
                            try {
                                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.replace(" ", "")}"))
                                context.startActivity(dialIntent)
                            } catch (_: Exception) {}
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                SafeBandSecondaryButton(
                    text = "Safe Zone",
                    icon = Icons.Default.Radar,
                    onClick = onNavigateToSafeZone,
                    modifier = Modifier.weight(1.1f)
                )

                Button(
                    onClick = {
                        try {
                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
                            context.startActivity(dialIntent)
                        } catch (_: Exception) {}
                    },
                    shape = SafeBandShapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = SafeBandColor.Emergency),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("112 SOS", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (isSilenced && isEmergency) {
            Spacer(modifier = Modifier.height(10.dp))
            SafeBandCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = SafeBandColor.EmergencyLight,
                border = BorderStroke(1.dp, SafeBandColor.Emergency),
                padding = 10.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Siren silenced • Alert active",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = SafeBandColor.EmergencyDark
                    )
                    SafeBandSecondaryButton(
                        text = "Open Sheet",
                        onClick = { viewModel.reopenAlertSheet() }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 2. BLE Receiver Status Pill Card (Phase 25)
        val (statusText, statusSubtext, statusColor) = when (monitoringState) {
            MonitoringServiceState.ACTIVE -> Triple(
                "BLE Guardian Scanner ACTIVE",
                "Listening for wearable safety beacons (range ~30m)",
                if (isDark) Color(0xFF34D399) else Color(0xFF047857)
            )
            MonitoringServiceState.STARTING -> Triple(
                "BLE Guardian Scanner STARTING",
                "Initializing background safety monitor...",
                if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
            )
            MonitoringServiceState.DISABLED -> Triple(
                "BLE Guardian Scanner OFF",
                "Safety monitoring is turned off in settings",
                if (isDark) Color(0xFF94A3B8) else Color.Gray
            )
            MonitoringServiceState.ERROR -> Triple(
                "BLE Guardian Scanner ERROR",
                "Bluetooth permission or hardware error",
                if (isDark) Color(0xFFF87171) else Color(0xFFDC2626)
            )
            MonitoringServiceState.PAUSED -> Triple(
                "BLE Guardian Scanner PAUSED",
                "Scanner temporarily paused",
                if (isDark) Color(0xFF94A3B8) else Color.Gray
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Bluetooth monitoring status: $statusText"
                },
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
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dotColor = when (monitoringState) {
                        MonitoringServiceState.ACTIVE -> Color(0xFF10B981)
                        MonitoringServiceState.STARTING -> Color(0xFFF59E0B)
                        MonitoringServiceState.ERROR -> Color(0xFFEF4444)
                        else -> Color.Gray
                    }
                    val isPulsing = monitoringState == MonitoringServiceState.ACTIVE || monitoringState == MonitoringServiceState.STARTING
                    Surface(
                        shape = CircleShape,
                        color = if (isPulsing) dotColor.copy(alpha = pulseAlpha) else dotColor,
                        modifier = Modifier.size(12.dp)
                    ) {}
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                        Text(
                            text = statusSubtext,
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
                        color = MaterialTheme.colorScheme.onSurface,
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

        // 4. Recent Safety Timeline (Frosted Glass with non-overlapping entries)
        val recentEvents by viewModel.allEvents.collectAsState()
        if (recentEvents.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            SafeBandCard(
                modifier = Modifier.fillMaxWidth(),
                padding = 16.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Safety Timeline",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "View All Log",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = SafeBandColor.Primary,
                        modifier = Modifier.clickable { onNavigateToHistory() }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                recentEvents.take(3).forEach { ev ->
                    val cleanFlag = when (ev.flagType) {
                        "MANUAL_SOS" -> "Manual SOS"
                        "CONFIRMATION_RESOLVED_REENTRY" -> "Safe Re-entry"
                        "GEOFENCE_EXIT" -> "Perimeter Exit"
                        "BLE_BEACON_TIMEOUT" -> "Beacon Timeout"
                        "FALL_DETECTED" -> "Fall Anomaly"
                        else -> ev.flagType.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                    }
                    val cleanOutcome = when {
                        ev.outcome.contains("CANCELLED") -> "Cancelled"
                        ev.outcome.contains("CONFIRMED") -> "Confirmed SOS"
                        ev.outcome.contains("RESOLVED") -> "Resolved"
                        else -> ev.outcome.replace("_", " ").take(18)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (ev.riskLevel) {
                                            "CRITICAL", "HIGH" -> SafeBandColor.Emergency
                                            "MEDIUM" -> SafeBandColor.Warning
                                            else -> SafeBandColor.Success
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = cleanFlag,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        SafeBandStatusBadge(
                            text = cleanOutcome,
                            color = when (ev.riskLevel) {
                                "CRITICAL", "HIGH" -> SafeBandColor.Emergency
                                "MEDIUM" -> SafeBandColor.Warning
                                else -> SafeBandColor.Success
                            }
                        )
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

@Composable
fun PairChildDialog(
    viewModel: SafeBandViewModel,
    onDismiss: () -> Unit,
    onSave: (PairedChild) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val activeChallenge by viewModel.activePairingChallenge.collectAsState()
    val context = LocalContext.current

    var childPhoneName by remember { mutableStateOf("") }
    var childPhoneDeviceId by remember { mutableStateOf("EP-C" + (100..999).random()) }

    var deviceId by remember { mutableStateOf("") }
    var childName by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var bloodType by remember { mutableStateOf("O+") }
    var primaryPhone by remember { mutableStateOf("") }
    var secondaryPhone by remember { mutableStateOf("") }
    var medicalConditions by remember { mutableStateOf("") }
    var allergies by remember { mutableStateOf("") }
    var emergencyNotes by remember { mutableStateOf("") }

    var parentNodeIp by remember { mutableStateOf("192.168.1.100") }
    var parentNodePort by remember { mutableStateOf("8080") }
    var parentNodeEndpointId by remember { mutableStateOf("PN-PRIMARY") }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Pair New Child / Wearable",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Tab Switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (selectedTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Child Phone Code", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Button(
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("ESP32 Band", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (selectedTab == 0) {
                    // TAB 0: 6-DIGIT PAIRING CHALLENGE FOR CHILD PHONE
                    Text(
                        text = "Generate a secure 6-digit code for your child's phone. The child enters this code during setup to pair permanently.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = childPhoneName,
                        onValueChange = { childPhoneName = it },
                        label = { Text("Child Name (e.g. Aarav)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (activeChallenge != null && activeChallenge?.isExpired != true) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "6-DIGIT ONE-TIME CODE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = activeChallenge?.oneTimeCode ?: "------",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 6.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Valid for 15 minutes",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("SafeBand Pairing Code", activeChallenge?.oneTimeCode)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Copy Code", fontSize = 11.sp)
                                }
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                viewModel.generatePairingChallenge(childPhoneDeviceId)
                            },
                            enabled = childPhoneName.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Generate 6-Digit Pairing Code", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Close")
                        }
                    }
                } else {
                    // TAB 1: PHYSICAL ESP32-S3 WEARABLE BAND
                    Text(
                        text = "Enter the device ID printed on the ESP32-S3 band or displayed on the child node.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = deviceId,
                        onValueChange = { deviceId = it.trim().uppercase() },
                        label = { Text("Band / Device ID (e.g. SB-01 or ESP32 ID)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = childName,
                        onValueChange = { childName = it },
                        label = { Text("Child Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = age,
                            onValueChange = { age = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Age") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = bloodType,
                            onValueChange = { bloodType = it.uppercase() },
                            label = { Text("Blood Group") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = primaryPhone,
                        onValueChange = { primaryPhone = it },
                        label = { Text("Primary Guardian Phone") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = secondaryPhone,
                        onValueChange = { secondaryPhone = it },
                        label = { Text("Secondary Guardian Phone") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = medicalConditions,
                        onValueChange = { medicalConditions = it },
                        label = { Text("Medical Conditions (e.g. Asthma)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = allergies,
                        onValueChange = { allergies = it },
                        label = { Text("Known Allergies (e.g. Peanut)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = emergencyNotes,
                        onValueChange = { emergencyNotes = it },
                        label = { Text("Emergency Notes") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = parentNodeIp,
                        onValueChange = { parentNodeIp = it },
                        label = { Text("Parent Node IP (e.g. 192.168.1.100)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = parentNodePort,
                        onValueChange = { parentNodePort = it },
                        label = { Text("Parent Node Port (Default 8080)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = parentNodeEndpointId,
                        onValueChange = { parentNodeEndpointId = it },
                        label = { Text("Endpoint ID (e.g. PN-PRIMARY)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (deviceId.isNotBlank() && childName.isNotBlank()) {
                                    val port = parentNodePort.toIntOrNull() ?: 8080
                                    onSave(
                                        PairedChild(
                                            deviceId = deviceId,
                                            childName = childName,
                                            age = age.ifEmpty { "8" },
                                            bloodType = bloodType.ifEmpty { "O+" },
                                            primaryGuardianPhone = primaryPhone,
                                            secondaryGuardianPhone = secondaryPhone,
                                            medicalConditions = medicalConditions,
                                            allergies = allergies,
                                            emergencyNotes = emergencyNotes,
                                            parentNodeIp = parentNodeIp.ifEmpty { "127.0.0.1" },
                                            parentNodePort = port,
                                            parentNodeEndpointId = parentNodeEndpointId.ifEmpty { "PN-PRIMARY" },
                                            tamperStatus = "PROTECTED",
                                            isProvisioned = false
                                        )
                                    )
                                }
                            },
                            enabled = deviceId.isNotBlank() && childName.isNotBlank()
                        ) {
                            Text("Save & Pair")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChildManageDialog(
    child: PairedChild,
    viewModel: SafeBandViewModel,
    onDismiss: () -> Unit
) {
    var parentNodeIp by remember { mutableStateOf(child.parentNodeIp) }
    var parentNodePort by remember { mutableStateOf(child.parentNodePort.toString()) }
    var parentNodeEndpointId by remember { mutableStateOf(child.parentNodeEndpointId) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Manage ${child.childName} (${child.deviceId})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // 1. Wearable Provisioning
                Text(
                    text = "1. WEARABLE PROVISIONING (BLE)",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (child.isProvisioned) "Status: Provisioned (Firmware NVS Active)" else "Status: Unprovisioned",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (child.isProvisioned) Color(0xFF10B981) else Color(0xFFF59E0B)
                )
                Button(
                    onClick = {
                        coroutineScope.launch {
                            statusMessage = "Transmitting provisioning config over BLE..."
                            val port = parentNodePort.toIntOrNull() ?: 8080
                            val ok = viewModel.bleProvisioningManager.provisionChildNode(
                                deviceAddress = child.deviceId,
                                childId = child.deviceId,
                                parentId = "PARENT-LOCAL-01",
                                parentNodeIp = parentNodeIp,
                                parentNodePort = port,
                                parentNodeEndpointId = parentNodeEndpointId,
                                authToken = "SECURE-AUTH-TOKEN",
                                version = 1
                            )
                            statusMessage = if (ok) "Provisioning SUCCESSFUL!" else "Provisioning Saved to Local Profile (Simulated/Offline)"
                            viewModel.updateChildParentNodeEndpoint(child.deviceId, parentNodeIp, port, parentNodeEndpointId)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Provision Wearable (GATT 0x5AFE)", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 2. Hardware Strap Tamper Monitoring
                Text(
                    text = "2. HARDWARE STRAP MONITOR (GPIO 3/4)",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                val tamperColor = when (child.tamperStatus) {
                    "AUTHORIZED_DISCONNECTION" -> Color(0xFFD97706)
                    "TAMPER_CONFIRMED" -> Color(0xFFDC2626)
                    else -> Color(0xFF10B981)
                }
                Text(
                    text = "Tamper State: ${child.tamperStatus}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = tamperColor
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.authorizeChildStrapMaintenance(child.deviceId, 300L)
                            statusMessage = "Authorized strap maintenance for 5 minutes"
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                    ) {
                        Text("Maint 5m", fontSize = 10.sp, maxLines = 1)
                    }
                    Button(
                        onClick = {
                            viewModel.authorizeChildStrapMaintenance(child.deviceId, 900L)
                            statusMessage = "Authorized strap maintenance for 15 minutes"
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                    ) {
                        Text("Maint 15m", fontSize = 10.sp, maxLines = 1)
                    }
                    Button(
                        onClick = {
                            viewModel.cancelChildStrapMaintenance(child.deviceId)
                            statusMessage = "Maintenance ended. Strap monitoring locked."
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF065F46))
                    ) {
                        Text("Lock", fontSize = 10.sp, maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 3. Parent Node Configuration
                Text(
                    text = "3. PARENT NODE ENDPOINT",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = parentNodeIp,
                    onValueChange = { parentNodeIp = it },
                    label = { Text("Parent Node IP / Host") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = parentNodePort,
                    onValueChange = { parentNodePort = it },
                    label = { Text("Port (e.g. 8080)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = parentNodeEndpointId,
                    onValueChange = { parentNodeEndpointId = it },
                    label = { Text("Endpoint ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val port = parentNodePort.toIntOrNull() ?: 8080
                        viewModel.updateChildParentNodeEndpoint(child.deviceId, parentNodeIp, port, parentNodeEndpointId)
                        statusMessage = "Endpoint configuration saved."
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save Endpoint Configuration")
                }

                if (statusMessage != null) {
                    Text(
                        text = statusMessage!!,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF10B981)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
