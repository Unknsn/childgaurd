package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import com.example.model.GeofenceState
import com.example.model.LocationConfidence
import com.example.model.RouteState
import com.example.model.RouteWaypoint
import com.example.model.SafeZone
import com.example.model.TrustedRoute
import com.example.model.VerifiedLocation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * True Geofence Engine & Location Confidence Helper (Batch A: Phases 1, 2, 6).
 *
 * Implements deterministic multi-state geofencing taking into account:
 * - GPS horizontal accuracy weighting
 * - Boundary distance differential (Delta = Distance - Radius)
 * - Temporal grace period (15s default) before confirming an exit
 * - Explicit boundary re-entry detection
 * - Route corridor deviation monitoring with orthogonal planar projections
 * - VerifiedLocation generation with confidence classification
 */
class LocationSafetyHelper(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private var fusedClient: FusedLocationProviderClient? = null

    // Raw location
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    // Verified Location (Phase 6)
    private val _verifiedLocation = MutableStateFlow<VerifiedLocation?>(null)
    val verifiedLocation: StateFlow<VerifiedLocation?> = _verifiedLocation.asStateFlow()

    // Backward-compatible legacy binary state
    private val _isOutsideSafeZone = MutableStateFlow(false)
    val isOutsideSafeZone: StateFlow<Boolean> = _isOutsideSafeZone.asStateFlow()

    // Legacy distance to safe zone (meters)
    private val _distanceToSafeZoneMeters = MutableStateFlow<Float?>(null)
    val distanceToSafeZoneMeters: StateFlow<Float?> = _distanceToSafeZoneMeters.asStateFlow()

    // Phase 1: True Geofence Engine states & metrics
    private val _geofenceState = MutableStateFlow(GeofenceState.SAFE)
    val geofenceState: StateFlow<GeofenceState> = _geofenceState.asStateFlow()

    private val _distanceFromCenterMeters = MutableStateFlow<Float?>(null)
    val distanceFromCenterMeters: StateFlow<Float?> = _distanceFromCenterMeters.asStateFlow()

    private val _distanceToBoundaryMeters = MutableStateFlow<Float?>(null)
    val distanceToBoundaryMeters: StateFlow<Float?> = _distanceToBoundaryMeters.asStateFlow()

    // Phase 2: Route corridor states & metrics
    private val _routeState = MutableStateFlow(RouteState.UNKNOWN)
    val routeState: StateFlow<RouteState> = _routeState.asStateFlow()

    private val _distanceToRouteCorridorMeters = MutableStateFlow<Float?>(null)
    val distanceToRouteCorridorMeters: StateFlow<Float?> = _distanceToRouteCorridorMeters.asStateFlow()

    // Geofence configuration parameters
    private var currentSafeZone: SafeZone = SafeZone()
    private var currentTrustedRoute: TrustedRoute = TrustedRoute()

    // State tracking for temporal grace period & hysteresis
    private var exitPendingTimestampMs: Long = 0L
    private val exitGracePeriodMs: Long = 15_000L // 15 seconds grace period
    private val accuracyThresholdMeters: Float = 40.0f // GPS accuracy cap

    init {
        try {
            fusedClient = LocationServices.getFusedLocationProviderClient(context)
        } catch (e: Exception) {
            Log.w("LocationSafetyHelper", "FusedLocationProviderClient init error", e)
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _currentLocation.value = location
            onNewLocationReceived(location)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    @SuppressLint("MissingPermission")
    fun startLocationTracking(safeZone: SafeZone? = null) {
        startLocationUpdates(safeZone ?: SafeZone())
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(safeZone: SafeZone) {
        currentSafeZone = safeZone
        try {
            fusedClient?.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                ?.addOnSuccessListener { loc ->
                    if (loc != null) {
                        _currentLocation.value = loc
                        onNewLocationReceived(loc)
                    }
                }
        } catch (_: SecurityException) {
        } catch (_: Exception) {}

        try {
            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    5000L,
                    5f,
                    locationListener
                )
            } else if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    5000L,
                    5f,
                    locationListener
                )
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {}
    }

    fun stopLocationUpdates() {
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (_: Exception) {}
    }

    fun setTrustedRoute(route: TrustedRoute) {
        currentTrustedRoute = route
        _currentLocation.value?.let { evaluateRouteCorridor(it, route) }
    }

    /**
     * Unified processor for newly arrived location fixes.
     */
    fun onNewLocationReceived(location: Location) {
        evaluateVerifiedLocation(location)
        evaluateSafeZone(location, currentSafeZone)
        evaluateRouteCorridor(location, currentTrustedRoute)
    }

    /**
     * Phase 6: Verified Location with deterministic confidence classification.
     */
    fun evaluateVerifiedLocation(location: Location): VerifiedLocation {
        val now = System.currentTimeMillis()
        val accuracy = if (location.hasAccuracy()) location.accuracy else 50f
        val ageMs = if (location.time > 0) max(0L, now - location.time) else 0L

        val confidence = when {
            accuracy <= 15f && ageMs < 30_000L -> LocationConfidence.HIGH
            accuracy <= 40f && ageMs < 90_000L -> LocationConfidence.MEDIUM
            accuracy <= 100f && ageMs < 180_000L -> LocationConfidence.LOW
            else -> LocationConfidence.UNKNOWN
        }

        val verified = VerifiedLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = accuracy,
            timestamp = now,
            confidence = confidence,
            source = "DEVICE_GPS"
        )
        _verifiedLocation.value = verified
        return verified
    }

    /**
     * Phase 1: True Geofence Engine evaluation.
     * Computes distance from center, delta to boundary, accuracy weighting,
     * hysteresis, grace periods, and re-entry detection.
     */
    fun evaluateSafeZone(location: Location?, safeZone: SafeZone) {
        if (location == null) return
        currentSafeZone = safeZone

        val results = FloatArray(1)
        Location.distanceBetween(
            location.latitude,
            location.longitude,
            safeZone.latitude,
            safeZone.longitude,
            results
        )
        val distCenter = results[0]
        val radius = safeZone.radiusMeters.toFloat()
        val deltaToBoundary = distCenter - radius // > 0 means geometrically outside

        _distanceFromCenterMeters.value = distCenter
        _distanceToBoundaryMeters.value = deltaToBoundary
        _distanceToSafeZoneMeters.value = distCenter

        val accuracy = if (location.hasAccuracy()) location.accuracy else 25f
        val isAccuracyAcceptable = accuracy <= accuracyThresholdMeters
        val prevState = _geofenceState.value
        val nowElapsed = SystemClock.elapsedRealtime()

        val newState: GeofenceState = when {
            // Re-entry detection: Child was pending exit or outside, now well inside
            (prevState == GeofenceState.OUTSIDE || prevState == GeofenceState.EXIT_PENDING) &&
                    deltaToBoundary <= -10f -> {
                exitPendingTimestampMs = 0L
                GeofenceState.REENTERED
            }

            // Child clearly inside the boundary
            deltaToBoundary < -15f -> {
                exitPendingTimestampMs = 0L
                GeofenceState.SAFE
            }

            // Approaching outer boundary: within 15 meters inside the perimeter
            deltaToBoundary >= -15f && deltaToBoundary <= 0f -> {
                exitPendingTimestampMs = 0L
                GeofenceState.APPROACHING
            }

            // Geometrically outside
            deltaToBoundary > 0f -> {
                if (!isAccuracyAcceptable) {
                    // Inaccurate reading (e.g. GPS error margin is larger than the exit delta)
                    if (deltaToBoundary < accuracy * 0.75f) {
                        // Uncertain: keep previous state or mark approaching rather than triggering panic
                        if (prevState == GeofenceState.OUTSIDE) GeofenceState.OUTSIDE else GeofenceState.APPROACHING
                    } else {
                        // Beyond accuracy margin, handle through grace period
                        handleExitGracePeriod(nowElapsed, prevState)
                    }
                } else {
                    // Accurate reading outside the perimeter
                    handleExitGracePeriod(nowElapsed, prevState)
                }
            }

            else -> GeofenceState.SAFE
        }

        _geofenceState.value = newState
        _isOutsideSafeZone.value = (newState == GeofenceState.OUTSIDE)
    }

    private fun handleExitGracePeriod(nowElapsed: Long, prevState: GeofenceState): GeofenceState {
        return if (prevState == GeofenceState.OUTSIDE) {
            GeofenceState.OUTSIDE
        } else if (exitPendingTimestampMs == 0L) {
            exitPendingTimestampMs = nowElapsed
            GeofenceState.EXIT_PENDING
        } else {
            val elapsedOutside = nowElapsed - exitPendingTimestampMs
            if (elapsedOutside >= exitGracePeriodMs) {
                GeofenceState.OUTSIDE
            } else {
                GeofenceState.EXIT_PENDING
            }
        }
    }

    /**
     * Phase 2: Route Deviation Corridor calculation.
     * Computes perpendicular distance from current location to the polyline route corridor.
     */
    fun evaluateRouteCorridor(location: Location?, route: TrustedRoute) {
        if (location == null || !route.isEnabled || route.waypoints.size < 2) {
            _routeState.value = RouteState.UNKNOWN
            _distanceToRouteCorridorMeters.value = null
            return
        }

        val accuracy = if (location.hasAccuracy()) location.accuracy else 25f
        if (accuracy > 65f) {
            _routeState.value = RouteState.UNKNOWN
            _distanceToRouteCorridorMeters.value = null
            return
        }

        val minDistance = computeDistanceToRoutePolyline(location.latitude, location.longitude, route.waypoints)
        _distanceToRouteCorridorMeters.value = minDistance

        val corridorRadius = route.corridorRadiusMeters.toFloat()
        val newState = when {
            minDistance <= corridorRadius * 0.7f -> RouteState.ON_ROUTE
            minDistance <= corridorRadius -> RouteState.APPROACHING_EDGE
            else -> RouteState.ROUTE_DEVIATION
        }
        _routeState.value = newState
    }

    /**
     * Computes minimum orthogonal distance from (lat, lon) to a sequence of waypoints.
     */
    fun computeDistanceToRoutePolyline(lat: Double, lon: Double, waypoints: List<RouteWaypoint>): Float {
        var minDistance = Float.MAX_VALUE
        for (i in 0 until waypoints.size - 1) {
            val distSegment = distanceToSegmentMeters(
                lat, lon,
                waypoints[i].latitude, waypoints[i].longitude,
                waypoints[i + 1].latitude, waypoints[i + 1].longitude
            )
            if (distSegment < minDistance) {
                minDistance = distSegment
            }
        }
        return minDistance
    }

    private fun distanceToSegmentMeters(
        pLat: Double, pLon: Double,
        aLat: Double, aLon: Double,
        bLat: Double, bLon: Double
    ): Float {
        // Equirectangular local projection centered around segment start
        val latMidRad = Math.toRadians(aLat)
        val metersPerDegLat = 111_139.0
        val metersPerDegLon = 111_139.0 * cos(latMidRad)

        val px = (pLon - aLon) * metersPerDegLon
        val py = (pLat - aLat) * metersPerDegLat

        val bx = (bLon - aLon) * metersPerDegLon
        val by = (bLat - aLat) * metersPerDegLat

        val segLenSq = bx * bx + by * by
        if (segLenSq == 0.0) {
            return hypot(px, py).toFloat()
        }

        val t = max(0.0, min(1.0, (px * bx + py * by) / segLenSq))
        val projX = t * bx
        val projY = t * by

        return hypot(px - projX, py - projY).toFloat()
    }

    // =========================================================================
    // Simulation & Evaluation controls
    // =========================================================================

    fun simulateOutsideSafeZone(isOutside: Boolean) {
        _isOutsideSafeZone.value = isOutside
        if (isOutside) {
            _distanceToSafeZoneMeters.value = 450f
            _distanceFromCenterMeters.value = 450f
            _distanceToBoundaryMeters.value = 350f
            _geofenceState.value = GeofenceState.OUTSIDE
        } else {
            _distanceToSafeZoneMeters.value = 40f
            _distanceFromCenterMeters.value = 40f
            _distanceToBoundaryMeters.value = -60f
            _geofenceState.value = GeofenceState.SAFE
        }
    }

    fun simulateGeofenceState(state: GeofenceState) {
        _geofenceState.value = state
        when (state) {
            GeofenceState.SAFE -> {
                _distanceFromCenterMeters.value = 30f
                _distanceToBoundaryMeters.value = -70f
                _isOutsideSafeZone.value = false
            }
            GeofenceState.APPROACHING -> {
                _distanceFromCenterMeters.value = 95f
                _distanceToBoundaryMeters.value = -5f
                _isOutsideSafeZone.value = false
            }
            GeofenceState.EXIT_PENDING -> {
                _distanceFromCenterMeters.value = 110f
                _distanceToBoundaryMeters.value = 10f
                _isOutsideSafeZone.value = false
            }
            GeofenceState.OUTSIDE -> {
                _distanceFromCenterMeters.value = 250f
                _distanceToBoundaryMeters.value = 150f
                _isOutsideSafeZone.value = true
            }
            GeofenceState.REENTERED -> {
                _distanceFromCenterMeters.value = 40f
                _distanceToBoundaryMeters.value = -60f
                _isOutsideSafeZone.value = false
            }
        }
    }

    fun simulateRouteState(state: RouteState) {
        _routeState.value = state
        when (state) {
            RouteState.ON_ROUTE -> _distanceToRouteCorridorMeters.value = 10f
            RouteState.APPROACHING_EDGE -> _distanceToRouteCorridorMeters.value = 45f
            RouteState.ROUTE_DEVIATION -> _distanceToRouteCorridorMeters.value = 120f
            RouteState.UNKNOWN -> _distanceToRouteCorridorMeters.value = null
        }
    }

    fun simulateVerifiedLocation(verified: VerifiedLocation) {
        _verifiedLocation.value = verified
    }
}
