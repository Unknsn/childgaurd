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

data class VerifiedLocation(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "GPS",
    val accuracyMeters: Float = 10f,
    val confidence: LocationConfidence = LocationConfidence.MEDIUM,
    val isSimulation: Boolean = false
) {
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
    val isSimulation: Boolean = false
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
