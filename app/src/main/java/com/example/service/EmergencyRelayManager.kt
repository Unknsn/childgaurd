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
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
 * Implements Feature 2: Community-Assisted Emergency Alert Propagation (v3.5.0 Hardened).
 *
 * Requirements:
 * - Detects incoming BLE emergency broadcasts (SOS vs Tamper).
 * - STRICT PRIVACY: Nearby participating smartphones do NOT store, log, or display
 *   child name, medical details, or parent phone numbers.
 * - Relaying smartphone captures its own location fix (via LocationManager).
 * - ZERO FAKE GPS: Never generates artificial coordinates when GPS is missing; forwards alert with null coordinates.
 * - Enriches pending queued records if late-arriving GPS fix becomes available.
 * - Stores record in `relay_forwarding_records` Room table.
 * - Automatically drains queued forwarding records when network connectivity returns.
 * - Relaying smartphone displays privacy-preserving notification.
 * - Dispatches authenticated payload with token headers to Parent Node endpoint.
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
        private const val MAX_FORWARDING_ATTEMPTS = 10
        private const val INCIDENT_EXPIRY_MS = 86_400_000L // 24 hours TTL

        fun createRelayPayloadJson(
            broadcastId: String,
            alertType: String,
            lat: Double?,
            lon: Double?,
            accuracy: Float?,
            detectedAt: Long
        ): String {
            val latStr = lat?.toString() ?: "null"
            val lonStr = lon?.toString() ?: "null"
            val accStr = accuracy?.toString() ?: "null"
            return """{"broadcastId":"$broadcastId","alertType":"$alertType","relayLatitude":$latStr,"relayLongitude":$lonStr,"accuracy":$accStr,"timestamp":$detectedAt}"""
        }

        fun createRelayPayloadJson(
            broadcastId: String,
            alertType: String,
            lat: Double,
            lon: Double,
            accuracy: Float,
            detectedAt: Long
        ): String {
            return createRelayPayloadJson(broadcastId, alertType, lat as Double?, lon as Double?, accuracy as Float?, detectedAt)
        }
    }

    private val lastRelayedTimestamps = ConcurrentHashMap<String, Long>()

    private val _latestRelayedRecord = MutableStateFlow<RelayForwardingRecord?>(null)
    val latestRelayedRecord: StateFlow<RelayForwardingRecord?> = _latestRelayedRecord.asStateFlow()

    init {
        createNotificationChannel()
        registerNetworkCallback()
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

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private fun registerNetworkCallback() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        Log.i(TAG, "Internet connectivity restored. Triggering relay queue drain...")
                        if (autoDispatch) {
                            coroutineScope.launch {
                                drainForwardingQueue()
                            }
                        }
                    }
                }
                networkCallback = callback
                cm.registerNetworkCallback(request, callback)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not register network callback: ${e.message}")
        }
    }

    fun unregisterNetworkCallback() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            networkCallback?.let { cm?.unregisterNetworkCallback(it) }
            networkCallback = null
        } catch (e: Exception) {
            Log.w(TAG, "Could not unregister network callback: ${e.message}")
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
            // Intelligent location enrichment: check if a GPS fix arrived after initial fix-less ingest
            val pendingRecord = _latestRelayedRecord.value
            if (pendingRecord != null && pendingRecord.deviceId == broadcastId && pendingRecord.relayLatitude == null) {
                val newLoc = overrideLocation?.let { Triple(it.first, it.second, 5.0f) } ?: captureCurrentLocation()
                if (newLoc != null) {
                    val enriched = pendingRecord.copy(
                        relayLatitude = newLoc.first,
                        relayLongitude = newLoc.second,
                        relayAccuracy = newLoc.third
                    )
                    _latestRelayedRecord.value = enriched
                    relayForwardingDao.insertRecord(enriched)
                    Log.i(TAG, "Enriched pending broadcast $broadcastId with late-arriving GPS fix: (${newLoc.first}, ${newLoc.second})")
                }
            }
            Log.d(TAG, "Broadcast $broadcastId already relayed within last 60s. Skipping duplicate.")
            return false
        }
        lastRelayedTimestamps[broadcastId] = now

        // 1. Capture local relay location fix (HONEST GPS: null if unavailable, NO fake coordinates!)
        val locationFix = overrideLocation?.let { Triple(it.first, it.second, 5.0f) } ?: captureCurrentLocation()
        val lat = locationFix?.first
        val lon = locationFix?.second
        val accuracy = locationFix?.third

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

    /**
     * Drains pending queued records with exponential backoff and 24h expiration.
     */
    suspend fun drainForwardingQueue(): Int {
        val queued = relayForwardingDao.getQueuedRecords()
        var successCount = 0
        val now = System.currentTimeMillis()
        for (record in queued) {
            // Check 24-hour incident expiration policy
            if (now - record.detectionTimestamp > INCIDENT_EXPIRY_MS) {
                relayForwardingDao.updateAttempt(record.id, "EXPIRED", now)
                Log.w(TAG, "Record ${record.id} expired (> 24h). Marked EXPIRED.")
                continue
            }
            // Check exponential backoff (10s * 2^(attempts)) capped at 5 minutes
            val backoffMs = (10_000L * (1L shl record.attempts.coerceAtMost(5))).coerceAtMost(300_000L)
            if (now - record.lastAttemptTimestamp < backoffMs) {
                continue
            }
            if (dispatchRelayPayload(record)) {
                successCount++
            }
        }
        return successCount
    }

    private fun captureCurrentLocation(): Triple<Double, Double, Float>? {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val hasFine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

            if (!hasFine && !hasCoarse) {
                Log.d(TAG, "Location permissions not granted. Relaying alert without GPS coordinates.")
                return null
            }

            @SuppressLint("MissingPermission")
            val gpsLoc: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            @SuppressLint("MissingPermission")
            val netLoc: Location? = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val best = gpsLoc ?: netLoc
            if (best != null && (best.latitude != 0.0 || best.longitude != 0.0)) {
                Triple(best.latitude, best.longitude, if (best.hasAccuracy()) best.accuracy else 15.0f)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not obtain GPS fix for relay location: ${e.message}")
            null
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
                lat = record.relayLatitude,
                lon = record.relayLongitude,
                accuracy = record.relayAccuracy,
                detectedAt = record.detectionTimestamp
            )

            var connection: HttpURLConnection? = null
            try {
                val url = URL(record.targetEndpoint)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; utf-8")
                connection.setRequestProperty("Accept", "application/json")
                val authToken = preferences.getParentNodeAuthTokenDirect()
                if (authToken.isNotBlank()) {
                    connection.setRequestProperty("X-SafeBand-Token", authToken)
                    connection.setRequestProperty("Authorization", "Bearer $authToken")
                }
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
                    val nextAttempts = record.attempts + 1
                    val newStatus = if (nextAttempts >= MAX_FORWARDING_ATTEMPTS) "FAILED" else "QUEUED"
                    relayForwardingDao.updateAttempt(record.id, newStatus, System.currentTimeMillis())
                    false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Network unreachable for ${record.targetEndpoint}. Record queued for later sync: ${e.message}")
                val nextAttempts = record.attempts + 1
                val newStatus = if (nextAttempts >= MAX_FORWARDING_ATTEMPTS) "FAILED" else "QUEUED"
                relayForwardingDao.updateAttempt(record.id, newStatus, System.currentTimeMillis())
                false
            } finally {
                connection?.disconnect()
            }
        }
    }
}
