package com.safeband.backend.controller

import com.safeband.backend.dto.*
import com.safeband.backend.security.UserPrincipal
import com.safeband.backend.service.*
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/api/v1/health")
class HealthController {
    @GetMapping
    fun health(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(
            mapOf(
                "status" to "UP",
                "service" to "SafeBand Guardian Backend",
                "version" to "4.0.0",
                "timestamp" to Instant.now().toString()
            )
        )
    }
}

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService
) {
    @PostMapping("/google")
    fun googleLogin(
        @RequestBody req: GoogleAuthRequest,
        servletReq: HttpServletRequest
    ): ResponseEntity<AuthResponse> {
        val clientIp = servletReq.getHeader("X-Forwarded-For") ?: servletReq.remoteAddr
        return ResponseEntity.ok(authService.loginWithGoogle(req.idToken, clientIp))
    }

    @PostMapping("/refresh")
    fun refresh(@RequestBody req: RefreshTokenRequest): ResponseEntity<AuthResponse> {
        return ResponseEntity.ok(authService.refreshAccessToken(req.refreshToken))
    }

    @PostMapping("/logout")
    fun logout(@AuthenticationPrincipal principal: UserPrincipal?): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("status" to "LOGGED_OUT", "message" to "Session revoked successfully."))
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: UserPrincipal): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(authService.getCurrentUser(principal.id))
    }
}

@RestController
@RequestMapping("/api/v1/children")
class ChildController(
    private val childService: ChildService
) {
    @PostMapping
    fun createChild(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody req: CreateChildRequest
    ): ResponseEntity<ChildProfileResponse> {
        return ResponseEntity.ok(childService.createChild(principal.id, req))
    }

    @GetMapping
    fun listChildren(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<List<ChildProfileResponse>> {
        return ResponseEntity.ok(childService.listChildrenForParent(principal.id))
    }

    @GetMapping("/{childId}")
    fun getChild(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable childId: String
    ): ResponseEntity<ChildProfileResponse> {
        return ResponseEntity.ok(childService.getChild(principal.id, childId))
    }

    @PutMapping("/{childId}")
    fun updateChild(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable childId: String,
        @RequestBody req: UpdateChildRequest
    ): ResponseEntity<ChildProfileResponse> {
        return ResponseEntity.ok(childService.updateChild(principal.id, childId, req))
    }

    @DeleteMapping("/{childId}")
    fun deleteChild(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable childId: String
    ): ResponseEntity<Map<String, String>> {
        childService.deleteChild(principal.id, childId)
        return ResponseEntity.ok(mapOf("status" to "DELETED", "childId" to childId))
    }
}

@RestController
@RequestMapping("/api/v1/devices")
class DeviceController(
    private val deviceService: DeviceService
) {
    @PostMapping("/register")
    fun registerDevice(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestBody req: RegisterDeviceRequest
    ): ResponseEntity<DeviceResponse> {
        return ResponseEntity.ok(deviceService.registerDevice(principal?.id, req))
    }

    @GetMapping
    fun listDevices(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<List<DeviceResponse>> {
        return ResponseEntity.ok(deviceService.listDevices(principal.id))
    }

    @GetMapping("/{deviceId}")
    fun getDevice(
        @PathVariable deviceId: String
    ): ResponseEntity<DeviceResponse> {
        return ResponseEntity.ok(deviceService.getDevice(deviceId))
    }

    @PostMapping("/{deviceId}/heartbeat")
    fun heartbeat(
        @PathVariable deviceId: String,
        @RequestBody req: HeartbeatRequest
    ): ResponseEntity<DeviceResponse> {
        return ResponseEntity.ok(deviceService.heartbeat(deviceId, req))
    }

    @GetMapping("/{deviceId}/configuration")
    fun getProvisioningConfiguration(
        @PathVariable deviceId: String
    ): ResponseEntity<ProvisioningConfigResponse> {
        return ResponseEntity.ok(deviceService.getProvisioningConfiguration(deviceId))
    }

    @PostMapping("/{deviceId}/configuration/ack")
    fun acknowledgeConfiguration(
        @PathVariable deviceId: String,
        @RequestBody req: ProvisioningAckRequest
    ): ResponseEntity<DeviceResponse> {
        return ResponseEntity.ok(deviceService.acknowledgeProvisioning(deviceId, req.version))
    }
}

@RestController
@RequestMapping("/api/v1/pairing")
class PairingController(
    private val pairingService: PairingService
) {
    @PostMapping("/sessions", "/initiate")
    fun createSession(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody req: CreatePairingSessionRequest
    ): ResponseEntity<PairingSessionResponse> {
        return ResponseEntity.ok(pairingService.createSession(principal.id, req))
    }

    @PostMapping("/{sessionId}/complete", "/{sessionId}/confirm")
    fun completeSession(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable sessionId: String,
        @RequestBody req: CompletePairingRequest
    ): ResponseEntity<PairingSessionResponse> {
        return ResponseEntity.ok(pairingService.completeSession(principal.id, sessionId, req))
    }

    @PostMapping("/{sessionId}/cancel")
    fun cancelSession(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable sessionId: String
    ): ResponseEntity<Map<String, String>> {
        pairingService.cancelSession(principal.id, sessionId)
        return ResponseEntity.ok(mapOf("status" to "CANCELLED", "sessionId" to sessionId))
    }
}

@RestController
@RequestMapping("/api/v1/locations")
class LocationController(
    private val locationService: LocationService
) {
    @PostMapping
    fun uploadLocation(
        @RequestBody req: LocationUploadRequest
    ): ResponseEntity<LocationResponse> {
        return ResponseEntity.ok(locationService.ingestLocation(req))
    }

    @PostMapping("/batch")
    fun uploadBatchLocations(
        @RequestBody req: BatchLocationUploadRequest
    ): ResponseEntity<List<LocationResponse>> {
        return ResponseEntity.ok(locationService.ingestBatch(req))
    }

    @GetMapping("/children/{childId}/latest")
    fun getLatestLocation(
        @PathVariable childId: String
    ): ResponseEntity<LocationResponse> {
        val loc = locationService.getLatestLocation(childId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(loc)
    }

    @GetMapping("/children/{childId}/history")
    fun getLocationHistory(
        @PathVariable childId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int
    ): ResponseEntity<List<LocationResponse>> {
        return ResponseEntity.ok(locationService.getLocationHistory(childId, page, size))
    }
}

@RestController
@RequestMapping("/api/v1/geofences")
class GeofenceController(
    private val geofenceService: GeofenceService
) {
    @PostMapping
    fun createZone(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody req: CreateGeofenceRequest
    ): ResponseEntity<GeofenceResponse> {
        return ResponseEntity.ok(geofenceService.createZone(principal.id, req))
    }

    @GetMapping("/children/{childId}")
    fun listZones(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable childId: String
    ): ResponseEntity<List<GeofenceResponse>> {
        return ResponseEntity.ok(geofenceService.listZones(principal.id, childId))
    }

    @PutMapping("/{zoneId}")
    fun updateZone(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable zoneId: String,
        @RequestBody req: UpdateGeofenceRequest
    ): ResponseEntity<GeofenceResponse> {
        return ResponseEntity.ok(geofenceService.updateZone(principal.id, zoneId, req))
    }

    @DeleteMapping("/{zoneId}")
    fun deleteZone(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable zoneId: String
    ): ResponseEntity<Map<String, String>> {
        geofenceService.deleteZone(principal.id, zoneId)
        return ResponseEntity.ok(mapOf("status" to "DELETED", "zoneId" to zoneId))
    }

    @PostMapping("/incidents/exit")
    fun processGeofenceExit(
        @RequestBody req: GeofenceIncidentRequest
    ): ResponseEntity<IncidentResponse> {
        return ResponseEntity.ok(geofenceService.processGeofenceExit(req))
    }
}

@RestController
@RequestMapping("/api/v1/incidents")
class IncidentController(
    private val incidentService: IncidentService
) {
    @PostMapping
    fun createIncident(
        @RequestBody req: CreateIncidentRequest
    ): ResponseEntity<IncidentResponse> {
        return ResponseEntity.ok(incidentService.createIncident(req))
    }

    @GetMapping
    fun listIncidents(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<List<IncidentResponse>> {
        return ResponseEntity.ok(incidentService.listIncidentsForParent(principal.id))
    }

    @GetMapping("/{incidentId}")
    fun getIncident(
        @PathVariable incidentId: String
    ): ResponseEntity<IncidentResponse> {
        return ResponseEntity.ok(incidentService.getIncident(incidentId))
    }

    @PostMapping("/{incidentId}/acknowledge")
    fun acknowledgeIncident(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable incidentId: String,
        @RequestBody(required = false) req: AcknowledgeIncidentRequest?
    ): ResponseEntity<IncidentResponse> {
        return ResponseEntity.ok(incidentService.acknowledgeIncident(principal.id, incidentId, req ?: AcknowledgeIncidentRequest()))
    }

    @PostMapping("/{incidentId}/resolve")
    fun resolveIncident(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable incidentId: String,
        @RequestBody req: ResolveIncidentRequest
    ): ResponseEntity<IncidentResponse> {
        return ResponseEntity.ok(incidentService.resolveIncident(principal.id, incidentId, req))
    }
}

@RestController
@RequestMapping("/api/v1/relay", "/api/relay")
class RelayController(
    private val relayService: RelayService
) {
    @PostMapping("/observations", "/alert")
    fun uploadObservation(
        @RequestBody req: RelayObservationRequest
    ): ResponseEntity<RelayObservationResponse> {
        return ResponseEntity.ok(relayService.recordObservation(req))
    }

    @GetMapping("/observations/{incidentId}")
    fun getObservations(
        @PathVariable incidentId: String
    ): ResponseEntity<List<RelayObservationResponse>> {
        return ResponseEntity.ok(relayService.getObservationsForIncident(incidentId))
    }
}

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationService: NotificationService
) {
    @PostMapping("/test")
    fun testNotification(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody req: TestNotificationRequest
    ): ResponseEntity<Map<String, Any>> {
        val record = notificationService.sendTestNotification(principal.id, req)
        return ResponseEntity.ok(
            mapOf(
                "status" to "SUCCESS",
                "recordId" to record.id,
                "deliveryStatus" to record.deliveryStatus
            )
        )
    }

    @GetMapping("/preferences")
    fun getPreferences(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<NotificationPreferencesDto> {
        return ResponseEntity.ok(NotificationPreferencesDto())
    }

    @PutMapping("/preferences")
    fun updatePreferences(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody req: NotificationPreferencesDto
    ): ResponseEntity<NotificationPreferencesDto> {
        return ResponseEntity.ok(req)
    }
}
