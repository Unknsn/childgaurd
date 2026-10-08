package com.example.model

import androidx.compose.ui.graphics.Color

/**
 * EXPLICITLY OUT OF SCOPE (future hardware/cloud phase):
 * - Dedicated wearable hardware with a physical tamper switch
 * - Long-range relay via fixed hubs
 * - Cloud-based escalation to authorities
 * - Multi-hop alert propagation beyond direct BLE range
 *
 * This prototype only needs to demonstrate the detection logic,
 * risk scoring, and local device-to-device alerting over BLE.
 */

enum class RiskLevel(
    val title: String,
    val description: String,
    val primaryColor: Color,
    val containerColor: Color,
    val onContainerColor: Color
) {
    NORMAL(
        title = "Normal",
        description = "All parameters safe. No anomalies detected.",
        primaryColor = Color(0xFF10B981), // Fresh emerald
        containerColor = Color(0xFFECFDF5),
        onContainerColor = Color(0xFF065F46)
    ),
    LOW(
        title = "Low Risk",
        description = "Single anomaly detected. Logged locally, no broadcast.",
        primaryColor = Color(0xFF3B82F6), // Calm blue
        containerColor = Color(0xFFEFF6FF),
        onContainerColor = Color(0xFF1E40AF)
    ),
    MEDIUM(
        title = "Warning",
        description = "Multiple anomalies detected. Escalating to BLE broadcast.",
        primaryColor = Color(0xFFF59E0B), // Vibrant amber
        containerColor = Color(0xFFFFFBEB),
        onContainerColor = Color(0xFF92400E)
    ),
    HIGH(
        title = "Emergency",
        description = "Guardian & trusted escalation. Imminent threat detected.",
        primaryColor = Color(0xFFEA580C), // Deep warning orange
        containerColor = Color(0xFFFFF7ED),
        onContainerColor = Color(0xFF9A3412)
    ),
    CRITICAL(
        title = "Critical Emergency",
        description = "Emergency pathway active. Manual SOS or compounding threats.",
        primaryColor = Color(0xFFDC2626), // Urgent crimson red
        containerColor = Color(0xFFFEF2F2),
        onContainerColor = Color(0xFF991B1B)
    )
}

enum class AlertFlag(val displayName: String, val code: String) {
    MOTION_ANOMALY("Motion Anomaly", "MOTION"),
    GEOFENCE_EXIT("Safe Zone Exit", "GEOFENCE"),
    MANUAL_SOS("Manual SOS", "SOS"),
    ROUTE_DEVIATION("Route Deviation", "ROUTE"),
    TAMPER("Band Tamper / Cut", "TAMPER"),
    BOUNDARY_VIOLATION("Repeated Boundary Violation", "BOUNDARY")
}

enum class GeofenceState(val displayName: String) {
    SAFE("Inside Safe Zone"),
    APPROACHING("Approaching Boundary"),
    EXIT_PENDING("Exit Pending Confirmation"),
    OUTSIDE("Outside Safe Zone"),
    REENTERED("Re-entered Safe Zone")
}

enum class RouteState(val displayName: String) {
    ON_ROUTE("On Designated Route"),
    APPROACHING_EDGE("Approaching Route Corridor Edge"),
    ROUTE_DEVIATION("Route Deviation Detected"),
    UNKNOWN("Route Not Active")
}

enum class SyncStatus(val displayName: String) {
    LOCAL_ONLY("Local Node Only"),
    PENDING_SYNC("Pending Relay/Sync"),
    RELAYED("Relayed via Peer Node"),
    ACKNOWLEDGED("Guardian Acknowledged"),
    SYNCED("Synchronized"),
    EXPIRED("Sync Window Expired")
}

enum class TrustRole(val displayName: String) {
    OWNER("Primary Child Wearer / Device"),
    TRUSTED_GUARDIAN("Verified Guardian / Parent"),
    INSTITUTION("Authorized School / Facility"),
    EMERGENCY_AUTHORITY("First Responder / Police"),
    ANONYMOUS_RELAY("Anonymous Mesh Relay Node")
}

enum class NodeConnectionState(val displayName: String) {
    ONLINE("Active Beaconing"),
    NEARBY("Direct BLE Range"),
    RELAYED("Observed via Peer Relay"),
    OFFLINE("Signal Lost / Stale"),
    UNKNOWN("Awaiting Initial Discovery")
}

data class SafetyNode(
    val nodeId: String,
    val ephemeralId: String = "",
    val role: TrustRole = TrustRole.ANONYMOUS_RELAY,
    val rssi: Int = -100,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val trustLevel: TrustRole = TrustRole.ANONYMOUS_RELAY,
    val connectionState: NodeConnectionState = NodeConnectionState.UNKNOWN
) {
    val isNearby: Boolean
        get() = connectionState == NodeConnectionState.NEARBY || connectionState == NodeConnectionState.ONLINE

    fun getSignalStrengthCategory(): String {
        return when {
            rssi >= -65 -> "Strong"
            rssi >= -85 -> "Moderate"
            rssi > -110 -> "Weak"
            else -> "Lost"
        }
    }
}

data class RouteWaypoint(
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

data class TrustedRoute(
    val id: String = "default_route",
    val name: String = "Home to School",
    val waypoints: List<RouteWaypoint> = emptyList(),
    val corridorRadiusMeters: Float = 60f,
    val isEnabled: Boolean = false
)

enum class LocationConfidence(val displayName: String) {
    HIGH("High Confidence"),
    MEDIUM("Medium Confidence"),
    LOW("Low Confidence"),
    UNKNOWN("Unknown Confidence")
}

object LocationSource {
    const val CHILD_GPS = "CHILD_GPS"
    const val GUARDIAN_PHONE = "GUARDIAN_PHONE"
    const val RELAY_OBSERVER = "RELAY_OBSERVER"
    const val SAFE_ZONE = "SAFE_ZONE"
    const val SIMULATION = "SIMULATION"
    const val SIMULATED = "SIMULATED"
    const val PHYSICAL_CHILD_NODE = "PHYSICAL_CHILD_NODE"
    const val NONE = "NONE"
    const val UNKNOWN = "UNKNOWN"

    fun formatSource(source: String): String {
        return when (source) {
            CHILD_GPS -> "Child GPS location"
            GUARDIAN_PHONE -> "Guardian phone location"
            RELAY_OBSERVER -> "Relay verified location"
            SAFE_ZONE -> "Safe Zone location"
            SIMULATION, SIMULATED -> "Simulated location"
            PHYSICAL_CHILD_NODE -> "Physical child node (No GPS)"
            else -> "Unknown location"
        }
    }
}

enum class MonitoringServiceState(val displayName: String) {
    DISABLED("BLE Guardian Scanner OFF"),
    STARTING("BLE Guardian Scanner STARTING"),
    ACTIVE("BLE Guardian Scanner ACTIVE"),
    PAUSED("BLE Guardian Scanner PAUSED"),
    ERROR("BLE Guardian Scanner ERROR")
}

data class VerifiedLocation(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "GPS",
    val accuracyMeters: Float = 10f,
    val confidence: LocationConfidence = LocationConfidence.MEDIUM,
    val isSimulation: Boolean = false,
    val resolvedAddress: String? = null,
    val addressDetails: GeoAddress? = null,
    val addressTimestamp: Long? = null,
    val isResolvingAddress: Boolean = false,
    val isAddressUnavailable: Boolean = false
) {
    fun isStale(now: Long = System.currentTimeMillis()): Boolean {
        return (now - timestamp) >= 5 * 60 * 1000L // 5 minutes
    }

    fun getDisplayTimeOrStaleString(now: Long = System.currentTimeMillis()): String {
        val ageMs = (now - timestamp).coerceAtLeast(0L)
        val ageSeconds = ageMs / 1000L
        val ageMinutes = ageSeconds / 60L
        return when {
            ageMinutes >= 5L -> "Last verified $ageMinutes min ago"
            ageSeconds < 5L -> "Verified just now"
            ageSeconds < 60L -> "Verified $ageSeconds sec ago"
            else -> "Verified $ageMinutes min ago"
        }
    }

    fun getRelativeTimeString(now: Long = System.currentTimeMillis()): String {
        val ageMs = (now - timestamp).coerceAtLeast(0L)
        val ageSeconds = ageMs / 1000L
        return when {
            ageSeconds < 5L -> "Verified just now"
            ageSeconds < 60L -> "Verified ${ageSeconds}s ago"
            ageSeconds < 3600L -> "Verified ${ageSeconds / 60L}m ago"
            else -> "Verified >1h ago"
        }
    }

    companion object {
        fun calculateConfidence(accuracyMeters: Float, ageMs: Long): LocationConfidence {
            return when {
                accuracyMeters <= 20f && ageMs < 30_000L -> LocationConfidence.HIGH
                accuracyMeters <= 50f && ageMs < 60_000L -> LocationConfidence.MEDIUM
                accuracyMeters > 0f -> LocationConfidence.LOW
                else -> LocationConfidence.UNKNOWN
            }
        }
    }
}

enum class AppMode {
    UNSELECTED,
    CHILD,
    PARENT
}

enum class IncidentStage(val displayName: String) {
    NONE("Normal"),
    WARNING("Anomaly Warning"),
    CONFIRMATION_PENDING("Confirmation Countdown"),
    CONFIRMED("Emergency Confirmed"),
    BROADCASTING("Beacon Broadcasting"),
    ACTIVE("Alert Active"),
    SILENCED("Alert Silenced"),
    CANCELLED("Alert Cancelled"),
    RESOLVED("Incident Resolved")
}

data class IncidentObservation(
    val observationId: String = "",
    val sourceNodeId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val riskLevel: RiskLevel = RiskLevel.MEDIUM,
    val hopCount: Int = 0,
    val rssi: Int = -75,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
)

data class SafetyIncident(
    val incidentId: String = "",
    val deviceId: String = "",
    val stage: IncidentStage = IncidentStage.NONE,
    val riskLevel: RiskLevel = RiskLevel.NORMAL,
    val triggerFlags: Set<AlertFlag> = emptySet(),
    val startTimestamp: Long = System.currentTimeMillis(),
    val lastUpdatedTimestamp: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val verifiedLocation: VerifiedLocation? = null,
    val address: GeoAddress? = null,
    val childBioProfile: ChildBioProfile? = null,
    val emergencyContacts: List<com.example.data.local.TrustedContact> = emptyList(),
    val resolutionReason: String? = null,
    val isSimulation: Boolean = false,
    val observations: List<IncidentObservation> = emptyList(),
    val observationCount: Int = 1,
    val sourceNodes: List<String> = emptyList(),
    val maxHopCount: Int = 0,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
) {
    val isEmergencyActive: Boolean
        get() = stage in setOf(
            IncidentStage.CONFIRMED,
            IncidentStage.BROADCASTING,
            IncidentStage.ACTIVE,
            IncidentStage.SILENCED
        )

    val isSirenAudible: Boolean
        get() = stage == IncidentStage.ACTIVE && (riskLevel == RiskLevel.CRITICAL || riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.MEDIUM)
}

data class SafeZone(
    val latitude: Double = 37.7749,
    val longitude: Double = -122.4194,
    val radiusMeters: Float = 150f,
    val name: String = "Designated Safe Zone"
)

data class GeoAddress(
    val fullAddress: String,
    val street: String = "",
    val area: String = "",
    val city: String = "",
    val state: String = "",
    val pinCode: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
) {
    fun formattedSummary(): String {
        val parts = mutableListOf<String>()
        if (street.isNotBlank()) parts.add(street)
        if (area.isNotBlank()) parts.add(area)
        if (city.isNotBlank()) parts.add(city)
        if (state.isNotBlank()) parts.add(state)
        val text = parts.joinToString(", ")
        return if (pinCode.isNotBlank()) {
            if (text.isNotBlank()) "$text - $pinCode" else pinCode
        } else {
            text.ifEmpty { fullAddress }
        }
    }
}

data class BleBeaconPayload(
    val deviceId: String,
    val riskLevel: RiskLevel,
    val timestamp: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val childBioProfile: ChildBioProfile? = null,
    val emergencyContacts: List<com.example.data.local.TrustedContact> = emptyList(),
    val address: GeoAddress? = null,
    val isSimulation: Boolean = false,
    val ephemeralId: String = "",
    val trustRole: TrustRole = TrustRole.ANONYMOUS_RELAY,
    val hopCount: Int = 0
)

data class ChildBioProfile(
    val childName: String = "Aarav",
    val age: String = "8",
    val bloodType: String = "O+",
    val primaryParentPhone: String = "+91 98765 43210",
    val secondaryContactPhone: String = "+91 98111 22334",
    val medicalConditions: String = "Asthma (Carries Inhaler)",
    val allergies: String = "Severe Peanut Allergy",
    val emergencyNotes: String = "Wears medical ID band. In emergency call parents or 112 immediately."
)

// =========================================================================
// Batch E: Timeline V2 & Incident Replay Models
// =========================================================================

data class TimelineItem(
    val id: String,
    val timestamp: Long,
    val title: String,
    val description: String,
    val riskLevel: RiskLevel,
    val flagType: String,
    val outcome: String,
    val syncStatus: SyncStatus,
    val observationCount: Int = 1,
    val sourceNode: String = "",
    val hopCount: Int = 0,
    val verifiedLocation: VerifiedLocation? = null,
    val isSimulation: Boolean = false,
    val rawEventId: Long? = null
)

data class ReplayStep(
    val stepIndex: Int,
    val label: String,
    val description: String,
    val geofenceState: GeofenceState,
    val routeState: RouteState,
    val riskLevel: RiskLevel,
    val stage: IncidentStage,
    val latitude: Double,
    val longitude: Double,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val observationCount: Int = 1
)

data class IncidentReplayState(
    val isPlaying: Boolean = false,
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 0,
    val currentStep: ReplayStep? = null,
    val isCompleted: Boolean = false
)

// =========================================================================
// Batch F: Battery & Connectivity Models
// =========================================================================

enum class BatteryState(val displayName: String) {
    NORMAL("Normal"),
    LOW("Low Battery"),
    CRITICAL("Critically Low")
}

enum class OperatingMode(val displayName: String) {
    NORMAL("Normal Mode"),
    POWER_SAVING("Power Saving Mode")
}

data class BatteryInfo(
    val percentage: Int? = null,
    val isCharging: Boolean = false,
    val batteryState: BatteryState = BatteryState.NORMAL,
    val operatingMode: OperatingMode = OperatingMode.NORMAL
) {
    fun getDisplaySummary(): String {
        val pctStr = percentage?.let { "$it%" } ?: "Unknown"
        val chargeStr = if (isCharging) " (Charging)" else ""
        return when (batteryState) {
            BatteryState.CRITICAL -> "Battery $pctStr$chargeStr • Critically low, safety monitoring affected"
            BatteryState.LOW -> "Battery $pctStr$chargeStr • Power saving recommended"
            BatteryState.NORMAL -> "Battery $pctStr$chargeStr • Normal operation"
        }
    }
}

enum class ConnectivityTier(val displayName: String) {
    ONLINE("Internet Connected"),
    NEARBY("Direct BLE Peer"),
    RELAYED("Mesh Relayed"),
    OFFLINE("Signal Lost / Offline"),
    UNKNOWN("Awaiting Status")
}

data class ConnectivityStatus(
    val isInternetAvailable: Boolean = false,
    val isBleAvailable: Boolean = false,
    val isBleAdvertising: Boolean = false,
    val hasNearbyPeer: Boolean = false,
    val hasRelayedPeer: Boolean = false,
    val lastSeenPeerTimestamp: Long = 0L,
    val tier: ConnectivityTier = ConnectivityTier.UNKNOWN
)

// =========================================================================
// Batch G: Evaluation Scenario Models
// =========================================================================

enum class ScenarioId(val displayName: String, val description: String) {
    NORMAL_DAY("1. Normal Transit", "Child transit along safe corridor to school without anomalies"),
    SAFE_ZONE_EXIT("2. Safe Zone Exit", "Child exits safe zone with grace period, countdown, and escalation"),
    MOTION_ANOMALY("3. Motion Anomaly", "Fall / sudden stillness triggers motion anomaly escalation"),
    TAMPER_MOVEMENT("4. Tamper + Movement", "Physical band tamper paired with motion triggers high emergency"),
    OFFLINE_EMERGENCY("5. Offline Emergency", "Emergency saved locally and queued for peer sync"),
    MULTI_RELAY_EMERGENCY("6. Multi-Relay Emergency", "Incident routed across 2 mesh peer relays with duplicate suppression"),
    MANUAL_SOS("7. Manual SOS", "Child holds SOS for 2s triggering immediate CRITICAL escalation")
}

data class ScenarioStatus(
    val activeScenarioId: ScenarioId? = null,
    val isRunning: Boolean = false,
    val currentStepDescription: String = "",
    val progressFraction: Float = 0f,
    val isCompleted: Boolean = false
)

