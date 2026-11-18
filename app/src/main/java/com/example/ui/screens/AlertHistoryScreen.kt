package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.IncidentStage
import com.example.model.RiskLevel
import com.example.model.SafetyIncident
import com.example.model.SyncStatus
import com.example.model.TimelineItem
import com.example.ui.SafeBandViewModel
import com.example.ui.components.IncidentDetailSheet
import com.example.ui.components.IncidentReplaySheet
import com.example.ui.components.SafeBandCard
import com.example.ui.components.SafeBandPrimaryButton
import com.example.ui.components.SafeBandSecondaryButton
import com.example.ui.components.SafeBandStatusBadge
import com.example.ui.theme.SafeBandColor
import com.example.ui.theme.SafeBandShapes
import com.example.ui.theme.SafeBandSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            .background(MaterialTheme.colorScheme.background)
            .padding(SafeBandSpacing.lg)
    ) {
        // Top Header
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
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(SafeBandSpacing.xs))
                }

                Surface(
                    shape = SafeBandShapes.medium,
                    color = SafeBandColor.PrimaryLight,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = SafeBandColor.Primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(SafeBandSpacing.sm))
                Column {
                    Text(
                        text = "Safety Event Timeline",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Cryptographically signed • Offline persistent log",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        tint = SafeBandColor.Primary
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
                            tint = SafeBandColor.Emergency
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(SafeBandSpacing.md))

        // Status Card: Audit status
        SafeBandCard(
            modifier = Modifier.fillMaxWidth(),
            padding = SafeBandSpacing.sm
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SafeBandSpacing.xs)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(SafeBandColor.Success)
                    )
                    Text(
                        text = "Offline SQLite & NVRAM Storage Active",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                SafeBandStatusBadge(
                    text = "SHA-256 SIGNED",
                    color = SafeBandColor.Primary
                )
            }
        }

        Spacer(modifier = Modifier.height(SafeBandSpacing.sm))

        // Filter Chips Row
        if (timelineItems.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(SafeBandSpacing.xs),
                modifier = Modifier.fillMaxWidth()
            ) {
                val filters = listOf("ALL", "CRITICAL", "EMERGENCY", "WARNING", "RELAY / SYNC", "RESOLVED")
                items(filters) { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                text = filter,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SafeBandColor.PrimaryLight,
                            selectedLabelColor = SafeBandColor.PrimaryDark
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(SafeBandSpacing.sm))
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
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
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

                    SafeBandSecondaryButton(
                        text = "Run Evaluation Replay",
                        icon = Icons.Default.PlayCircle,
                        onClick = {
                            viewModel.startIncidentReplay()
                            showReplaySheet = true
                        }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(SafeBandSpacing.sm)
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

    // Incident Detail Dialog
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

    // Incident Replay Sheet
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
            title = { Text("Clear All Safety History?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently remove all offline safety event logs from this device. Real incidents cannot be recovered once cleared.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SafeBandColor.Emergency)
                ) {
                    Text("Clear All", fontWeight = FontWeight.Bold)
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
 * Visual Timeline Item Card with modern design system styling.
 */
@Composable
fun TimelineCardItem(
    item: TimelineItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormat = SimpleDateFormat("HH:mm:ss • dd MMM", Locale.US)
    val timeStr = timeFormat.format(Date(item.timestamp))

    val riskColor = when (item.riskLevel) {
        RiskLevel.CRITICAL -> SafeBandColor.Emergency
        RiskLevel.HIGH -> SafeBandColor.Emergency
        RiskLevel.MEDIUM -> SafeBandColor.Warning
        RiskLevel.LOW -> SafeBandColor.Success
        RiskLevel.NORMAL -> SafeBandColor.Primary
    }

    SafeBandCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        borderColor = if (item.riskLevel == RiskLevel.CRITICAL) SafeBandColor.Emergency.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
        padding = SafeBandSpacing.md
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Visual Node Indicator Dot
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 4.dp, end = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(riskColor)
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
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    SafeBandStatusBadge(
                        text = item.riskLevel.title,
                        color = riskColor
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Metadata Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Observation Count Chip
                    if (item.observationCount > 1) {
                        SafeBandStatusBadge(
                            text = "×${item.observationCount} OBS",
                            color = SafeBandColor.Primary
                        )
                    }

                    // Sync Status Chip
                    SafeBandStatusBadge(
                        text = item.syncStatus.name,
                        color = SafeBandColor.Slate500
                    )

                    // Simulation Indicator
                    if (item.isSimulation) {
                        SafeBandStatusBadge(
                            text = "SIM",
                            color = SafeBandColor.Warning
                        )
                    }
                }
            }
        }
    }
}
