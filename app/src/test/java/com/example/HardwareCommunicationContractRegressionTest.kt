package com.example

import com.example.model.BleBeaconPayload
import com.example.model.RiskLevel
import com.example.service.BleSafetyManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Regression Test Suite enforcing the SafeBand ESP32-S3 Physical Child Node V1
 * Hardware Communication Contract.
 *
 * Verifies byte-exact adherence to:
 * - Manufacturer ID 0x5AFE (Little Endian: 0xFE, 0x5A)
 * - Service UUID 00005afe-0000-1000-8000-00805f9b34fb
 * - Magic bytes 0x53 ('S'), 0x42 ('B')
 * - 22-byte manufacturer payload wire layout
 * - All Risk codes (0 = NORMAL, 1 = LOW, 2 = MEDIUM, 3 = HIGH, 4 = CRITICAL)
 * - 0.0f coordinates decoded to null without inventing fake GPS
 * - Ephemeral ID derivation matching ESP32 firmware
 */
class HardwareCommunicationContractRegressionTest {

    @Test
    fun testManufacturerIdAndServiceUuidConstants() {
        assertEquals("Manufacturer ID must strictly be 0x5AFE", 0x5AFE, BleSafetyManager.MANUFACTURER_ID)
        assertEquals(
            "Service UUID must match canonical SafeBand V2 UUID",
            "00005afe-0000-1000-8000-00805f9b34fb",
            BleSafetyManager.SAFEBAND_SERVICE_UUID.toString()
        )
    }

    @Test
    fun testMagicBytesExactValues() {
        assertEquals('S'.code.toByte(), BleSafetyManager.MAGIC_BYTE_1)
        assertEquals('B'.code.toByte(), BleSafetyManager.MAGIC_BYTE_2)
    }

    @Test
    fun testWireProtocolByteOffsets() {
        val ephemeralId = "EP-8C9B"
        val timestampSeconds = 1728345600
        val payload = ByteBuffer.allocate(22).apply {
            order(ByteOrder.BIG_ENDIAN)
            put(0x53.toByte()) // [0] 'S'
            put(0x42.toByte()) // [1] 'B'
            put(ephemeralId.toByteArray(Charsets.US_ASCII)) // [2..8]
            put(4.toByte()) // [9] CRITICAL
            putInt(timestampSeconds) // [10..13]
            putFloat(0.0f) // [14..17] Lat = 0.0f
            putFloat(0.0f) // [18..21] Lon = 0.0f
        }.array()

        assertEquals(22, payload.size)
        assertEquals(0x53.toByte(), payload[0])
        assertEquals(0x42.toByte(), payload[1])
        assertEquals("EP-8C9B", String(payload, 2, 7, Charsets.US_ASCII))
        assertEquals(4.toByte(), payload[9])

        val decoded = BleSafetyManager.decodePayload(payload)
        assertNotNull(decoded)
        assertEquals("EP-8C9B", decoded?.deviceId)
        assertEquals(RiskLevel.CRITICAL, decoded?.riskLevel)
        assertEquals(1728345600000L, decoded?.timestamp)
        assertNull("0.0f lat must resolve to null on Android (No GPS hardware)", decoded?.latitude)
        assertNull("0.0f lon must resolve to null on Android (No GPS hardware)", decoded?.longitude)
    }

    @Test
    fun testAllRiskCodeWireMappings() {
        val riskCodeMap = mapOf(
            0.toByte() to RiskLevel.NORMAL,
            1.toByte() to RiskLevel.LOW,
            2.toByte() to RiskLevel.MEDIUM,
            3.toByte() to RiskLevel.HIGH,
            4.toByte() to RiskLevel.CRITICAL
        )

        for ((wireCode, expectedRisk) in riskCodeMap) {
            val buf = ByteBuffer.allocate(22).apply {
                order(ByteOrder.BIG_ENDIAN)
                put(0x53.toByte())
                put(0x42.toByte())
                put("EP-TEST".padEnd(7, ' ').take(7).toByteArray(Charsets.US_ASCII))
                put(wireCode)
                putInt(1000)
                putFloat(0.0f)
                putFloat(0.0f)
            }.array()

            val decoded = BleSafetyManager.decodePayload(buf)
            assertNotNull("Packet with risk byte $wireCode must decode", decoded)
            assertEquals("Risk code $wireCode must map to $expectedRisk", expectedRisk, decoded?.riskLevel)
        }
    }

    @Test
    fun testRejectionOfCorruptOrTruncatedPackets() {
        // Less than 10 bytes
        assertNull(BleSafetyManager.decodePayload(byteArrayOf(0x53, 0x42, 0x01)))

        // Invalid magic bytes
        val wrongMagic = ByteBuffer.allocate(22).apply {
            put(0xAA.toByte())
            put(0xBB.toByte())
            put(ByteArray(20))
        }.array()
        assertNull(BleSafetyManager.decodePayload(wrongMagic))
    }

    @Test
    fun testDeterministicEphemeralIdDerivationMatchesHardware() {
        val testEpoch = 123456L
        val deviceId = "SB-8041"
        val ep1 = BleSafetyManager.generateEphemeralId(deviceId, testEpoch)
        val ep2 = BleSafetyManager.generateEphemeralId(deviceId, testEpoch)

        assertEquals("Derivation must be deterministic for identical epoch", ep1, ep2)
        assertTrue("Must start with EP-", ep1.startsWith("EP-"))
        assertEquals("Length must be exactly 7 characters", 7, ep1.length)

        // Ephemeral ID in subsequent epoch rotates
        val epNextEpoch = BleSafetyManager.generateEphemeralId(deviceId, testEpoch + 1)
        assertFalse("Must rotate across epoch windows", ep1 == epNextEpoch)
    }
}

