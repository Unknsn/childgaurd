package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import com.example.model.SafeZone
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocationSafetyHelper(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private var fusedClient: FusedLocationProviderClient? = null

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val _isOutsideSafeZone = MutableStateFlow(false)
    val isOutsideSafeZone: StateFlow<Boolean> = _isOutsideSafeZone.asStateFlow()

    private val _distanceToSafeZoneMeters = MutableStateFlow<Float?>(null)
    val distanceToSafeZoneMeters: StateFlow<Float?> = _distanceToSafeZoneMeters.asStateFlow()

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
        // Try fused client last location
        try {
            fusedClient?.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                ?.addOnSuccessListener { loc ->
                    if (loc != null) {
                        _currentLocation.value = loc
                        evaluateSafeZone(loc, safeZone)
                    }
                }
        } catch (_: SecurityException) {
        } catch (_: Exception) {}

        // Fallback or continuous listener
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

    fun evaluateSafeZone(location: Location?, safeZone: SafeZone) {
        if (location == null) return
        val results = FloatArray(1)
        Location.distanceBetween(
            location.latitude,
            location.longitude,
            safeZone.latitude,
            safeZone.longitude,
            results
        )
        val distance = results[0]
        _distanceToSafeZoneMeters.value = distance
        _isOutsideSafeZone.value = distance > safeZone.radiusMeters
    }

    fun simulateOutsideSafeZone(isOutside: Boolean) {
        _isOutsideSafeZone.value = isOutside
        if (isOutside) {
            _distanceToSafeZoneMeters.value = 450f
        } else {
            _distanceToSafeZoneMeters.value = 40f
        }
    }
}
