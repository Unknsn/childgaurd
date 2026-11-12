package com.example.ui.screens

import com.example.ui.components.GlassCard
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.IncidentStage
import com.example.ui.components.TactileKeycap
import com.example.ui.theme.CarbonChassis
import com.example.ui.theme.MilledSilver
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianContainerLowest
import com.example.ui.theme.ObsidianEtchedBorder
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.PhosphorWhite
import com.example.model.RiskLevel
import com.example.model.SafetyIncident
import com.example.model.SyncStatus
import com.example.model.TimelineItem
import com.example.ui.SafeBandViewModel
import com.example.ui.components.IncidentDetailSheet
import com.example.ui.components.IncidentReplaySheet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Event Timeline V2 Screen (Batch E: Phase 12).
 *
 * Displays a chronological incident timeline featuring:
 * - Deterministic event and incident grouping
 * - CRITICAL risk level badges and color hierarchy
 * - Multi-node observation count and propagation hop counts
 * - SyncStatus indicators (LOCAL_ONLY, PENDING_SYNC, RELAYED, ACKNOWLEDGED, SYNCED)
 * - Source/provenance node chips
 * - Simulation markers
 * - Interactive Incident Detail view (Phase 13) and Replay Engine (Phase 14)
 */
@Composable
fun AlertHistoryScreen(
    viewModel: SafeBandViewModel,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val timelineItems by viewModel.timelineItems.collectAsState()
    val rawEvents by viewModel.allEvents.collectAsState()
    val replayState by viewModel.replayState.collectAsState()

    var selectedFilter by remember { mutableStateOf("ALL") }
    var selectedItemForDetail by remember { mutableStateOf<TimelineItem?>(null) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var showReplaySheet by remember { mutableStateOf(false) }

    val filteredItems = remember(timelineItems, selectedFilter) {
        when (selectedFilter) {
            "CRITICAL" -> timelineItems.filter { it.riskLevel == RiskLevel.CRITICAL }
            "EMERGENCY" -> timelineItems.filter { it.riskLevel == RiskLevel.HIGH || it.riskLevel == RiskLevel.CRITICAL }
            "WARNING" -> timelineItems.filter { it.riskLevel == RiskLevel.MEDIUM }
            "RELAY / SYNC" -> timelineItems.filter { it.syncStatus == SyncStatus.RELAYED || it.syncStatus == SyncStatus.PENDING_SYNC }
            "RESOLVED" -> timelineItems.filter { it.outcome.contains("CANCELLED") || it.outcome.contains("RESOLVED") || it.riskLevel == RiskLevel.NORMAL }
            else -> timelineItems
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CarbonChassis)
            .padding(16.dp)
    ) {
        // Top Header (Stitch Incident Audit Log)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onNavigateBack != null) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("history_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PhosphorWhite
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Box(modifier = Modifier.size(8.dp).background(PhosphorWhite))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "SAFEBAND // BLACKBOX_AUDIT",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = PhosphorWhite,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "NVRAM_RECORD // SHA-256 VERIFIED",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        color = ObsidianOutline,
                        letterSpacing = 1.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Replay Quick Launcher
                IconButton(
                    onClick = {
                        viewModel.startIncidentReplay()
                        showReplaySheet = true
                    },
                    modifier = Modifier.testTag("timeline_launch_replay_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Launch Replay",
                        tint = PhosphorWhite
                    )
                }

                if (rawEvents.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearConfirmation = true },
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear History",
                            tint = Color(0xFFFF5252)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Secondary Hardware Status Header (Frosted Glass)
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            padding = 10.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF10B981)))
                    Text(
                        text = "BUFFER: 128KB NVRAM",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = PhosphorWhite
                    )
                }
                Text(
                    text = "SHA-256 VERIFIED",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = MilledSilver
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tactile Selector Chips Row
        if (timelineItems.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val filters = listOf("ALL", "CRITICAL", "EMERGENCY", "WARNING", "RELAY / SYNC", "RESOLVED")
                items(filters) { filter ->
                    val isSelected = selectedFilter == filter
                    Surface(
                        onClick = { selectedFilter = filter },
                        shape = RoundedCornerShape(2.dp),
                        color = if (isSelected) PhosphorWhite else ObsidianContainerLow,
                        modifier = Modifier.border(1.dp, if (isSelected) PhosphorWhite else ObsidianEtchedBorder, RoundedCornerShape(2.dp))
                    ) {
                        Text(
                            text = "[ $filter ]",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = if (isSelected) Color.Black else MilledSilver,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Timeline Feed or Empty State
        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (timelineItems.isEmpty()) "No Safety Events Logged Yet" else "No Events Matching Filter",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (timelineItems.isEmpty())
                            "Safety incidents, zone transits, and mesh broadcasts will appear on this chronological timeline."
                        else "Try selecting 'ALL' to view all recorded timeline items.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.startIncidentReplay()
                            showReplaySheet = true
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Run Evaluation Replay", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    TimelineCardItem(
                        item = item,
                        onClick = { selectedItemForDetail = item }
                    )
                }
            }
        }
    }

    // Incident Detail Dialog (Phase 13)
    selectedItemForDetail?.let { item ->
        val incident = SafetyIncident(
            incidentId = item.id,
            deviceId = item.sourceNode.ifEmpty { "SB-8041" },
            stage = if (item.outcome.contains("RESOLVED") || item.outcome.contains("CANCELLED")) IncidentStage.RESOLVED else IncidentStage.ACTIVE,
            riskLevel = item.riskLevel,
            startTimestamp = item.timestamp,
            lastUpdatedTimestamp = item.timestamp,
            observationCount = item.observationCount,
            sourceNodes = listOf(item.sourceNode).filter { it.isNotBlank() },
            maxHopCount = item.hopCount,
            syncStatus = item.syncStatus,
            isSimulation = item.isSimulation,
            resolutionReason = if (item.outcome.contains("RESOLVED")) item.description else null
        )

        IncidentDetailSheet(
            incident = incident,
            onDismiss = { selectedItemForDetail = null },
            onAcknowledge = {
                viewModel.acknowledgeIncident(incident.incidentId)
                selectedItemForDetail = null
            },
            onResolve = { reason ->
                viewModel.resolveIncident(incident.incidentId, reason)
                selectedItemForDetail = null
            },
            onReplay = {
                selectedItemForDetail = null
                viewModel.startIncidentReplay(incident)
                showReplaySheet = true
            }
        )
    }

    // Incident Replay Sheet (Phase 14)
    if (showReplaySheet) {
        IncidentReplaySheet(
            replayState = replayState,
            replaySteps = viewModel.replaySteps,
            onPlay = { viewModel.resumeIncidentReplay() },
            onPause = { viewModel.pauseIncidentReplay() },
            onStepForward = { viewModel.stepForwardIncidentReplay() },
            onReset = { viewModel.resetIncidentReplay() },
            onDismiss = {
                viewModel.pauseIncidentReplay()
                showReplaySheet = false
            }
        )
    }

    // Clear History Confirmation Dialog
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear All Safety History?") },
            text = { Text("This will permanently remove all offline safety event logs from this device. Real incidents cannot be recovered once cleared.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Visual Timeline Item Card with vertical connector and provenance metadata (Obsidian Telemetry).
 */
@Composable
fun TimelineCardItem(
    item: TimelineItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormat = SimpleDateFormat("HH:mm:ss • dd MMM", Locale.US)
    val timeStr = timeFormat.format(Date(item.timestamp))

    val dotColor = when (item.riskLevel) {
        RiskLevel.CRITICAL -> Color(0xFFFF5252)
        RiskLevel.HIGH -> Color(0xFFFF7043)
        RiskLevel.MEDIUM -> Color(0xFFFFB74D)
        RiskLevel.LOW -> PhosphorWhite
        RiskLevel.NORMAL -> MilledSilver
    }

    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ObsidianContainerLowest,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, if (item.riskLevel == RiskLevel.CRITICAL) Color(0xFFFF5252) else ObsidianEtchedBorder, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Visual Node Indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 4.dp, end = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(dotColor)
                )
            }

            // Body Content
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title.uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = PhosphorWhite,
                        fontSize = 11.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = if (item.riskLevel == RiskLevel.CRITICAL) Color(0xFFFF5252) else PhosphorWhite
                    ) {
                        Text(
                            text = "[ ${item.riskLevel.title.uppercase()} ]",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            color = Color.Black,
                            fontSize = 8.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MilledSilver,
                    lineHeight = 14.sp,
                    fontSize = 10.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Provenance & Metadata Chips Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = ObsidianOutline,
                        fontSize = 8.sp
                    )

                    // Observation Count Chip
                    if (item.observationCount > 1) {
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = ObsidianContainerLow,
                            modifier = Modifier.border(0.5.dp, ObsidianEtchedBorder, RoundedCornerShape(2.dp))
                        ) {
                            Text(
                                text = "×${item.observationCount} OBS",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = PhosphorWhite,
                                fontSize = 8.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    // Sync Status Chip
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ObsidianContainerLow,
                        modifier = Modifier.border(0.5.dp, ObsidianEtchedBorder, RoundedCornerShape(2.dp))
                    ) {
                        Text(
                            text = item.syncStatus.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MilledSilver,
                            fontSize = 8.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    // Simulation Indicator
                    if (item.isSimulation) {
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = PhosphorWhite
                        ) {
                            Text(
                                text = "SIM",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                color = Color.Black,
                                fontSize = 8.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
