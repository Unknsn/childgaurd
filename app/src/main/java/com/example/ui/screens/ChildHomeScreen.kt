package com.example.ui.screens

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AlertFlag
import com.example.model.RiskLevel
import com.example.ui.SafeBandViewModel
import com.example.ui.components.HardwarePrerequisiteBanner
import com.example.ui.components.SafeBandCard
import com.example.ui.components.SafeBandPrimaryButton
import com.example.ui.components.SafeBandSecondaryButton
import com.example.ui.components.SafeBandStatusBadge
import com.example.ui.theme.SafeBandColor
import com.example.ui.theme.SafeBandShapes
import com.example.ui.theme.SafeBandSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

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
    val isMotionActive by viewModel.motionDetector.isAnomalyActive.collectAsState()
    val isOutsideSafeZone by viewModel.locationHelper.isOutsideSafeZone.collectAsState()
    val verifiedLocation by viewModel.verifiedLocation.collectAsState()
    val deviceId by viewModel.deviceId.collectAsState()
    val childProfile by viewModel.childBioProfile.collectAsState()
    val batteryInfo by viewModel.batteryInfo.collectAsState()
    val connectivityStatus by viewModel.connectivityStatus.collectAsState()
    val activeAuthorizations by viewModel.activeAuthorizations.collectAsState()
    val activeJourney by viewModel.activeJourney.collectAsState()
    val discoveredNodes by viewModel.discoveredSafetyNodes.collectAsState()

    var isDemoControlsExpanded by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var holdState by remember { mutableStateOf(SosHoldState.IDLE) }
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current

    // Reassurance state
    val isEmergency = currentRisk == RiskLevel.CRITICAL || currentRisk == RiskLevel.HIGH || currentRisk == RiskLevel.MEDIUM
    val isSosActive = activeFlags.contains(AlertFlag.MANUAL_SOS) || holdState == SosHoldState.ACTIVATED
    val nearestNode = discoveredNodes.values.firstOrNull { it.isNearby }

    // Pulsing animation for SOS button
    val infiniteTransition = rememberInfiniteTransition(label = "SosPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SosScale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SafeBandSpacing.sm)
                    ) {
                        Surface(
                            shape = SafeBandShapes.medium,
                            color = if (isEmergency) SafeBandColor.EmergencyLight else SafeBandColor.PrimaryLight,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = if (isEmergency) SafeBandColor.Emergency else SafeBandColor.Primary,
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
                            Text(
                                text = "$deviceId • ${if (isAdvertising) "BLE Active" else "BLE Standby"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Battery Pill
                    Surface(
                        shape = SafeBandShapes.pill,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(end = SafeBandSpacing.xs)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if ((batteryInfo.percentage ?: 100) < 20) Icons.Default.BatteryAlert else Icons.Default.BatteryFull,
                                contentDescription = null,
                                tint = if ((batteryInfo.percentage ?: 100) < 20) SafeBandColor.Emergency else SafeBandColor.Success,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = batteryInfo.percentage?.let { "$it%" } ?: "95%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onNavigateToHistory,
                        modifier = Modifier.testTag("history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Event Log",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = SafeBandSpacing.lg)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(SafeBandSpacing.sm))

            // Hardware prerequisite banner
            val isBtOn by viewModel.isBluetoothHardwareEnabled.collectAsState()
            val isLocOn by viewModel.isLocationHardwareEnabled.collectAsState()

            if (!isBtOn || !isLocOn) {
                HardwarePrerequisiteBanner(
                    isBluetoothEnabled = isBtOn,
                    isLocationEnabled = isLocOn,
                    onEnableBluetooth = {
                        try { context.startActivity(viewModel.getEnableBluetoothIntent()) } catch (_: Exception) {}
                    },
                    onEnableLocation = {
                        try { context.startActivity(viewModel.getEnableLocationIntent()) } catch (_: Exception) {}
                    },
                    modifier = Modifier.padding(bottom = SafeBandSpacing.sm)
                )
            }

            // 1. Reassurance Hero Banner
            SafeBandCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = if (isEmergency) SafeBandColor.EmergencyLight else SafeBandColor.SuccessLight,
                border = BorderStroke(
                    1.dp,
                    if (isEmergency) SafeBandColor.Emergency.copy(alpha = 0.5f) else SafeBandColor.Success.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SafeBandSpacing.sm)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isEmergency) SafeBandColor.Emergency else SafeBandColor.Success)
                        )
                        Text(
                            text = when {
                                isEmergency -> "EMERGENCY BROADCAST ACTIVE"
                                nearestNode != null -> "GUARDIAN IN RANGE"
                                else -> "YOU ARE PROTECTED"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isEmergency) SafeBandColor.EmergencyDark else SafeBandColor.SuccessDark
                        )
                    }

                    SafeBandStatusBadge(
                        text = when {
                            nearestNode != null -> "Direct Link"
                            isEmergency -> "SOS Signal"
                            isAdvertising -> "Broadcasting"
                            else -> "Standby"
                        },
                        color = if (isEmergency) SafeBandColor.Emergency else SafeBandColor.Success
                    )
                }
            }

            // Authorized pickup card if active
            if (activeAuthorizations.isNotEmpty()) {
                val auth = activeAuthorizations.first()
                Spacer(modifier = Modifier.height(SafeBandSpacing.sm))
                SafeBandCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = SafeBandColor.PrimaryLight
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = SafeBandColor.Primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(SafeBandSpacing.sm))
                        Column {
                            Text(
                                text = "Authorized Pickup: ${auth.authorizedPersonName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = SafeBandColor.PrimaryDark
                            )
                            Text(
                                text = "Relation: ${auth.relationship} • Code: ${auth.verificationToken}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SafeBandColor.PrimaryDark.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Supervised journey card if active
            if (activeJourney != null) {
                Spacer(modifier = Modifier.height(SafeBandSpacing.sm))
                SafeBandCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = SafeBandColor.SecondaryLight
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = null,
                            tint = SafeBandColor.Secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(SafeBandSpacing.sm))
                        Column {
                            Text(
                                text = "Supervised Journey: Heading to ${activeJourney!!.destLabel}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = SafeBandColor.SecondaryDark
                            )
                            Text(
                                text = "Safety corridor active • Real-time telemetry synced",
                                style = MaterialTheme.typography.bodySmall,
                                color = SafeBandColor.SecondaryDark.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(SafeBandSpacing.lg))

            // 2. Center Hero: High-Visibility SOS Trigger
            SafeBandCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = if (isSosActive) SafeBandColor.EmergencyLight else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.5.dp,
                    if (isSosActive) SafeBandColor.Emergency else SafeBandColor.Slate200
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "EMERGENCY BEACON",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    SafeBandStatusBadge(
                        text = if (isSosActive) "BROADCASTING" else "ARMED & READY",
                        color = if (isSosActive) SafeBandColor.Emergency else SafeBandColor.Success
                    )
                }

                Spacer(modifier = Modifier.height(SafeBandSpacing.lg))

                // Circular Hold Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(190.dp)
                        .align(Alignment.CenterHorizontally)
                        .scale(if (isSosActive) pulseScale else 1f)
                ) {
                    // Outer track & progress indicator
                    if (holdState == SosHoldState.HOLDING) {
                        CircularProgressIndicator(
                            progress = { holdProgress },
                            modifier = Modifier.size(186.dp),
                            color = SafeBandColor.Emergency,
                            strokeWidth = 6.dp,
                            trackColor = SafeBandColor.Slate200
                        )
                    } else if (isSosActive) {
                        CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.size(186.dp),
                            color = SafeBandColor.Emergency,
                            strokeWidth = 6.dp
                        )
                    }

                    // Main circular button
                    Surface(
                        shape = CircleShape,
                        color = if (isSosActive) SafeBandColor.Emergency else SafeBandColor.Emergency,
                        shadowElevation = if (isSosActive) 8.dp else 4.dp,
                        modifier = Modifier
                            .size(150.dp)
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
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Hold 2s for SOS",
                                    tint = SafeBandColor.White,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = when {
                                        isSosActive -> "ALARM\nACTIVE"
                                        holdState == SosHoldState.HOLDING -> "${(holdProgress * 100).toInt()}%"
                                        else -> "HOLD 2s\nFOR SOS"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                    color = SafeBandColor.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(SafeBandSpacing.md))

                Text(
                    text = if (isSosActive) "Tap SOS button to cancel alarm" else "Press and hold for 2 seconds to alert parents",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            Spacer(modifier = Modifier.height(SafeBandSpacing.md))

            // Quick Call Actions
            val guardianPhone = childProfile.primaryParentPhone
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SafeBandSpacing.sm)
            ) {
                SafeBandPrimaryButton(
                    text = "Call Guardian",
                    icon = Icons.Default.Phone,
                    onClick = {
                        if (guardianPhone.isNotBlank()) {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$guardianPhone"))
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                SafeBandSecondaryButton(
                    text = if (isSosActive) "Cancel Siren" else "Sound Siren",
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    onClick = {
                        if (isSosActive) {
                            viewModel.cancelManualSos()
                        } else {
                            viewModel.triggerManualSos()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Indian Emergency Helplines quick buttons
            Spacer(modifier = Modifier.height(SafeBandSpacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SafeBandSpacing.sm)
            ) {
                SafeBandSecondaryButton(
                    text = "Police (112)",
                    icon = Icons.Default.Phone,
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f)
                )

                SafeBandSecondaryButton(
                    text = "Childline (1098)",
                    icon = Icons.Default.Phone,
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:1098"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Dispatch Countdown Tray (When Counting Down)
            if (isCountingDown) {
                Spacer(modifier = Modifier.height(SafeBandSpacing.md))
                SafeBandCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = SafeBandColor.EmergencyLight,
                    border = BorderStroke(1.5.dp, SafeBandColor.Emergency)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DISPATCH COUNTDOWN",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = SafeBandColor.EmergencyDark
                        )
                        Text(
                            text = "00:00:%02d".format(countdownSeconds),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = SafeBandColor.EmergencyDark
                        )
                    }
                    Spacer(modifier = Modifier.height(SafeBandSpacing.sm))
                    Button(
                        onClick = { viewModel.cancelConfirmationCountdown() },
                        colors = ButtonDefaults.buttonColors(containerColor = SafeBandColor.Emergency),
                        shape = SafeBandShapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel False Alarm", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(SafeBandSpacing.md))

            // Essential Status Strip
            SafeBandCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SAFE ZONE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isOutsideSafeZone) "Perimeter Breach" else "Inside Safe Zone",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isOutsideSafeZone) SafeBandColor.Emergency else SafeBandColor.Success
                        )
                    }

                    Column {
                        Text(
                            text = "CONNECTIVITY",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = connectivityStatus.tier.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "LOCATION",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = verifiedLocation?.confidence?.displayName ?: "High Accuracy",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Debug diagnostics simulator
            if (com.example.BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(SafeBandSpacing.md))

                SafeBandCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isDemoControlsExpanded = !isDemoControlsExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(SafeBandSpacing.sm))
                            Text(
                                text = "Diagnostics & Simulator (Debug)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (isDemoControlsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    AnimatedVisibility(visible = isDemoControlsExpanded) {
                        Column {
                            Spacer(modifier = Modifier.height(SafeBandSpacing.sm))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(SafeBandSpacing.sm)
                            ) {
                                SafeBandSecondaryButton(
                                    text = if (isMotionActive) "Stop Shake" else "Simulate Fall",
                                    onClick = { viewModel.toggleSimulatedMotionAnomaly() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("simulate_motion_button")
                                )

                                SafeBandSecondaryButton(
                                    text = if (isOutsideSafeZone) "Inside Zone" else "Simulate Exit",
                                    onClick = { viewModel.toggleSimulatedGeofenceExit() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("simulate_geofence_button")
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(SafeBandSpacing.xxl))
        }
    }
}
