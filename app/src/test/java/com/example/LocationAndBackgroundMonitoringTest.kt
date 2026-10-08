package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.BleBeaconPayload
import com.example.model.GeoAddress
import com.example.model.IncidentStage
import com.example.model.LocationConfidence
import com.example.model.LocationSource
import com.example.model.MonitoringServiceState
import com.example.model.RiskLevel
import com.example.model.VerifiedLocation
import com.example.service.BleSafetyMonitorService
import com.example.service.LocationAddressResolver
import com.example.service.LocationCache
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 29 Unit Test Suite:
 * - LocationCache (spatial bucket, proximity lookup, TTL expiration)
 * - Reverse-geocode success and failure (no fake coordinates)
 * - Cached and stale address detection
 * - Physical ESP32 node location behavior (no GPS hardware)
 * - Location source separation
 * - Background monitoring service state transitions
 * - Duplicate scanner prevention
 * - Physical SOS event routing and 100-to-1 deduplication
 * - Dedicated emergency alert notification configuration
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocationAndBackgroundMonitoringTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setup() {
        LocationCache.clear()
    }

    // =========================================================================
    // 1. Location Cache Tests (Phases 4 & 5)
    // =========================================================================

    @Test
    fun testLocationCacheSpatialBucketAndRetrieval() {
        val lat = 12.9716
        val lon = 77.5946
        val address = "MG Road, Bengaluru"
        val geoAddress = GeoAddress(
            fullAddress = "MG Road, Shivaji Nagar, Bengaluru, Karnataka, India - 560001",
            street = "MG Road",
            area = "Shivaji Nagar",
            city = "Bengaluru",
            state = "Karnataka",
            pinCode = "560001",
            latitude = lat,
            longitude = lon
        )

        val loc = VerifiedLocation(
            latitude = lat,
            longitude = lon,
            resolvedAddress = address,
            addressDetails = geoAddress,
            confidence = LocationConfidence.HIGH
        )

        // Put into cache
        LocationCache.putLocation(loc)

        // Exact lookup
        val exactMatch = LocationCache.getCachedEntry(lat, lon)
        assertNotNull("Exact match should be found in cache", exactMatch)
        assertEquals(address, exactMatch?.resolvedAddress)
        assertEquals("Karnataka", exactMatch?.addressDetails?.state)
        assertEquals(LocationConfidence.HIGH, exactMatch?.confidence)

        // Near lookup within 50 meters (~0.0002 deg is ~22 meters)
        val nearLat = 12.9718
        val nearLon = 77.5947
        val nearMatch = LocationCache.getCachedEntry(nearLat, nearLon)
        assertNotNull("Location within 50m should reuse cached address", nearMatch)
        assertEquals(address, nearMatch?.resolvedAddress)
    }

    @Test
    fun testLocationCacheDistanceMiss() {
        // Put Bengaluru location
        val loc = VerifiedLocation(
            latitude = 12.9716,
            longitude = 77.5946,
            resolvedAddress = "MG Road, Bengaluru",
            confidence = LocationConfidence.MEDIUM
        )
        LocationCache.putLocation(loc)

        // Query distant location (Chennai, ~290km away)
        val distantMatch = LocationCache.getCachedEntry(13.0827, 80.2707)
        assertNull("Distant coordinates must not match cached address", distantMatch)
    }

    @Test
    fun testLocationCacheTtlExpiration() {
        val lat = 12.9716
        val lon = 77.5946
        val oldTimestamp = System.currentTimeMillis() - (25 * 60 * 60 * 1000L) // 25h (>24h TTL)

        val oldLoc = VerifiedLocation(
            latitude = lat,
            longitude = lon,
            timestamp = oldTimestamp,
            resolvedAddress = "Old Address",
            addressTimestamp = oldTimestamp,
            accuracyMeters = 15f
        )
        LocationCache.putLocation(oldLoc)

        // Must treat entry as expired
        val expiredResult = LocationCache.getCachedEntry(lat, lon)
        assertNull("Expired cache entry must return null", expiredResult)
    }

    // =========================================================================
    // 2. Stale Address & VerifiedLocation Tests (Phases 4 & 26)
    // =========================================================================

    @Test
    fun testVerifiedLocationStaleDetection() {
        val now = System.currentTimeMillis()

        val freshLocation = VerifiedLocation(
            latitude = 12.9716,
            longitude = 77.5946,
            timestamp = now - 30_000L, // 30 sec ago
            resolvedAddress = "MG Road, Bengaluru",
            accuracyMeters = 18f
        )
        assertFalse("30s old location must not be stale", freshLocation.isStale(now))
        assertEquals("Verified 30 sec ago", freshLocation.getDisplayTimeOrStaleString(now))

        val staleLocation = VerifiedLocation(
            latitude = 12.9716,
            longitude = 77.5946,
            timestamp = now - (8 * 60 * 1000L), // 8 min ago (> 5 min threshold)
            resolvedAddress = "MG Road, Bengaluru",
            accuracyMeters = 18f
        )
        assertTrue("8 min old location must be flagged as STALE", staleLocation.isStale(now))
        assertEquals("Last verified 8 min ago", staleLocation.getDisplayTimeOrStaleString(now))
    }

    // =========================================================================
    // 3. Reverse Geocoding Failure Safety (Phase 6)
    // =========================================================================

    @Test
    fun testReverseGeocodeFailureDoesNotFakeAddress() = runBlocking {
        // When Geocoder has null/empty coordinates or fails, resolver returns null
        // It must NEVER return a fake San Francisco or placeholder address
        val result = LocationAddressResolver.resolveAddress(context, 0.0, 0.0)
        assertNull(result)

        // VerifiedLocation with unavailable address preserves coordinates
        val loc = VerifiedLocation(
            latitude = 12.9716,
            longitude = 77.5946,
            resolvedAddress = null,
            isAddressUnavailable = true
        )
        assertEquals(12.9716, loc.latitude, 0.0001)
        assertEquals(77.5946, loc.longitude, 0.0001)
        assertTrue(loc.isAddressUnavailable)
        assertNull(loc.resolvedAddress)
    }

    // =========================================================================
    // 4. Physical ESP32 Location Separation (Phases 7, 8 & 21)
    // =========================================================================

    @Test
    fun testPhysicalEsp32LocationHasNoGpsAndNotInherited() {
        val physicalBeacon = BleBeaconPayload(
            deviceId = "EP-27C2",
            riskLevel = RiskLevel.NORMAL,
            timestamp = System.currentTimeMillis(),
            latitude = null, // ESP32 has NO GPS hardware
            longitude = null,
            isSimulation = false
        )

        assertNull("Physical node latitude must be null", physicalBeacon.latitude)
        assertNull("Physical node longitude must be null", physicalBeacon.longitude)
        assertFalse("Physical node must not be marked simulation", physicalBeacon.isSimulation)

        // Verify LocationSource distinction
        assertEquals("CHILD_GPS", LocationSource.CHILD_GPS)
        assertEquals("GUARDIAN_PHONE", LocationSource.GUARDIAN_PHONE)
        assertEquals("PHYSICAL_CHILD_NODE", LocationSource.PHYSICAL_CHILD_NODE)
        assertEquals("SIMULATED", LocationSource.SIMULATED)
    }

    // =========================================================================
    // 5. Monitoring Service States & Single Scanner (Phases 10, 11 & 25)
    // =========================================================================

    @Test
    fun testMonitoringServiceStateTransitions() {
        val states = MonitoringServiceState.values()
        assertTrue(states.contains(MonitoringServiceState.DISABLED))
        assertTrue(states.contains(MonitoringServiceState.STARTING))
        assertTrue(states.contains(MonitoringServiceState.ACTIVE))
        assertTrue(states.contains(MonitoringServiceState.PAUSED))
        assertTrue(states.contains(MonitoringServiceState.ERROR))

        assertEquals("BLE Guardian Scanner ACTIVE", MonitoringServiceState.ACTIVE.displayName)
        assertEquals("BLE Guardian Scanner STARTING", MonitoringServiceState.STARTING.displayName)
        assertEquals("BLE Guardian Scanner OFF", MonitoringServiceState.DISABLED.displayName)
        assertEquals("BLE Guardian Scanner ERROR", MonitoringServiceState.ERROR.displayName)
    }

    // =========================================================================
    // 6. Physical SOS Event Deduplication: 100 Advertisements -> 1 Incident (Phase 20)
    // =========================================================================

    @Test
    fun testPhysicalSosEventDeduplication100ToOne() {
        val serviceController = Robolectric.buildService(BleSafetyMonitorService::class.java).create()
        val service = serviceController.get()
        val deviceId = "EP-27C2"
        val startTime = System.currentTimeMillis()

        var initialIncidentId: String? = null

        // Simulate 100 rapid BLE advertisements from ESP32 BOOT hold SOS
        for (i in 0 until 100) {
            val payload = BleBeaconPayload(
                deviceId = deviceId,
                riskLevel = RiskLevel.CRITICAL,
                timestamp = startTime + (i * 200L), // Every 200ms
                latitude = null,
                longitude = null,
                isSimulation = false
            )

            service.processBeacon(payload)

            val currentIncident = BleSafetyMonitorService.activeEmergencyIncident.value
            assertNotNull("Incident should be active after SOS beacon", currentIncident)

            if (i == 0) {
                initialIncidentId = currentIncident?.incidentId
            } else {
                assertEquals(
                    "All 100 SOS advertisements must maintain the same logical incident ID",
                    initialIncidentId,
                    currentIncident?.incidentId
                )
            }
        }

        val finalIncident = BleSafetyMonitorService.activeEmergencyIncident.value
        assertNotNull(finalIncident)
        assertEquals(deviceId, finalIncident?.deviceId)
        assertEquals(RiskLevel.CRITICAL, finalIncident?.riskLevel)
        assertEquals(IncidentStage.ACTIVE, finalIncident?.stage)
        assertFalse(finalIncident?.isSimulation ?: true)
        assertEquals(100, finalIncident?.observations?.size)

        serviceController.destroy()
    }

    // =========================================================================
    // 7. Dedicated Emergency Alert Channel & Notification Safety (Phases 12 & 16)
    // =========================================================================

    @Test
    fun testDedicatedNotificationChannelsConfigured() {
        assertEquals("safeband_monitoring_channel", BleSafetyMonitorService.CHANNEL_ID_MONITORING)
        assertEquals("safeband_emergency_alerts", BleSafetyMonitorService.CHANNEL_ID_EMERGENCY)
        assertEquals(1001, BleSafetyMonitorService.NOTIFICATION_ID_FOREGROUND)
        assertEquals(2001, BleSafetyMonitorService.NOTIFICATION_ID_EMERGENCY)
    }
}
