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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.example.data.local.SchoolProfile
import com.example.data.local.SchoolVerificationLog
import com.example.ui.SafeBandViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolManagementScreen(
    viewModel: SafeBandViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Active School, 1 = Verification Logs, 2 = Holidays / Exceptions
    val activeSchool by viewModel.activeSchool.collectAsState()
    val schoolStatus by viewModel.schoolStatus.collectAsState()
    val schoolLogs by viewModel.schoolLogs.collectAsState()

    var showAddSchoolDialog by remember { mutableStateOf(false) }
    var holidayReason by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("School Arrival & Departure", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("school_back_button")
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
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("School Profile") },
                    modifier = Modifier.testTag("tab_school_profile")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Verification Logs (${schoolLogs.size})") },
                    modifier = Modifier.testTag("tab_school_logs")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Holidays / Exceptions") },
                    modifier = Modifier.testTag("tab_school_exceptions")
                )
            }

            when (selectedTab) {
                0 -> SchoolProfileTab(
                    school = activeSchool,
                    status = schoolStatus,
                    onEditSchool = { showAddSchoolDialog = true }
                )

                1 -> SchoolVerificationLogsTab(logs = schoolLogs)

                2 -> SchoolExceptionsTab(
                    school = activeSchool,
                    holidayReason = holidayReason,
                    onReasonChange = { holidayReason = it },
                    onAddHoliday = {
                        val sch = activeSchool
                        if (sch != null && holidayReason.isNotBlank()) {
                            val todayEpochDay = System.currentTimeMillis() / (24 * 3600 * 1000L)
                            viewModel.addSchoolHoliday(sch.schoolId, todayEpochDay, holidayReason)
                            holidayReason = ""
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SchoolProfileTab(
    school: SchoolProfile?,
    status: com.example.model.SchoolStatus,
    onEditSchool: () -> Unit
) {
    if (school == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(imageVector = Icons.Default.School, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(54.dp))
                Spacer(modifier = Modifier.height(14.dp))
                Text("No School Registered", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onEditSchool) {
                    Text("Register School Safe Zone")
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                            Icon(imageVector = Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = school.schoolName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (status.isInsideZone) Color(0xFFECFDF5) else Color(0xFFEFF6FF)
                        ) {
                            Text(
                                text = if (status.isInsideZone) "IN SCHOOL ZONE" else "OUTSIDE ZONE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (status.isInsideZone) Color(0xFF047857) else Color(0xFF1D4ED8),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(text = school.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "Safe Zone Radius: ${school.radiusMeters.toInt()}m", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                    Spacer(modifier = Modifier.height(14.dp))

                    // Arrival & Departure Windows
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Expected Arrival Window: ${formatTime(school.expectedArrivalStartMinutes)} - ${formatTime(school.expectedArrivalEndMinutes)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Expected Dismissal Window: ${formatTime(school.expectedDepartureStartMinutes)} - ${formatTime(school.expectedDepartureEndMinutes)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Active Days: Monday through Friday • Schedule Exceptions Checked",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SchoolVerificationLogsTab(logs: List<SchoolVerificationLog>) {
    if (logs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(imageVector = Icons.Default.History, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("No School Verification Events Logged", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(logs) { log ->
                val (color, icon, title) = when (log.eventType) {
                    "ARRIVAL_CONFIRMED" -> Triple(Color(0xFF047857), Icons.Default.CheckCircle, "Arrival Verified")
                    "DEPARTURE_CONFIRMED" -> Triple(Color(0xFF1D4ED8), Icons.Default.CheckCircle, "Departure Verified")
                    "LATE_ARRIVAL_WARNING" -> Triple(Color(0xFFD97706), Icons.Default.Warning, "Late Arrival Warning")
                    "UNEXPECTED_DEPARTURE_ALERT" -> Triple(Color(0xFFDC2626), Icons.Default.Warning, "Unexpected Departure Alert")
                    else -> Triple(Color.Gray, Icons.Default.History, log.eventType)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = log.details, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${SimpleDateFormat("MMM dd • hh:mm a", Locale.getDefault()).format(Date(log.timestamp))} • Confidence: ${log.locationConfidence}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SchoolExceptionsTab(
    school: SchoolProfile?,
    holidayReason: String,
    onReasonChange: (String) -> Unit,
    onAddHoliday: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("School Holiday & Schedule Exceptions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            text = "Register planned school holidays, teacher professional days, or emergency closures so SafeBand Guardian does not trigger false late arrival warnings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = holidayReason,
            onValueChange = onReasonChange,
            label = { Text("Holiday / Exception Reason") },
            placeholder = { Text("e.g. Winter Break or Teacher Conference") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Button(
            onClick = onAddHoliday,
            enabled = holidayReason.isNotBlank() && school != null,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.EventBusy, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Register Today as School Holiday")
        }
    }
}

private fun formatTime(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    val ampm = if (h >= 12) "PM" else "AM"
    val displayH = if (h > 12) h - 12 else if (h == 0) 12 else h
    return "%02d:%02d %s".format(displayH, m, ampm)
}

