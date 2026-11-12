package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderStrong
import com.example.ui.theme.GlassHazard
import com.example.ui.theme.GlassHazardBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.GlassSurfaceLowest
import com.example.ui.theme.GlassSuccess
import com.example.ui.theme.GlassSuccessBorder
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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
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
import androidx.compose.material.icons.filled.VerifiedUser
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
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.HardwarePrerequisiteBanner
import com.example.ui.components.SegmentedMeter
import com.example.ui.components.TactileKeycap
import com.example.ui.theme.CarbonChassis
import com.example.ui.theme.MilledSilver
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.ObsidianContainerLowest
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianEtchedBorder
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.PhosphorWhite

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
    val activeAuthorizations by viewModel.activeAuthorizations.collectAsState()
    val activeJourney by viewModel.activeJourney.collectAsState()

    var isDemoControlsExpanded by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var holdState by remember { mutableStateOf(SosHoldState.IDLE) }
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current

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
                        Column {
                            Text(
                                text = "SAFEBAND // BLE_P2P",
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = PhosphorWhite,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "$deviceId // BLE LINKED",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = ObsidianOutline,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                },
                actions = {
                    // Battery Segmented Meter
                    Row(
                        modifier = Modifier
                            .background(ObsidianContainerLowest, RoundedCornerShape(2.dp))
                            .border(1.dp, ObsidianEtchedBorder, RoundedCornerShape(2.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${batteryInfo.percentage ?: 88}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = PhosphorWhite
                        )
                        SegmentedMeter(
                            filledSegments = ((batteryInfo.percentage ?: 88) * 8) / 100,
                            totalSegments = 8,
                            segmentHeight = 8,
                            modifier = Modifier.width(36.dp)
                        )
                    }
                    IconButton(
                        onClick = onNavigateToHistory,
                        modifier = Modifier.testTag("history_button")
                    ) {
                        Icon(imageVector = Icons.Default.History, contentDescription = "Event Log", tint = MilledSilver)
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = MilledSilver)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CarbonChassis
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CarbonChassis)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

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
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            // 1. REASSURANCE GLASS CARD
            val isEmergency = currentRisk == RiskLevel.CRITICAL || currentRisk == RiskLevel.HIGH || currentRisk == RiskLevel.MEDIUM
            val isSosActive = activeFlags.contains(AlertFlag.MANUAL_SOS) || holdState == SosHoldState.ACTIVATED

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = if (isEmergency) GlassHazard else GlassSurface,
                borderColor = if (isEmergency) GlassHazardBorder else GlassBorder,
                padding = 12.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isEmergency) Color(0xFFFF5252) else Color(0xFF10B981))
                        )
                        Text(
                            text = if (isEmergency) "ALERT BROADCAST ACTIVE" else "GUARDIAN IN RANGE",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (isEmergency) Color(0xFFFF5252) else PhosphorWhite,
                            letterSpacing = 0.5.sp,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = "14m • RSSI -52 dBm",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MilledSilver,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }

            // Child Authorized Pickup Reassurance Badge
            if (activeAuthorizations.isNotEmpty()) {
                val auth = activeAuthorizations.first()
                Spacer(modifier = Modifier.height(8.dp))
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassSurfaceElevated,
                    borderColor = GlassBorderStrong,
                    padding = 10.dp
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.VerifiedUser, contentDescription = null, tint = PhosphorWhite, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "AUTHORIZED PICKUP: ${auth.authorizedPersonName.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = PhosphorWhite,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "RELATION: ${auth.relationship.uppercase()} • TOKEN: ${auth.verificationToken}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = MilledSilver
                            )
                        }
                    }
                }
            }

            // Child Supervised Journey Reassurance Badge
            if (activeJourney != null) {
                Spacer(modifier = Modifier.height(8.dp))
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassSurfaceElevated,
                    borderColor = GlassBorderStrong,
                    padding = 10.dp
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, tint = PhosphorWhite, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "SUPERVISED JOURNEY: HEADING TO ${activeJourney!!.destLabel.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = PhosphorWhite,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "SAFETY CORRIDOR ACTIVE • TELEMETRY SYNCED",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = MilledSilver
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. CENTER HERO: FROSTED GLASS SOS DIAL
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = if (isSosActive) GlassHazard else GlassSurface,
                borderColor = if (isSosActive) GlassHazardBorder else GlassBorder,
                padding = 16.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SAFEGUARD BEACON",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = ObsidianOutline,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (isSosActive) "● BROADCASTING" else "● ARMED",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (isSosActive) Color(0xFFFF5252) else Color(0xFF10B981),
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Knurled Dial Housing with Hold Progress Ring
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(190.dp)
                        .align(Alignment.CenterHorizontally)
                        .background(GlassSurfaceLowest, CircleShape)
                        .border(1.5.dp, if (isSosActive) GlassHazardBorder else GlassBorder, CircleShape)
                ) {
                    if (holdState == SosHoldState.HOLDING) {
                        CircularProgressIndicator(
                            progress = { holdProgress },
                            modifier = Modifier.size(186.dp),
                            color = PhosphorWhite,
                            strokeWidth = 4.dp,
                            trackColor = GlassSurfaceElevated
                        )
                    } else if (isSosActive) {
                        CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.size(186.dp),
                            color = Color(0xFFFF5252),
                            strokeWidth = 4.dp
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = if (isSosActive) PhosphorWhite else CarbonChassis,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .size(142.dp)
                            .border(1.5.dp, if (isSosActive) Color(0xFFFF5252) else GlassBorderStrong, CircleShape)
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
                                    tint = if (isSosActive) Color.Black else PhosphorWhite,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = when {
                                        isSosActive -> "ALARM\nACTIVE"
                                        holdState == SosHoldState.HOLDING -> "${(holdProgress * 100).toInt()}%"
                                        else -> "HOLD 2s\nFOR SOS"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 14.sp,
                                    color = if (isSosActive) Color.Black else PhosphorWhite
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "EMERGENCY BEACON TRANSMITTER // BLE 0x5AFE",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = ObsidianOutline,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Actions: Call Guardian & Sound Siren
            val guardianPhone = childProfile.primaryParentPhone
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassButton(
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
                GlassButton(
                    text = if (isSosActive) "Cancel Alarm" else "Sound Siren",
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    isDestructive = isSosActive,
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

            // False-Alarm Dispatch Intercept Tray (When Counting Down)
            if (isCountingDown) {
                Spacer(modifier = Modifier.height(10.dp))
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassHazard,
                    borderColor = GlassHazardBorder,
                    padding = 12.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DISPATCH COUNTDOWN",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF6B6B),
                            fontSize = 10.sp
                        )
                        Text(
                            text = "[ 00:00:%02d ]".format(countdownSeconds),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = PhosphorWhite
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    GlassButton(
                        text = "Abort / Cancel Alarm",
                        isDestructive = true,
                        onClick = { viewModel.cancelConfirmationCountdown() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Essential Status Strip (Frosted Glass)
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                padding = 12.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SAFE ZONE",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = ObsidianOutline
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isOutsideSafeZone) "Perimeter Breach" else "Inside Perimeter",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isOutsideSafeZone) Color(0xFFFF5252) else PhosphorWhite
                        )
                    }

                    Column {
                        Text(
                            text = "CONNECTIVITY",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = ObsidianOutline
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = connectivityStatus.tier.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = PhosphorWhite
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "LOCATION",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = ObsidianOutline
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = verifiedLocation?.confidence?.displayName ?: "High Accuracy",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = PhosphorWhite
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Collapsible Sensor Diagnostics & Simulators (Demo Mode)
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                padding = 12.dp
            ) {
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
                            tint = MilledSilver,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DIAGNOSTICS & SIMULATOR",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MilledSilver,
                            fontSize = 10.sp
                        )
                    }
                    Icon(
                        imageVector = if (isDemoControlsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MilledSilver,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = isDemoControlsExpanded) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.toggleSimulatedMotionAnomaly() },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("simulate_motion_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isMotionActive) Color(0xFFF59E0B) else GlassSurfaceElevated,
                                    contentColor = PhosphorWhite
                                )
                            ) {
                                Text(
                                    text = if (isMotionActive) "Stop Shake" else "Simulate Fall",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }
                            Button(
                                onClick = { viewModel.toggleSimulatedGeofenceExit() },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("simulate_geofence_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isOutsideSafeZone) Color(0xFFEF4444) else GlassSurfaceElevated,
                                    contentColor = PhosphorWhite
                                )
                            ) {
                                Text(
                                    text = if (isOutsideSafeZone) "Inside Zone" else "Simulate Exit",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
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
