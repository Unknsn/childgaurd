package com.example.service

import android.location.Location
import android.util.Log
import com.example.data.local.JourneyEvent
import com.example.data.local.SupervisedJourney
import com.example.data.repository.SafetyRepository
import com.example.model.JourneySafetyStatus
import com.example.model.LocationConfidence
import com.example.model.SafetyNode
import com.example.model.VerifiedLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Supervised Journey Safety Mode.
 *
 * Implements:
 * - Start to Destination corridor compliance.
 * - Route deviation detection based on verified observations.
 * - Unexpected prolonged stop detection (stationary in non-safe transit corridor).
 * - ETA exceeded monitoring.
 * - Device disconnected / BLE lost monitoring during transit.
 * - Distinguishes genuine safety events from missing / low-confidence GPS observations.
 */
class JourneySafetyManager(
    private val repository: SafetyRepository,
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "JourneySafetyManager"
        private const val PROLONGED_STOP_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes
        private const val DISCONNECTION_THRESHOLD_MS = 90_000L // 90 seconds
    }

    private val _journeyStatus = MutableStateFlow(JourneySafetyStatus())
    val journeyStatus: StateFlow<JourneySafetyStatus> = _journeyStatus.asStateFlow()

    private var lastObservedLocation: VerifiedLocation? = null
    private var stationaryStartTimestamp: Long? = null
    private var hasAlertedDeviation = false
    private var hasAlertedEta = false
    private var hasAlertedProlongedStop = false
    private var hasAlertedDisconnection = false

    /**
     * Evaluates current child state against the active supervised journey.
     */
    fun evaluateJourney(
        journey: SupervisedJourney?,
        currentLocation: VerifiedLocation?,
        childNode: SafetyNode? = null,
        currentTime: Long = System.currentTimeMillis()
    ) {
        if (journey == null || journey.status != "ACTIVE") {
            _journeyStatus.value = JourneySafetyStatus(activeJourney = journey)
            return
        }

        var isDeviation = false
        var isProlongedStop = false
        var isEtaExceeded = false
        var isDisconnected = false
        var distanceToDest = 0f
        var progressPercent = 0f

        // 1. Destination Distance and Progress Calculation
        if (currentLocation != null) {
            val distResults = FloatArray(1)
            Location.distanceBetween(
                currentLocation.latitude,
                currentLocation.longitude,
                journey.destLat,
                journey.destLon,
                distResults
            )
            distanceToDest = distResults[0]

            val totalJourneyDist = FloatArray(1)
            Location.distanceBetween(
                journey.startLat,
                journey.startLon,
                journey.destLat,
                journey.destLon,
                totalJourneyDist
            )

            progressPercent = if (totalJourneyDist[0] > 0) {
                ((1f - (distanceToDest / totalJourneyDist[0])) * 100f).coerceIn(0f, 100f)
            } else 0f

            // Check if destination safely reached
            if (distanceToDest <= journey.destRadiusMeters &&
                (currentLocation.confidence == LocationConfidence.HIGH || currentLocation.confidence == LocationConfidence.MEDIUM)
            ) {
                scope.launch {
                    repository.completeSupervisedJourney(journey.journeyId)
                }
                _journeyStatus.value = JourneySafetyStatus(
                    activeJourney = journey,
                    distanceRemainingMeters = distanceToDest,
                    progressPercent = 100f
                )
                return
            }

            // 2. Route Corridor Deviation
            // Check perpendicular distance from current point to line segment (start -> dest)
            val crossTrackDist = calculateCrossTrackDistanceMeters(
                startLat = journey.startLat,
                startLon = journey.startLon,
                endLat = journey.destLat,
                endLon = journey.destLon,
                ptLat = currentLocation.latitude,
                ptLon = currentLocation.longitude
            )

            // Only report deviation if location confidence is reliable (HIGH or MEDIUM)
            if (crossTrackDist > journey.corridorRadiusMeters &&
                (currentLocation.confidence == LocationConfidence.HIGH || currentLocation.confidence == LocationConfidence.MEDIUM) &&
                !currentLocation.isStale()
            ) {
                isDeviation = true
                if (!hasAlertedDeviation) {
                    hasAlertedDeviation = true
                    scope.launch {
                        repository.recordJourneyEvent(
                            journeyId = journey.journeyId,
                            eventType = "DEVIATION",
                            latitude = currentLocation.latitude,
                            longitude = currentLocation.longitude,
                            details = "Child deviated ${crossTrackDist.toInt()}m from safe corridor (allowed: ${journey.corridorRadiusMeters.toInt()}m)",
                            isAlert = true
                        )
                    }
                }
            }

            // 3. Unexpected Prolonged Stop
            val prevLoc = lastObservedLocation
            if (prevLoc != null) {
                val moveDist = FloatArray(1)
                Location.distanceBetween(
                    prevLoc.latitude,
                    prevLoc.longitude,
                    currentLocation.latitude,
                    currentLocation.longitude,
                    moveDist
                )

                // If movement is less than 20m, child is stationary
                if (moveDist[0] < 20f) {
                    if (stationaryStartTimestamp == null) {
                        stationaryStartTimestamp = currentTime
                    } else if (currentTime - stationaryStartTimestamp!! >= PROLONGED_STOP_THRESHOLD_MS) {
                        isProlongedStop = true
                        if (!hasAlertedProlongedStop) {
                            hasAlertedProlongedStop = true
                            scope.launch {
                                repository.recordJourneyEvent(
                                    journeyId = journey.journeyId,
                                    eventType = "PROLONGED_STOP",
                                    latitude = currentLocation.latitude,
                                    longitude = currentLocation.longitude,
                                    details = "Unexpected prolonged stop: child stationary for > 5 min in route corridor",
                                    isAlert = true
                                )
                            }
                        }
                    }
                } else {
                    stationaryStartTimestamp = null
                    hasAlertedProlongedStop = false
                }
            } else {
                stationaryStartTimestamp = currentTime
            }

            lastObservedLocation = currentLocation
        }

        // 4. Expected Arrival Exceeded
        if (currentTime > journey.expectedArrivalTimestamp) {
            isEtaExceeded = true
            if (!hasAlertedEta) {
                hasAlertedEta = true
                scope.launch {
                    val overrunMinutes = ((currentTime - journey.expectedArrivalTimestamp) / 60000L).toInt()
                    repository.recordJourneyEvent(
                        journeyId = journey.journeyId,
                        eventType = "ETA_EXCEEDED",
                        latitude = currentLocation?.latitude,
                        longitude = currentLocation?.longitude,
                        details = "Expected arrival at ${journey.destLabel} exceeded by $overrunMinutes minutes",
                        isAlert = true
                    )
                }
            }
        }

        // 5. Device Disconnected / BLE Beacon Lost during active journey
        if (childNode != null) {
            val signalAge = currentTime - childNode.lastSeenTimestamp
            if (signalAge > DISCONNECTION_THRESHOLD_MS) {
                isDisconnected = true
                if (!hasAlertedDisconnection) {
                    hasAlertedDisconnection = true
                    scope.launch {
                        repository.recordJourneyEvent(
                            journeyId = journey.journeyId,
                            eventType = "DISCONNECTED",
                            latitude = currentLocation?.latitude,
                            longitude = currentLocation?.longitude,
                            details = "Wearable BLE beacon lost during active journey (> ${signalAge / 1000}s)",
                            isAlert = true
                        )
                    }
                }
            } else {
                hasAlertedDisconnection = false
            }
        }

        _journeyStatus.value = JourneySafetyStatus(
            activeJourney = journey,
            isDeviation = isDeviation,
            isProlongedStop = isProlongedStop,
            isEtaExceeded = isEtaExceeded,
            isDeviceDisconnected = isDisconnected,
            distanceRemainingMeters = distanceToDest,
            progressPercent = progressPercent
        )
    }

    /**
     * Calculates perpendicular cross-track distance in meters from point (ptLat, ptLon)
     * to corridor line connecting (startLat, startLon) and (endLat, endLon).
     */
    private fun calculateCrossTrackDistanceMeters(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        ptLat: Double,
        ptLon: Double
    ): Float {
        val distToStart = FloatArray(1)
        Location.distanceBetween(ptLat, ptLon, startLat, startLon, distToStart)

        val distToEnd = FloatArray(1)
        Location.distanceBetween(ptLat, ptLon, endLat, endLon, distToEnd)

        val totalCorridor = FloatArray(1)
        Location.distanceBetween(startLat, startLon, endLat, endLon, totalCorridor)

        if (totalCorridor[0] <= 1f) return distToStart[0]

        // Semi-perimeter approximation for triangle area
        val a = distToStart[0].toDouble()
        val b = distToEnd[0].toDouble()
        val c = totalCorridor[0].toDouble()
        val s = (a + b + c) / 2.0
        val areaSq = s * (s - a) * (s - b) * (s - c)
        if (areaSq <= 0.0) return 0f

        val area = sqrt(areaSq)
        val height = (2.0 * area) / c
        return height.toFloat()
    }
}

