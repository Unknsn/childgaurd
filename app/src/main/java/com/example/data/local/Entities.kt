package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "safety_events")
data class SafetyEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val flagType: String, // e.g. "SOS", "MOTION", "GEOFENCE", "BLE_RECEIVED"
    val riskLevel: String, // "NORMAL", "LOW", "MEDIUM", "HIGH", "CRITICAL"
    val outcome: String, // "CONFIRMED_ESCALATED", "CANCELLED_BY_USER", "LOGGED_LOCAL", "ALERT_RECEIVED", "SIMULATED"
    val deviceId: String,
    val details: String = "",
    val syncStatus: String = "LOCAL_ONLY",
    val observationId: String = "",
    val hopCount: Int = 0
)

@Entity(tableName = "trusted_contacts")
data class TrustedContact(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phoneNumber: String,
    val relationship: String = "Guardian"
)

// =============================================================================
// Feature A: Safe Pickup and Handover Verification Entities
// =============================================================================

@Entity(tableName = "pickup_authorizations")
data class PickupAuthorization(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val authId: String, // UUID
    val childName: String,
    val authorizedPersonName: String,
    val relationship: String,
    val phoneNumber: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long,
    val isOneTime: Boolean = true,
    val verificationToken: String, // Secure 6-character / hex verification token
    val status: String = "ACTIVE", // "ACTIVE", "USED", "EXPIRED", "REVOKED"
    val parentSignature: String = "" // Parent authorization audit hash
)

@Entity(tableName = "handover_records")
data class HandoverRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val handoverId: String, // UUID
    val authId: String,
    val childName: String,
    val pickupPersonName: String,
    val relationship: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // "APPROVED", "REJECTED", "AUTO_EXPIRED"
    val decisionBy: String = "Primary Parent",
    val locationSummary: String = "",
    val notes: String = ""
)

// =============================================================================
// Feature B: Emergency Escalation System Entities
// =============================================================================

@Entity(tableName = "escalation_configs")
data class EscalationConfigEntity(
    @PrimaryKey
    val id: Long = 1,
    val tier1DelaySeconds: Int = 30,
    val tier2DelaySeconds: Int = 60,
    val autoEscalateEnabled: Boolean = true,
    val tier1Role: String = "Primary Parent",
    val tier2Role: String = "Trusted Guardians",
    val tier3Role: String = "Authorized Institution & Emergency Contacts",
    val notifyPoliceClaimForbidden: Boolean = true
)

@Entity(tableName = "escalation_records")
data class EscalationRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val incidentId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tier: Int, // 1 = Parent, 2 = Guardians, 3 = Institution
    val recipientName: String,
    val recipientContact: String,
    val status: String = "DISPATCHED", // "DISPATCHED", "ACKNOWLEDGED", "TIMEOUT_EXPIRED", "CANCELLED"
    val acknowledgedBy: String? = null,
    val acknowledgedAt: Long? = null
)

// =============================================================================
// Feature C: School Arrival and Departure Verification Entities
// =============================================================================

@Entity(tableName = "school_profiles")
data class SchoolProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val schoolId: String, // UUID
    val schoolName: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 200f,
    val expectedArrivalStartMinutes: Int = 465, // 07:45 (7 * 60 + 45)
    val expectedArrivalEndMinutes: Int = 510,   // 08:30 (8 * 60 + 30)
    val expectedDepartureStartMinutes: Int = 870, // 14:30 (14 * 60 + 30)
    val expectedDepartureEndMinutes: Int = 930,   // 15:30 (15 * 60 + 30)
    val schoolDaysMask: Int = 31, // Mon-Fri (1 | 2 | 4 | 8 | 16)
    val isActive: Boolean = true
)

@Entity(tableName = "school_schedule_exceptions")
data class SchoolScheduleException(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val schoolId: String,
    val dateEpochDay: Long, // LocalDate.toEpochDay or epoch ms / (24*3600*1000)
    val reason: String = "School Holiday",
    val isHoliday: Boolean = true
)

@Entity(tableName = "school_verification_logs")
data class SchoolVerificationLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val schoolId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String, // "ARRIVAL_CONFIRMED", "DEPARTURE_CONFIRMED", "LATE_ARRIVAL_WARNING", "UNEXPECTED_DEPARTURE_ALERT"
    val locationConfidence: String = "MEDIUM",
    val details: String = ""
)

// =============================================================================
// Feature D: Journey Safety Mode Entities
// =============================================================================

@Entity(tableName = "supervised_journeys")
data class SupervisedJourney(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val journeyId: String, // UUID
    val name: String = "Supervised Journey",
    val startLabel: String = "Starting Point",
    val startLat: Double,
    val startLon: Double,
    val destLabel: String = "Destination",
    val destLat: Double,
    val destLon: Double,
    val destRadiusMeters: Float = 100f,
    val corridorRadiusMeters: Float = 80f,
    val expectedDurationMinutes: Int = 30,
    val startTimestamp: Long = System.currentTimeMillis(),
    val expectedArrivalTimestamp: Long = System.currentTimeMillis() + 30 * 60 * 1000L,
    val actualArrivalTimestamp: Long? = null,
    val guardianContactsToNotify: String = "",
    val status: String = "ACTIVE", // "ACTIVE", "COMPLETED", "DEVIATED", "STOPPED", "CANCELLED"
    val waypointsJson: String = "[]"
)

@Entity(tableName = "journey_events")
data class JourneyEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val journeyId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String, // "STARTED", "ON_TRACK", "DEVIATION", "PROLONGED_STOP", "ETA_EXCEEDED", "DISCONNECTED", "ARRIVED"
    val latitude: Double? = null,
    val longitude: Double? = null,
    val details: String = "",
    val isSafetyAlert: Boolean = false
)

// =============================================================================
// Feature E: Emergency Response Coordination Entities
// =============================================================================

@Entity(tableName = "incident_coordination_records")
data class IncidentCoordinationRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val incidentId: String,
    val responderName: String,
    val responderPhone: String,
    val responderRole: String = "Primary Parent",
    val assignedTimestamp: Long = System.currentTimeMillis(),
    val status: String = "CLAIMED", // "CLAIMED", "EN_ROUTE", "ON_SCENE", "CHILD_LOCATED", "SAFE_RESOLVED"
    val currentMilestone: String = "Responder acknowledged and responding",
    val resolutionReason: String? = null,
    val resolvedTimestamp: Long? = null
)

// =============================================================================
// v3.0.0: Multi-Child & Paired Band Registry Entities
// =============================================================================

@Entity(tableName = "paired_children")
data class PairedChild(
    @PrimaryKey
    val deviceId: String, // e.g. "SB-A1B2" or physical ESP32 MAC/salt
    val childName: String,
    val age: String,
    val bloodType: String,
    val primaryGuardianPhone: String,
    val secondaryGuardianPhone: String = "",
    val medicalConditions: String = "",
    val allergies: String = "",
    val emergencyNotes: String = "",
    val avatarColorHex: String = "#00F5D4",
    val isPhysicalEsp32: Boolean = true,
    val lastSeenTimestamp: Long = 0L,
    val batteryLevel: Int = 100
)

@Entity(tableName = "child_specific_contacts")
data class ChildSpecificContact(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val childDeviceId: String,
    val contactName: String,
    val phoneNumber: String,
    val relationship: String = "Guardian"
)

@Entity(tableName = "playbook_action_logs")
data class PlaybookActionLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val incidentId: String,
    val stepNumber: Int,
    val stepTitle: String,
    val actionTimestamp: Long = System.currentTimeMillis(),
    val actionSummary: String,
    val actor: String = "Primary Parent"
)

// =============================================================================
// v3.3.0: Parent Identity, Provisioning & Family Guardian Entities
// =============================================================================

@Entity(tableName = "parent_accounts")
data class ParentAccountEntity(
    @PrimaryKey
    val userId: String,
    val googleSub: String,
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis(),
    val authStatus: String = "AUTHENTICATED"
)

@Entity(tableName = "provisioning_challenges")
data class ProvisioningChallengeEntity(
    @PrimaryKey
    val challengeId: String,
    val childDeviceId: String,
    val oneTimeCode: String,
    val expiresAt: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "PENDING", // "PENDING", "VERIFIED", "EXPIRED", "REVOKED"
    val parentUserId: String
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt || status != "PENDING"
}

@Entity(tableName = "family_guardians")
data class FamilyGuardianEntity(
    @PrimaryKey
    val guardianId: String,
    val parentUserId: String,
    val name: String,
    val phoneNumber: String,
    val relationship: String,
    val role: String = "TRUSTED_GUARDIAN", // "TRUSTED_GUARDIAN", "EMERGENCY_CONTACT", "INSTITUTION"
    val status: String = "ACTIVE", // "ACTIVE", "PENDING", "REVOKED"
    val createdAt: Long = System.currentTimeMillis()
)
