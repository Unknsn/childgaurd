package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChildBioProfile

@Composable
fun EditChildBioDialog(
    initialProfile: ChildBioProfile,
    onDismiss: () -> Unit,
    onSave: (ChildBioProfile) -> Unit
) {
    var name by remember { mutableStateOf(initialProfile.childName) }
    var age by remember { mutableStateOf(initialProfile.age) }
    var bloodType by remember { mutableStateOf(initialProfile.bloodType) }
    var primaryPhone by remember { mutableStateOf(initialProfile.primaryParentPhone) }
    var secondaryPhone by remember { mutableStateOf(initialProfile.secondaryContactPhone) }
    var medicalConditions by remember { mutableStateOf(initialProfile.medicalConditions) }
    var allergies by remember { mutableStateOf(initialProfile.allergies) }
    var emergencyNotes by remember { mutableStateOf(initialProfile.emergencyNotes) }

    val bloodOptions = listOf("O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEE2E2),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MedicalServices,
                        contentDescription = null,
                        tint = Color(0xFFDC2626),
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Edit Child Bio & Medical Data",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Included in alert banners & SOS message dispatches",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Child Name & Age in a row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Child Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.weight(2f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = age,
                        onValueChange = { age = it },
                        label = { Text("Age") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Blood Type Chip Selector
                Column {
                    Text(
                        text = "Blood Group",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        bloodOptions.forEach { option ->
                            FilterChip(
                                selected = bloodType.equals(option, ignoreCase = true),
                                onClick = { bloodType = option },
                                label = { Text(option, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFDC2626),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // Primary Parent Phone
                OutlinedTextField(
                    value = primaryPhone,
                    onValueChange = { primaryPhone = it },
                    label = { Text("Primary Parent / Guardian Phone") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Secondary Emergency Phone
                OutlinedTextField(
                    value = secondaryPhone,
                    onValueChange = { secondaryPhone = it },
                    label = { Text("Secondary Contact Phone (Optional)") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Medical Conditions
                OutlinedTextField(
                    value = medicalConditions,
                    onValueChange = { medicalConditions = it },
                    label = { Text("Medical Conditions (e.g. Asthma, Diabetes)") },
                    leadingIcon = { Icon(Icons.Default.MedicalServices, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                // Allergies
                OutlinedTextField(
                    value = allergies,
                    onValueChange = { allergies = it },
                    label = { Text("Known Allergies (e.g. Peanuts, Penicillin)") },
                    leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                // Emergency Notes
                OutlinedTextField(
                    value = emergencyNotes,
                    onValueChange = { emergencyNotes = it },
                    label = { Text("Emergency Notes & Special Instructions") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ChildBioProfile(
                            childName = name.trim().ifEmpty { "Child" },
                            age = age.trim().ifEmpty { "N/A" },
                            bloodType = bloodType.trim().ifEmpty { "Unknown" },
                            primaryParentPhone = primaryPhone.trim(),
                            secondaryContactPhone = secondaryPhone.trim(),
                            medicalConditions = medicalConditions.trim().ifEmpty { "None Reported" },
                            allergies = allergies.trim().ifEmpty { "None Reported" },
                            emergencyNotes = emergencyNotes.trim()
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save Bio Profile", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text("Cancel")
            }
        }
    )
}
