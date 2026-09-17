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
        description = "Critical alert active. Immediate BLE alert and escalation.",
        primaryColor = Color(0xFFEF4444), // Bright warning red
        containerColor = Color(0xFFFEF2F2),
        onContainerColor = Color(0xFF991B1B)
    )
}

enum class AlertFlag(val displayName: String, val code: String) {
    MOTION_ANOMALY("Motion Anomaly", "MOTION"),
    GEOFENCE_EXIT("Safe Zone Exit", "GEOFENCE"),
    MANUAL_SOS("Manual SOS / Tamper", "SOS")
}

enum class AppMode {
    UNSELECTED,
    CHILD,
    PARENT
}

data class SafeZone(
    val latitude: Double = 37.7749,
    val longitude: Double = -122.4194,
    val radiusMeters: Float = 150f,
    val name: String = "Designated Safe Zone"
)

data class BleBeaconPayload(
    val deviceId: String,
    val riskLevel: RiskLevel,
    val timestamp: Long,
    val latitude: Double? = null,
    val longitude: Double? = null
)
