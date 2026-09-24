package com.example

import com.example.engine.RiskEngine
import com.example.model.AlertFlag
import com.example.model.RiskLevel
import com.example.service.BleSafetyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskEngineTest {

    @Test
    fun testRiskEngineEvaluation() {
        // No flags -> Normal
        assertEquals(RiskLevel.NORMAL, RiskEngine.calculateRisk(emptySet()))

        // Motion Anomaly alone -> Low
        assertEquals(RiskLevel.LOW, RiskEngine.calculateRisk(setOf(AlertFlag.MOTION_ANOMALY)))

        // Geofence Exit alone -> Low
        assertEquals(RiskLevel.LOW, RiskEngine.calculateRisk(setOf(AlertFlag.GEOFENCE_EXIT)))

        // Motion Anomaly + Geofence Exit -> Medium (escalation)
        assertEquals(
            RiskLevel.MEDIUM,
            RiskEngine.calculateRisk(setOf(AlertFlag.MOTION_ANOMALY, AlertFlag.GEOFENCE_EXIT))
        )

        // Manual SOS alone -> High (immediate emergency)
        assertEquals(RiskLevel.HIGH, RiskEngine.calculateRisk(setOf(AlertFlag.MANUAL_SOS)))

        // Manual SOS with others -> High
        assertEquals(
            RiskLevel.HIGH,
            RiskEngine.calculateRisk(setOf(AlertFlag.MANUAL_SOS, AlertFlag.MOTION_ANOMALY))
        )
    }

    @Test
    fun testBleBeaconPayloadEncodingAndDecoding() {
        val deviceId = "SB-8041"
        val riskLevel = RiskLevel.HIGH
        val lat = 37.77492
        val lon = -122.41942

        val encodedBytes = BleSafetyManager.encodePayload(
            deviceId = deviceId,
            riskLevel = riskLevel,
            lat = lat,
            lon = lon
        )
        assertTrue(encodedBytes.size <= 24) // BLE advertisement manufacturer data limit

        val decodedPayload = BleSafetyManager.decodePayload(encodedBytes)
        assertNotNull(decodedPayload)
        assertEquals("SB-8041", decodedPayload?.deviceId)
        assertEquals(RiskLevel.HIGH, decodedPayload?.riskLevel)
        assertEquals(lat, decodedPayload?.latitude ?: 0.0, 0.0001)
        assertEquals(lon, decodedPayload?.longitude ?: 0.0, 0.0001)
    }

    @Test
    fun testNormalRiskBeaconEncodingAndDecoding() {
        val encodedBytes = BleSafetyManager.encodePayload(
            deviceId = "SB-NORM",
            riskLevel = RiskLevel.NORMAL,
            lat = 37.77,
            lon = -122.41
        )
        val decoded = BleSafetyManager.decodePayload(encodedBytes)
        assertNotNull(decoded)
        assertEquals("SB-NORM", decoded?.deviceId)
        assertEquals(RiskLevel.NORMAL, decoded?.riskLevel)
    }

    @Test
    fun testChildBioProfileDefaultValuesAndDataModel() {
        val profile = com.example.model.ChildBioProfile(
            childName = "Emma",
            age = "7",
            bloodType = "B+",
            primaryParentPhone = "+15551234",
            secondaryContactPhone = "+15555678",
            medicalConditions = "Type 1 Diabetes",
            allergies = "Latex",
            emergencyNotes = "Check glucose levels"
        )
        assertEquals("Emma", profile.childName)
        assertEquals("7", profile.age)
        assertEquals("B+", profile.bloodType)
        assertEquals("+15551234", profile.primaryParentPhone)
        assertEquals("Type 1 Diabetes", profile.medicalConditions)
        assertEquals("Latex", profile.allergies)
    }

    @Test
    fun testGeoAddressFormattedSummary() {
        val geo = com.example.model.GeoAddress(
            fullAddress = "12 MG Road, Koramangala, Bengaluru, Karnataka 560034",
            street = "12 MG Road",
            area = "Koramangala",
            city = "Bengaluru",
            state = "Karnataka",
            pinCode = "560034",
            latitude = 12.9352,
            longitude = 77.6245
        )
        val summary = geo.formattedSummary()
        assertTrue(summary.contains("MG Road"))
        assertTrue(summary.contains("Koramangala"))
        assertTrue(summary.contains("Bengaluru"))
        assertTrue(summary.contains("560034"))
    }

    @Test
    fun testMultiPacketBleEncodingAndDecoding() {
        val deviceId = "SB-TEST"
        val bio = com.example.model.ChildBioProfile(
            childName = "Rohan",
            age = "9",
            bloodType = "O+",
            primaryParentPhone = "+919876543210"
        )

        // 1. Bio Core
        val coreBytes = BleSafetyManager.encodeBioCorePayload(deviceId, bio)
        assertTrue(coreBytes.size <= 24)
        val decodedCore = BleSafetyManager.decodePacket(coreBytes) as? BleSafetyManager.Companion.DecodedPacket.BioCore
        assertNotNull(decodedCore)
        assertEquals("SB-TEST", decodedCore?.deviceId)
        assertEquals("O+", decodedCore?.bloodType)
        assertEquals("9", decodedCore?.age)
        assertEquals("Rohan", decodedCore?.childName)

        // 2. Bio Phone
        val phoneBytes = BleSafetyManager.encodeBioPhonePayload(deviceId, bio.primaryParentPhone)
        assertTrue(phoneBytes.size <= 24)
        val decodedPhone = BleSafetyManager.decodePacket(phoneBytes) as? BleSafetyManager.Companion.DecodedPacket.BioPhone
        assertNotNull(decodedPhone)
        assertEquals("SB-TEST", decodedPhone?.deviceId)
        assertEquals("+919876543210", decodedPhone?.phone)

        // 3. Emergency Contact
        val contactBytes = BleSafetyManager.encodeContactPayload(deviceId, "112")
        assertTrue(contactBytes.size <= 24)
        val decodedContact = BleSafetyManager.decodePacket(contactBytes) as? BleSafetyManager.Companion.DecodedPacket.EmergencyContact
        assertNotNull(decodedContact)
        assertEquals("112", decodedContact?.phone)
    }
}
