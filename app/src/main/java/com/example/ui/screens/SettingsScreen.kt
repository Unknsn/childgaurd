package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppMode
import com.example.model.RiskLevel
import com.example.model.UpdateState
import com.example.ui.SafeBandViewModel
import com.example.ui.components.EditChildBioDialog
import com.example.ui.components.SafeBandCard
import com.example.ui.components.SafeBandPrimaryButton
import com.example.ui.components.SafeBandSecondaryButton
import com.example.ui.components.SafeBandSectionHeader
import com.example.ui.components.SafeBandStatusBadge
import com.example.ui.theme.SafeBandColor
import com.example.ui.theme.SafeBandShapes
import com.example.ui.theme.SafeBandSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SafeBandViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToUpdate: () -> Unit = {},
    onNavigateToPickup: () -> Unit = {},
    onNavigateToSchool: () -> Unit = {},
    onNavigateToJourney: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentMode by viewModel.appMode.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val activeChallenge by viewModel.activePairingChallenge.collectAsState()
    val deviceId by viewModel.deviceId.collectAsState()
    val bioProfile by viewModel.childBioProfile.collectAsState()
    val safetyMonitoringEnabled by viewModel.safetyMonitoringEnabled.collectAsState()
    var deviceIdInput by remember(deviceId) { mutableStateOf(deviceId) }
    var idSavedMessage by remember { mutableStateOf(false) }
    var showEditBioDialog by remember { mutableStateOf(false) }
    var isEmergencyProfileUnlocked by remember { mutableStateOf(false) }
    var showLockChildDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings & Configuration",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
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
                .padding(horizontal = SafeBandSpacing.lg, vertical = SafeBandSpacing.md)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Section: Protection Services
            SafeBandSectionHeader(
                title = "PROTECTION SERVICES",
                subtitle = "Active background safety monitoring & telemetry"
            )

            Spacer(modifier = Modifier.height(SafeBandSpacing.sm))

            // Always-On Safety Monitoring Setting Card
            SafeBandCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("safety_monitoring_card"),
                border = BorderStroke(
                    1.dp,
                    if (safetyMonitoringEnabled) SafeBandColor.Success.copy(alpha = 0.4f) else SafeBandColor.Emergency.copy(alpha = 0.4f)
                )
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
                        Surface(
                            shape = SafeBandShapes.medium,
                            color = if (safetyMonitoringEnabled) SafeBandColor.SuccessLight else SafeBandColor.EmergencyLight,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = if (safetyMonitoringEnabled) SafeBandColor.Success else SafeBandColor.Emergency,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(SafeBandSpacing.md))
                        Column {
                            Text(
                                text = "Safety Monitoring",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            SafeBandStatusBadge(
                                text = if (safetyMonitoringEnabled) "ACTIVE BACKGROUND" else "MONITORING DISABLED",
                                color = if (safetyMonitoringEnabled) SafeBandColor.Success else SafeBandColor.Emergency
                            )
                        }
                    }

                    Switch(
                        checked = safetyMonitoringEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.setSafetyMonitoringEnabled(enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SafeBandColor.White,
                            checkedTrackColor = SafeBandColor.Primary,
                            uncheckedThumbColor = SafeBandColor.Slate400,
                            uncheckedTrackColor = SafeBandColor.Slate200
                        ),
                        modifier = Modifier.testTag("safety_monitoring_switch")
                    )
                }

                Spacer(modifier = Modifier.height(SafeBandSpacing.sm))

                Text(
                    text = if (safetyMonitoringEnabled) {
                        "SafeBand Guardian runs continuously in the background to detect child wearable SOS beacons even when the phone is locked."
                    } else {
                        "⚠️ WARNING: Background monitoring is turned off. Child emergency beacons will not be caught while the app is closed."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (safetyMonitoringEnabled) MaterialTheme.colorScheme.onSurfaceVariant else SafeBandColor.Emergency
                )
            }

            Spacer(modifier = Modifier.height(SafeBandSpacing.lg))

            // Section: Identity & Pairing
            SafeBandSectionHeader(
                title = "GUARDIAN & PAIRING",
                subtitle = "Manage parent credentials and linked child devices"
            )

            Spacer(modifier = Modifier.height(SafeBandSpacing.sm))

            // 1. Parent Account Management Card
            SafeBandCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("parent_account_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = SafeBandShapes.medium,
                            color = SafeBandColor.PrimaryLight,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = SafeBandColor.Primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(SafeBandSpacing.md))
                        Column {
                            Text(
                                text = "Parent Account",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = currentUser?.displayName ?: "Verified Guardian",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    SafeBandStatusBadge(
                        text = currentUser?.role?.name ?: "PRIMARY_PARENT",
                        color = SafeBandColor.Primary
                    )
                }

                Spacer(modifier = Modifier.height(SafeBandSpacing.xs))

                Text(
                    text = currentUser?.email ?: "Offline Local Guardian Session",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(SafeBandSpacing.md))

                SafeBandSecondaryButton(
                    text = "Sign Out Guardian Account",
                    icon = Icons.AutoMirrored.Filled.Logout,
                    onClick = {
                        viewModel.signOutParent()
                        onNavigateBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("parent_sign_out_button")
                )
            }

            Spacer(modifier = Modifier.height(SafeBandSpacing.md))

            // 2. Child Device Provisioning & 6-Digit Code Generator Card
            SafeBandCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("provisioning_generator_card")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = SafeBandShapes.medium,
                        color = SafeBandColor.SecondaryLight,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = null,
                                tint = SafeBandColor.Secondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(SafeBandSpacing.md))
                    Column {
                        Text(
                            text = "Pair Child Device",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "15-minute cryptographic pairing challenge",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(SafeBandSpacing.sm))

                Text(
                    text = "Generate a single-use 6-digit code to securely link a child's phone or band to this guardian account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(SafeBandSpacing.md))

                if (activeChallenge != null && activeChallenge?.isExpired != true) {
                    Surface(
                        shape = SafeBandShapes.large,
                        color = SafeBandColor.PrimaryLight.copy(alpha = 0.5f),
                        border = BorderStroke(1.5.dp, SafeBandColor.Primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(SafeBandSpacing.md),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "ACTIVE PAIRING CHALLENGE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = SafeBandColor.Primary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(SafeBandSpacing.xs))
                            Text(
                                text = activeChallenge?.oneTimeCode ?: "------",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 8.sp,
                                color = SafeBandColor.PrimaryDark
                            )
                            Spacer(modifier = Modifier.height(SafeBandSpacing.xs))
                            Text(
                                text = "Expires in 15 minutes • Single use only",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(SafeBandSpacing.sm))
                            SafeBandSecondaryButton(
                                text = "Copy Pairing Code",
                                icon = Icons.Default.ContentCopy,
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("SafeBand Pairing Code", activeChallenge?.oneTimeCode)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Pairing code copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                } else {
                    SafeBandPrimaryButton(
                        text = "Generate 6-Digit Pairing Code",
                        icon = Icons.Default.Key,
                        onClick = { viewModel.generatePairingChallenge(deviceId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("generate_pairing_code_button")
                    )
                }
            }

            Spacer(modifier = Modifier.height(SafeBandSpacing.md))

            // 3. Child Mode Lockdown Card
            SafeBandCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("child_lockdown_card")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = SafeBandShapes.medium,
                        color = SafeBandColor.WarningLight,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = SafeBandColor.Warning,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(SafeBandSpacing.md))
                    Column {
                        Text(
                            text = "Lock into Child Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Strict device lockdown with PIN safeguard",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(SafeBandSpacing.sm))

                Text(
                    text = "Transfer this phone into locked Child Mode. The app will broadcast safety beacons and restrict navigation. Exiting requires your 6-digit guardian PIN.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(SafeBandSpacing.md))

                SafeBandSecondaryButton(
                    text = "Lock Device into Child Mode",
                    icon = Icons.Default.Lock,
                    onClick = { showLockChildDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lock_into_child_mode_button")
                )
            }

            Spacer(modifier = Modifier.height(SafeBandSpacing.lg))

            // Section: Hardware & Medical
            SafeBandSectionHeader(
                title = "HARDWARE & EMERGENCY PROFILE",
                subtitle = "BLE beacon configuration and medical safety profile"
            )

            Spacer(modifier = Modifier.height(SafeBandSpacing.sm))

            // Device Identifier Configuration Card
            SafeBandCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = SafeBandShapes.medium,
                        color = SafeBandColor.Slate100,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Devices,
                                contentDescription = null,
                                tint = SafeBandColor.Slate700,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(SafeBandSpacing.md))
                    Column {
                        Text(
                            text = "Child Device Identifier",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Used in BLE advertising beacon payload (max 7 chars)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(SafeBandSpacing.md))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = deviceIdInput,
                        onValueChange = {
                            if (it.length <= 7) {
                                deviceIdInput = it.uppercase()
                                idSavedMessage = false
                            }
                        },
                        singleLine = true,
                        shape = SafeBandShapes.medium,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("device_id_input")
                    )

                    Spacer(modifier = Modifier.width(SafeBandSpacing.sm))

                    Button(
                        onClick = {
                            viewModel.setCustomDeviceId(deviceIdInput.ifEmpty { "SB-0001" })
                            idSavedMessage = true
                        },
                        shape = SafeBandShapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = SafeBandColor.Primary),
                        modifier = Modifier.testTag("save_device_id_button")
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }

                if (idSavedMessage) {
                    Spacer(modifier = Modifier.height(SafeBandSpacing.xs))
                    Text(
                        text = "✓ Device ID updated successfully",
                        style = MaterialTheme.typography.labelSmall,
                        color = SafeBandColor.Success,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(SafeBandSpacing.md))

            // Child Bio & Medical Data Profile Card
            SafeBandCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("child_bio_settings_card")
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
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MedicalServices,
                                    contentDescription = null,
                                    tint = SafeBandColor.Emergency,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(SafeBandSpacing.md))
                        Column {
                            Text(
                                text = "Emergency Medical Profile",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Blood type, allergies & conditions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    SafeBandStatusBadge(
                        text = if (isEmergencyProfileUnlocked) "🩸 ${bioProfile.bloodType}" else "🔒 Protected",
                        color = if (isEmergencyProfileUnlocked) SafeBandColor.Emergency else SafeBandColor.Primary
                    )
                }

                Spacer(modifier = Modifier.height(SafeBandSpacing.sm))

                if (!isEmergencyProfileUnlocked) {
                    Text(
                        text = "Medical information is protected. Details (blood type, allergies, conditions) are restricted to authorized emergency contexts and never broadcast over unencrypted BLE.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(SafeBandSpacing.md))

                    SafeBandSecondaryButton(
                        text = "View Emergency Profile",
                        icon = Icons.Default.Lock,
                        onClick = { isEmergencyProfileUnlocked = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("view_emergency_profile_button")
                    )
                } else {
                    Surface(
                        shape = SafeBandShapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(SafeBandSpacing.md)) {
                            Text(
                                text = "Child: ${bioProfile.childName} (Age: ${bioProfile.age})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Parent Phone: ${bioProfile.primaryParentPhone}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (bioProfile.secondaryContactPhone.isNotBlank()) {
                                Text(
                                    text = "Secondary Phone: ${bioProfile.secondaryContactPhone}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "Conditions: ${bioProfile.medicalConditions.ifEmpty { "None recorded" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Allergies: ${bioProfile.allergies.ifEmpty { "None recorded" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(SafeBandSpacing.md))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SafeBandSpacing.sm)
                    ) {
                        SafeBandPrimaryButton(
                            text = "Edit Profile",
                            icon = Icons.Default.Edit,
                            onClick = { showEditBioDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("edit_bio_profile_button")
                        )

                        SafeBandSecondaryButton(
                            text = "Hide",
                            icon = Icons.Default.VisibilityOff,
                            onClick = { isEmergencyProfileUnlocked = false },
                            modifier = Modifier.weight(0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(SafeBandSpacing.lg))

            // Section: Diagnostics & Hardware Testing
            SafeBandSectionHeader(
                title = "DIAGNOSTICS & HARDWARE TESTING",
                subtitle = "Hardware buzzer, haptic vibrator and audio sirens"
            )

            Spacer(modifier = Modifier.height(SafeBandSpacing.sm))

            // Siren & Vibrator Test Card
            SafeBandCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = SafeBandShapes.medium,
                        color = SafeBandColor.WarningLight,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = SafeBandColor.Warning,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(SafeBandSpacing.md))
                    Column {
                        Text(
                            text = "Alarm & Siren Diagnostics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Verify hardware speaker and haptic patterns",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(SafeBandSpacing.md))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SafeBandSpacing.sm)
                ) {
                    SafeBandSecondaryButton(
                        text = "Test Warning",
                        onClick = { viewModel.alertNotifier.startAlert(RiskLevel.MEDIUM) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_warning_alarm_button")
                    )

                    Button(
                        onClick = { viewModel.alertNotifier.startAlert(RiskLevel.HIGH) },
                        shape = SafeBandShapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = SafeBandColor.Emergency),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_emergency_alarm_button")
                    ) {
                        Text("Test Siren", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(SafeBandSpacing.sm))

                SafeBandSecondaryButton(
                    text = "Silence Alarm",
                    onClick = { viewModel.alertNotifier.stopAlert() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stop_test_alarm_button")
                )
            }

            Spacer(modifier = Modifier.height(SafeBandSpacing.lg))

            // Section: Institutional Modules & Updates
            SafeBandSectionHeader(
                title = "MODULES & UPDATES",
                subtitle = "Institutional handovers, routes and app versions"
            )

            Spacer(modifier = Modifier.height(SafeBandSpacing.sm))

            // Application Updates Card
            val updateInfo by viewModel.updateInfo.collectAsState()
            SafeBandCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_updates_card"),
                border = BorderStroke(
                    1.dp,
                    if (updateInfo.state == UpdateState.UPDATE_AVAILABLE) SafeBandColor.Primary else MaterialTheme.colorScheme.outlineVariant
                )
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
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = SafeBandColor.Primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(SafeBandSpacing.md))
                        Column {
                            Text(
                                text = "Application Updates",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Current: v${updateInfo.currentVersionName} (Build ${updateInfo.currentVersionCode})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (updateInfo.state == UpdateState.UPDATE_AVAILABLE) {
                        SafeBandStatusBadge(
                            text = "NEW VERSION",
                            color = SafeBandColor.Primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(SafeBandSpacing.md))

                SafeBandPrimaryButton(
                    text = if (updateInfo.state == UpdateState.UPDATE_AVAILABLE) "View & Install Update" else "Check for Updates",
                    icon = Icons.Default.SystemUpdate,
                    onClick = onNavigateToUpdate,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(SafeBandSpacing.md))

            // Section: Laptop Backend & Secure Tunnel (v4.0.0)
            val backendBaseUrl by viewModel.backendBaseUrl.collectAsState()
            val backendHealth by viewModel.backendHealth.collectAsState()
            var editingUrl by remember(backendBaseUrl) { mutableStateOf(backendBaseUrl) }
            var isEditingBackendUrl by remember { mutableStateOf(false) }

            SafeBandCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_backend_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = SafeBandShapes.medium,
                            color = SafeBandColor.PrimaryLight,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CloudQueue,
                                    contentDescription = null,
                                    tint = SafeBandColor.Primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(SafeBandSpacing.md))
                        Column {
                            Text(
                                text = "Backend & Cloud Tunnel",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Remote tracking & relay synchronization",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val badgeColor = when {
                        backendHealth.startsWith("HEALTHY") -> SafeBandColor.Success
                        backendHealth == "CHECKING..." -> SafeBandColor.Warning
                        backendHealth == "UNREACHABLE" -> SafeBandColor.Emergency
                        else -> SafeBandColor.Slate400
                    }
                    SafeBandStatusBadge(
                        text = backendHealth,
                        color = badgeColor
                    )
                }

                Spacer(modifier = Modifier.height(SafeBandSpacing.md))

                if (isEditingBackendUrl) {
                    OutlinedTextField(
                        value = editingUrl,
                        onValueChange = { editingUrl = it },
                        label = { Text("Backend / Cloudflare Tunnel URL") },
                        placeholder = { Text("https://xxxx.trycloudflare.com or http://10.0.2.2:8080") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = SafeBandShapes.medium
                    )
                    Spacer(modifier = Modifier.height(SafeBandSpacing.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = {
                            editingUrl = backendBaseUrl
                            isEditingBackendUrl = false
                        }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(SafeBandSpacing.sm))
                        Button(
                            onClick = {
                                viewModel.updateBackendBaseUrl(editingUrl)
                                isEditingBackendUrl = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SafeBandColor.PrimaryBrand)
                        ) {
                            Text("Save & Connect")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Active Endpoint:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = backendBaseUrl,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { isEditingBackendUrl = true }) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit URL", tint = SafeBandColor.Primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(SafeBandSpacing.sm))

                SafeBandSecondaryButton(
                    text = "Test Backend Connectivity",
                    icon = Icons.Default.Refresh,
                    onClick = { viewModel.testBackendHealth() },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(SafeBandSpacing.md))

            // Escalation Hierarchy Card
            val escalationConfig by viewModel.escalationConfig.collectAsState()
            var t1Delay by remember(escalationConfig) { mutableIntStateOf(escalationConfig.tier1DelaySeconds) }
            var t2Delay by remember(escalationConfig) { mutableIntStateOf(escalationConfig.tier2DelaySeconds) }
            SafeBandCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_escalation_card")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = SafeBandShapes.medium,
                        color = SafeBandColor.EmergencyLight,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Emergency,
                                contentDescription = null,
                                tint = SafeBandColor.Emergency,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(SafeBandSpacing.md))
                    Column {
                        Text(
                            text = "Escalation Hierarchy",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tier 1: Parent ➜ Tier 2: Guardians (${t1Delay}s) ➜ Tier 3: Helplines (${t2Delay}s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(SafeBandSpacing.md))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SafeBandSpacing.sm)
                ) {
                    SafeBandSecondaryButton(
                        text = "T1 Delay: ${t1Delay}s",
                        onClick = {
                            t1Delay = if (t1Delay == 30) 60 else 30
                            viewModel.saveEscalationConfig(escalationConfig.copy(tier1DelaySeconds = t1Delay))
                        },
                        modifier = Modifier.weight(1f)
                    )
                    SafeBandSecondaryButton(
                        text = "T2 Delay: ${t2Delay}s",
                        onClick = {
                            t2Delay = if (t2Delay == 60) 120 else 60
                            viewModel.saveEscalationConfig(escalationConfig.copy(tier2DelaySeconds = t2Delay))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(SafeBandSpacing.md))

            // Quick Access Modules Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SafeBandSpacing.sm)
            ) {
                SafeBandCard(
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = SafeBandColor.Primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(SafeBandSpacing.xs))
                    Text(
                        text = "Pickup Pass",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Authorized handovers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(SafeBandSpacing.sm))
                    SafeBandSecondaryButton(
                        text = "Open",
                        onClick = onNavigateToPickup,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SafeBandCard(
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = SafeBandColor.Primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(SafeBandSpacing.xs))
                    Text(
                        text = "School Zone",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Arrival & departure",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(SafeBandSpacing.sm))
                    SafeBandSecondaryButton(
                        text = "Open",
                        onClick = onNavigateToSchool,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(SafeBandSpacing.sm))

            SafeBandCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = SafeBandShapes.medium,
                            color = SafeBandColor.SecondaryLight,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                                    contentDescription = null,
                                    tint = SafeBandColor.Secondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(SafeBandSpacing.md))
                        Column {
                            Text(
                                text = "Supervised Journey Mode",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Transit safety corridor monitoring",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    SafeBandSecondaryButton(
                        text = "Open",
                        onClick = onNavigateToJourney
                    )
                }
            }

            Spacer(modifier = Modifier.height(SafeBandSpacing.lg))

            // About SafeBand & Privacy Card
            SafeBandCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = SafeBandShapes.medium,
                            color = SafeBandColor.PrimaryLight,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = SafeBandColor.Primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(SafeBandSpacing.md))
                        Column {
                            Text(
                                text = "SafeBand Guardian",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Production Release v3.6.0",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    SafeBandStatusBadge(
                        text = "v3.6.0",
                        color = SafeBandColor.Primary
                    )
                }

                Spacer(modifier = Modifier.height(SafeBandSpacing.sm))

                Text(
                    text = "• Built with Jetpack Compose & Material 3\n• Offline-first Room SQLite Schema v6 & DataStore\n• Keystore-backed AES-256-GCM authenticated commands\n• Direct 2.4 GHz Bluetooth Low Energy signaling (0x5AFE)\n• GPIO3/GPIO4 hardware strap tamper monitoring\n• Indian Emergency Helplines (Police 112 & Childline 1098)\n• Zero internet tracking or central surveillance",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(SafeBandSpacing.xxl))
        }

        if (showEditBioDialog) {
            EditChildBioDialog(
                initialProfile = bioProfile,
                onDismiss = { showEditBioDialog = false },
                onSave = { updated ->
                    showEditBioDialog = false
                    viewModel.updateChildBioProfile(updated)
                }
            )
        }

        if (showLockChildDialog) {
            AlertDialog(
                onDismissRequest = { showLockChildDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = SafeBandColor.Primary
                    )
                },
                title = {
                    Text(
                        text = "Lock into Child Mode",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to lock this device into Child Mode? It will broadcast BLE beacons and restrict access to guardian settings until unlocked with your guardian PIN.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showLockChildDialog = false
                            viewModel.lockIntoChildMode(deviceId)
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SafeBandColor.Primary)
                    ) {
                        Text("Confirm & Lock Device")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLockChildDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
