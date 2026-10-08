package com.example.service

import com.example.model.GeoAddress
import com.example.model.LocationConfidence
import com.example.model.LocationSource
import com.example.model.VerifiedLocation
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Local Location & Address Cache (Phases 4, 5, 6).
 *
 * Implements:
 * - Spatial bucket caching (~50m threshold) to avoid redundant reverse-geocoding calls.
 * - In-memory and last-known location caching for immediate display.
 * - Stale and freshness calculations.
 * - Offline cached address retrieval with age indicator.
 */
object LocationCache {

    private const val SPATIAL_DISTANCE_THRESHOLD_METERS = 50.0
    private const val ADDRESS_TTL_MILLIS = 24 * 60 * 60 * 1000L // 24 hours

    data class CachedEntry(
        val latitude: Double,
        val longitude: Double,
        val timestamp: Long,
        val accuracyMeters: Float,
        val source: String,
        val confidence: LocationConfidence,
        val resolvedAddress: String?,
        val addressDetails: GeoAddress?,
        val addressTimestamp: Long?
    )

    // Spatial key: rounded to 3 decimal places (~110m bucket) for fast index
    private val memoryCache = ConcurrentHashMap<String, CachedEntry>()

    @Volatile
    private var lastKnownLocation: VerifiedLocation? = null

    private fun makeSpatialBucketKey(lat: Double, lon: Double): String {
        val latBucket = (lat * 1000).toInt()
        val lonBucket = (lon * 1000).toInt()
        return "$latBucket:$lonBucket"
    }

    /**
     * Calculates equirectangular approximate distance between two coordinate pairs in meters.
     */
    fun computeDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val latMidRad = Math.toRadians((lat1 + lat2) / 2.0)
        val metersPerDegLat = 111_139.0
        val metersPerDegLon = 111_139.0 * cos(latMidRad)
        val dy = (lat1 - lat2) * metersPerDegLat
        val dx = (lon1 - lon2) * metersPerDegLon
        return hypot(dx, dy)
    }

    /**
     * Checks if coordinates match an existing entry within the spatial threshold (~50m).
     */
    fun isSameLocation(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Boolean {
        return computeDistanceMeters(lat1, lon1, lat2, lon2) <= SPATIAL_DISTANCE_THRESHOLD_METERS
    }

    /**
     * Retrieves cached entry if within 50m and address has not expired.
     */
    fun getCachedEntry(latitude: Double?, longitude: Double?, now: Long = System.currentTimeMillis()): CachedEntry? {
        if (latitude == null || longitude == null || (latitude == 0.0 && longitude == 0.0)) return null

        // 1. Direct bucket lookup
        val key = makeSpatialBucketKey(latitude, longitude)
        val direct = memoryCache[key]
        if (direct != null && isSameLocation(latitude, longitude, direct.latitude, direct.longitude)) {
            val addrTime = direct.addressTimestamp ?: direct.timestamp
            if (now - addrTime < ADDRESS_TTL_MILLIS) {
                return direct
            }
        }

        // 2. Iterate nearby cached entries within spatial distance threshold
        for (entry in memoryCache.values) {
            if (isSameLocation(latitude, longitude, entry.latitude, entry.longitude)) {
                val addrTime = entry.addressTimestamp ?: entry.timestamp
                if (now - addrTime < ADDRESS_TTL_MILLIS) {
                    return entry
                }
            }
        }

        return null
    }

    /**
     * Retrieves human-readable address from cache if available.
     */
    fun getCachedAddress(latitude: Double?, longitude: Double?, now: Long = System.currentTimeMillis()): String? {
        return getCachedEntry(latitude, longitude, now)?.resolvedAddress
    }

    /**
     * Stores or updates a verified location fix with its associated address.
     */
    fun putLocation(
        verifiedLocation: VerifiedLocation,
        now: Long = System.currentTimeMillis()
    ) {
        val key = makeSpatialBucketKey(verifiedLocation.latitude, verifiedLocation.longitude)
        val existing = memoryCache[key]

        val finalAddress = verifiedLocation.resolvedAddress ?: existing?.resolvedAddress
        val finalAddressDetails = verifiedLocation.addressDetails ?: existing?.addressDetails
        val finalAddressTime = verifiedLocation.addressTimestamp ?: existing?.addressTimestamp ?: if (finalAddress != null) now else null

        val entry = CachedEntry(
            latitude = verifiedLocation.latitude,
            longitude = verifiedLocation.longitude,
            timestamp = verifiedLocation.timestamp,
            accuracyMeters = verifiedLocation.accuracyMeters,
            source = verifiedLocation.source,
            confidence = verifiedLocation.confidence,
            resolvedAddress = finalAddress,
            addressDetails = finalAddressDetails,
            addressTimestamp = finalAddressTime
        )

        memoryCache[key] = entry
        lastKnownLocation = verifiedLocation.copy(
            resolvedAddress = finalAddress,
            addressDetails = finalAddressDetails,
            addressTimestamp = finalAddressTime
        )
    }

    /**
     * Updates only the address for an existing coordinate fix.
     */
    fun putAddress(
        latitude: Double,
        longitude: Double,
        address: String,
        details: GeoAddress? = null,
        now: Long = System.currentTimeMillis()
    ) {
        val key = makeSpatialBucketKey(latitude, longitude)
        val existing = memoryCache[key]

        val entry = CachedEntry(
            latitude = latitude,
            longitude = longitude,
            timestamp = existing?.timestamp ?: now,
            accuracyMeters = existing?.accuracyMeters ?: 10f,
            source = existing?.source ?: LocationSource.CHILD_GPS,
            confidence = existing?.confidence ?: LocationConfidence.MEDIUM,
            resolvedAddress = address,
            addressDetails = details,
            addressTimestamp = now
        )
        memoryCache[key] = entry

        val currentLast = lastKnownLocation
        if (currentLast != null && isSameLocation(latitude, longitude, currentLast.latitude, currentLast.longitude)) {
            lastKnownLocation = currentLast.copy(
                resolvedAddress = address,
                addressDetails = details,
                addressTimestamp = now,
                isResolvingAddress = false,
                isAddressUnavailable = false
            )
        }
    }

    fun getLastKnownLocation(): VerifiedLocation? = lastKnownLocation

    fun setLastKnownLocation(location: VerifiedLocation?) {
        lastKnownLocation = location
        if (location != null) {
            putLocation(location)
        }
    }

    fun clear() {
        memoryCache.clear()
        lastKnownLocation = null
    }
}
