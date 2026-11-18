package com.example.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.local.RelayForwardingDao
import com.example.data.local.RelayForwardingRecord
import com.example.data.preferences.SafeBandPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * EmergencyRelayManager
 *
 * Implements Feature 2: Community-Assisted Emergency Alert Propagation.
 *
 * Requirements:
 * - Detects incoming BLE emergency broadcasts (SOS vs Tamper).
 * - STRICT PRIVACY: Nearby participating smartphones do NOT store, log, or display
 *   child name, medical details, or parent phone numbers.
 * - Relaying smartphone captures its own location fix (via LocationManager).
 * - Stores record in `relay_forwarding_records` Room table.
 * - Relaying smartphone displays privacy-preserving notification:
 *   "Nearby Emergency Broadcast Detected".
 * - Dispatches payload to Parent Node endpoint (IP:Port) via HTTP or queues for retry.
 */
class EmergencyRelayManager(
    private val context: Context,
    private val relayForwardingDao: RelayForwardingDao,
    private val preferences: SafeBandPreferences,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val autoDispatch: Boolean = true
) {
    companion object {
        private const val TAG = "EmergencyRelayMgr"
        const val RELAY_NOTIFICATION_CHANNEL_ID = "safeband_community_relay"
        const val RELAY_NOTIFICATION_ID = 3001
        private const val DEDUPLICATION_WINDOW_MS = 60_000L // 60s deduplication per broadcast ID

        fun createRelayPayloadJson(
            broadcastId: String,
            alertType: String,
            lat: Double,
            lon: Double,
            accuracy: Float,
            detectedAt: Long
        ): String {
            return """{"broadcastId":"$broadcastId","alertType":"$alertType","relayLatitude":$lat,"relayLongitude":$lon,"accuracy":$accuracy,"timestamp":$detectedAt}"""
        }
    }

    private val lastRelayedTimestamps = ConcurrentHashMap<String, Long>()

    private val _latestRelayedRecord = MutableStateFlow<RelayForwardingRecord?>(null)
    val latestRelayedRecord: StateFlow<RelayForwardingRecord?> = _latestRelayedRecord.asStateFlow()

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "SafeBand Community Relay"
            val descriptionText = "Notifications for nearby child safety broadcasts forwarded by this device"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(RELAY_NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Ingests a detected emergency broadcast from BleSafetyMonitorService.
     */
    suspend fun handleEmergencyBroadcast(
        broadcastId: String,
        alertType: String,
        overrideLocation: Pair<Double, Double>? = null
    ): Boolean {
        val relayEnabled = preferences.isCommunityRelayEnabledDirect()
        if (!relayEnabled) {
            Log.d(TAG, "Community relay is disabled in preferences. Skipping relay forwarding.")
            return false
        }

        val now = System.currentTimeMillis()
        val lastRelayed = lastRelayedTimestamps[broadcastId] ?: 0L
        if (now - lastRelayed < DEDUPLICATION_WINDOW_MS) {
            Log.d(TAG, "Broadcast $broadcastId already relayed within last 60s. Skipping duplicate.")
            return false
        }
        lastRelayedTimestamps[broadcastId] = now

        // 1. Capture local relay location fix
        val locationFix = overrideLocation ?: captureCurrentLocation()
        val lat = locationFix.first
        val lon = locationFix.second
        val accuracy = if (overrideLocation != null) 5.0f else 15.0f

        // 2. Resolve parent node endpoint configuration
        val parentIp = preferences.getParentNodeIpDirect().ifEmpty { "127.0.0.1" }
        val parentPort = preferences.getParentNodePortDirect()
        val endpointUrl = "http://$parentIp:$parentPort/api/relay/alert"

        // 3. Create persistent Room forwarding record (Strict Privacy: NO Child PII stored)
        val forwardingRecord = RelayForwardingRecord(
            incidentId = "INC-$broadcastId-$now",
            deviceId = broadcastId,
            eventType = alertType,
            relayLatitude = lat,
            relayLongitude = lon,
            relayAccuracy = accuracy,
            targetEndpoint = endpointUrl,
            forwardStatus = "QUEUED",
            detectionTimestamp = now
        )

        val insertedId = relayForwardingDao.insertRecord(forwardingRecord)
        val recordWithId = forwardingRecord.copy(id = insertedId)
        _latestRelayedRecord.value = recordWithId
        Log.i(TAG, "Created RelayForwardingRecord [${recordWithId.id}] for broadcast $broadcastId at ($lat, $lon)")

        // 4. Post Privacy-Preserving User Notification on Relaying Device
        postPrivacyPreservingRelayNotification(alertType)

        // 5. Asynchronously forward alert payload to Parent Node endpoint
        if (autoDispatch) {
            coroutineScope.launch {
                dispatchRelayPayload(recordWithId)
            }
        }

        return true
    }

    private fun captureCurrentLocation(): Pair<Double, Double> {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPerm && locationManager != null) {
                @SuppressLint("MissingPermission")
                val gpsLoc: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                @SuppressLint("MissingPermission")
                val netLoc: Location? = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                val best = gpsLoc ?: netLoc
                if (best != null) {
                    Pair(best.latitude, best.longitude)
                } else {
                    Pair(37.7749, -122.4194) // Default fallback coordinates
                }
            } else {
                Pair(37.7749, -122.4194)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not obtain GPS fix for relay location: ${e.message}")
            Pair(37.7749, -122.4194)
        }
    }

    private fun postPrivacyPreservingRelayNotification(alertType: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val typeLabel = if (alertType.contains("TAMPER", ignoreCase = true)) "Band Tamper / Cut" else "Emergency SOS"

        val notification = NotificationCompat.Builder(context, RELAY_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Nearby Emergency Broadcast Detected")
            .setContentText("Participating community node detected $typeLabel. Relayed location fix to guardian.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Participating community node detected a nearby $typeLabel broadcast.\n" +
                    "Your device securely relayed its location coordinates to assist emergency response.\n" +
                    "No personal identity details were collected or exposed."
                )
            )
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(RELAY_NOTIFICATION_ID, notification)
    }

    suspend fun dispatchRelayPayload(record: RelayForwardingRecord): Boolean {
        return withContext(Dispatchers.IO) {
            val jsonPayload = createRelayPayloadJson(
                broadcastId = record.deviceId,
                alertType = record.eventType,
                lat = record.relayLatitude ?: 0.0,
                lon = record.relayLongitude ?: 0.0,
                accuracy = record.relayAccuracy ?: 15.0f,
                detectedAt = record.detectionTimestamp
            )

            var connection: HttpURLConnection? = null
            try {
                val url = URL(record.targetEndpoint)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; utf-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.doOutput = true

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(jsonPayload)
                    writer.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    Log.i(TAG, "Relay payload successfully dispatched to ${record.targetEndpoint} (Code: $responseCode)")
                    relayForwardingDao.markDeliveredDirect(
                        record.id,
                        System.currentTimeMillis(),
                        "DIRECT_ACK"
                    )
                    true
                } else {
                    Log.w(TAG, "Relay dispatch returned non-200 code: $responseCode. Queuing for retry.")
                    relayForwardingDao.updateAttempt(record.id, "QUEUED", System.currentTimeMillis())
                    false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Network unreachable for ${record.targetEndpoint}. Record queued for later sync: ${e.message}")
                relayForwardingDao.updateAttempt(record.id, "QUEUED", System.currentTimeMillis())
                false
            } finally {
                connection?.disconnect()
            }
        }
    }
}
