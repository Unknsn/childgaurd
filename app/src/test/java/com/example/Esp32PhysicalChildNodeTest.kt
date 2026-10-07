package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.RiskEngine
import com.example.model.AlertFlag
import com.example.model.BleBeaconPayload
import com.example.model.IncidentStage
import com.example.model.NodeConnectionState
import com.example.model.RiskLevel
import com.example.model.SafetyIncident
import com.example.service.BleSafetyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer

/**
 * Unit Test Suite for ESP32-S3 Physical Child Node V1 Interoperability & Privacy.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Esp32PhysicalChildNodeTest {

    private lateinit var bleManager: BleSafetyManager
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setup() {
        bleManager = BleSafetyManager(context)
    }

    /**
     * Builds the exact 22-byte manufacturer payload as formatted by ESP32-S3 firmware.
     */
    private fun buildEsp32Payload(
        ephemeralId: String,
        riskByte: Byte,
        timeSeconds: Int,
        lat: Float = 0.0f,
        lon: Float = 0.0f
    ): ByteArray {
        val buffer = ByteBuffer.allocate(22)
        buffer.put(0x53.toByte()) // 'S'
        buffer.put(0x42.toByte()) // 'B'
        val idBytes = ephemeralId.padEnd(7, ' ').take(7).toByteArray(Charsets.US_ASCII)
        buffer.put(idBytes)
        buffer.put(riskByte)
        buffer.putInt(timeSeconds)
        buffer.putFloat(lat)
        buffer.putFloat(lon)
        return buffer.array()
    }

    @Test
    fun testEsp32NormalBeaconDecoding() {
        // ESP32 sends NORMAL (0), lat=0.0f, lon=0.0f (no GPS hardware)
        val espPayload = buildEsp32Payload(
            ephemeralId = "EP-27C2",
            riskByte = 0,
            timeSeconds = 1728345600,
            lat = 0.0f,
            lon = 0.0f
        )

        val decoded = BleSafetyManager.decodePayload(espPayload)
        assertNotNull(decoded)
        assertEquals("EP-27C2", decoded?.deviceId)
        assertEquals(RiskLevel.NORMAL, decoded?.riskLevel)
        assertEquals(1728345600000L, decoded?.timestamp)
        // Verify no fake coordinates are created for physical ESP32 node
        assertNull(decoded?.latitude)
        assertNull(decoded?.longitude)
        assertFalse(decoded?.isSimulation ?: true)
    }

    @Test
    fun testEsp32PhysicalSosBeaconDecoding() {
        // ESP32 sends CRITICAL (4) when BOOT button is held for 2 seconds
        val espPayload = buildEsp32Payload(
            ephemeralId = "EP-27C2",
            riskByte = 4, // CRITICAL
            timeSeconds = 1728345610,
            lat = 0.0f,
            lon = 0.0f
        )

        val decoded = BleSafetyManager.decodePayload(espPayload)
        assertNotNull(decoded)
        assertEquals("EP-27C2", decoded?.deviceId)
        assertEquals(RiskLevel.CRITICAL, decoded?.riskLevel)
        assertFalse(decoded?.isSimulation ?: true)
    }

    @Test
    fun testPhysicalSosTriggersCriticalIncidentWithoutFakeGps() {
        val payload = BleBeaconPayload(
            deviceId = "EP-27C2",
            riskLevel = RiskLevel.CRITICAL,
            timestamp = System.currentTimeMillis(),
            latitude = null,
            longitude = null,
            isSimulation = false
        )

        // Risk Engine processes MANUAL_SOS as CRITICAL immediately
        val risk = RiskEngine.calculateRisk(setOf(AlertFlag.MANUAL_SOS))
        assertEquals(RiskLevel.CRITICAL, risk)

        // SafetyIncident created from physical node
        val incident = SafetyIncident(
            incidentId = "INC-EP-27C2-${payload.timestamp}",
            deviceId = payload.deviceId,
            stage = IncidentStage.ACTIVE,
            riskLevel = payload.riskLevel,
            triggerFlags = setOf(AlertFlag.MANUAL_SOS),
            latitude = payload.latitude,
            longitude = payload.longitude,
            verifiedLocation = null, // No fake coordinates
            isSimulation = payload.isSimulation
        )

        assertTrue(incident.isEmergencyActive)
        assertEquals(RiskLevel.CRITICAL, incident.riskLevel)
        assertFalse(incident.isSimulation)
        assertNull(incident.latitude)
        assertNull(incident.longitude)
        assertNull(incident.verifiedLocation)
    }

    @Test
    fun testDeterministicEphemeralIdDerivation() {
        // Verify deterministic rotation mechanism
        val epoch = 1000L
        val eph1 = BleSafetyManager.generateEphemeralId("SB-CHLD", epoch)
        val eph2 = BleSafetyManager.generateEphemeralId("SB-CHLD", epoch)
        assertEquals(eph1, eph2)
        assertTrue(eph1.startsWith("EP-"))
        assertEquals(7, eph1.length)

        // Next 15-minute epoch produces rotation
        val ephNext = BleSafetyManager.generateEphemeralId("SB-CHLD", epoch + 1L)
        assertFalse(eph1 == ephNext)
    }

    @Test
    fun testPhysicalNodeObservationRegistry() {
        bleManager.recordNodeObservation(
            nodeId = "SB-CHLD",
            ephemeralId = "EP-27C2",
            rssi = -68,
            hopCount = 0
        )

        val nodes = bleManager.discoveredSafetyNodes.value
        assertTrue(nodes.containsKey("EP-27C2"))
        val node = nodes["EP-27C2"]
        assertNotNull(node)
        assertEquals(NodeConnectionState.NEARBY, node?.connectionState)
        assertEquals("Moderate", node?.getSignalStrengthCategory())
    }
}
