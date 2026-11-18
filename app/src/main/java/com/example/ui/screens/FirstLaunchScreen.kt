package com.example.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.auth.AuthStatus
import com.example.model.AppMode
import com.example.ui.SafeBandViewModel
import com.example.ui.components.SafeBandCard
import com.example.ui.components.SafeBandPrimaryButton
import com.example.ui.components.SafeBandSecondaryButton
import com.example.ui.components.SafeBandTextField
import com.example.ui.theme.SafeBandColor
import com.example.ui.theme.SafeBandShapes
import com.example.ui.theme.SafeBandSpacing

/**
 * FirstLaunchScreen (v3.6.0 Design Overhaul)
 * Premium onboarding interface enforcing intentional role separation.
 * - Parent experience: Cloud login or 100% offline guardian profile.
 * - Child experience: Parent-authorized 6-digit cryptographic pairing.
 */
@Composable
fun FirstLaunchScreen(
    viewModel: SafeBandViewModel,
    onNavigateToChildProvisioning: () -> Unit,
    modifier: Modifier = Modifier
) {
    val authStatus by viewModel.authStatus.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    var showOfflineParentDialog by remember { mutableStateOf(false) }
    var offlineParentName by remember { mutableStateOf("") }
    var offlineParentEmail by remember { mutableStateOf("") }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    var isSigningIn by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = SafeBandSpacing.lg)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(SafeBandSpacing.xxxl))

            // App Emblem
            Surface(
                shape = CircleShape,
                color = SafeBandColor.PrimaryBrand.copy(alpha = 0.12f),
                border = BorderStroke(1.5.dp, SafeBandColor.PrimaryBrand.copy(alpha = 0.35f)),
                modifier = Modifier.size(76.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "SafeBand Guardian",
                        tint = SafeBandColor.PrimaryBrand,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(SafeBandSpacing.md))

            Text(
                text = "SafeBand Guardian",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Community-Powered Child Safety & Emergency Relay",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = SafeBandSpacing.xs)
            )

            Text(
                text = "Decentralized BLE safety beacons, geofence corridors, and offline emergency protection.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = SafeBandSpacing.xs, start = 16.dp, end = 16.dp)
            )

            Spacer(modifier = Modifier.height(SafeBandSpacing.xxl))

            // Signing in progress banner
            if (isSigningIn) {
                SafeBandCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(SafeBandSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = SafeBandColor.PrimaryBrand,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(SafeBandSpacing.md))
                        Text(
                            text = "Authenticating Parent Account...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(SafeBandSpacing.md))
            }

            // Authentication Error Banner
            authErrorMessage?.let { err ->
                SafeBandCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = SafeBandColor.EmergencySurfaceLight,
                    borderColor = SafeBandColor.EmergencyBorder
                ) {
                    Text(
                        text = "Authentication Notice: $err",
                        style = MaterialTheme.typography.bodySmall,
                        color = SafeBandColor.EmergencyDark,
                        modifier = Modifier.padding(SafeBandSpacing.md)
                    )
                }
                Spacer(modifier = Modifier.height(SafeBandSpacing.md))
            }

            // Section Label
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = SafeBandSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SELECT DEVICE ROLE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }

            // OPTION 1: PARENT / GUARDIAN SETUP
            SafeBandCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_parent_card"),
                shape = SafeBandShapes.ExtraLarge,
                elevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(SafeBandSpacing.lg)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = SafeBandColor.PrimaryBrand.copy(alpha = 0.12f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FamilyRestroom,
                                    contentDescription = null,
                                    tint = SafeBandColor.PrimaryBrand,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(SafeBandSpacing.md))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Parent & Guardian",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Monitor child bands, configure safe zones, issue pairing codes, and respond to alerts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(SafeBandSpacing.md))

                    // Primary Action: Google Sign-In
                    SafeBandPrimaryButton(
                        text = "Sign in with Google",
                        icon = Icons.Default.Person,
                        onClick = {
                            isSigningIn = true
                            authErrorMessage = null
                            viewModel.signInWithGoogle { result ->
                                isSigningIn = false
                                result.onFailure { exc ->
                                    authErrorMessage = exc.localizedMessage ?: "Google Sign-In cancelled or unavailable. Use Offline Parent mode."
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("google_sign_in_button")
                    )

                    Spacer(modifier = Modifier.height(SafeBandSpacing.xs))

                    // Secondary Action: Offline Parent Setup
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = { showOfflineParentDialog = true },
                            modifier = Modifier.testTag("offline_parent_button")
                        ) {
                            Text(
                                text = "Set up as Offline Parent (No Account Required)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = SafeBandColor.PrimaryBrand
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(SafeBandSpacing.md))

            // OPTION 2: CHILD DEVICE SETUP
            SafeBandCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_child_card"),
                shape = SafeBandShapes.ExtraLarge,
                elevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(SafeBandSpacing.lg)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = SafeBandColor.Success.copy(alpha = 0.12f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Watch,
                                    contentDescription = null,
                                    tint = SafeBandColor.SuccessDark,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(SafeBandSpacing.md))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Child Device",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Transmits safety beacons, checks geofences, and provides simple emergency SOS activation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(SafeBandSpacing.md))

                    SafeBandSecondaryButton(
                        text = "Pair with Parent (6-Digit Code)",
                        icon = Icons.Default.QrCodeScanner,
                        onClick = onNavigateToChildProvisioning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("enter_child_code_button")
                    )
                }
            }

            // Quick Evaluator Demo (Debug Only)
            if (com.example.BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(SafeBandSpacing.md))

                SafeBandCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(SafeBandSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Quick Evaluation Mode",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Direct sandbox testing without pairing PIN.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(SafeBandSpacing.xs)) {
                            TextButton(onClick = { viewModel.lockIntoChildMode("EP-C0D7") }) {
                                Text("Child Demo", style = MaterialTheme.typography.labelSmall, color = SafeBandColor.PrimaryBrand)
                            }
                            TextButton(onClick = {
                                viewModel.signInAsGuestParent("Demo Parent", "parent@safeband.local")
                            }) {
                                Text("Parent Demo", style = MaterialTheme.typography.labelSmall, color = SafeBandColor.PrimaryBrand)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(SafeBandSpacing.xxxl))
        }

        // Offline Parent Profile Dialog
        if (showOfflineParentDialog) {
            AlertDialog(
                onDismissRequest = { showOfflineParentDialog = false },
                shape = SafeBandShapes.ExtraLarge,
                title = {
                    Text(
                        text = "Offline Guardian Profile",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(SafeBandSpacing.md)) {
                        Text(
                            text = "SafeBand operates 100% offline. Creating a local profile gives you full guardian privileges without cloud login.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SafeBandTextField(
                            value = offlineParentName,
                            onValueChange = { offlineParentName = it },
                            label = "Guardian Name",
                            placeholder = "e.g. Sarah Connor",
                            singleLine = true
                        )
                        SafeBandTextField(
                            value = offlineParentEmail,
                            onValueChange = { offlineParentEmail = it },
                            label = "Contact Phone or Email",
                            placeholder = "e.g. +1 (555) 019-2834",
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    SafeBandPrimaryButton(
                        text = "Confirm & Start",
                        onClick = {
                            val name = offlineParentName.ifBlank { "Guardian" }
                            val email = offlineParentEmail.ifBlank { "guardian@local" }
                            viewModel.signInAsGuestParent(name, email)
                            showOfflineParentDialog = false
                        }
                    )
                },
                dismissButton = {
                    TextButton(onClick = { showOfflineParentDialog = false }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    }
}
