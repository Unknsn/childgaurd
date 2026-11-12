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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppMode
import com.example.ui.theme.CarbonChassis
import com.example.ui.theme.MilledSilver
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianContainerLowest
import com.example.ui.theme.ObsidianEtchedBorder
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.PhosphorWhite

@Composable
fun ModeSelectionScreen(
    onSelectMode: (AppMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CarbonChassis)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App Telemetry Logo
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = ObsidianContainerLowest,
                modifier = Modifier
                    .size(68.dp)
                    .border(1.5.dp, PhosphorWhite, RoundedCornerShape(4.dp))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = "SafeBand Logo",
                        tint = PhosphorWhite,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "SAFEBAND // BLE_P2P",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = PhosphorWhite,
                letterSpacing = 2.sp
            )

            Text(
                text = "PEER-TO-PEER EARLY WARNING SYSTEM",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = ObsidianOutline,
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = "Autonomous RF beacon telemetry protecting children with zero cloud dependency and verified zero fake-GPS contracts.",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MilledSilver,
                textAlign = TextAlign.Center,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 6.dp, start = 12.dp, end = 12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 3 Core Product Pillars
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                color = ObsidianContainerLowest,
                border = BorderStroke(1.dp, ObsidianEtchedBorder)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PillarRow(
                        icon = Icons.Default.Lock,
                        title = "100% OFFLINE & EPHEMERAL",
                        description = "Direct peer-to-peer 2.4GHz BLE broadcasts. No central tracking servers."
                    )
                    PillarRow(
                        icon = Icons.Default.LocationOn,
                        title = "SAFE-ZONE RADAR SCOPE",
                        description = "Radial boundary monitoring alerts guardians if child moves beyond perimeter."
                    )
                    PillarRow(
                        icon = Icons.Default.NotificationsActive,
                        title = "EMERGENCY DND OVERRIDE",
                        description = "Priority alarm bypass, alerting child bio, and Indian 112/1098 helplines."
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "SELECT NODE ARCHITECTURE TO INITIALIZE",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = ObsidianOutline,
                fontSize = 8.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Child Device Card
            Card(
                onClick = { onSelectMode(AppMode.CHILD) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("select_child_mode_card"),
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianContainerLow),
                border = BorderStroke(1.5.dp, PhosphorWhite)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = PhosphorWhite,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Watch,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CHILD WEARABLE MODE",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = PhosphorWhite,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Monitors 6-axis accelerometer, tracks safe-zone boundary, and transmits emergency beacon with tactile 2-second hold SOS trigger.",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MilledSilver,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Parent / Guardian Card
            Card(
                onClick = { onSelectMode(AppMode.PARENT) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("select_parent_mode_card"),
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianContainerLow),
                border = BorderStroke(1.dp, ObsidianEtchedBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ObsidianContainerLowest,
                        modifier = Modifier
                            .size(44.dp)
                            .border(1.dp, PhosphorWhite, RoundedCornerShape(2.dp))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.FamilyRestroom,
                                contentDescription = null,
                                tint = PhosphorWhite,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PARENT GUARDIAN MONITOR",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = PhosphorWhite,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Continuously scans for BLE safety packets, manages multiple child bands, computes safe-zone corridor deviations, and coordinates emergency response.",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MilledSilver,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "ROLE MAY BE RECONFIGURED ANYTIME IN HARDWARE SETTINGS",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                color = ObsidianOutline,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PillarRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(ObsidianContainerLow)
                .border(0.5.dp, ObsidianEtchedBorder),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PhosphorWhite,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                color = PhosphorWhite
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MilledSilver,
                fontSize = 8.sp
            )
        }
    }
}
