package com.example.service

import android.content.Context
import android.util.Log
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
                repository.recordEscalationStep(
                    incidentId = incidentId,
                    tier = 1,
                    recipientName = "Primary Parent (${incident.childBioProfile?.childName ?: "Child"})",
                    recipientContact = parentPhone
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
                    repository.recordEscalationStep(
                        incidentId = incidentId,
                        tier = 2,
                        recipientName = guardian.name,
                        recipientContact = guardian.phoneNumber
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
                    repository.recordEscalationStep(
                        incidentId = incidentId,
                        tier = 3,
                        recipientName = inst.name,
                        recipientContact = inst.phoneNumber
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

