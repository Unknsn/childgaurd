package com.example.ui.screens

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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import com.example.model.AlertFlag
import com.example.model.BatteryState
import com.example.model.ConnectivityTier
import com.example.model.GeofenceState
import com.example.model.LocationConfidence
import com.example.model.OperatingMode
import com.example.model.RiskLevel
import com.example.model.RouteState
import com.example.model.TrustedRoute
import com.example.model.VerifiedLocation
import com.example.ui.SafeBandViewModel
import com.example.ui.components.CountdownAlertCard
import com.example.ui.components.RiskStatusBanner

enum class SosHoldState {
    IDLE,
    HOLDING,
    ACTIVATING,
    ACTIVATED,
    CANCELLED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildHomeScreen(
    viewModel: SafeBandViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentRisk by viewModel.currentRiskLevel.collectAsState()
    val activeFlags by viewModel.activeFlags.collectAsState()
    val isCountingDown by viewModel.isCountingDown.collectAsState()
    val countdownSeconds by viewModel.countdownRemainingSeconds.collectAsState()
    val isAdvertising by viewModel.bleManager.isAdvertising.collectAsState()
    val accelMagnitude by viewModel.motionDetector.currentMagnitude.collectAsState()
    val isMotionActive by viewModel.motionDetector.isAnomalyActive.collectAsState()
    val isOutsideSafeZone by viewModel.locationHelper.isOutsideSafeZone.collectAsState()
    val geofenceState by viewModel.geofenceState.collectAsState()
    val routeState by viewModel.routeState.collectAsState()
    val verifiedLocation by viewModel.verifiedLocation.collectAsState()
    val trustedRoute by viewModel.trustedRoute.collectAsState()
    val deviceId by viewModel.deviceId.collectAsState()
    val safeZone by viewModel.safeZone.collectAsState()
    val childProfile by viewModel.childBioProfile.collectAsState()
    val batteryInfo by viewModel.batteryInfo.collectAsState()
    val connectivityStatus by viewModel.connectivityStatus.collectAsState()

    var isDemoControlsExpanded by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var holdState by remember { mutableStateOf(SosHoldState.IDLE) }
    val hapticFeedback = LocalHapticFeedback.current

    // Pulse animation for SOS button
    val infiniteTransition = rememberInfiniteTransition(label = "SosPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SosScale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SafeBand",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = deviceId,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = childProfile.childName.ifEmpty { "Child Safety Wearable" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToHistory,
                        modifier = Modifier.testTag("history_button")
                    ) {
                        Icon(imageVector = Icons.Default.History, contentDescription = "Event Log")
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // 1. Child Safety Reassurance Hero Card (Calm when normal, alerting during danger)
            val isEmergency = currentRisk == RiskLevel.CRITICAL || currentRisk == RiskLevel.HIGH || currentRisk == RiskLevel.MEDIUM
            val heroBgColor = when {
                isEmergency -> Color(0xFFFEF2F2)
                isCountingDown -> Color(0xFFFFFBEB)
                else -> Color(0xFFECFDF5)
            }
            val heroBorderColor = when {
                isEmergency -> Color(0xFFF87171)
                isCountingDown -> Color(0xFFFCD34D)
                else -> Color(0xFF6EE7B7)
            }
            val heroIconTint = when {
                isEmergency -> Color(0xFFDC2626)
                isCountingDown -> Color(0xFFD97706)
                else -> Color(0xFF059669)
            }
            val heroTitle = when {
                isEmergency -> "HELP REQUESTED"
                isCountingDown -> "CHECK REQUIRED"
                else -> "YOU ARE SAFE"
            }
            val heroSubtitle = when {
                isEmergency -> "Broadcasting emergency alert with your location to guardians."
                isCountingDown -> "Movement or boundary notice detected. Confirm you are OK below."
                else -> "SafeBand is active and protecting you. Inside ${safeZone.name}."
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = heroBgColor),
                border = BorderStroke(1.5.dp, heroBorderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = heroIconTint.copy(alpha = 0.15f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when {
                                    isEmergency -> Icons.Default.Warning
                                    isCountingDown -> Icons.Default.Security
                                    else -> Icons.Default.CheckCircle
                                },
                                contentDescription = null,
                                tint = heroIconTint,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = heroTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = heroIconTint,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = heroSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Risk Status Banner (Preserved for compatibility and indicator flags)
            RiskStatusBanner(
                riskLevel = currentRisk,
                activeFlags = activeFlags,
                isAdvertising = isAdvertising
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Visible Countdown Card (False Alarm Mitigation Window: "I'm OK, Cancel")
            CountdownAlertCard(
                isVisible = isCountingDown,
                secondsRemaining = countdownSeconds,
                onCancelClicked = { viewModel.cancelConfirmationCountdown() }
            )

            if (isCountingDown) {
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 4. Large Tactile SOS Panic Button (Phase 19: Press and Hold 2 Seconds)
            val isSosActive = activeFlags.contains(AlertFlag.MANUAL_SOS) || holdState == SosHoldState.ACTIVATED

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSosActive) Color(0xFFFEF2F2) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                border = BorderStroke(
                    1.5.dp,
                    if (isSosActive) Color(0xFFEF4444) else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when {
                            isSosActive -> "EMERGENCY SOS IS ACTIVE"
                            holdState == SosHoldState.HOLDING -> "ACTIVATING SOS..."
                            holdState == SosHoldState.CANCELLED -> "SOS CANCELLED (RELEASED EARLY)"
                            else -> "MANUAL SOS PANIC BUTTON"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = if (isSosActive || holdState == SosHoldState.HOLDING) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = when {
                            isSosActive -> "Broadcasting immediate high-risk emergency beacon to guardians. Tap button to cancel."
                            holdState == SosHoldState.HOLDING -> "Keep holding... %.1fs remaining".format(2.0f * (1f - holdProgress))
                            holdState == SosHoldState.CANCELLED -> "Press and hold continuously for 2 seconds to trigger emergency."
                            else -> "Press and hold for 2 full seconds to trigger emergency alarm."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // 2-Second Hold SOS Interactive Container
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(160.dp)
                    ) {
                        // Outer Progress Ring (Shows 2-second hold progress)
                        if (holdState == SosHoldState.HOLDING) {
                            CircularProgressIndicator(
                                progress = { holdProgress },
                                modifier = Modifier.size(158.dp),
                                color = Color(0xFFDC2626),
                                strokeWidth = 6.dp,
                                trackColor = Color(0xFFFCA5A5).copy(alpha = 0.4f)
                            )
                        } else if (isSosActive) {
                            CircularProgressIndicator(
                                progress = { 1f },
                                modifier = Modifier.size(158.dp),
                                color = Color(0xFFDC2626),
                                strokeWidth = 4.dp
                            )
                        }

                        // Circular Tactile SOS Button
                        Surface(
                            shape = CircleShape,
                            color = if (isSosActive) Color(0xFFB91C1C) else if (holdState == SosHoldState.HOLDING) Color(0xFFDC2626) else Color(0xFFDC2626),
                            shadowElevation = if (isSosActive) 14.dp else 6.dp,
                            modifier = Modifier
                                .size((136 * (if (isSosActive) pulseScale else 1.0f)).dp)
                                .clip(CircleShape)
                                .pointerInput(isSosActive) {
                                    if (isSosActive) {
                                        detectTapGestures(
                                            onTap = {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.cancelManualSos()
                                                holdState = SosHoldState.IDLE
                                                holdProgress = 0f
                                            }
                                        )
                                    } else {
                                        detectTapGestures(
                                            onPress = {
                                                holdState = SosHoldState.HOLDING
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                val startTime = System.currentTimeMillis()
                                                var reachedActivation = false
                                                try {
                                                    while (true) {
                                                        val elapsed = System.currentTimeMillis() - startTime
                                                        val p = (elapsed / 2000f).coerceIn(0f, 1f)
                                                        holdProgress = p
                                                        if (p >= 1f) {
                                                            reachedActivation = true
                                                            holdState = SosHoldState.ACTIVATED
                                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            viewModel.triggerManualSos()
                                                            break
                                                        }
                                                        delay(20L)
                                                    }
                                                    tryAwaitRelease()
                                                } catch (_: CancellationException) {
                                                    if (!reachedActivation) {
                                                        holdState = SosHoldState.CANCELLED
                                                        holdProgress = 0f
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                                .testTag("manual_sos_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Sos,
                                        contentDescription = "Emergency SOS Button. Press and hold 2 seconds to activate.",
                                        tint = Color.White,
                                        modifier = Modifier.size(52.dp)
                                    )
                                    Text(
                                        text = when {
                                            isSosActive -> "TAP TO CANCEL"
                                            holdState == SosHoldState.HOLDING -> "${(holdProgress * 100).toInt()}%"
                                            else -> "HOLD 2s"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White.copy(alpha = 0.95f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Batch F: Telemetry Status Cards (Phase 15 Battery & Phase 16 Connectivity)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Battery Intelligence Card
                val batteryBg = when (batteryInfo.batteryState) {
                    BatteryState.NORMAL -> Color(0xFFF0FDF4)
                    BatteryState.LOW -> Color(0xFFFFFBEB)
                    BatteryState.CRITICAL -> Color(0xFFFEF2F2)
                }
                val batteryTint = when (batteryInfo.batteryState) {
                    BatteryState.NORMAL -> Color(0xFF16A34A)
                    BatteryState.LOW -> Color(0xFFD97706)
                    BatteryState.CRITICAL -> Color(0xFFDC2626)
                }
                val batteryIcon = when {
                    batteryInfo.isCharging -> Icons.Default.BatteryChargingFull
                    batteryInfo.batteryState == BatteryState.CRITICAL -> Icons.Default.BatteryAlert
                    else -> Icons.Default.BatteryFull
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = batteryBg),
                    border = BorderStroke(1.dp, batteryTint.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = batteryIcon,
                                contentDescription = "Battery Status",
                                tint = batteryTint,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${batteryInfo.percentage ?: "--"}%",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = batteryTint
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (batteryInfo.operatingMode == OperatingMode.POWER_SAVING)
                                "Power Saving Mode"
                            else
                                "Battery ${batteryInfo.batteryState.name.lowercase().replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Connectivity Status Card
                val connBg = when (connectivityStatus.tier) {
                    ConnectivityTier.ONLINE -> Color(0xFFF0FDF4)
                    ConnectivityTier.NEARBY -> Color(0xFFEFF6FF)
                    ConnectivityTier.RELAYED -> Color(0xFFFAF5FF)
                    ConnectivityTier.OFFLINE -> Color(0xFFFFFBEB)
                    ConnectivityTier.UNKNOWN -> Color(0xFFF3F4F6)
                }
                val connTint = when (connectivityStatus.tier) {
                    ConnectivityTier.ONLINE -> Color(0xFF16A34A)
                    ConnectivityTier.NEARBY -> Color(0xFF2563EB)
                    ConnectivityTier.RELAYED -> Color(0xFF9333EA)
                    ConnectivityTier.OFFLINE -> Color(0xFFD97706)
                    ConnectivityTier.UNKNOWN -> Color.Gray
                }
                val connIcon = when (connectivityStatus.tier) {
                    ConnectivityTier.ONLINE -> Icons.Default.CloudDone
                    ConnectivityTier.NEARBY -> Icons.Default.Radar
                    ConnectivityTier.RELAYED -> Icons.Default.Wifi
                    ConnectivityTier.OFFLINE -> Icons.Default.CloudOff
                    ConnectivityTier.UNKNOWN -> Icons.Default.CloudOff
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = connBg),
                    border = BorderStroke(1.dp, connTint.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = connIcon,
                                contentDescription = "Connectivity Tier",
                                tint = connTint,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = connectivityStatus.tier.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = connTint
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (connectivityStatus.tier) {
                                ConnectivityTier.ONLINE -> "Internet Cloud OK"
                                ConnectivityTier.NEARBY -> "Direct BLE Peer"
                                ConnectivityTier.RELAYED -> "Mesh Relay Fresh"
                                ConnectivityTier.OFFLINE -> "BLE Radio Only"
                                ConnectivityTier.UNKNOWN -> "Scanning Mesh"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Essential Status Indicators (Child Friendly)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Zone Chip
                val zoneChipColor = when (geofenceState) {
                    com.example.model.GeofenceState.SAFE -> Color(0xFFECFDF5)
                    com.example.model.GeofenceState.APPROACHING -> Color(0xFFFEF3C7)
                    com.example.model.GeofenceState.EXIT_PENDING -> Color(0xFFFFF7ED)
                    com.example.model.GeofenceState.OUTSIDE -> Color(0xFFFEF2F2)
                    com.example.model.GeofenceState.REENTERED -> Color(0xFFECFDF5)
                }
                val zoneTextColor = when (geofenceState) {
                    com.example.model.GeofenceState.SAFE -> Color(0xFF059669)
                    com.example.model.GeofenceState.APPROACHING -> Color(0xFFD97706)
                    com.example.model.GeofenceState.EXIT_PENDING -> Color(0xFFEA580C)
                    com.example.model.GeofenceState.OUTSIDE -> Color(0xFFDC2626)
                    com.example.model.GeofenceState.REENTERED -> Color(0xFF047857)
                }
                val zoneLabel = when (geofenceState) {
                    com.example.model.GeofenceState.SAFE -> "Inside Zone"
                    com.example.model.GeofenceState.APPROACHING -> "Near Edge"
                    com.example.model.GeofenceState.EXIT_PENDING -> "Exit Pending"
                    com.example.model.GeofenceState.OUTSIDE -> "Outside Zone"
                    com.example.model.GeofenceState.REENTERED -> "Re-entered"
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = zoneChipColor,
                    border = BorderStroke(1.dp, zoneTextColor.copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = zoneTextColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = zoneLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = zoneTextColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Route Corridor Chip (if active)
                if (trustedRoute.isEnabled) {
                    val routeColor = when (routeState) {
                        com.example.model.RouteState.ON_ROUTE -> Color(0xFF10B981)
                        com.example.model.RouteState.APPROACHING_EDGE -> Color(0xFFF59E0B)
                        com.example.model.RouteState.ROUTE_DEVIATION -> Color(0xFFEF4444)
                        com.example.model.RouteState.UNKNOWN -> Color.Gray
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = routeColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, routeColor.copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = routeColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (routeState) {
                                    com.example.model.RouteState.ON_ROUTE -> "On Route"
                                    com.example.model.RouteState.APPROACHING_EDGE -> "Route Edge"
                                    com.example.model.RouteState.ROUTE_DEVIATION -> "Off Route"
                                    com.example.model.RouteState.UNKNOWN -> "No Route"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = routeColor,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Guard Band Chip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Band Active",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Verified Location line (Phase 6)
            Text(
                text = "📍 Location: ${verifiedLocation?.getRelativeTimeString() ?: "Verified recently"} • ${verifiedLocation?.confidence?.displayName ?: "High Confidence"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 6. Prototype & Simulation Controls (Demarcated Demo Mode)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isDemoControlsExpanded = !isDemoControlsExpanded },
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
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PROTOTYPE SENSOR SIMULATORS (DEMO MODE)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        IconButton(
                            onClick = { isDemoControlsExpanded = !isDemoControlsExpanded },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (isDemoControlsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Toggle Simulators",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    AnimatedVisibility(visible = isDemoControlsExpanded) {
                        Column {
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Simulate fall impact and geofence perimeter exit without physical movement:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.toggleSimulatedMotionAnomaly() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("simulate_motion_button"),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isMotionActive) Color(0xFFF59E0B) else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (isMotionActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Text(
                                        text = if (isMotionActive) "Stop Shake" else "Simulate Fall",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                Button(
                                    onClick = { viewModel.toggleSimulatedGeofenceExit() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("simulate_geofence_button"),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isOutsideSafeZone) Color(0xFFEF4444) else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (isOutsideSafeZone) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Text(
                                        text = if (isOutsideSafeZone) "Inside Zone" else "Simulate Exit",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Sensor Diagnostics row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Accelerometer: %.1f m/s² (%s)".format(
                                        accelMagnitude,
                                        if (isMotionActive) "Anomaly" else "Rest"
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Safe Zone: ${safeZone.radiusMeters.toInt()}m",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
