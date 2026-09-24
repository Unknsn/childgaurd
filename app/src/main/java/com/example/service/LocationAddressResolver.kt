package com.example.service

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import com.example.model.GeoAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

object LocationAddressResolver {
    private const val TAG = "LocationAddressResolver"

    // Simple cache to avoid repeated geocoding for identical or close coordinates
    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var cachedAddress: GeoAddress? = null

    suspend fun resolveAddress(
        context: Context,
        latitude: Double?,
        longitude: Double?
    ): GeoAddress? = withContext(Dispatchers.IO) {
        if (latitude == null || longitude == null || (latitude == 0.0 && longitude == 0.0)) {
            return@withContext null
        }

        // Return cache if within ~30 meters (approx 0.0003 deg)
        val prevLat = lastLat
        val prevLon = lastLon
        val prevAddr = cachedAddress
        if (prevLat != null && prevLon != null && prevAddr != null) {
            val dist = Math.hypot(latitude - prevLat, longitude - prevLon)
            if (dist < 0.0003) {
                return@withContext prevAddr
            }
        }

        if (!Geocoder.isPresent()) {
            val fallback = createFallbackAddress(latitude, longitude)
            cachedAddress = fallback
            lastLat = latitude
            lastLon = longitude
            return@withContext fallback
        }

        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val address = withTimeoutOrNull(4000L) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine<Address?> { cont ->
                        try {
                            geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    cont.resume(addresses.firstOrNull())
                                }

                                override fun onError(errorMessage: String?) {
                                    Log.w(TAG, "GeocodeListener error: $errorMessage")
                                    cont.resume(null)
                                }
                            })
                        } catch (e: Exception) {
                            Log.w(TAG, "Exception starting GeocodeListener", e)
                            cont.resume(null)
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val list = geocoder.getFromLocation(latitude, longitude, 1)
                    list?.firstOrNull()
                }
            }

            if (address != null) {
                val streetParts = listOfNotNull(address.subThoroughfare, address.thoroughfare).filter { it.isNotBlank() }
                val street = if (streetParts.isNotEmpty()) streetParts.joinToString(" ") else (address.featureName ?: "")
                val area = address.subLocality ?: address.locality ?: ""
                val city = address.locality ?: address.subAdminArea ?: ""
                val state = address.adminArea ?: ""
                val pinCode = address.postalCode ?: ""
                val full = address.getAddressLine(0) ?: listOfNotNull(street, area, city, state, pinCode).filter { it.isNotBlank() }.joinToString(", ")

                val result = GeoAddress(
                    fullAddress = full,
                    street = street,
                    area = area,
                    city = city,
                    state = state,
                    pinCode = pinCode,
                    latitude = latitude,
                    longitude = longitude
                )
                cachedAddress = result
                lastLat = latitude
                lastLon = longitude
                return@withContext result
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reverse geocode ($latitude, $longitude): ${e.message}")
        }

        val fallback = createFallbackAddress(latitude, longitude)
        cachedAddress = fallback
        lastLat = latitude
        lastLon = longitude
        fallback
    }

    private fun createFallbackAddress(latitude: Double, longitude: Double): GeoAddress {
        val latDir = if (latitude >= 0) "N" else "S"
        val lonDir = if (longitude >= 0) "E" else "W"
        val coordStr = "%.4f° %s, %.4f° %s".format(Math.abs(latitude), latDir, Math.abs(longitude), lonDir)
        return GeoAddress(
            fullAddress = "Location Coordinates: $coordStr",
            street = "Vicinity $coordStr",
            area = "Safe Area Vicinity",
            city = "",
            state = "",
            pinCode = "",
            latitude = latitude,
            longitude = longitude
        )
    }
}
