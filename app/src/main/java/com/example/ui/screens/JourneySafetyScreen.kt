package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.JourneyEvent
import com.example.data.local.SupervisedJourney
import com.example.model.JourneySafetyStatus
import com.example.ui.SafeBandViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneySafetyScreen(
    viewModel: SafeBandViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeJourney by viewModel.activeJourney.collectAsState()
    val journeyStatus by viewModel.journeyStatus.collectAsState()
    val verifiedLocation by viewModel.verifiedLocation.collectAsState()
    val activeSchool by viewModel.activeSchool.collectAsState()

    // Form inputs
    var journeyName by remember { mutableStateOf("Walk to School") }
    var startLabel by remember { mutableStateOf("Home") }
    var destLabel by remember { mutableStateOf(activeSchool?.schoolName ?: "Lincoln Elementary") }
    var corridorRadius by remember { mutableStateOf("80") }
    var durationMinutes by remember { mutableIntStateOf(25) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Journey Safety Mode", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("journey_back_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (activeJourney != null) {
                ActiveJourneyMonitorCard(
                    journey = activeJourney!!,
                    status = journeyStatus,
                    onStop = { viewModel.stopSupervisedJourney(activeJourney!!.journeyId) }
                )
            } else {
                StartJourneyForm(
                    journeyName = journeyName,
                    onJourneyNameChange = { journeyName = it },
                    startLabel = startLabel,
                    onStartLabelChange = { startLabel = it },
                    destLabel = destLabel,
                    onDestLabelChange = { destLabel = it },
                    corridorRadius = corridorRadius,
                    onCorridorRadiusChange = { corridorRadius = it },
                    durationMinutes = durationMinutes,
                    onDurationMinutesChange = { durationMinutes = it },
                    onStart = {
                        val startLat = verifiedLocation?.latitude ?: 37.7749
                        val startLon = verifiedLocation?.longitude ?: -122.4194
                        val destLat = activeSchool?.latitude ?: 37.7650
                        val destLon = activeSchool?.longitude ?: -122.4600
                        val radius = corridorRadius.toFloatOrNull() ?: 80f

                        viewModel.startSupervisedJourney(
                            name = journeyName,
                            startLabel = startLabel,
                            startLat = startLat,
                            startLon = startLon,
                            destLabel = destLabel,
                            destLat = destLat,
                            destLon = destLon,
                            corridorRadiusMeters = radius,
                            expectedDurationMinutes = durationMinutes
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ActiveJourneyMonitorCard(
    journey: SupervisedJourney,
    status: JourneySafetyStatus,
    onStop: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = journey.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        status.isDeviation -> Color(0xFFFEF2F2)
                        status.isProlongedStop -> Color(0xFFFFFBEB)
                        else -> Color(0xFFECFDF5)
                    }
                ) {
                    Text(
                        text = when {
                            status.isDeviation -> "DEVIATION"
                            status.isProlongedStop -> "PROLONGED STOP"
                            status.isEtaExceeded -> "ETA EXCEEDED"
                            status.isDeviceDisconnected -> "BEACON LOST"
                            else -> "ON ROUTE"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            status.isDeviation -> Color(0xFFDC2626)
                            status.isProlongedStop -> Color(0xFFD97706)
                            else -> Color(0xFF047857)
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "${journey.startLabel}  ➜  ${journey.destLabel}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Allowed Corridor: ${journey.corridorRadiusMeters.toInt()}m • Expected: ${journey.expectedDurationMinutes} min",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Transit Progress", style = MaterialTheme.typography.labelSmall)
                Text(text = "${status.progressPercent.toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { status.progressPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            if (status.distanceRemainingMeters > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${status.distanceRemainingMeters.toInt()}m remaining to destination",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("End Journey Supervision")
            }
        }
    }
}

@Composable
private fun StartJourneyForm(
    journeyName: String,
    onJourneyNameChange: (String) -> Unit,
    startLabel: String,
    onStartLabelChange: (String) -> Unit,
    destLabel: String,
    onDestLabelChange: (String) -> Unit,
    corridorRadius: String,
    onCorridorRadiusChange: (String) -> Unit,
    durationMinutes: Int,
    onDurationMinutesChange: (Int) -> Unit,
    onStart: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Start Supervised Transit",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Supervises child transit along a safe corridor. Alerts if the child deviates from the path, stops unexpectedly, or if the BLE beacon disconnects.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = journeyName,
            onValueChange = onJourneyNameChange,
            label = { Text("Journey Name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = startLabel,
                onValueChange = onStartLabelChange,
                label = { Text("Starting Location") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = destLabel,
                onValueChange = onDestLabelChange,
                label = { Text("Destination") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
        }

        OutlinedTextField(
            value = corridorRadius,
            onValueChange = onCorridorRadiusChange,
            label = { Text("Permitted Corridor Width (Meters)") },
            placeholder = { Text("e.g. 80") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Text("Expected Travel Duration", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(15, 25, 35, 45).forEach { mins ->
                OutlinedButton(
                    onClick = { onDurationMinutesChange(mins) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (durationMinutes == mins) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("${mins}m")
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("start_journey_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Activate Supervised Journey", fontWeight = FontWeight.Bold)
        }
    }
}

