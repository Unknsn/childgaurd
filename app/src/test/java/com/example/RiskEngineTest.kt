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
}
