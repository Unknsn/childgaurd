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

/**
 * Privacy-Preserving Asynchronous Reverse Geocoder (Phases 2, 3, 5, 6).
 *
 * Requirements:
 * - Runs asynchronously on Dispatchers.IO. Never blocks UI or BLE processing.
 * - Integrates with LocationCache to prevent repeated reverse-geocoding for identical coordinates.
 * - On failure: returns null (NEVER returns fake fallback addresses, default coordinates, or San Francisco).
 * - Human-readable address extraction: Street, Area, City, State, PIN code.
 */
object LocationAddressResolver {
    private const val TAG = "LocationAddressResolver"
    private const val GEOCODE_TIMEOUT_MS = 3500L

    suspend fun resolveAddress(
        context: Context,
        latitude: Double?,
        longitude: Double?
    ): GeoAddress? = withContext(Dispatchers.IO) {
        if (latitude == null || longitude == null || (latitude == 0.0 && longitude == 0.0)) {
            return@withContext null
        }

        // 1. Check LocationCache first
        val cached = LocationCache.getCachedEntry(latitude, longitude)
        if (cached?.addressDetails != null) {
            return@withContext cached.addressDetails
        } else if (cached?.resolvedAddress != null) {
            return@withContext GeoAddress(
                fullAddress = cached.resolvedAddress,
                street = "",
                area = "",
                city = "",
                state = "",
                pinCode = "",
                latitude = latitude,
                longitude = longitude
            )
        }

        if (!Geocoder.isPresent()) {
            Log.w(TAG, "Geocoder service is not present on this device")
            return@withContext null
        }

        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val address = withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
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

                // Cache the resolved result
                LocationCache.putAddress(latitude, longitude, result.formattedSummary(), result)
                return@withContext result
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reverse geocode ($latitude, $longitude): ${e.message}")
        }

        // On failure: return null (never return fake mock address)
        null
    }

    suspend fun resolveFormattedAddress(
        context: Context,
        latitude: Double?,
        longitude: Double?
    ): String? {
        val geo = resolveAddress(context, latitude, longitude)
        return geo?.formattedSummary()
    }
}
