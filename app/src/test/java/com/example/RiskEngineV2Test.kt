package com.example

import com.example.engine.RiskEngine
import com.example.model.AlertFlag
import com.example.model.BleBeaconPayload
import com.example.model.RiskLevel
import com.example.model.SafetyIncident
import com.example.model.IncidentStage
import com.example.service.BleSafetyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit Test Suite for Risk Engine V2, Alert Confirmation Window & Alert Severity (Batch B).
 */
class RiskEngineV2Test {

    @Test
    fun testNoSignalsProducesNormal() {
        assertEquals(RiskLevel.NORMAL, RiskEngine.calculateRisk(emptySet()))
        assertEquals(0, RiskEngine.calculateScore(emptyMap()))
    }

    @Test
    fun testSingleSignalWeighting() {
        // Motion Anomaly: 20 pts -> LOW (1..25)
        assertEquals(RiskLevel.LOW, RiskEngine.calculateRisk(setOf(AlertFlag.MOTION_ANOMALY)))

        // Geofence Exit: 30 pts -> MEDIUM (26..54)
        assertEquals(RiskLevel.MEDIUM, RiskEngine.calculateRisk(setOf(AlertFlag.GEOFENCE_EXIT)))

        // Route Deviation: 25 pts -> LOW (1..25)
        assertEquals(RiskLevel.LOW, RiskEngine.calculateRisk(setOf(AlertFlag.ROUTE_DEVIATION)))

        // Repeated Boundary Violation: 25 pts -> LOW (1..25)
        assertEquals(RiskLevel.LOW, RiskEngine.calculateRisk(setOf(AlertFlag.BOUNDARY_VIOLATION)))

        // Band Tamper / Cut: 40 pts -> MEDIUM (26..54)
        assertEquals(RiskLevel.MEDIUM, RiskEngine.calculateRisk(setOf(AlertFlag.TAMPER)))
    }

    @Test
    fun testMultipleSignalsCompounding() {
        // Geofence Exit (30) + Route Deviation (25) = 55 -> HIGH (55..79)
        val routeExitRisk = RiskEngine.calculateRisk(setOf(AlertFlag.GEOFENCE_EXIT, AlertFlag.ROUTE_DEVIATION))
        assertEquals(RiskLevel.HIGH, routeExitRisk)

        // Tamper (40) + Motion Anomaly (20) = 60 -> HIGH (55..79)
        val tamperMotionRisk = RiskEngine.calculateRisk(setOf(AlertFlag.TAMPER, AlertFlag.MOTION_ANOMALY))
        assertEquals(RiskLevel.HIGH, tamperMotionRisk)

        // Tamper (40) + Geofence Exit (30) + Boundary Violation (25) = 95 -> CRITICAL (>=80)
        val compoundCriticalRisk = RiskEngine.calculateRisk(
            setOf(AlertFlag.TAMPER, AlertFlag.GEOFENCE_EXIT, AlertFlag.BOUNDARY_VIOLATION)
        )
        assertEquals(RiskLevel.CRITICAL, compoundCriticalRisk)
    }

    @Test
    fun testManualSosAlwaysProducesCriticalImmediately() {
        // SOS alone -> immediate CRITICAL (no waiting, no extra evidence)
        assertEquals(RiskLevel.CRITICAL, RiskEngine.calculateRisk(setOf(AlertFlag.MANUAL_SOS)))

        // SOS with other flags -> always CRITICAL
        assertEquals(
            RiskLevel.CRITICAL,
            RiskEngine.calculateRisk(setOf(AlertFlag.MANUAL_SOS, AlertFlag.MOTION_ANOMALY))
        )

        // SOS bypasses confirmation countdown
        assertFalse(RiskEngine.requiresConfirmationCountdown(RiskLevel.CRITICAL, triggeredBySos = true))
    }

    @Test
    fun testNonSosRequiresConfirmationCountdown() {
        // MEDIUM non-SOS requires confirmation
        assertTrue(RiskEngine.requiresConfirmationCountdown(RiskLevel.MEDIUM, triggeredBySos = false))

        // HIGH non-SOS requires confirmation
        assertTrue(RiskEngine.requiresConfirmationCountdown(RiskLevel.HIGH, triggeredBySos = false))

        // CRITICAL compounded non-SOS requires confirmation
        assertTrue(RiskEngine.requiresConfirmationCountdown(RiskLevel.CRITICAL, triggeredBySos = false))

        // NORMAL and LOW do NOT enter confirmation countdown
        assertFalse(RiskEngine.requiresConfirmationCountdown(RiskLevel.NORMAL, triggeredBySos = false))
        assertFalse(RiskEngine.requiresConfirmationCountdown(RiskLevel.LOW, triggeredBySos = false))
    }

    @Test
    fun testTemporalSignalDecay() {
        val now = 1_000_000L

        // Fresh signal (30s old): 100% weight of TAMPER (40) = 40
        val freshSignals = mapOf(AlertFlag.TAMPER to (now - 30_000L))
        assertEquals(40, RiskEngine.calculateScore(freshSignals, now))
        assertEquals(RiskLevel.MEDIUM, RiskEngine.calculateRiskWithTimestamp(freshSignals, now))

        // Decayed signal (90s old): 50% weight of TAMPER (40 / 2) = 20 -> LOW
        val decayedSignals = mapOf(AlertFlag.TAMPER to (now - 90_000L))
        assertEquals(20, RiskEngine.calculateScore(decayedSignals, now))
        assertEquals(RiskLevel.LOW, RiskEngine.calculateRiskWithTimestamp(decayedSignals, now))

        // Expired signal (130s old): 0% weight -> 0 -> NORMAL
        val expiredSignals = mapOf(AlertFlag.TAMPER to (now - 130_000L))
        assertEquals(0, RiskEngine.calculateScore(expiredSignals, now))
        assertEquals(RiskLevel.NORMAL, RiskEngine.calculateRiskWithTimestamp(expiredSignals, now))
    }

    @Test
    fun testBleBroadcastPolicy() {
        // NORMAL & LOW do NOT broadcast over BLE
        assertFalse(RiskEngine.shouldBroadcastBle(RiskLevel.NORMAL))
        assertFalse(RiskEngine.shouldBroadcastBle(RiskLevel.LOW))

        // MEDIUM, HIGH, and CRITICAL DO broadcast over BLE
        assertTrue(RiskEngine.shouldBroadcastBle(RiskLevel.MEDIUM))
        assertTrue(RiskEngine.shouldBroadcastBle(RiskLevel.HIGH))
        assertTrue(RiskEngine.shouldBroadcastBle(RiskLevel.CRITICAL))
    }

    @Test
    fun testBleEncodingAndDecodingCriticalRisk() {
        val encodedBytes = BleSafetyManager.encodePayload(
            deviceId = "SB-CRIT",
            riskLevel = RiskLevel.CRITICAL,
            lat = 13.0326,
            lon = 77.5928
        )
        assertTrue(encodedBytes.size <= 24)

        val decoded = BleSafetyManager.decodePayload(encodedBytes)
        assertNotNull(decoded)
        assertEquals("SB-CRIT", decoded?.deviceId)
        assertEquals(RiskLevel.CRITICAL, decoded?.riskLevel)
        assertEquals(13.0326, decoded?.latitude ?: 0.0, 0.001)
        assertEquals(77.5928, decoded?.longitude ?: 0.0, 0.001)
    }

    @Test
    fun testSimulatedEventDistinctionFromRealEvents() {
        val realPayload = BleBeaconPayload(
            deviceId = "SB-REAL",
            riskLevel = RiskLevel.HIGH,
            timestamp = System.currentTimeMillis(),
            isSimulation = false
        )
        assertFalse(realPayload.isSimulation)

        val demoPayload = BleBeaconPayload(
            deviceId = "SB-DEMO",
            riskLevel = RiskLevel.HIGH,
            timestamp = System.currentTimeMillis(),
            isSimulation = true
        )
        assertTrue(demoPayload.isSimulation)

        val realIncident = SafetyIncident(
            incidentId = "INC-1",
            deviceId = "SB-REAL",
            isSimulation = false
        )
        assertFalse(realIncident.isSimulation)

        val demoIncident = SafetyIncident(
            incidentId = "INC-2",
            deviceId = "SB-DEMO",
            isSimulation = true
        )
        assertTrue(demoIncident.isSimulation)
    }

    @Test
    fun testIncidentSirenAudibility() {
        val criticalIncident = SafetyIncident(
            stage = IncidentStage.ACTIVE,
            riskLevel = RiskLevel.CRITICAL
        )
        assertTrue(criticalIncident.isSirenAudible)

        val highIncident = SafetyIncident(
            stage = IncidentStage.ACTIVE,
            riskLevel = RiskLevel.HIGH
        )
        assertTrue(highIncident.isSirenAudible)

        val lowIncident = SafetyIncident(
            stage = IncidentStage.ACTIVE,
            riskLevel = RiskLevel.LOW
        )
        assertFalse(lowIncident.isSirenAudible)

        val silencedCritical = SafetyIncident(
            stage = IncidentStage.SILENCED,
            riskLevel = RiskLevel.CRITICAL
        )
        assertFalse(silencedCritical.isSirenAudible)
    }
}
