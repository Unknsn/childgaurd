package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.BleBeaconPayload
import com.example.model.ChildBioProfile
import com.example.model.NodeConnectionState
import com.example.model.RiskLevel
import com.example.model.TrustRole
import com.example.service.BleSafetyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit Test Suite for Privacy-Preserving BLE, Ephemeral Identifiers, Trust Roles, and Node Discovery (Batch D).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrivacyBleTest {

    private lateinit var manager: BleSafetyManager
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setup() {
        manager = BleSafetyManager(context)
    }

    @Test
    fun testPublicBlePacketDoesNotExposeSensitiveChildPii() {
        val childBio = ChildBioProfile(
            childName = "Aarav Sharma",
            age = "9",
            bloodType = "O+",
            primaryParentPhone = "+91 98765 43210",
            medicalConditions = "Asthma with Inhaler",
            allergies = "Peanut Allergy",
            emergencyNotes = "Call parents immediately"
        )

        val ephemeralId = BleSafetyManager.generateEphemeralId("SB-8041", epochWindow = 1000L)
        val publicBytes = BleSafetyManager.encodePayload(
            deviceId = "SB-8041",
            riskLevel = RiskLevel.CRITICAL,
            lat = 13.0326,
            lon = 77.5928,
            ephemeralId = ephemeralId
        )

        val payloadString = String(publicBytes, Charsets.ISO_8859_1)

        // Verify that NO sensitive child data is present in the public packet
        assertFalse(payloadString.contains("Aarav"))
        assertFalse(payloadString.contains("Sharma"))
        assertFalse(payloadString.contains("98765"))
        assertFalse(payloadString.contains("Asthma"))
        assertFalse(payloadString.contains("Peanut"))
        assertFalse(payloadString.contains("Inhaler"))
        assertFalse(payloadString.contains("O+"))

        // Verify manufacturer format
        assertEquals(0x53.toByte(), publicBytes[0]) // 'S'
        assertEquals(0x42.toByte(), publicBytes[1]) // 'B'
        assertTrue(publicBytes.size <= 24)
    }

    @Test
    fun testEphemeralIdentifierRotation() {
        val deviceId = "SB-8041"

        // Epoch window 1
        val eph1 = BleSafetyManager.generateEphemeralId(deviceId, epochWindow = 100L)
        assertTrue(eph1.startsWith("EP-"))
        assertEquals(7, eph1.length) // "EP-" (3) + 4 hex chars = 7 chars

        // Epoch window 2 (15 minutes later)
        val eph2 = BleSafetyManager.generateEphemeralId(deviceId, epochWindow = 101L)
        assertTrue(eph2.startsWith("EP-"))
        assertEquals(7, eph2.length)

        // Ephemeral identifiers must rotate across time windows
        assertNotEquals(eph1, eph2)

        // Deterministic reproduction for identical epoch window
        val eph1Recomputed = BleSafetyManager.generateEphemeralId(deviceId, epochWindow = 100L)
        assertEquals(eph1, eph1Recomputed)
    }

    @Test
    fun testParserCompatibility() {
        val ephemeralId = "EP-A1B2"
        val bytes = BleSafetyManager.encodePayload(
            deviceId = "SB-CHLD",
            riskLevel = RiskLevel.HIGH,
            lat = 12.9716,
            lon = 77.5946,
            ephemeralId = ephemeralId
        )

        val decoded = BleSafetyManager.decodePayload(bytes)
        assertNotNull(decoded)
        assertEquals(ephemeralId, decoded?.deviceId)
        assertEquals(RiskLevel.HIGH, decoded?.riskLevel)
        assertEquals(12.9716, decoded?.latitude ?: 0.0, 0.001)
        assertEquals(77.5946, decoded?.longitude ?: 0.0, 0.001)
    }

    @Test
    fun testTrustRoleAccessFiltering() {
        val childRealId = "SB-8041"
        val ephemeralId = BleSafetyManager.generateEphemeralId(childRealId, epochWindow = 500L)

        // 1. Unpaired Anonymous Relay node:
        val anonymousRole = manager.resolveTrustRole(ephemeralId)
        assertEquals(TrustRole.ANONYMOUS_RELAY, anonymousRole)
        assertEquals(ephemeralId, manager.resolveRealDeviceId(ephemeralId)) // Unknown real ID

        // 2. Verified Guardian Registration:
        manager.registerPairedDevice(childRealId, TrustRole.TRUSTED_GUARDIAN)
        manager.associateEphemeralId(ephemeralId, childRealId)

        // Resolved as TRUSTED_GUARDIAN and maps back to real ID
        val guardianRole = manager.resolveTrustRole(ephemeralId)
        assertEquals(TrustRole.TRUSTED_GUARDIAN, guardianRole)
        assertEquals(childRealId, manager.resolveRealDeviceId(ephemeralId))
    }

    @Test
    fun testSafetyNodeDiscoveryAndConnectionStates() {
        val now = System.currentTimeMillis()

        // 1. Record nearby strong beacon
        manager.recordNodeObservation(
            nodeId = "SB-NODE-1",
            ephemeralId = "EP-4455",
            rssi = -60,
            hopCount = 0
        )

        val nodes = manager.discoveredSafetyNodes.value
        assertEquals(1, nodes.size)
        val node1 = nodes["EP-4455"]
        assertNotNull(node1)
        assertEquals("EP-4455", node1?.ephemeralId)
        assertEquals("Strong", node1?.getSignalStrengthCategory())
        assertEquals(NodeConnectionState.NEARBY, node1?.connectionState)

        // 2. Record multi-hop relayed beacon
        manager.recordNodeObservation(
            nodeId = "SB-NODE-2",
            ephemeralId = "EP-9988",
            rssi = -80,
            hopCount = 2
        )
        val node2 = manager.discoveredSafetyNodes.value["EP-9988"]
        assertNotNull(node2)
        assertEquals(NodeConnectionState.RELAYED, node2?.connectionState)

        // 3. Pruning stale nodes
        manager.pruneStaleNodes(now + 60_000L) // 60s later
        val staleNode1 = manager.discoveredSafetyNodes.value["EP-4455"]
        assertEquals(NodeConnectionState.OFFLINE, staleNode1?.connectionState)
    }
}
