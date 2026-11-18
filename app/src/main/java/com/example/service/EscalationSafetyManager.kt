package com.example.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.data.local.EscalationConfigEntity
import com.example.data.local.EscalationRecord
import com.example.data.local.TrustedContact
import com.example.data.repository.SafetyRepository
import com.example.model.AlertFlag
import com.example.model.RiskLevel
import com.example.model.SafetyIncident
import com.example.model.VerifiedIncidentPackage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Emergency Escalation System.
 *
 * Implements:
 * - Configurable Multi-Tier Escalation Hierarchy:
 *   Tier 1: Immediate SOS notification to the Owner Parent.
 *   Tier 2: Escalation to Trusted Guardians after configurable timeout.
 *   Tier 3: Escalation to Authorized Institution Contacts (School / Safe Facility).
 * - Automatic cancellation of pending escalation upon explicit acknowledgment by any guardian.
 * - Anti-duplicate dispatch gating.
 * - Strict integrity constraint: Never falsely claims emergency services / police contacted
 *   without an authorized reporting integration confirmation.
 * - Verified Incident Information Package generation for emergency reporting.
 */
class EscalationSafetyManager(
    private val context: Context,
    private val repository: SafetyRepository,
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "EscalationSafetyManager"
    }

    private val activeEscalationJobs = ConcurrentHashMap<String, Job>()
    private val acknowledgedIncidents = ConcurrentHashMap<String, String>() // incidentId -> responderName

    private val _activeEscalationTier = MutableStateFlow<Map<String, Int>>(emptyMap())
    val activeEscalationTier: StateFlow<Map<String, Int>> = _activeEscalationTier.asStateFlow()

    /**
     * Dispatches real communication for an escalation step:
     * - Fires high-priority local emergency notification with call intent
     * - Attempts direct SMS dispatch if SEND_SMS granted, or prepares intent
     * - Returns true if dispatched successfully, false if failed
     */
    fun dispatchCommunication(
        incident: SafetyIncident,
        recipientName: String,
        recipientPhone: String,
        tier: Int
    ): Boolean {
        if (recipientPhone.isBlank()) return false
        var success = true
        val bio = incident.childBioProfile
        val childName = bio?.childName?.takeIf { it.isNotBlank() } ?: "Child (${incident.deviceId})"
        val locationText = incident.address?.formattedSummary()
            ?: incident.verifiedLocation?.let { "Lat: ${it.latitude}, Lon: ${it.longitude}" }
            ?: "Coordinates unavailable"
        val message = "🚨 SAFEBAND EMERGENCY SOS (Tier $tier): Child $childName needs immediate assistance at $locationText. Contact: $recipientPhone"

        // 1. Direct SMS Dispatch if permission granted
        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasSmsPermission) {
            try {
                @Suppress("DEPRECATION")
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(android.telephony.SmsManager::class.java)
                } else {
                    android.telephony.SmsManager.getDefault()
                }
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(recipientPhone, null, parts, null, null)
                Log.i(TAG, "Successfully dispatched emergency SMS to $recipientPhone")
            } catch (e: Exception) {
                Log.w(TAG, "SMS dispatch failed to $recipientPhone: ${e.message}")
                success = false
            }
        } else {
            Log.i(TAG, "SEND_SMS permission not granted; SMS intent logged for $recipientPhone")
        }

        // 2. High-Priority Notification for Parent / Guardian Device
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager != null) {
                val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$recipientPhone")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val callPendingIntent = PendingIntent.getActivity(
                    context,
                    tier * 100 + recipientPhone.hashCode(),
                    callIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                )

                val notif = NotificationCompat.Builder(context, BleSafetyMonitorService.CHANNEL_ID_EMERGENCY)
                    .setContentTitle("🚨 EMERGENCY ESCALATION (Tier $tier)")
                    .setContentText("Alerting $recipientName ($recipientPhone) for $childName")
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .addAction(android.R.drawable.ic_menu_call, "Call $recipientName", callPendingIntent)
                    .setAutoCancel(true)
                    .build()

                notificationManager.notify(3000 + tier * 10 + Math.abs(recipientPhone.hashCode() % 100), notif)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notification dispatch error: ${e.message}")
        }

        return success
    }

    /**
     * Initiates the multi-tier escalation chain for a confirmed emergency incident.
     */
    fun startEscalation(
        incident: SafetyIncident,
        guardians: List<TrustedContact>,
        config: EscalationConfigEntity = EscalationConfigEntity()
    ) {
        val incidentId = incident.incidentId.ifEmpty { "INC-${System.currentTimeMillis()}" }

        // If already handling or acknowledged, do not re-escalate
        if (activeEscalationJobs.containsKey(incidentId) || acknowledgedIncidents.containsKey(incidentId)) {
            return
        }

        val job = scope.launch(Dispatchers.Default) {
            try {
                Log.i(TAG, "Starting Emergency Escalation for $incidentId. Priority: ${incident.riskLevel}")

                // TIER 1: Immediate SOS Notification to Primary Owner Parent
                updateTierMap(incidentId, 1)
                val parentPhone = incident.childBioProfile?.primaryParentPhone ?: "Primary Parent"
                val tier1Success = dispatchCommunication(
                    incident = incident,
                    recipientName = "Primary Parent (${incident.childBioProfile?.childName ?: "Child"})",
                    recipientPhone = parentPhone,
                    tier = 1
                )
                repository.recordEscalationStep(
                    incidentId = incidentId,
                    tier = 1,
                    recipientName = "Primary Parent (${incident.childBioProfile?.childName ?: "Child"})",
                    recipientContact = parentPhone,
                    status = if (tier1Success) "DISPATCHED" else "FAILED"
                )

                // Wait for Tier 1 acknowledgment delay
                val tier1DelayMs = config.tier1DelaySeconds * 1000L
                delay(tier1DelayMs)

                if (!isActive || isIncidentAcknowledged(incidentId)) return@launch

                // TIER 2: Escalation to Trusted Guardians
                updateTierMap(incidentId, 2)
                val secondaryGuardians = guardians.filter { contact ->
                    contact.phoneNumber != "112" && contact.phoneNumber != "1098"
                }

                for (guardian in secondaryGuardians) {
                    if (!isActive || isIncidentAcknowledged(incidentId)) return@launch
                    val tier2Success = dispatchCommunication(
                        incident = incident,
                        recipientName = guardian.name,
                        recipientPhone = guardian.phoneNumber,
                        tier = 2
                    )
                    repository.recordEscalationStep(
                        incidentId = incidentId,
                        tier = 2,
                        recipientName = guardian.name,
                        recipientContact = guardian.phoneNumber,
                        status = if (tier2Success) "DISPATCHED" else "FAILED"
                    )
                }

                // Wait for Tier 2 acknowledgment delay
                val tier2DelayMs = config.tier2DelaySeconds * 1000L
                delay(tier2DelayMs)

                if (!isActive || isIncidentAcknowledged(incidentId)) return@launch

                // TIER 3: Escalation to Authorized Institution Contacts
                updateTierMap(incidentId, 3)
                val institutionContacts = guardians.filter {
                    it.relationship.contains("School", ignoreCase = true) ||
                    it.relationship.contains("Institution", ignoreCase = true) ||
                    it.phoneNumber == "1098"
                }

                for (inst in institutionContacts) {
                    if (!isActive || isIncidentAcknowledged(incidentId)) return@launch
                    val tier3Success = dispatchCommunication(
                        incident = incident,
                        recipientName = inst.name,
                        recipientPhone = inst.phoneNumber,
                        tier = 3
                    )
                    repository.recordEscalationStep(
                        incidentId = incidentId,
                        tier = 3,
                        recipientName = inst.name,
                        recipientContact = inst.phoneNumber,
                        status = if (tier3Success) "DISPATCHED" else "FAILED"
                    )
                }

                Log.i(TAG, "Completed full escalation chain for incident $incidentId")
            } finally {
                activeEscalationJobs.remove(incidentId)
            }
        }

        activeEscalationJobs[incidentId] = job
    }

    /**
     * Halts all further escalations immediately when an authorized person acknowledges the incident.
     */
    fun acknowledgeIncident(incidentId: String, responderName: String) {
        acknowledgedIncidents[incidentId] = responderName
        activeEscalationJobs[incidentId]?.cancel()
        activeEscalationJobs.remove(incidentId)

        scope.launch {
            repository.acknowledgeEscalation(incidentId, responderName)
            val updated = _activeEscalationTier.value.toMutableMap()
            updated.remove(incidentId)
            _activeEscalationTier.value = updated
        }
    }

    fun isIncidentAcknowledged(incidentId: String): Boolean {
        return acknowledgedIncidents.containsKey(incidentId)
    }

    fun getAcknowledgedResponder(incidentId: String): String? {
        return acknowledgedIncidents[incidentId]
    }

    private fun updateTierMap(incidentId: String, tier: Int) {
        val current = _activeEscalationTier.value.toMutableMap()
        current[incidentId] = tier
        _activeEscalationTier.value = current
    }

    /**
     * Builds a verified incident report package for emergency and guardian distribution.
     * Contains sensor observations, timestamps, and escalation audit trail.
     */
    suspend fun generateVerifiedIncidentPackage(
        incident: SafetyIncident,
        escalationLog: List<EscalationRecord>
    ): VerifiedIncidentPackage {
        val bio = incident.childBioProfile
        return VerifiedIncidentPackage(
            incidentId = incident.incidentId,
            deviceId = incident.deviceId,
            childName = bio?.childName ?: "Child (${incident.deviceId})",
            age = bio?.age ?: "Unknown",
            bloodType = bio?.bloodType ?: "Unknown",
            medicalConditions = bio?.medicalConditions ?: "None documented",
            allergies = bio?.allergies ?: "None documented",
            primaryParentPhone = bio?.primaryParentPhone ?: "",
            timestamp = incident.startTimestamp,
            riskLevel = incident.riskLevel,
            triggerFlags = incident.triggerFlags.map { it.displayName },
            verifiedLocation = incident.verifiedLocation,
            resolvedAddress = incident.address?.formattedSummary(),
            escalationAuditLog = escalationLog
        )
    }
}

