package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppMode
import com.example.ui.SafeBandViewModel
import com.example.ui.components.SafeBandAvatar
import com.example.ui.components.SafeBandCard
import com.example.ui.components.SafeBandMetricChip
import com.example.ui.components.SafeBandPrimaryButton
import com.example.ui.components.SafeBandSecondaryButton
import com.example.ui.components.SafeBandStatusBadge
import com.example.ui.components.StatusBadgeStyle
import com.example.ui.theme.SafeBandColor
import com.example.ui.theme.SafeBandShapes
import com.example.ui.theme.SafeBandSpacing

/**
 * ChildSettingsScreen (v3.6.0 Design Overhaul)
 * Protected child-mode settings and status viewer.
 * Exiting Child Mode requires guardian PIN authentication.
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
                        text = "Child Device Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("child_settings_back")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = SafeBandSpacing.lg)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(SafeBandSpacing.sm))

            // 1. CHILD PROFILE CARD
            SafeBandCard(
                modifier = Modifier.fillMaxWidth(),
                shape = SafeBandShapes.ExtraLarge,
                elevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(SafeBandSpacing.lg)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SafeBandAvatar(
                            name = childBio.childName.ifBlank { "Child" },
                            size = 52.dp,
                            badgeColor = if (isAdvertising) SafeBandColor.Success else SafeBandColor.Warning
                        )

                        Spacer(modifier = Modifier.width(SafeBandSpacing.md))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = childBio.childName.ifBlank { "Child Device" },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Node ID: $deviceId",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        SafeBandStatusBadge(
                            style = if (isAdvertising) StatusBadgeStyle.CONNECTED else StatusBadgeStyle.STALE,
                            label = if (isAdvertising) "Active" else "Standby"
                        )
                    }

                    Spacer(modifier = Modifier.height(SafeBandSpacing.lg))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SafeBandSpacing.sm)
                    ) {
                        SafeBandMetricChip(
                            icon = Icons.Default.BatteryFull,
                            value = "${batteryInfo.percentage ?: 88}%",
                            label = "Battery",
                            modifier = Modifier.weight(1f),
                            iconTint = SafeBandColor.Success
                        )
                        SafeBandMetricChip(
                            icon = Icons.Default.Radar,
                            value = "%.0fm".format(safeZone.radiusMeters),
                            label = "Safe Zone",
                            modifier = Modifier.weight(1f),
                            iconTint = SafeBandColor.PrimaryBrand
                        )
                        SafeBandMetricChip(
                            icon = Icons.Default.Watch,
                            value = "10 Hz",
                            label = "BLE Rate",
                            modifier = Modifier.weight(1f),
                            iconTint = SafeBandColor.SecondaryBrand
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(SafeBandSpacing.md))

            // 2. EMERGENCY HELPLINES CARD
            SafeBandCard(
                modifier = Modifier.fillMaxWidth(),
                shape = SafeBandShapes.Large
            ) {
                Column(modifier = Modifier.padding(SafeBandSpacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = SafeBandColor.Emergency,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(SafeBandSpacing.sm))
                        Text(
                            text = "Emergency Helplines",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(SafeBandSpacing.sm))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SafeBandSpacing.sm)
                    ) {
                        SafeBandSecondaryButton(
                            text = "Police (112)",
                            icon = Icons.Default.Emergency,
                            onClick = {
                                try {
                                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
                                    context.startActivity(dialIntent)
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.weight(1f)
                        )
                        SafeBandSecondaryButton(
                            text = "Childline (1098)",
                            icon = Icons.Default.Phone,
                            onClick = {
                                try {
                                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:1098"))
                                    context.startActivity(dialIntent)
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(SafeBandSpacing.lg))

            // 3. GUARDIAN UNLOCK ACTION
            SafeBandCard(
                modifier = Modifier.fillMaxWidth(),
                shape = SafeBandShapes.Large,
                borderColor = SafeBandColor.PrimaryBrand.copy(alpha = 0.3f)
            ) {
                Column(
                    modifier = Modifier.padding(SafeBandSpacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = SafeBandColor.PrimaryBrand,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(SafeBandSpacing.xs))
                    Text(
                        text = "Guardian Mode Locked",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "To exit Child Mode or configure settings, enter the Guardian Master PIN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    SafeBandPrimaryButton(
                        text = "Unlock with Guardian PIN",
                        icon = Icons.Default.Lock,
                        onClick = {
                            enteredPin = ""
                            pinErrorMessage = null
                            showUnlockDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("unlock_guardian_button")
                    )
                }
            }

            Spacer(modifier = Modifier.height(SafeBandSpacing.xxl))
        }

        // Guardian Master PIN Unlock Dialog
        if (showUnlockDialog) {
            AlertDialog(
                onDismissRequest = { showUnlockDialog = false },
                shape = SafeBandShapes.ExtraLarge,
                title = {
                    Text(
                        text = "Enter Guardian PIN",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(SafeBandSpacing.sm)) {
                        Text(
                            text = "Please enter your 6-digit Master PIN to exit Child Mode.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = enteredPin,
                            onValueChange = {
                                if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                    enteredPin = it
                                }
                            },
                            label = { Text("6-Digit PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            isError = pinErrorMessage != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("guardian_pin_input")
                        )
                        if (pinErrorMessage != null) {
                            Text(
                                text = pinErrorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = SafeBandColor.Emergency
                            )
                        }
                    }
                },
                confirmButton = {
                    SafeBandPrimaryButton(
                        text = "Unlock",
                        onClick = {
                            if (viewModel.unlockChildMode(enteredPin)) {
                                showUnlockDialog = false
                                onUnlockSuccess()
                            } else {
                                pinErrorMessage = "Incorrect PIN. Please try again."
                            }
                        }
                    )
                },
                dismissButton = {
                    TextButton(onClick = { showUnlockDialog = false }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    }
}
