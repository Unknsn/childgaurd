package com.safeband.backend

import com.safeband.backend.dto.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.*
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.Base64

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("standalone")
class BackendApiIntegrationTest {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    private fun createSimulatedGoogleToken(sub: String, email: String, name: String): String {
        val header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".toByteArray())
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"$sub\",\"email\":\"$email\",\"name\":\"$name\"}".toByteArray())
        return "$header.$payload.SimulatedSignature"
    }

    @Test
    fun testCompleteEndToEndBackendPipeline() {
        // 1. Google Authentication
        val token = createSimulatedGoogleToken("google-parent-101", "parent.guardian@example.com", "John Guardian")
        val authReq = GoogleAuthRequest(idToken = token)
        val authResp = restTemplate.postForEntity("/api/v1/auth/google", authReq, AuthResponse::class.java)

        assertEquals(HttpStatus.OK, authResp.statusCode)
        assertNotNull(authResp.body?.accessToken)
        val jwt = authResp.body!!.accessToken
        val parentUserId = authResp.body!!.user.id

        val authHeaders = HttpHeaders().apply {
            setBearerAuth(jwt)
            contentType = MediaType.APPLICATION_JSON
        }

        // 2. Register Child Profile
        val childReq = CreateChildRequest(
            childName = "Aarav Guardian",
            age = "9",
            bloodType = "O+",
            primaryParentPhone = "+919876543210",
            medicalNotes = "Mild Asthma",
            allergies = "Peanuts"
        )
        val childResp = restTemplate.exchange(
            "/api/v1/children",
            HttpMethod.POST,
            HttpEntity(childReq, authHeaders),
            ChildProfileResponse::class.java
        )
        assertEquals(HttpStatus.OK, childResp.statusCode)
        val childId = childResp.body!!.id
        assertEquals("Aarav Guardian", childResp.body!!.childName)

        // 3. Register ESP32 Wearable Device
        val devReq = RegisterDeviceRequest(
            deviceId = "SB-CHLD-TEST1",
            deviceType = "ESP32_WEARABLE",
            hardwareNodeType = "PHYSICAL_CHILD_NODE"
        )
        val devResp = restTemplate.exchange(
            "/api/v1/devices/register",
            HttpMethod.POST,
            HttpEntity(devReq, authHeaders),
            DeviceResponse::class.java
        )
        assertEquals(HttpStatus.OK, devResp.statusCode)

        // 4. Create and Complete Pairing Session
        val pairReq = CreatePairingSessionRequest(childId = childId, deviceId = "SB-CHLD-TEST1")
        val pairSessResp = restTemplate.exchange(
            "/api/v1/pairing/sessions",
            HttpMethod.POST,
            HttpEntity(pairReq, authHeaders),
            PairingSessionResponse::class.java
        )
        assertEquals(HttpStatus.OK, pairSessResp.statusCode)
        val sessionId = pairSessResp.body!!.sessionId

        val completePairResp = restTemplate.exchange(
            "/api/v1/pairing/$sessionId/complete",
            HttpMethod.POST,
            HttpEntity(CompletePairingRequest("NONCE_OK"), authHeaders),
            PairingSessionResponse::class.java
        )
        assertEquals(HttpStatus.OK, completePairResp.statusCode)
        assertEquals("COMPLETED", completePairResp.body!!.status)

        // 5. Ingest 1-Minute Child Location
        val locReq = LocationUploadRequest(
            childId = childId,
            deviceId = "SB-CHLD-TEST1",
            latitude = 12.9716,
            longitude = 77.5946,
            accuracyMeters = 4.5f,
            sequenceNumber = 1L,
            fixTimestamp = Instant.now(),
            batteryPercentage = 95
        )
        val locResp = restTemplate.postForEntity("/api/v1/locations", locReq, LocationResponse::class.java)
        assertEquals(HttpStatus.OK, locResp.statusCode)
        assertEquals(12.9716, locResp.body!!.latitude)

        // 6. Geofence Safe Zone Creation & Violation
        val zoneReq = CreateGeofenceRequest(
            childId = childId,
            name = "Home Safe Zone",
            latitude = 12.9716,
            longitude = 77.5946,
            radiusMeters = 150f
        )
        val zoneResp = restTemplate.exchange(
            "/api/v1/geofences",
            HttpMethod.POST,
            HttpEntity(zoneReq, authHeaders),
            GeofenceResponse::class.java
        )
        assertEquals(HttpStatus.OK, zoneResp.statusCode)
        val zoneId = zoneResp.body!!.id

        // Process Geofence Exit
        val geoExitReq = GeofenceIncidentRequest(
            childId = childId,
            zoneId = zoneId,
            latitude = 12.9780,
            longitude = 77.6000,
            accuracyMeters = 6.0f,
            fixTimestamp = Instant.now()
        )
        val geoExitResp = restTemplate.postForEntity("/api/v1/geofences/incidents/exit", geoExitReq, IncidentResponse::class.java)
        assertEquals(HttpStatus.OK, geoExitResp.statusCode)
        assertEquals("GEOFENCE_EXIT", geoExitResp.body!!.incidentType)

        // 7. Emergency SOS Incident Creation
        val sosReq = CreateIncidentRequest(
            childId = childId,
            deviceId = "SB-CHLD-TEST1",
            incidentType = "MANUAL_SOS",
            riskLevel = "CRITICAL",
            latitude = 12.9780,
            longitude = 77.6000,
            accuracyMeters = 5.0f
        )
        val sosResp = restTemplate.postForEntity("/api/v1/incidents", sosReq, IncidentResponse::class.java)
        assertEquals(HttpStatus.OK, sosResp.statusCode)
        val incidentId = sosResp.body!!.incidentId
        assertEquals("ACTIVE", sosResp.body!!.status)

        // 8. Bystander Relay Observation Upload
        val relayReq = RelayObservationRequest(
            incidentId = incidentId,
            relayDeviceId = "RELAY-PHONE-BYSTANDER-88",
            latitude = 12.9785,
            longitude = 77.6005,
            accuracyMeters = 12.0f,
            rssi = -68,
            hopCount = 1,
            observedAt = Instant.now()
        )
        val relayResp = restTemplate.postForEntity("/api/v1/relay/observations", relayReq, RelayObservationResponse::class.java)
        assertEquals(HttpStatus.OK, relayResp.statusCode)
        assertEquals(12.9785, relayResp.body!!.latitude)

        // 9. Parent Incident Retrieval (ensures child details are accessible to parent, but isolated from public)
        val parentIncResp = restTemplate.exchange(
            "/api/v1/incidents/$incidentId",
            HttpMethod.GET,
            HttpEntity<Unit>(authHeaders),
            IncidentResponse::class.java
        )
        assertEquals(HttpStatus.OK, parentIncResp.statusCode)
        assertEquals("Aarav Guardian", parentIncResp.body!!.childProfile?.childName)
        assertEquals(1, parentIncResp.body!!.observations.size)
        assertEquals("RELAY-PHONE-BYSTANDER-88", parentIncResp.body!!.observations[0].relayDeviceId)

        // 10. Parent Acknowledges and Resolves Incident
        val ackResp = restTemplate.exchange(
            "/api/v1/incidents/$incidentId/acknowledge",
            HttpMethod.POST,
            HttpEntity(AcknowledgeIncidentRequest("Parent en route"), authHeaders),
            IncidentResponse::class.java
        )
        assertEquals(HttpStatus.OK, ackResp.statusCode)
        assertEquals("ACKNOWLEDGED", ackResp.body!!.status)

        val resolveResp = restTemplate.exchange(
            "/api/v1/incidents/$incidentId/resolve",
            HttpMethod.POST,
            HttpEntity(ResolveIncidentRequest("Child safely with guardian"), authHeaders),
            IncidentResponse::class.java
        )
        assertEquals(HttpStatus.OK, resolveResp.statusCode)
        assertEquals("RESOLVED", resolveResp.body!!.status)
    }
}
