package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.SafeBandViewModel
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.theme.CarbonChassis
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderStrong
import com.example.ui.theme.GlassHazard
import com.example.ui.theme.GlassHazardBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.GlassSurfaceLowest
import com.example.ui.theme.MilledSilver
import com.example.ui.theme.ObsidianEtchedBorder
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.PhosphorWhite

/**
 * ChildSettingsScreen (v3.3)
 * Replaces generic settings when operating in Child Mode.
 * Strictly prevents accidental or unauthorized mode switching:
 * - Displays child device status, linked wearable telemetry, and emergency contacts.
 * - Mode transition requires 6-digit Guardian authorization PIN or master pairing challenge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildSettingsScreen(
    viewModel: SafeBandViewModel,
    onNavigateBack: () -> Unit,
    onUnlockSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val childBio by viewModel.childBioProfile.collectAsState()
    val deviceId by viewModel.deviceId.collectAsState()
    val batteryInfo by viewModel.batteryInfo.collectAsState()
    val isAdvertising by viewModel.bleManager.isAdvertising.collectAsState()
    val safeZone by viewModel.safeZone.collectAsState()
    val context = LocalContext.current

    var showUnlockDialog by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }
    var pinErrorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "CHILD PROTECTION STATUS",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = PhosphorWhite,
                        letterSpacing = 1.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("child_settings_back")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PhosphorWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CarbonChassis)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CarbonChassis)
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. CHILD PROFILE CARD
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = GlassSurface,
                borderColor = GlassBorderStrong,
                padding = 16.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = GlassSurfaceLowest,
                        modifier = Modifier
                            .size(52.dp)
                            .border(1.5.dp, PhosphorWhite, RoundedCornerShape(8.dp))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = PhosphorWhite,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = childBio.childName.ifBlank { "Child Device" }.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = PhosphorWhite,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "NODE ID: $deviceId",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = ObsidianOutline,
                            fontSize = 10.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, Color(0xFF10B981))
                    ) {
                        Text(
                            text = if (isAdvertising) "● BROADCASTING" else "● STANDBY",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            fontSize = 8.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("BATTERY LEVEL", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = ObsidianOutline)
                        Text("${batteryInfo.percentage ?: 88}%", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = PhosphorWhite)
                    }
                    Column {
                        Text("SAFE ZONE RADIUS", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = ObsidianOutline)
                        Text("%.0fm".format(safeZone.radiusMeters), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = PhosphorWhite)
                    }
                    Column {
                        Text("BLE FREQUENCY", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = ObsidianOutline)
                        Text("10 Hz Fast", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = PhosphorWhite)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. EMERGENCY HELPLINES CARD
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = GlassSurfaceLowest,
                borderColor = GlassBorder,
                padding = 14.dp
            ) {
                Text(
                    text = "NATIONAL EMERGENCY HELPLINES",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = ObsidianOutline,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassButton(
                        text = "Call 112 (SOS)",
                        icon = Icons.Default.Emergency,
                        isDestructive = true,
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = "Call 1098 (Childline)",
                        icon = Icons.Default.Phone,
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:1098"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. CHILD PROTECTION LOCKDOWN BANNER
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = GlassSurfaceElevated,
                borderColor = GlassBorderStrong,
                padding = 16.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GlassSurfaceLowest)
                            .border(1.dp, PhosphorWhite, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = PhosphorWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "DEVICE LOCKED IN CHILD MODE",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = PhosphorWhite,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Unrestricted mode switching is disabled.",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MilledSilver,
                            fontSize = 9.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "This protection ensures the device continuously monitors safe-zone boundaries and broadcasts emergency beacons. To exit or reconfigure, your parent or guardian must enter their 6-digit authorization code.",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MilledSilver,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                GlassButton(
                    text = "Guardian Authorization / Unlock",
                    icon = Icons.Default.LockOpen,
                    onClick = {
                        enteredPin = ""
                        pinErrorMessage = null
                        showUnlockDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("child_unlock_button")
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Unlock PIN Dialog
        if (showUnlockDialog) {
            AlertDialog(
                onDismissRequest = { showUnlockDialog = false },
                containerColor = CarbonChassis,
                title = {
                    Text(
                        text = "GUARDIAN AUTHORIZATION",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = PhosphorWhite
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Enter parent authorization PIN or active 6-digit pairing code to unlock this device.",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MilledSilver,
                            fontSize = 11.sp
                        )

                        OutlinedTextField(
                            value = enteredPin,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() }.take(6)
                                enteredPin = filtered
                            },
                            label = { Text("6-Digit Code", fontFamily = FontFamily.Monospace) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 4.sp,
                                textAlign = TextAlign.Center
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PhosphorWhite,
                                unfocusedBorderColor = GlassBorder,
                                focusedTextColor = PhosphorWhite,
                                unfocusedTextColor = PhosphorWhite
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("guardian_pin_input")
                        )

                        pinErrorMessage?.let { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFFF5252),
                                fontSize = 10.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    GlassButton(
                        text = "Authorize & Unlock",
                        enabled = enteredPin.length == 6,
                        onClick = {
                            val success = viewModel.unlockChildMode(enteredPin)
                            if (success) {
                                showUnlockDialog = false
                                onUnlockSuccess()
                            } else {
                                pinErrorMessage = "Invalid authorization PIN. Child protection remains active."
                            }
                        }
                    )
                },
                dismissButton = {
                    TextButton(onClick = { showUnlockDialog = false }) {
                        Text("Cancel", color = MilledSilver, fontFamily = FontFamily.Monospace)
                    }
                }
            )
        }
    }
}
