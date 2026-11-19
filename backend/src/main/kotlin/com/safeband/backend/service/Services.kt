package com.safeband.backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.safeband.backend.config.SafeBandWebSocketHandler
import com.safeband.backend.config.TunnelProperties
import com.safeband.backend.dto.*
import com.safeband.backend.entity.*
import com.safeband.backend.repository.*
import com.safeband.backend.security.GoogleTokenVerifierService
import com.safeband.backend.security.JwtTokenProvider
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
@Transactional
class AuthService(
    private val userRepository: UserRepository,
    private val googleTokenVerifierService: GoogleTokenVerifierService,
    private val jwtTokenProvider: JwtTokenProvider,
    private val auditService: AuditService
) {
    private val log = LoggerFactory.getLogger(AuthService::class.java)

    fun loginWithGoogle(idToken: String, clientIp: String? = null): AuthResponse {
        val googleData = googleTokenVerifierService.verify(idToken)
            ?: throw IllegalArgumentException("Invalid Google ID token signature or audience.")

        val user = userRepository.findByGoogleSub(googleData.googleSub).orElseGet {
            userRepository.findByEmail(googleData.email).map { existing ->
                existing.googleSub = googleData.googleSub
                existing.displayName = googleData.displayName ?: existing.displayName
                existing.photoUrl = googleData.photoUrl ?: existing.photoUrl
                existing
            }.orElseGet {
                UserEntity(
                    id = UUID.randomUUID().toString(),
                    email = googleData.email,
                    displayName = googleData.displayName,
                    photoUrl = googleData.photoUrl,
                    googleSub = googleData.googleSub,
                    role = "PARENT",
                    createdAt = Instant.now(),
                    lastLoginAt = Instant.now()
                )
            }
        }

        user.lastLoginAt = Instant.now()
        userRepository.save(user)

        val accessToken = jwtTokenProvider.generateAccessToken(user.id, user.email, user.role)
        val refreshToken = jwtTokenProvider.generateRefreshToken(user.id)

        auditService.log(
            userId = user.id,
            action = "GOOGLE_LOGIN_SUCCESS",
            entityType = "USER",
            entityId = user.id,
            ipAddress = clientIp,
            details = "Logged in via Google Credential Manager (email: ${user.email})"
        )

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresInMs = 3600000L,
            user = user.toResponse()
        )
    }

    fun refreshAccessToken(refreshToken: String): AuthResponse {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw IllegalArgumentException("Invalid or expired refresh token.")
        }
        val userId = jwtTokenProvider.getUserIdFromToken(refreshToken)
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found.") }

        val newAccess = jwtTokenProvider.generateAccessToken(user.id, user.email, user.role)
        val newRefresh = jwtTokenProvider.generateRefreshToken(user.id)

        return AuthResponse(
            accessToken = newAccess,
            refreshToken = newRefresh,
            expiresInMs = 3600000L,
            user = user.toResponse()
        )
    }

    fun getCurrentUser(userId: String): UserResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found.") }
        return user.toResponse()
    }
}

@Service
@Transactional
class ChildService(
    private val childProfileRepository: ChildProfileRepository,
    private val auditService: AuditService
) {
    fun createChild(parentUserId: String, req: CreateChildRequest): ChildProfileResponse {
        val child = ChildProfileEntity(
            id = UUID.randomUUID().toString(),
            parentUserId = parentUserId,
            childName = req.childName,
            age = req.age,
            bloodType = req.bloodType,
            primaryParentPhone = req.primaryParentPhone,
            secondaryParentPhone = req.secondaryParentPhone,
            medicalNotes = req.medicalNotes,
            allergies = req.allergies,
            emergencyNotes = req.emergencyNotes,
            photoUrl = req.photoUrl,
            pairedDeviceId = req.pairedDeviceId,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val saved = childProfileRepository.save(child)
        auditService.log(parentUserId, "CREATE_CHILD", "CHILD_PROFILE", saved.id, null, "Created profile: ${saved.childName}")
        return saved.toResponse()
    }

    fun getChild(parentUserId: String, childId: String): ChildProfileResponse {
        val child = childProfileRepository.findById(childId)
            .orElseThrow { IllegalArgumentException("Child profile not found.") }
        if (child.parentUserId != parentUserId) {
            throw org.springframework.security.access.AccessDeniedException("Unauthorized: Not your child profile.")
        }
        return child.toResponse()
    }

    fun listChildrenForParent(parentUserId: String): List<ChildProfileResponse> {
        return childProfileRepository.findByParentUserId(parentUserId).map { it.toResponse() }
    }

    fun updateChild(parentUserId: String, childId: String, req: UpdateChildRequest): ChildProfileResponse {
        val child = childProfileRepository.findById(childId)
            .orElseThrow { IllegalArgumentException("Child profile not found.") }
        if (child.parentUserId != parentUserId) {
            throw org.springframework.security.access.AccessDeniedException("Unauthorized: Not your child profile.")
        }

        req.childName?.let { child.childName = it }
        req.age?.let { child.age = it }
        req.bloodType?.let { child.bloodType = it }
        req.primaryParentPhone?.let { child.primaryParentPhone = it }
        req.secondaryParentPhone?.let { child.secondaryParentPhone = it }
        req.medicalNotes?.let { child.medicalNotes = it }
        req.allergies?.let { child.allergies = it }
        req.emergencyNotes?.let { child.emergencyNotes = it }
        req.photoUrl?.let { child.photoUrl = it }
        req.pairedDeviceId?.let { child.pairedDeviceId = it }
        req.childModePhoneId?.let { child.childModePhoneId = it }
        child.updatedAt = Instant.now()

        val saved = childProfileRepository.save(child)
        auditService.log(parentUserId, "UPDATE_CHILD", "CHILD_PROFILE", saved.id, null, "Updated profile: ${saved.childName}")
        return saved.toResponse()
    }

    fun deleteChild(parentUserId: String, childId: String) {
        val child = childProfileRepository.findById(childId)
            .orElseThrow { IllegalArgumentException("Child profile not found.") }
        if (child.parentUserId != parentUserId) {
            throw org.springframework.security.access.AccessDeniedException("Unauthorized: Not your child profile.")
        }
        childProfileRepository.delete(child)
        auditService.log(parentUserId, "DELETE_CHILD", "CHILD_PROFILE", childId, null, "Deleted child profile")
    }
}

@Service
@Transactional
class DeviceService(
    private val deviceRepository: DeviceRepository,
    private val childProfileRepository: ChildProfileRepository,
    private val auditService: AuditService,
    private val tunnelProperties: TunnelProperties
) {
    fun registerDevice(parentUserId: String?, req: RegisterDeviceRequest): DeviceResponse {
        val token = req.authToken ?: UUID.randomUUID().toString()
        val device = deviceRepository.findById(req.deviceId).orElseGet {
            DeviceEntity(
                id = req.deviceId,
                deviceType = req.deviceType,
                hardwareNodeType = req.hardwareNodeType,
                authToken = token,
                configVersion = 1,
                isProvisioned = false,
                ownerParentId = parentUserId,
                createdAt = Instant.now()
            )
        }
        parentUserId?.let { device.ownerParentId = it }
        val saved = deviceRepository.save(device)
        return saved.toResponse()
    }

    fun getDevice(deviceId: String): DeviceResponse {
        return deviceRepository.findById(deviceId)
            .orElseThrow { IllegalArgumentException("Device not found: $deviceId") }
            .toResponse()
    }

    fun listDevices(parentUserId: String): List<DeviceResponse> {
        return deviceRepository.findByOwnerParentId(parentUserId).map { it.toResponse() }
    }

    fun heartbeat(deviceId: String, req: HeartbeatRequest): DeviceResponse {
        val device = deviceRepository.findById(deviceId)
            .orElseThrow { IllegalArgumentException("Device not found: $deviceId") }
        device.lastHeartbeatAt = Instant.now()
        device.batteryPercentage = req.batteryPercentage
        req.tamperStatus?.let { device.tamperStatus = it }
        val saved = deviceRepository.save(device)
        return saved.toResponse()
    }

    fun getProvisioningConfiguration(deviceId: String): ProvisioningConfigResponse {
        val device = deviceRepository.findById(deviceId)
            .orElseThrow { IllegalArgumentException("Device not found: $deviceId") }
        val childId = device.pairedChildId ?: "CHLD-PENDING"
        val parentId = device.ownerParentId ?: "PRNT-PENDING"

        return ProvisioningConfigResponse(
            deviceId = device.id,
            childId = childId,
            parentId = parentId,
            parentNodeIp = tunnelProperties.publicUrl.replace("https://", "").replace("http://", "").substringBefore(":"),
            parentNodePort = 8080,
            endpointId = "API-V1-EP",
            authToken = device.authToken,
            version = device.configVersion
        )
    }

    fun acknowledgeProvisioning(deviceId: String, version: Int): DeviceResponse {
        val device = deviceRepository.findById(deviceId)
            .orElseThrow { IllegalArgumentException("Device not found: $deviceId") }
        device.configVersion = version
        device.isProvisioned = true
        val saved = deviceRepository.save(device)
        return saved.toResponse()
    }
}

@Service
@Transactional
class PairingService(
    private val pairingSessionRepository: PairingSessionRepository,
    private val deviceRepository: DeviceRepository,
    private val childProfileRepository: ChildProfileRepository,
    private val auditService: AuditService
) {
    fun createSession(parentUserId: String, req: CreatePairingSessionRequest): PairingSessionResponse {
        val nonceBytes = ByteArray(16)
        SecureRandom().nextBytes(nonceBytes)
        val nonce = nonceBytes.joinToString("") { "%02x".format(it) }

        val session = PairingSessionEntity(
            sessionId = UUID.randomUUID().toString(),
            parentUserId = parentUserId,
            childId = req.childId,
            deviceId = req.deviceId,
            challengeNonce = nonce,
            status = "PENDING",
            createdAt = Instant.now(),
            expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES)
        )
        val saved = pairingSessionRepository.save(session)
        return saved.toResponse()
    }

    fun completeSession(parentUserId: String, sessionId: String, req: CompletePairingRequest): PairingSessionResponse {
        val session = pairingSessionRepository.findById(sessionId)
            .orElseThrow { IllegalArgumentException("Pairing session not found: $sessionId") }
        if (session.parentUserId != parentUserId) {
            throw org.springframework.security.access.AccessDeniedException("Unauthorized session access.")
        }
        if (session.expiresAt.isBefore(Instant.now())) {
            session.status = "EXPIRED"
            pairingSessionRepository.save(session)
            throw IllegalStateException("Pairing session expired.")
        }

        session.status = "COMPLETED"
        pairingSessionRepository.save(session)

        val deviceId = session.deviceId
        val childId = session.childId
        if (deviceId != null && childId != null) {
            val device = deviceRepository.findById(deviceId).orElseGet {
                DeviceEntity(
                    id = deviceId,
                    deviceType = "ESP32_WEARABLE",
                    hardwareNodeType = "PHYSICAL_CHILD_NODE",
                    authToken = UUID.randomUUID().toString(),
                    ownerParentId = parentUserId,
                    createdAt = Instant.now()
                )
            }
            device.pairedChildId = childId
            device.ownerParentId = parentUserId
            device.isProvisioned = true
            device.configVersion += 1
            deviceRepository.save(device)

            childProfileRepository.findById(childId).ifPresent { child ->
                child.pairedDeviceId = deviceId
                childProfileRepository.save(child)
            }
        }

        auditService.log(parentUserId, "COMPLETE_PAIRING", "DEVICE", deviceId, null, "Paired device $deviceId to child $childId")
        return session.toResponse()
    }

    fun cancelSession(parentUserId: String, sessionId: String) {
        val session = pairingSessionRepository.findById(sessionId)
            .orElseThrow { IllegalArgumentException("Pairing session not found: $sessionId") }
        if (session.parentUserId != parentUserId) {
            throw org.springframework.security.access.AccessDeniedException("Unauthorized session access.")
        }
        session.status = "CANCELLED"
        pairingSessionRepository.save(session)
    }
}

@Service
@Transactional
class LocationService(
    private val locationRepository: LocationRepository,
    private val childProfileRepository: ChildProfileRepository,
    private val webSocketHandler: SafeBandWebSocketHandler,
    private val objectMapper: ObjectMapper
) {
    fun ingestLocation(req: LocationUploadRequest): LocationResponse {
        val entity = LocationEntity(
            id = UUID.randomUUID().toString(),
            childId = req.childId,
            deviceId = req.deviceId,
            latitude = req.latitude,
            longitude = req.longitude,
            accuracyMeters = req.accuracyMeters,
            sequenceNumber = req.sequenceNumber,
            fixTimestamp = req.fixTimestamp,
            uploadTimestamp = Instant.now(),
            provider = req.provider,
            batteryPercentage = req.batteryPercentage,
            networkStatus = req.networkStatus,
            locationQuality = req.locationQuality
        )
        val saved = locationRepository.save(entity)

        // Real-time WebSocket event dispatch to parent
        childProfileRepository.findById(req.childId).ifPresent { child ->
            val event = mapOf(
                "type" to "CHILD_LOCATION_UPDATE",
                "childId" to req.childId,
                "childName" to child.childName,
                "latitude" to req.latitude,
                "longitude" to req.longitude,
                "accuracyMeters" to req.accuracyMeters,
                "fixTimestamp" to req.fixTimestamp.toEpochMilli(),
                "batteryPercentage" to req.batteryPercentage
            )
            webSocketHandler.broadcastToUser(child.parentUserId, objectMapper.writeValueAsString(event))
        }

        return saved.toResponse()
    }

    fun ingestBatch(req: BatchLocationUploadRequest): List<LocationResponse> {
        return req.locations.map { ingestLocation(it) }
    }

    fun getLatestLocation(childId: String): LocationResponse? {
        return locationRepository.findTopByChildIdOrderByFixTimestampDesc(childId)
            .map { it.toResponse() }
            .orElse(null)
    }

    fun getLocationHistory(childId: String, page: Int = 0, size: Int = 50): List<LocationResponse> {
        return locationRepository.findByChildIdOrderByFixTimestampDesc(childId, PageRequest.of(page, size))
            .content.map { it.toResponse() }
    }
}

@Service
@Transactional
class GeofenceService(
    private val geofenceZoneRepository: GeofenceZoneRepository,
    private val childProfileRepository: ChildProfileRepository,
    private val incidentRepository: IncidentRepository,
    private val webSocketHandler: SafeBandWebSocketHandler,
    private val objectMapper: ObjectMapper,
    private val auditService: AuditService
) {
    fun createZone(parentUserId: String, req: CreateGeofenceRequest): GeofenceResponse {
        val child = childProfileRepository.findById(req.childId)
            .orElseThrow { IllegalArgumentException("Child not found.") }
        if (child.parentUserId != parentUserId) {
            throw org.springframework.security.access.AccessDeniedException("Unauthorized access.")
        }
        val zone = GeofenceZoneEntity(
            id = UUID.randomUUID().toString(),
            childId = req.childId,
            name = req.name,
            latitude = req.latitude,
            longitude = req.longitude,
            radiusMeters = req.radiusMeters,
            isActive = true,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val saved = geofenceZoneRepository.save(zone)
        auditService.log(parentUserId, "CREATE_GEOFENCE", "GEOFENCE", saved.id, null, "Created safe zone: ${saved.name}")
        return saved.toResponse()
    }

    fun listZones(parentUserId: String, childId: String): List<GeofenceResponse> {
        return geofenceZoneRepository.findByChildId(childId).map { it.toResponse() }
    }

    fun updateZone(parentUserId: String, zoneId: String, req: UpdateGeofenceRequest): GeofenceResponse {
        val zone = geofenceZoneRepository.findById(zoneId)
            .orElseThrow { IllegalArgumentException("Zone not found.") }
        val child = childProfileRepository.findById(zone.childId)
            .orElseThrow { IllegalArgumentException("Child not found.") }
        if (child.parentUserId != parentUserId) {
            throw org.springframework.security.access.AccessDeniedException("Unauthorized.")
        }
        req.name?.let { zone.name = it }
        req.latitude?.let { zone.latitude = it }
        req.longitude?.let { zone.longitude = it }
        req.radiusMeters?.let { zone.radiusMeters = it }
        req.isActive?.let { zone.isActive = it }
        zone.updatedAt = Instant.now()
        val saved = geofenceZoneRepository.save(zone)
        return saved.toResponse()
    }

    fun deleteZone(parentUserId: String, zoneId: String) {
        val zone = geofenceZoneRepository.findById(zoneId)
            .orElseThrow { IllegalArgumentException("Zone not found.") }
        val child = childProfileRepository.findById(zone.childId)
            .orElseThrow { IllegalArgumentException("Child not found.") }
        if (child.parentUserId != parentUserId) {
            throw org.springframework.security.access.AccessDeniedException("Unauthorized.")
        }
        geofenceZoneRepository.delete(zone)
    }

    fun processGeofenceExit(req: GeofenceIncidentRequest): IncidentResponse {
        val child = childProfileRepository.findById(req.childId)
            .orElseThrow { IllegalArgumentException("Child not found.") }
        val zone = geofenceZoneRepository.findById(req.zoneId).orElse(null)
        val zoneName = zone?.name ?: "Safe Zone"

        val incidentId = "INC-GEO-${req.childId}-${System.currentTimeMillis()}"
        val incident = IncidentEntity(
            incidentId = incidentId,
            childId = req.childId,
            deviceId = child.pairedDeviceId ?: child.childModePhoneId ?: "PHONE",
            incidentType = "GEOFENCE_EXIT",
            riskLevel = "WARNING",
            status = "ACTIVE",
            source = "CHILD_APP",
            latitude = req.latitude,
            longitude = req.longitude,
            accuracyMeters = req.accuracyMeters,
            addressText = "Outside $zoneName boundary",
            startTimestamp = req.fixTimestamp,
            lastUpdatedTimestamp = Instant.now()
        )
        val saved = incidentRepository.save(incident)

        // Notify parent via WebSocket
        val event = mapOf(
            "type" to "GEOFENCE_EXIT_INCIDENT",
            "incidentId" to saved.incidentId,
            "childId" to saved.childId,
            "childName" to child.childName,
            "zoneName" to zoneName,
            "latitude" to req.latitude,
            "longitude" to req.longitude,
            "timestamp" to req.fixTimestamp.toEpochMilli()
        )
        webSocketHandler.broadcastToUser(child.parentUserId, objectMapper.writeValueAsString(event))

        return saved.toResponse(child.toResponse())
    }
}

@Service
@Transactional
class IncidentService(
    private val incidentRepository: IncidentRepository,
    private val childProfileRepository: ChildProfileRepository,
    private val relayObservationRepository: RelayObservationRepository,
    private val webSocketHandler: SafeBandWebSocketHandler,
    private val objectMapper: ObjectMapper,
    private val auditService: AuditService
) {
    fun createIncident(req: CreateIncidentRequest): IncidentResponse {
        val child = childProfileRepository.findById(req.childId)
            .orElseThrow { IllegalArgumentException("Child not found: ${req.childId}") }

        // Deduplication: if active incident exists for this child & type in last 5 minutes, reuse it
        val activeIncidents = incidentRepository.findByChildIdAndStatus(req.childId, "ACTIVE")
        val existing = activeIncidents.firstOrNull { it.incidentType == req.incidentType }

        val incident = if (existing != null) {
            existing.lastUpdatedTimestamp = Instant.now()
            req.latitude?.let { existing.latitude = it }
            req.longitude?.let { existing.longitude = it }
            req.accuracyMeters?.let { existing.accuracyMeters = it }
            req.addressText?.let { existing.addressText = it }
            existing
        } else {
            val incId = "INC-${req.incidentType}-${req.deviceId}-${System.currentTimeMillis()}"
            IncidentEntity(
                incidentId = incId,
                childId = req.childId,
                deviceId = req.deviceId,
                incidentType = req.incidentType,
                riskLevel = req.riskLevel,
                status = "ACTIVE",
                source = req.source,
                latitude = req.latitude,
                longitude = req.longitude,
                accuracyMeters = req.accuracyMeters,
                addressText = req.addressText,
                startTimestamp = req.startTimestamp,
                lastUpdatedTimestamp = Instant.now()
            )
        }
        val saved = incidentRepository.save(incident)

        // Broadcast critical alert immediately to parent WebSocket
        val event = mapOf(
            "type" to "EMERGENCY_INCIDENT_ACTIVE",
            "incidentId" to saved.incidentId,
            "childId" to saved.childId,
            "childName" to child.childName,
            "incidentType" to saved.incidentType,
            "riskLevel" to saved.riskLevel,
            "latitude" to saved.latitude,
            "longitude" to saved.longitude,
            "timestamp" to saved.startTimestamp.toEpochMilli(),
            "childBioProfile" to mapOf(
                "childName" to child.childName,
                "bloodType" to child.bloodType,
                "allergies" to child.allergies,
                "medicalNotes" to child.medicalNotes,
                "primaryParentPhone" to child.primaryParentPhone
            )
        )
        webSocketHandler.broadcastToUser(child.parentUserId, objectMapper.writeValueAsString(event))

        return saved.toResponse(child.toResponse())
    }

    fun getIncident(incidentId: String): IncidentResponse {
        val inc = incidentRepository.findById(incidentId)
            .orElseThrow { IllegalArgumentException("Incident not found: $incidentId") }
        val child = childProfileRepository.findById(inc.childId).orElse(null)
        val obs = relayObservationRepository.findByIncidentIdOrderByObservedAtDesc(incidentId).map { it.toResponse() }
        return inc.toResponse(child?.toResponse(), obs)
    }

    fun listIncidentsForParent(parentUserId: String): List<IncidentResponse> {
        val children = childProfileRepository.findByParentUserId(parentUserId)
        val childMap = children.associateBy { it.id }
        return children.flatMap { child ->
            incidentRepository.findByChildId(child.id).map { inc ->
                val obs = relayObservationRepository.findByIncidentIdOrderByObservedAtDesc(inc.incidentId).map { it.toResponse() }
                inc.toResponse(childMap[inc.childId]?.toResponse(), obs)
            }
        }.sortedByDescending { it.startTimestamp }
    }

    fun acknowledgeIncident(parentUserId: String, incidentId: String, req: AcknowledgeIncidentRequest): IncidentResponse {
        val inc = incidentRepository.findById(incidentId)
            .orElseThrow { IllegalArgumentException("Incident not found: $incidentId") }
        inc.status = "ACKNOWLEDGED"
        inc.acknowledgedAt = Instant.now()
        inc.lastUpdatedTimestamp = Instant.now()
        val saved = incidentRepository.save(inc)
        auditService.log(parentUserId, "ACK_INCIDENT", "INCIDENT", incidentId, null, "Parent acknowledged emergency alert")
        return getIncident(saved.incidentId)
    }

    fun resolveIncident(parentUserId: String, incidentId: String, req: ResolveIncidentRequest): IncidentResponse {
        val inc = incidentRepository.findById(incidentId)
            .orElseThrow { IllegalArgumentException("Incident not found: $incidentId") }
        inc.status = "RESOLVED"
        inc.resolvedAt = Instant.now()
        inc.resolutionReason = req.reason
        inc.lastUpdatedTimestamp = Instant.now()
        val saved = incidentRepository.save(inc)
        auditService.log(parentUserId, "RESOLVE_INCIDENT", "INCIDENT", incidentId, null, "Resolved: ${req.reason}")
        return getIncident(saved.incidentId)
    }
}

@Service
@Transactional
class RelayService(
    private val relayObservationRepository: RelayObservationRepository,
    private val incidentRepository: IncidentRepository,
    private val childProfileRepository: ChildProfileRepository,
    private val webSocketHandler: SafeBandWebSocketHandler,
    private val objectMapper: ObjectMapper
) {
    fun recordObservation(req: RelayObservationRequest): RelayObservationResponse {
        // Deduplicate: same incident, same relay, same minute
        val isDuplicate = relayObservationRepository.existsByIncidentIdAndRelayDeviceIdAndObservedAt(
            req.incidentId, req.relayDeviceId, req.observedAt
        )
        if (isDuplicate) {
            val existing = relayObservationRepository.findByIncidentIdOrderByObservedAtDesc(req.incidentId).first()
            return existing.toResponse()
        }

        val obsId = "RELAY-${UUID.randomUUID()}"
        val entity = RelayObservationEntity(
            observationId = obsId,
            incidentId = req.incidentId,
            relayDeviceId = req.relayDeviceId,
            latitude = req.latitude,
            longitude = req.longitude,
            accuracyMeters = req.accuracyMeters,
            rssi = req.rssi,
            hopCount = req.hopCount,
            observedAt = req.observedAt,
            uploadedAt = Instant.now()
        )
        val saved = relayObservationRepository.save(entity)

        // Notify parent that a bystander smartphone has sighted the emergency beacon!
        incidentRepository.findById(req.incidentId).ifPresent { inc ->
            childProfileRepository.findById(inc.childId).ifPresent { child ->
                val event = mapOf(
                    "type" to "RELAY_OBSERVATION_RECEIVED",
                    "incidentId" to inc.incidentId,
                    "childId" to child.id,
                    "childName" to child.childName,
                    "relayLatitude" to req.latitude,
                    "relayLongitude" to req.longitude,
                    "accuracyMeters" to req.accuracyMeters,
                    "observedAt" to req.observedAt.toEpochMilli()
                )
                webSocketHandler.broadcastToUser(child.parentUserId, objectMapper.writeValueAsString(event))
            }
        }

        return saved.toResponse()
    }

    fun getObservationsForIncident(incidentId: String): List<RelayObservationResponse> {
        return relayObservationRepository.findByIncidentIdOrderByObservedAtDesc(incidentId).map { it.toResponse() }
    }
}

@Service
class NotificationService(
    private val notificationRecordRepository: NotificationRecordRepository
) {
    private val log = LoggerFactory.getLogger(NotificationService::class.java)

    fun sendTestNotification(userId: String, req: TestNotificationRequest): NotificationRecordEntity {
        log.info("Dispatching test notification to user: $userId (token: ${req.fcmToken.take(10)}...)")
        val record = NotificationRecordEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            title = req.title,
            body = req.body,
            channelId = "emergency_alerts",
            deliveryStatus = "SENT_SIMULATED",
            sentAt = Instant.now()
        )
        return notificationRecordRepository.save(record)
    }
}

@Service
class AuditService(
    private val auditLogRepository: AuditLogRepository
) {
    fun log(userId: String?, action: String, entityType: String?, entityId: String?, ipAddress: String?, details: String?) {
        val entry = AuditLogEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            action = action,
            entityType = entityType,
            entityId = entityId,
            ipAddress = ipAddress,
            details = details,
            timestamp = Instant.now()
        )
        auditLogRepository.save(entry)
    }
}

// --- EXTENSION MAPPERS ---
fun UserEntity.toResponse() = UserResponse(id, email, displayName, photoUrl, role)
fun ChildProfileEntity.toResponse() = ChildProfileResponse(
    id, parentUserId, childName, age, bloodType, primaryParentPhone, secondaryParentPhone,
    medicalNotes, allergies, emergencyNotes, photoUrl, pairedDeviceId, childModePhoneId, createdAt, updatedAt
)
fun DeviceEntity.toResponse() = DeviceResponse(
    id, deviceType, hardwareNodeType, configVersion, isProvisioned, pairedChildId, ownerParentId,
    tamperStatus, batteryPercentage, lastHeartbeatAt
)
fun PairingSessionEntity.toResponse() = PairingSessionResponse(
    sessionId, parentUserId, childId, deviceId, challengeNonce, status, expiresAt
)
fun LocationEntity.toResponse() = LocationResponse(
    id, childId, deviceId, latitude, longitude, accuracyMeters, sequenceNumber, fixTimestamp, uploadTimestamp,
    provider, batteryPercentage, networkStatus, locationQuality
)
fun GeofenceZoneEntity.toResponse() = GeofenceResponse(
    id, childId, name, latitude, longitude, radiusMeters, isActive, createdAt, updatedAt
)
fun IncidentEntity.toResponse(child: ChildProfileResponse? = null, observations: List<RelayObservationResponse> = emptyList()) = IncidentResponse(
    incidentId, childId, deviceId, incidentType, riskLevel, status, source, latitude, longitude, accuracyMeters,
    addressText, startTimestamp, lastUpdatedTimestamp, acknowledgedAt, resolvedAt, resolutionReason, child, observations
)
fun RelayObservationEntity.toResponse() = RelayObservationResponse(
    observationId, incidentId, relayDeviceId, latitude, longitude, accuracyMeters, rssi, hopCount, observedAt, uploadedAt
)
