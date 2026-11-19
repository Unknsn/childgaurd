package com.safeband.backend.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "users")
data class UserEntity(
    @Id
    val id: String,

    @Column(nullable = false, unique = true)
    var email: String,

    @Column(name = "display_name")
    var displayName: String? = null,

    @Column(name = "photo_url", length = 1024)
    var photoUrl: String? = null,

    @Column(name = "google_sub", unique = true)
    var googleSub: String? = null,

    @Column(nullable = false, length = 32)
    var role: String = "PARENT",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "last_login_at", nullable = false)
    var lastLoginAt: Instant = Instant.now()
)

@Entity
@Table(name = "child_profiles")
data class ChildProfileEntity(
    @Id
    val id: String,

    @Column(name = "parent_user_id", nullable = false)
    val parentUserId: String,

    @Column(name = "child_name", nullable = false, length = 100)
    var childName: String,

    @Column(length = 10)
    var age: String? = null,

    @Column(name = "blood_type", length = 10)
    var bloodType: String? = null,

    @Column(name = "primary_parent_phone", length = 32)
    var primaryParentPhone: String? = null,

    @Column(name = "secondary_parent_phone", length = 32)
    var secondaryParentPhone: String? = null,

    @Column(name = "medical_notes", columnDefinition = "TEXT")
    var medicalNotes: String? = null,

    @Column(columnDefinition = "TEXT")
    var allergies: String? = null,

    @Column(name = "emergency_notes", columnDefinition = "TEXT")
    var emergencyNotes: String? = null,

    @Column(name = "photo_url", length = 1024)
    var photoUrl: String? = null,

    @Column(name = "paired_device_id", length = 64)
    var pairedDeviceId: String? = null,

    @Column(name = "child_mode_phone_id", length = 64)
    var childModePhoneId: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

@Entity
@Table(name = "devices")
data class DeviceEntity(
    @Id
    val id: String,

    @Column(name = "device_type", nullable = false, length = 32)
    var deviceType: String,

    @Column(name = "hardware_node_type", nullable = false, length = 64)
    var hardwareNodeType: String,

    @Column(name = "auth_token", nullable = false)
    var authToken: String,

    @Column(name = "config_version", nullable = false)
    var configVersion: Int = 1,

    @Column(name = "is_provisioned", nullable = false)
    var isProvisioned: Boolean = false,

    @Column(name = "paired_child_id")
    var pairedChildId: String? = null,

    @Column(name = "owner_parent_id")
    var ownerParentId: String? = null,

    @Column(name = "last_heartbeat_at")
    var lastHeartbeatAt: Instant? = null,

    @Column(name = "tamper_status", length = 32)
    var tamperStatus: String = "PROTECTED",

    @Column(name = "tamper_authorized_until")
    var tamperAuthorizedUntil: Instant? = null,

    @Column(name = "battery_percentage")
    var batteryPercentage: Int = 100,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "pairing_sessions")
data class PairingSessionEntity(
    @Id
    @Column(name = "session_id")
    val sessionId: String,

    @Column(name = "parent_user_id", nullable = false)
    val parentUserId: String,

    @Column(name = "child_id")
    var childId: String? = null,

    @Column(name = "device_id")
    var deviceId: String? = null,

    @Column(name = "challenge_nonce", nullable = false)
    val challengeNonce: String,

    @Column(nullable = false, length = 32)
    var status: String = "PENDING",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant
)

@Entity
@Table(name = "locations")
data class LocationEntity(
    @Id
    val id: String,

    @Column(name = "child_id", nullable = false)
    val childId: String,

    @Column(name = "device_id", nullable = false)
    val deviceId: String,

    @Column(nullable = false)
    val latitude: Double,

    @Column(nullable = false)
    val longitude: Double,

    @Column(name = "accuracy_meters")
    val accuracyMeters: Float? = null,

    @Column(name = "sequence_number", nullable = false)
    val sequenceNumber: Long,

    @Column(name = "fix_timestamp", nullable = false)
    val fixTimestamp: Instant,

    @Column(name = "upload_timestamp", nullable = false)
    val uploadTimestamp: Instant = Instant.now(),

    @Column(length = 32)
    val provider: String? = "GPS",

    @Column(name = "battery_percentage")
    val batteryPercentage: Int? = null,

    @Column(name = "network_status", length = 32)
    val networkStatus: String? = null,

    @Column(name = "location_quality", length = 32)
    val locationQuality: String? = null
)

@Entity
@Table(name = "geofence_zones")
data class GeofenceZoneEntity(
    @Id
    val id: String,

    @Column(name = "child_id", nullable = false)
    val childId: String,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(nullable = false)
    var latitude: Double,

    @Column(nullable = false)
    var longitude: Double,

    @Column(name = "radius_meters", nullable = false)
    var radiusMeters: Float,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

@Entity
@Table(name = "incidents")
data class IncidentEntity(
    @Id
    @Column(name = "incident_id")
    val incidentId: String,

    @Column(name = "child_id", nullable = false)
    val childId: String,

    @Column(name = "device_id", nullable = false)
    val deviceId: String,

    @Column(name = "incident_type", nullable = false, length = 32)
    val incidentType: String,

    @Column(name = "risk_level", nullable = false, length = 32)
    var riskLevel: String,

    @Column(nullable = false, length = 32)
    var status: String = "ACTIVE",

    @Column(nullable = false, length = 32)
    val source: String,

    var latitude: Double? = null,
    var longitude: Double? = null,

    @Column(name = "accuracy_meters")
    var accuracyMeters: Float? = null,

    @Column(name = "address_text", length = 512)
    var addressText: String? = null,

    @Column(name = "start_timestamp", nullable = false)
    val startTimestamp: Instant,

    @Column(name = "last_updated_timestamp", nullable = false)
    var lastUpdatedTimestamp: Instant = Instant.now(),

    @Column(name = "acknowledged_at")
    var acknowledgedAt: Instant? = null,

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null,

    @Column(name = "resolution_reason", length = 512)
    var resolutionReason: String? = null
)

@Entity
@Table(name = "relay_observations")
data class RelayObservationEntity(
    @Id
    @Column(name = "observation_id")
    val observationId: String,

    @Column(name = "incident_id", nullable = false)
    val incidentId: String,

    @Column(name = "relay_device_id", nullable = false)
    val relayDeviceId: String,

    @Column(nullable = false)
    val latitude: Double,

    @Column(nullable = false)
    val longitude: Double,

    @Column(name = "accuracy_meters")
    val accuracyMeters: Float? = null,

    val rssi: Int? = null,

    @Column(name = "hop_count")
    val hopCount: Int = 1,

    @Column(name = "observed_at", nullable = false)
    val observedAt: Instant,

    @Column(name = "uploaded_at", nullable = false)
    val uploadedAt: Instant = Instant.now()
)

@Entity
@Table(name = "notification_records")
data class NotificationRecordEntity(
    @Id
    val id: String,

    @Column(name = "user_id", nullable = false)
    val userId: String,

    @Column(name = "incident_id")
    val incidentId: String? = null,

    @Column(nullable = false)
    val title: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val body: String,

    @Column(name = "channel_id", nullable = false)
    val channelId: String,

    @Column(name = "delivery_status", nullable = false, length = 32)
    var deliveryStatus: String,

    @Column(name = "sent_at", nullable = false)
    val sentAt: Instant = Instant.now()
)

@Entity
@Table(name = "audit_logs")
data class AuditLogEntity(
    @Id
    val id: String,

    @Column(name = "user_id")
    val userId: String? = null,

    @Column(nullable = false, length = 64)
    val action: String,

    @Column(name = "entity_type", length = 64)
    val entityType: String? = null,

    @Column(name = "entity_id", length = 64)
    val entityId: String? = null,

    @Column(name = "ip_address", length = 64)
    val ipAddress: String? = null,

    @Column(columnDefinition = "TEXT")
    val details: String? = null,

    @Column(nullable = false)
    val timestamp: Instant = Instant.now()
)
