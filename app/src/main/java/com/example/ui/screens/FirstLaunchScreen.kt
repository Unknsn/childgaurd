package com.example.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.auth.AuthStatus
import com.example.model.AppMode
import com.example.ui.SafeBandViewModel
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.theme.CarbonChassis
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderStrong
import com.example.ui.theme.GlassHazard
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.GlassSurfaceLowest
import com.example.ui.theme.MilledSilver
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianContainerLowest
import com.example.ui.theme.ObsidianEtchedBorder
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.PhosphorWhite

/**
 * FirstLaunchScreen (v3.3 Architecture)
 * Enforces intentional role separation between Parent and Child experiences.
 * - Parent setup triggers Google Sign-In with modern Credential Manager (or offline fallback).
 * - Child setup leads directly into parent-authorized 6-digit cryptographic pairing.
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
            .background(CarbonChassis)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App Emblem
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = GlassSurfaceLowest,
                modifier = Modifier
                    .size(72.dp)
                    .border(1.5.dp, PhosphorWhite, RoundedCornerShape(8.dp))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = "SafeBand Guardian",
                        tint = PhosphorWhite,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "SAFEBAND GUARDIAN",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = PhosphorWhite,
                letterSpacing = 2.sp
            )

            Text(
                text = "PEER-TO-PEER CHILD SAFETY SYSTEM // v3.3",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = ObsidianOutline,
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = "Cryptographic BLE safety beacons, geofence corridors, and offline emergency protection.",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MilledSilver,
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isSigningIn) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassSurfaceElevated,
                    padding = 16.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = PhosphorWhite,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "Authenticating Parent Account...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = PhosphorWhite
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            authErrorMessage?.let { err ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassHazard,
                    borderColor = Color(0xFFFF5252),
                    padding = 12.dp
                ) {
                    Text(
                        text = "Authentication Notice: $err",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFFF5252),
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(
                text = "INITIALIZE DEVICE ROLE",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = ObsidianOutline,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // OPTION 1: PARENT / GUARDIAN SETUP
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_parent_card"),
                backgroundColor = GlassSurface,
                borderColor = GlassBorderStrong,
                padding = 16.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = GlassSurfaceLowest,
                        modifier = Modifier
                            .size(46.dp)
                            .border(1.dp, PhosphorWhite, RoundedCornerShape(6.dp))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.FamilyRestroom,
                                contentDescription = null,
                                tint = PhosphorWhite,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PARENT / GUARDIAN",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = PhosphorWhite,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Monitor child bands, configure safe-zones, issue pairing codes, and manage emergency response.",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MilledSilver,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Primary Action: Google Sign-In
                GlassButton(
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

                Spacer(modifier = Modifier.height(8.dp))

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
                            text = "Set up as Offline Parent (No Google Account)",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MilledSilver,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // OPTION 2: CHILD DEVICE SETUP
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_child_card"),
                backgroundColor = GlassSurface,
                borderColor = GlassBorder,
                padding = 16.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = GlassSurfaceLowest,
                        modifier = Modifier
                            .size(46.dp)
                            .border(1.dp, PhosphorWhite, RoundedCornerShape(6.dp))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Watch,
                                contentDescription = null,
                                tint = PhosphorWhite,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CHILD DEVICE",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = PhosphorWhite,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Transmits 0x5AFE BLE beacons, checks geofences, and provides tactile SOS trigger.",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MilledSilver,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                GlassButton(
                    text = "Pair with Parent (6-Digit Code)",
                    icon = Icons.Default.QrCodeScanner,
                    onClick = onNavigateToChildProvisioning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("enter_child_code_button")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // OPTION 3: QUICK WEARABLE HARDWARE DEMO (EVALUATOR/DEVELOPER PATH)
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = GlassSurfaceLowest,
                borderColor = ObsidianEtchedBorder,
                padding = 12.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "QUICK EVALUATION MODE",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = PhosphorWhite,
                            fontSize = 9.sp
                        )
                        Text(
                            text = "Direct testing without provisioning PIN.",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = ObsidianOutline,
                            fontSize = 8.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { viewModel.lockIntoChildMode("EP-C0D7") }
                        ) {
                            Text(
                                text = "Child Demo",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = PhosphorWhite,
                                fontSize = 9.sp
                            )
                        }
                        TextButton(
                            onClick = {
                                viewModel.signInAsGuestParent("Demo Parent", "parent@safeband.local")
                            }
                        ) {
                            Text(
                                text = "Parent Demo",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = PhosphorWhite,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Offline Parent Profile Dialog
        if (showOfflineParentDialog) {
            AlertDialog(
                onDismissRequest = { showOfflineParentDialog = false },
                containerColor = CarbonChassis,
                title = {
                    Text(
                        text = "OFFLINE PARENT PROFILE",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = PhosphorWhite
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "SafeBand operates 100% offline. Creating a local profile gives you full guardian privileges without cloud login.",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MilledSilver,
                            fontSize = 11.sp
                        )
                        OutlinedTextField(
                            value = offlineParentName,
                            onValueChange = { offlineParentName = it },
                            label = { Text("Parent / Guardian Name", fontFamily = FontFamily.Monospace) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = offlineParentEmail,
                            onValueChange = { offlineParentEmail = it },
                            label = { Text("Contact Email / Phone", fontFamily = FontFamily.Monospace) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    GlassButton(
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
                        Text("Cancel", color = MilledSilver, fontFamily = FontFamily.Monospace)
                    }
                }
            )
        }
    }
}
