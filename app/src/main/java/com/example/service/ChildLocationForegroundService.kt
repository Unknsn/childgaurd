package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.preferences.SafeBandPreferences
import com.example.network.SafeBandApiClient
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * ChildLocationForegroundService
 *
 * Implements the mandatory 1-minute child location update policy (Section 8).
 * Declares FOREGROUND_SERVICE_TYPE_LOCATION, operates FusedLocationProviderClient
 * at 60-second target intervals, and uploads verified GPS telemetry to backend.
 */
class ChildLocationForegroundService : Service() {

    companion object {
        private const val TAG = "ChildLocationService"
        private const val NOTIFICATION_ID = 9110
        private const val CHANNEL_ID = "safeband_child_location_channel"
        private const val TARGET_INTERVAL_MS = 60000L // 60 seconds (1 minute policy)
        private const val FASTEST_INTERVAL_MS = 30000L // 30 seconds

        fun start(context: Context) {
            val intent = Intent(context, ChildLocationForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ChildLocationForegroundService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var apiClient: SafeBandApiClient
    private lateinit var preferences: SafeBandPreferences
    private val sequenceNumber = AtomicLong(0)

    override fun onCreate() {
        super.onCreate()
        preferences = SafeBandPreferences(applicationContext)
        apiClient = SafeBandApiClient(preferences)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification("Initializing Child Safety Protection..."))

        setupLocationUpdates()
        Log.i(TAG, "ChildLocationForegroundService started. 60-second location interval active.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SafeBand Child Safety Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Periodic GPS location monitoring and emergency beacon relay"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(statusText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SafeBand Guardian Active")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun setupLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, TARGET_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .setMaxUpdateDelayMillis(TARGET_INTERVAL_MS * 2)
            .setMinUpdateDistanceMeters(5f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    onLocationFixObtained(location)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing: ${e.message}")
        }
    }

    private fun onLocationFixObtained(location: Location) {
        val seq = sequenceNumber.incrementAndGet()
        Log.i(TAG, "[LOCATION-FIX #$seq] Lat=${location.latitude}, Lon=${location.longitude}, Acc=${location.accuracy}m")

        // Update foreground notification status
        val statusText = "Protected • Last location sync: %.4f, %.4f (±%.1fm)".format(
            location.latitude, location.longitude, location.accuracy
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildForegroundNotification(statusText))

        serviceScope.launch {
            try {
                val childId = preferences.getAssociatedChildIdDirect() ?: "CHLD-LOCAL"
                val deviceId = preferences.getOrCreateDeviceId()
                val batteryPct = getBatteryPercentage()

                val uploadResult = apiClient.uploadLocation(
                    childId = childId,
                    deviceId = deviceId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracy,
                    seq = seq,
                    batteryPct = batteryPct
                )

                if (uploadResult.isSuccess) {
                    Log.d(TAG, "Uploaded location #$seq to backend endpoint successfully.")
                } else {
                    Log.w(TAG, "Backend location upload queued for retry: ${uploadResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing location update: ${e.message}")
            }
        }
    }

    private fun getBatteryPercentage(): Int {
        return try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        } catch (_: Exception) { 100 }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
        Log.i(TAG, "ChildLocationForegroundService stopped.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
