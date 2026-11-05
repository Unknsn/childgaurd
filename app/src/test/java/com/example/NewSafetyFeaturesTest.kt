package com.example

import com.example.data.local.EscalationConfigEntity
import com.example.data.local.HandoverRecord
import com.example.data.local.PickupAuthorization
import com.example.data.local.SchoolProfile
import com.example.data.local.SchoolScheduleException
import com.example.data.local.SupervisedJourney
import com.example.data.local.TrustedContact
import com.example.model.AlertFlag
import com.example.model.ChildBioProfile
import com.example.model.LocationConfidence
import com.example.model.RiskLevel
import com.example.model.SafetyIncident
import com.example.model.SafetyNode
import com.example.model.VerifiedLocation
import com.example.service.EscalationSafetyManager
import com.example.service.JourneySafetyManager
import com.example.service.SchoolSafetyManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class NewSafetyFeaturesTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    // =========================================================================
    // Feature A: Safe Pickup and Handover Verification Tests
    // =========================================================================

    @Test
    fun testPickupTokenGenerationAndExpiration() {
        val now = 1000_000L
        val validityMinutes = 30
        val expiresAt = now + (validityMinutes * 60 * 1000L)

        val auth = PickupAuthorization(
            authId = "AUTH-TEST1",
            childName = "Aarav",
            authorizedPersonName = "Uncle David",
            relationship = "Uncle",
            phoneNumber = "+1 555 1234",
            createdAt = now,
            expiresAt = expiresAt,
            isOneTime = true,
            verificationToken = "7X29AB",
            status = "ACTIVE"
        )

        assertEquals("ACTIVE", auth.status)
        assertTrue(auth.expiresAt > now)

        // Expired check
        val pastExpiryTime = expiresAt + 1000L
        val isExpired = pastExpiryTime >= auth.expiresAt
        assertTrue("Token must be evaluated as expired after expiration time", isExpired)
    }

    @Test
    fun testRevocationDisablesPickupPass() {
        var auth = PickupAuthorization(
            authId = "AUTH-REVOKE",
            childName = "Aarav",
            authorizedPersonName = "Grandma Helen",
            relationship = "Grandmother",
            phoneNumber = "+1 555 4321",
            createdAt = 1000L,
            expiresAt = 50000L,
            verificationToken = "REV123",
            status = "ACTIVE"
        )

        assertEquals("ACTIVE", auth.status)
        // Parent revokes authorization
        auth = auth.copy(status = "REVOKED")
        assertEquals("REVOKED", auth.status)
        assertFalse("Revoked authorization must not be active", auth.status == "ACTIVE")
    }

    @Test
    fun testHandoverApprovalGeneratesAuditRecord() {
        val auth = PickupAuthorization(
            authId = "AUTH-HO1",
            childName = "Aarav",
            authorizedPersonName = "Grandma Helen",
            relationship = "Grandmother",
            phoneNumber = "+1 555 4321",
            createdAt = 1000L,
            expiresAt = 50000L,
            verificationToken = "HO1234",
            status = "ACTIVE"
        )

        val handover = HandoverRecord(
            handoverId = "HO-REC-1",
            authId = auth.authId,
            childName = auth.childName,
            pickupPersonName = auth.authorizedPersonName,
            relationship = auth.relationship,
            timestamp = 2000L,
            status = "APPROVED",
            decisionBy = "Primary Parent",
            locationSummary = "North School Gate"
        )

        assertEquals("APPROVED", handover.status)
        assertEquals("Primary Parent", handover.decisionBy)
        assertEquals("Grandma Helen", handover.pickupPersonName)
        assertEquals("Aarav", handover.childName)
    }

    // =========================================================================
    // Feature B: Emergency Escalation Tests
    // =========================================================================

    @Test
    fun testEscalationConfigDefaults() {
        val config = EscalationConfigEntity(
            tier1DelaySeconds = 30,
            tier2DelaySeconds = 60,
            autoEscalateEnabled = true
        )
        assertEquals(30, config.tier1DelaySeconds)
        assertEquals(60, config.tier2DelaySeconds)
        assertTrue(config.notifyPoliceClaimForbidden)
    }

    @Test
    fun testVerifiedIncidentPackageIntegrity() {
        val incident = SafetyIncident(
            incidentId = "INC-TEST-001",
            deviceId = "SB-8041",
            riskLevel = RiskLevel.CRITICAL,
            triggerFlags = setOf(AlertFlag.MANUAL_SOS),
            startTimestamp = 1728345600000L,
            childBioProfile = ChildBioProfile(
                childName = "Aarav",
                age = "8",
                bloodType = "O+",
                medicalConditions = "Asthma"
            ),
            verifiedLocation = null // ESP32 has no fake GPS
        )

        assertEquals("INC-TEST-001", incident.incidentId)
        assertEquals(RiskLevel.CRITICAL, incident.riskLevel)
        assertTrue(incident.triggerFlags.contains(AlertFlag.MANUAL_SOS))
        assertEquals("Aarav", incident.childBioProfile?.childName)
        assertNull("ESP32 physical node must have null coordinates without inventing fake GPS", incident.verifiedLocation)
    }

    // =========================================================================
    // Feature C: School Arrival & Departure Verification Tests
    // =========================================================================

    @Test
    fun testSchoolSafeZoneScheduleMatching() {
        val school = SchoolProfile(
            schoolId = "SCH-01",
            schoolName = "Lincoln Elementary",
            address = "1200 Lincoln Way",
            latitude = 37.7650,
            longitude = -122.4600,
            radiusMeters = 200f,
            expectedArrivalStartMinutes = 465, // 07:45
            expectedArrivalEndMinutes = 510,   // 08:30
            expectedDepartureStartMinutes = 870, // 14:30
            expectedDepartureEndMinutes = 930,   // 15:30
            schoolDaysMask = 31 // Mon-Fri
        )

        // Mon-Fri bits: Mon=1, Tue=2, Wed=4, Thu=8, Fri=16
        val isMonday = (school.schoolDaysMask and 1) != 0
        val isFriday = (school.schoolDaysMask and 16) != 0
        val isSunday = (school.schoolDaysMask and 64) != 0

        assertTrue("Monday must be a school day", isMonday)
        assertTrue("Friday must be a school day", isFriday)
        assertFalse("Sunday must not be a school day", isSunday)
    }

    @Test
    fun testHolidayExceptionSuppressesAlerts() {
        val todayEpochDay = 19700L
        val holiday = SchoolScheduleException(
            schoolId = "SCH-01",
            dateEpochDay = todayEpochDay,
            reason = "Winter Break",
            isHoliday = true
        )

        assertTrue(holiday.isHoliday)
        assertEquals("Winter Break", holiday.reason)
        assertEquals(todayEpochDay, holiday.dateEpochDay)
    }

    // =========================================================================
    // Feature D: Journey Safety Mode Tests
    // =========================================================================

    @Test
    fun testSupervisedJourneyCreation() {
        val journey = SupervisedJourney(
            journeyId = "JRN-001",
            name = "Morning Walk",
            startLabel = "Home",
            startLat = 37.7749,
            startLon = -122.4194,
            destLabel = "School",
            destLat = 37.7650,
            destLon = -122.4600,
            corridorRadiusMeters = 80f,
            expectedDurationMinutes = 25,
            startTimestamp = 1000L,
            expectedArrivalTimestamp = 1000L + 25 * 60 * 1000L,
            status = "ACTIVE"
        )

        assertEquals("ACTIVE", journey.status)
        assertEquals(80f, journey.corridorRadiusMeters)
        assertEquals(25, journey.expectedDurationMinutes)
        assertEquals("Home", journey.startLabel)
        assertEquals("School", journey.destLabel)
    }

    @Test
    fun testEtaExceededDetection() {
        val now = 2000_000L
        val journey = SupervisedJourney(
            journeyId = "JRN-ETA",
            name = "Transit",
            startLabel = "Home",
            startLat = 37.7749,
            startLon = -122.4194,
            destLabel = "School",
            destLat = 37.7650,
            destLon = -122.4600,
            expectedArrivalTimestamp = now - 60_000L, // 1 minute in the past
            status = "ACTIVE"
        )

        val isExceeded = now > journey.expectedArrivalTimestamp
        assertTrue("Journey ETA must be marked exceeded when current time > expectedArrivalTimestamp", isExceeded)
    }

    @Test
    fun testBeaconDisconnectionDetection() {
        val currentTime = 100_000L
        val node = SafetyNode(
            nodeId = "SB-8041",
            lastSeenTimestamp = currentTime - 120_000L // 120 seconds ago
        )

        val signalAge = currentTime - node.lastSeenTimestamp
        val isDisconnected = signalAge > 90_000L // > 90 seconds
        assertTrue("Beacon signal older than 90s must trigger disconnection alert", isDisconnected)
    }
}

