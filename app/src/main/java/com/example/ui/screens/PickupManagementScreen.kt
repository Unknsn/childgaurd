package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.HandoverRecord
import com.example.data.local.PickupAuthorization
import com.example.ui.SafeBandViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickupManagementScreen(
    viewModel: SafeBandViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Active Passes, 1 = Issue Pass, 2 = Verify & Approve, 3 = History

    val activeAuthorizations by viewModel.activeAuthorizations.collectAsState()
    val allAuthorizations by viewModel.allAuthorizations.collectAsState()
    val handoverRecords by viewModel.allHandoverRecords.collectAsState()
    val childBio by viewModel.childBioProfile.collectAsState()

    // Form State
    var personName by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("Grandparent") }
    var phoneNumber by remember { mutableStateOf("") }
    var validityMinutes by remember { mutableIntStateOf(60) }
    var isOneTime by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }
    var createdPassMessage by remember { mutableStateOf<PickupAuthorization?>(null) }

    // Verification Tab State
    var verifyTokenInput by remember { mutableStateOf("") }
    var verifiedAuthResult by remember { mutableStateOf<PickupAuthorization?>(null) }
    var verificationError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Safe Pickup & Handover", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("pickup_back_button")
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
                    text = { Text("Active (${activeAuthorizations.size})") },
                    modifier = Modifier.testTag("tab_active_passes")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Issue Pass") },
                    modifier = Modifier.testTag("tab_issue_pass")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Verify Code") },
                    modifier = Modifier.testTag("tab_verify_handover")
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("History") },
                    modifier = Modifier.testTag("tab_handover_history")
                )
            }

            when (selectedTab) {
                0 -> ActiveAuthorizationsList(
                    authorizations = activeAuthorizations,
                    onRevoke = { authId -> viewModel.revokePickupAuthorization(authId) },
                    onCopyToken = { token ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Pickup Token", token))
                        Toast.makeText(context, "Pickup Token $token copied", Toast.LENGTH_SHORT).show()
                    },
                    onNavigateToIssue = { selectedTab = 1 }
                )

                1 -> IssuePickupPassForm(
                    childName = childBio.childName.ifEmpty { "Aarav" },
                    personName = personName,
                    onPersonNameChange = { personName = it },
                    relationship = relationship,
                    onRelationshipChange = { relationship = it },
                    phoneNumber = phoneNumber,
                    onPhoneNumberChange = { phoneNumber = it },
                    validityMinutes = validityMinutes,
                    onValidityChange = { validityMinutes = it },
                    notes = notes,
                    onNotesChange = { notes = it },
                    createdPass = createdPassMessage,
                    onIssue = {
                        viewModel.createPickupAuthorization(
                            childName = childBio.childName.ifEmpty { "Child" },
                            authorizedPersonName = personName,
                            relationship = relationship,
                            phoneNumber = phoneNumber,
                            validityMinutes = validityMinutes,
                            isOneTime = isOneTime,
                            notes = notes,
                            onCreated = { auth ->
                                createdPassMessage = auth
                                personName = ""
                                phoneNumber = ""
                                notes = ""
                            }
                        )
                    }
                )

                2 -> VerifyAndApproveHandoverSection(
                    tokenInput = verifyTokenInput,
                    onTokenChange = {
                        verifyTokenInput = it.uppercase()
                        verificationError = null
                    },
                    verifiedAuth = verifiedAuthResult,
                    error = verificationError,
                    onVerify = {
                        scope.launch {
                            val res = viewModel.verifyPickupToken(verifyTokenInput)
                            if (res != null) {
                                verifiedAuthResult = res
                                verificationError = null
                            } else {
                                verifiedAuthResult = null
                                verificationError = "Invalid, expired, or revoked pickup authorization token."
                            }
                        }
                    },
                    onApprove = { auth ->
                        viewModel.approveHandover(auth, parentName = "Primary Parent")
                        verifiedAuthResult = null
                        verifyTokenInput = ""
                        Toast.makeText(context, "Handover Approved Successfully", Toast.LENGTH_SHORT).show()
                        selectedTab = 3
                    },
                    onReject = { auth ->
                        viewModel.rejectHandover(auth, reason = "Parent declined handover")
                        verifiedAuthResult = null
                        verifyTokenInput = ""
                        Toast.makeText(context, "Handover Declined", Toast.LENGTH_SHORT).show()
                    }
                )

                3 -> HandoverHistoryList(records = handoverRecords)
            }
        }
    }
}

@Composable
private fun ActiveAuthorizationsList(
    authorizations: List<PickupAuthorization>,
    onRevoke: (String) -> Unit,
    onCopyToken: (String) -> Unit,
    onNavigateToIssue: () -> Unit
) {
    if (authorizations.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "No Active Pickup Passes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Issue a secure, time-limited pass to authorize a friend, family member, or babysitter to pick up your child.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onNavigateToIssue,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("issue_first_pass_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Issue New Pickup Pass")
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(authorizations) { auth ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFEFF6FF),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = Color(0xFF1D4ED8),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = auth.authorizedPersonName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${auth.relationship} • Child: ${auth.childName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFECFDF5)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF047857),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Verification Token Display
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Verification Token (Code)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = auth.verificationToken,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 3.sp
                                    )
                                }

                                IconButton(onClick = { onCopyToken(auth.verificationToken) }) {
                                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Token")
                                }
                            }
                        }

                        val remainingMinutes = ((auth.expiresAt - System.currentTimeMillis()) / 60000L).coerceAtLeast(0L)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Expires in $remainingMinutes minutes (Valid until ${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(auth.expiresAt))})",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (remainingMinutes < 15) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = { onRevoke(auth.authId) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Revoke Immediately")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IssuePickupPassForm(
    childName: String,
    personName: String,
    onPersonNameChange: (String) -> Unit,
    relationship: String,
    onRelationshipChange: (String) -> Unit,
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    validityMinutes: Int,
    onValidityChange: (Int) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    createdPass: PickupAuthorization?,
    onIssue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (createdPass != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                border = BorderStroke(1.dp, Color(0xFFA7F3D0))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pickup Pass Created Successfully!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF065F46)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Authorized Person: ${createdPass.authorizedPersonName} (${createdPass.relationship})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF047857)
                    )
                    Text(
                        text = "Verification Code: ${createdPass.verificationToken}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF065F46)
                    )
                }
            }
        }

        OutlinedTextField(
            value = personName,
            onValueChange = onPersonNameChange,
            label = { Text("Authorized Person Full Name") },
            placeholder = { Text("e.g. Grandma Helen or Uncle David") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pickup_person_name_input"),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = relationship,
            onValueChange = onRelationshipChange,
            label = { Text("Relationship to Child") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = onPhoneNumberChange,
            label = { Text("Phone Number") },
            placeholder = { Text("+1 (555) 000-0000") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Text(
            text = "Pass Validity Window",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(30, 60, 120, 240).forEach { mins ->
                val label = if (mins >= 60) "${mins / 60}h" else "${mins}m"
                OutlinedButton(
                    onClick = { onValidityChange(mins) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (validityMinutes == mins) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(label, fontWeight = FontWeight.Bold)
                }
            }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            label = { Text("Pickup Notes / Instructions (Optional)") },
            placeholder = { Text("e.g. Parked at front school gate in silver sedan") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Button(
            onClick = onIssue,
            enabled = personName.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("issue_pass_submit_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.QrCode, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Issue Pickup Authorization Pass", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VerifyAndApproveHandoverSection(
    tokenInput: String,
    onTokenChange: (String) -> Unit,
    verifiedAuth: PickupAuthorization?,
    error: String?,
    onVerify: () -> Unit,
    onApprove: (PickupAuthorization) -> Unit,
    onReject: (PickupAuthorization) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Verify Authorized Handover",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "When an authorized pickup person arrives, enter the 6-character verification code from their pass to verify their identity.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = tokenInput,
            onValueChange = onTokenChange,
            label = { Text("Enter 6-Character Verification Code") },
            placeholder = { Text("e.g. 7X29AB") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("verify_token_input"),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Button(
            onClick = onVerify,
            enabled = tokenInput.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("verify_code_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Verify Pickup Code")
        }

        if (error != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFEF2F2),
                border = BorderStroke(1.dp, Color(0xFFFECACA))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = Color(0xFFEF4444))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = error, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF991B1B))
                }
            }
        }

        if (verifiedAuth != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF1D4ED8))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Verified Authorized Pickup Person",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E40AF)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "Name: ${verifiedAuth.authorizedPersonName}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(text = "Relationship: ${verifiedAuth.relationship}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Child: ${verifiedAuth.childName}", style = MaterialTheme.typography.bodyMedium)
                    if (verifiedAuth.notes.isNotBlank()) {
                        Text(text = "Instructions: ${verifiedAuth.notes}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onReject(verifiedAuth) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reject_handover_button"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Reject")
                        }

                        Button(
                            onClick = { onApprove(verifiedAuth) },
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("approve_handover_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Approve Handover", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HandoverHistoryList(records: List<HandoverRecord>) {
    if (records.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(imageVector = Icons.Default.History, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "No Previous Handover Records", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(records) { record ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${record.pickupPersonName} (${record.relationship})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (record.status == "APPROVED") Color(0xFFECFDF5) else Color(0xFFFEF2F2)
                            ) {
                                Text(
                                    text = record.status,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (record.status == "APPROVED") Color(0xFF047857) else Color(0xFFDC2626),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        val dateStr = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(record.timestamp))
                        Text(
                            text = "$dateStr • Decision by: ${record.decisionBy}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

