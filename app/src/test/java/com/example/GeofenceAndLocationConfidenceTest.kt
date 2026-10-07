package com.example

import android.location.Location
import com.example.model.GeofenceState
import com.example.model.LocationConfidence
import com.example.model.RouteState
import com.example.model.RouteWaypoint
import com.example.model.SafeZone
import com.example.model.TrustedRoute
import com.example.model.VerifiedLocation
import com.example.service.LocationSafetyHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.test.core.app.ApplicationProvider
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeofenceAndLocationConfidenceTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val helper = LocationSafetyHelper(context)

    // Center of safe zone: 37.7749, -122.4194 with 100m radius
    private val safeZone = SafeZone(
        latitude = 37.7749,
        longitude = -122.4194,
        radiusMeters = 100f,
        name = "Test Safe Zone"
    )

    private fun createMockLocation(lat: Double, lon: Double, accuracy: Float = 10f, time: Long = System.currentTimeMillis()): Location {
        return Location("GPS").apply {
            latitude = lat
            longitude = lon
            this.accuracy = accuracy
            this.time = time
        }
    }

    @Test
    fun testClearlyInsideSafeZone() {
        // Location exactly at center (0m distance)
        val loc = createMockLocation(37.7749, -122.4194, accuracy = 5f)
        helper.evaluateSafeZone(loc, safeZone)

        assertEquals(GeofenceState.SAFE, helper.geofenceState.value)
        assertFalse(helper.isOutsideSafeZone.value)
        assertTrue(helper.distanceToBoundaryMeters.value!! < 0) // Negative means inside
        assertTrue(helper.distanceFromCenterMeters.value!! < 20f)
    }

    @Test
    fun testApproachingBoundarySafeZone() {
        // Radius is 100m. Approaching buffer is 20m (80m to 100m).
        // 1 deg lat is approx 111,139m -> 0.00078 deg is approx 86m.
        val loc = createMockLocation(37.7749 + 0.00078, -122.4194, accuracy = 8f)
        helper.evaluateSafeZone(loc, safeZone)

        assertEquals(GeofenceState.APPROACHING, helper.geofenceState.value)
        assertFalse(helper.isOutsideSafeZone.value)
        assertTrue(helper.distanceToBoundaryMeters.value!! <= 0) // Still inside
    }

    @Test
    fun testSingleInaccurateReadingDoesNotTriggerConfirmedExit() {
        // Location is 115m from center (outside 100m radius), but accuracy is 75m (very noisy!)
        val noisyLoc = createMockLocation(37.7749 + 0.00105, -122.4194, accuracy = 75f)
        helper.evaluateSafeZone(noisyLoc, safeZone)

        // Must be treated as uncertain (APPROACHING / EXIT_PENDING) rather than immediately confirming OUTSIDE
        assertTrue(helper.geofenceState.value == GeofenceState.APPROACHING || helper.geofenceState.value == GeofenceState.EXIT_PENDING)
        assertFalse(helper.isOutsideSafeZone.value) // Not confirmed yet
    }

    @Test
    fun testConfirmedExitRequiresGracePeriodAndTimeConfirmation() {
        // First reading outside boundary (120m from center, good accuracy)
        val loc1 = createMockLocation(37.7749 + 0.0011, -122.4194, accuracy = 10f)
        helper.evaluateSafeZone(loc1, safeZone)

        // First outside reading starts EXIT_PENDING
        assertEquals(GeofenceState.EXIT_PENDING, helper.geofenceState.value)
        assertFalse(helper.isOutsideSafeZone.value)

        // Simulate reading right away without grace period passing
        helper.evaluateSafeZone(loc1, safeZone)
        assertEquals(GeofenceState.EXIT_PENDING, helper.geofenceState.value)
        assertFalse(helper.isOutsideSafeZone.value)
    }

    @Test
    fun testReentryFromOutsideExplicitlyDetected() {
        // Child was outside
        helper.simulateOutsideSafeZone(true)
        assertEquals(GeofenceState.OUTSIDE, helper.geofenceState.value)
        assertTrue(helper.isOutsideSafeZone.value)

        // Child moves back inside (center)
        val insideLoc = createMockLocation(37.7749, -122.4194, accuracy = 5f)
        helper.evaluateSafeZone(insideLoc, safeZone)

        // Transition to REENTERED explicitly detected
        assertEquals(GeofenceState.REENTERED, helper.geofenceState.value)
        assertFalse(helper.isOutsideSafeZone.value)

        // Next stable inside reading settles to SAFE
        helper.evaluateSafeZone(insideLoc, safeZone)
        assertEquals(GeofenceState.SAFE, helper.geofenceState.value)
    }

    @Test
    fun testRouteCorridorEvaluationStates() {
        val waypoints = listOf(
            RouteWaypoint("Home", 37.7749, -122.4194),
            RouteWaypoint("Bus Stop", 37.7760, -122.4194),
            RouteWaypoint("School", 37.7780, -122.4194)
        )
        val route = TrustedRoute(
            id = "test_route",
            name = "School Route",
            waypoints = waypoints,
            corridorRadiusMeters = 50f,
            isEnabled = true
        )

        // 1. Directly on route line (at 37.7755, -122.4194)
        helper.evaluateRouteCorridor(createMockLocation(37.7755, -122.4194), route)
        assertEquals(RouteState.ON_ROUTE, helper.routeState.value)
        assertTrue(helper.distanceToRouteCorridorMeters.value!! < 10f)

        // 2. Approaching edge: approx 40m perpendicular offset (within 50m corridor)
        // 0.0004 deg lon is approx 35m
        helper.evaluateRouteCorridor(createMockLocation(37.7755, -122.4194 + 0.00045), route)
        assertEquals(RouteState.APPROACHING_EDGE, helper.routeState.value)

        // 3. Route Deviation: 120m away from route (0.0014 deg lon offset)
        helper.evaluateRouteCorridor(createMockLocation(37.7755, -122.4194 + 0.0014), route)
        assertEquals(RouteState.ROUTE_DEVIATION, helper.routeState.value)
        assertTrue(helper.distanceToRouteCorridorMeters.value!! > 50f)

        // 4. Disabled Route
        helper.evaluateRouteCorridor(createMockLocation(37.7755, -122.4194), route.copy(isEnabled = false))
        assertEquals(RouteState.UNKNOWN, helper.routeState.value)
    }

    @Test
    fun testVerifiedLocationConfidenceAndRelativeTimeString() {
        val now = System.currentTimeMillis()

        // Fresh & Accurate (< 30s, <= 20m) -> HIGH
        val highLoc = VerifiedLocation(
            latitude = 37.7749,
            longitude = -122.4194,
            timestamp = now - 4000L,
            accuracyMeters = 8f,
            confidence = VerifiedLocation.calculateConfidence(8f, 4000L)
        )
        assertEquals(LocationConfidence.HIGH, highLoc.confidence)
        assertEquals("Verified just now", highLoc.getRelativeTimeString(now))

        // Fresh & Moderate Accuracy (< 60s, <= 50m) -> MEDIUM
        val medLoc = VerifiedLocation(
            latitude = 37.7749,
            longitude = -122.4194,
            timestamp = now - 35000L,
            accuracyMeters = 35f,
            confidence = VerifiedLocation.calculateConfidence(35f, 35000L)
        )
        assertEquals(LocationConfidence.MEDIUM, medLoc.confidence)
        assertEquals("Verified 35s ago", medLoc.getRelativeTimeString(now))

        // Stale or Low Accuracy (> 60s or > 50m) -> LOW
        val staleLoc = VerifiedLocation(
            latitude = 37.7749,
            longitude = -122.4194,
            timestamp = now - 180000L,
            accuracyMeters = 60f,
            confidence = VerifiedLocation.calculateConfidence(60f, 180000L)
        )
        assertEquals(LocationConfidence.LOW, staleLoc.confidence)
        assertEquals("Verified 3m ago", staleLoc.getRelativeTimeString(now))
    }
}
