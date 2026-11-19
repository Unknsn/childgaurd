package com.safeband.backend.dto

import java.time.Instant

// --- AUTH DTOs ---
data class GoogleAuthRequest(
    val idToken: String
)

data class RefreshTokenRequest(
    val refreshToken: String
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresInMs: Long,
    val user: UserResponse
)

data class UserResponse(
    val id: String,
    val email: String,
    val displayName: String?,
    val photoUrl: String?,
    val role: String
)

// --- CHILD DTOs ---
data class CreateChildRequest(
    val childName: String,
    val age: String? = null,
    val bloodType: String? = null,
    val primaryParentPhone: String? = null,
    val secondaryParentPhone: String? = null,
    val medicalNotes: String? = null,
    val allergies: String? = null,
    val emergencyNotes: String? = null,
    val photoUrl: String? = null,
    val pairedDeviceId: String? = null
)

data class UpdateChildRequest(
    val childName: String? = null,
    val age: String? = null,
    val bloodType: String? = null,
    val primaryParentPhone: String? = null,
    val secondaryParentPhone: String? = null,
    val medicalNotes: String? = null,
    val allergies: String? = null,
    val emergencyNotes: String? = null,
    val photoUrl: String? = null,
    val pairedDeviceId: String? = null,
    val childModePhoneId: String? = null
)

data class ChildProfileResponse(
    val id: String,
    val parentUserId: String,
    val childName: String,
    val age: String?,
    val bloodType: String?,
    val primaryParentPhone: String?,
    val secondaryParentPhone: String?,
    val medicalNotes: String?,
    val allergies: String?,
    val emergencyNotes: String?,
    val photoUrl: String?,
    val pairedDeviceId: String?,
    val childModePhoneId: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)

// --- DEVICE & PAIRING DTOs ---
data class RegisterDeviceRequest(
    val deviceId: String,
    val deviceType: String, // "ESP32_WEARABLE" or "CHILD_PHONE"
    val hardwareNodeType: String,
    val authToken: String? = null
)

data class DeviceResponse(
    val id: String,
    val deviceType: String,
    val hardwareNodeType: String,
    val configVersion: Int,
    val isProvisioned: Boolean,
    val pairedChildId: String?,
    val ownerParentId: String?,
    val tamperStatus: String,
    val batteryPercentage: Int,
    val lastHeartbeatAt: Instant?
)

data class HeartbeatRequest(
    val batteryPercentage: Int,
    val tamperStatus: String? = "PROTECTED"
)

data class CreatePairingSessionRequest(
    val childId: String,
    val deviceId: String
)

data class CompletePairingRequest(
    val challengeResponse: String
)

data class PairingSessionResponse(
    val sessionId: String,
    val parentUserId: String,
    val childId: String?,
    val deviceId: String?,
    val challengeNonce: String,
    val status: String,
    val expiresAt: Instant
)

data class ProvisioningConfigResponse(
    val deviceId: String,
    val childId: String,
    val parentId: String,
    val parentNodeIp: String,
    val parentNodePort: Int,
    val endpointId: String,
    val authToken: String,
    val version: Int
)

data class ProvisioningAckRequest(
    val version: Int,
    val status: String
)

// --- LOCATION DTOs ---
data class LocationUploadRequest(
    val childId: String,
    val deviceId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val sequenceNumber: Long,
    val fixTimestamp: Instant,
    val provider: String? = "GPS",
    val batteryPercentage: Int? = null,
    val networkStatus: String? = "CELLULAR",
    val locationQuality: String? = "GOOD"
)

data class BatchLocationUploadRequest(
    val locations: List<LocationUploadRequest>
)

data class LocationResponse(
    val id: String,
    val childId: String,
    val deviceId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val sequenceNumber: Long,
    val fixTimestamp: Instant,
    val uploadTimestamp: Instant,
    val provider: String?,
    val batteryPercentage: Int?,
    val networkStatus: String?,
    val locationQuality: String?
)

// --- GEOFENCE DTOs ---
data class CreateGeofenceRequest(
    val childId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float
)

data class UpdateGeofenceRequest(
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Float? = null,
    val isActive: Boolean? = null
)

data class GeofenceResponse(
    val id: String,
    val childId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class GeofenceIncidentRequest(
    val childId: String,
    val zoneId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val fixTimestamp: Instant
)

// --- INCIDENT & RELAY DTOs ---
data class CreateIncidentRequest(
    val childId: String,
    val deviceId: String,
    val incidentType: String, // "MANUAL_SOS", "TAMPER", "GEOFENCE_EXIT"
    val riskLevel: String = "CRITICAL",
    val source: String = "ESP32_BLE",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val addressText: String? = null,
    val startTimestamp: Instant = Instant.now()
)

data class IncidentResponse(
    val incidentId: String,
    val childId: String,
    val deviceId: String,
    val incidentType: String,
    val riskLevel: String,
    val status: String,
    val source: String,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val addressText: String?,
    val startTimestamp: Instant,
    val lastUpdatedTimestamp: Instant,
    val acknowledgedAt: Instant?,
    val resolvedAt: Instant?,
    val resolutionReason: String?,
    val childProfile: ChildProfileResponse? = null,
    val observations: List<RelayObservationResponse> = emptyList()
)

data class AcknowledgeIncidentRequest(
    val note: String? = null
)

data class ResolveIncidentRequest(
    val reason: String
)

data class RelayObservationRequest(
    val incidentId: String,
    val relayDeviceId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val rssi: Int? = null,
    val hopCount: Int = 1,
    val observedAt: Instant = Instant.now()
)

data class RelayObservationResponse(
    val observationId: String,
    val incidentId: String,
    val relayDeviceId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val rssi: Int?,
    val hopCount: Int,
    val observedAt: Instant,
    val uploadedAt: Instant
)

// --- NOTIFICATION DTOs ---
data class NotificationPreferencesDto(
    val sosAlerts: Boolean = true,
    val tamperAlerts: Boolean = true,
    val geofenceAlerts: Boolean = true,
    val lowBatteryAlerts: Boolean = true,
    val fcmToken: String? = null
)

data class TestNotificationRequest(
    val fcmToken: String,
    val title: String = "SafeBand Test Alert",
    val body: String = "This is a verified test push notification from SafeBand Backend."
)
