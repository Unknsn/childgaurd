package com.example.network

import android.content.Context
import android.util.Log
import com.example.data.preferences.SafeBandPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class BackendAuthResult(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String,
    val displayName: String?,
    val role: String
)

data class BackendHealthStatus(
    val status: String,
    val version: String,
    val latencyMs: Long
)

class SafeBandApiClient(
    private val preferences: SafeBandPreferences
) {
    companion object {
        private const val TAG = "SafeBandApiClient"
        private const val TIMEOUT_MS = 10000
    }

    private fun getBaseUrl(): String {
        return preferences.getBackendBaseUrlDirect().trim().removeSuffix("/")
    }

    private fun getAuthToken(): String? {
        return preferences.getBackendAccessTokenDirect()
    }

    private suspend fun executeRequest(
        endpoint: String,
        method: String,
        bodyJson: String? = null,
        requiresAuth: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val fullUrl = "${getBaseUrl()}$endpoint"
            val url = URL(fullUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                if (requiresAuth) {
                    val token = getAuthToken()
                    if (!token.isNullOrBlank()) {
                        setRequestProperty("Authorization", "Bearer $token")
                    }
                }
            }

            if (bodyJson != null && (method == "POST" || method == "PUT" || method == "PATCH")) {
                conn.doOutput = true
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(bodyJson)
                    writer.flush()
                }
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } ?: ""

            if (code in 200..299) {
                Result.success(responseText)
            } else {
                Log.w(TAG, "Request to $endpoint failed with HTTP $code: $responseText")
                Result.failure(Exception("HTTP $code: $responseText"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network failure calling $endpoint: ${e.message}")
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    // --- HEALTH CHECK ---
    suspend fun checkHealth(): Result<BackendHealthStatus> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val result = executeRequest("/api/v1/health", "GET", requiresAuth = false)
        val latency = System.currentTimeMillis() - start
        if (result.isSuccess) {
            try {
                val json = JSONObject(result.getOrThrow())
                Result.success(
                    BackendHealthStatus(
                        status = json.optString("status", "UNKNOWN"),
                        version = json.optString("version", "4.0.0"),
                        latencyMs = latency
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Health check failed"))
        }
    }

    // --- GOOGLE AUTHENTICATION ---
    suspend fun authenticateWithGoogle(idToken: String): Result<BackendAuthResult> {
        val body = JSONObject().put("idToken", idToken).toString()
        val res = executeRequest("/api/v1/auth/google", "POST", body, requiresAuth = false)
        return if (res.isSuccess) {
            try {
                val json = JSONObject(res.getOrThrow())
                val user = json.getJSONObject("user")
                val auth = BackendAuthResult(
                    accessToken = json.getString("accessToken"),
                    refreshToken = json.getString("refreshToken"),
                    userId = user.getString("id"),
                    email = user.getString("email"),
                    displayName = if (user.has("displayName") && !user.isNull("displayName")) user.getString("displayName") else null,
                    role = user.getString("role")
                )
                // Persist tokens
                preferences.setBackendTokens(auth.accessToken, auth.refreshToken)
                Result.success(auth)
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else Result.failure(res.exceptionOrNull() ?: Exception("Google auth failed"))
    }

    // --- CHILD PROFILES ---
    suspend fun createChildProfile(
        childName: String,
        age: String?,
        bloodType: String?,
        phone: String?,
        medicalNotes: String?,
        allergies: String?,
        pairedDeviceId: String?
    ): Result<String> {
        val body = JSONObject().apply {
            put("childName", childName)
            put("age", age ?: "")
            put("bloodType", bloodType ?: "O+")
            put("primaryParentPhone", phone ?: "")
            put("medicalNotes", medicalNotes ?: "")
            put("allergies", allergies ?: "")
            put("pairedDeviceId", pairedDeviceId ?: "")
        }.toString()
        val res = executeRequest("/api/v1/children", "POST", body)
        return if (res.isSuccess) {
            try {
                val json = JSONObject(res.getOrThrow())
                Result.success(json.getString("id"))
            } catch (e: Exception) { Result.failure(e) }
        } else Result.failure(res.exceptionOrNull() ?: Exception("Create child failed"))
    }

    suspend fun listChildren(): Result<List<JSONObject>> {
        val res = executeRequest("/api/v1/children", "GET")
        return if (res.isSuccess) {
            try {
                val array = JSONArray(res.getOrThrow())
                val list = mutableListOf<JSONObject>()
                for (i in 0 until array.length()) {
                    list.add(array.getJSONObject(i))
                }
                Result.success(list)
            } catch (e: Exception) { Result.failure(e) }
        } else Result.failure(res.exceptionOrNull() ?: Exception("List children failed"))
    }

    // --- 1-MINUTE LOCATION UPLOAD ---
    suspend fun uploadLocation(
        childId: String,
        deviceId: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        seq: Long,
        batteryPct: Int
    ): Result<Boolean> {
        val body = JSONObject().apply {
            put("childId", childId)
            put("deviceId", deviceId)
            put("latitude", latitude)
            put("longitude", longitude)
            put("accuracyMeters", accuracyMeters)
            put("sequenceNumber", seq)
            put("fixTimestamp", java.time.Instant.now().toString())
            put("batteryPercentage", batteryPct)
        }.toString()

        val res = executeRequest("/api/v1/locations", "POST", body, requiresAuth = false)
        return if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Upload location failed"))
    }

    suspend fun getLatestChildLocation(childId: String): Result<JSONObject?> {
        val res = executeRequest("/api/v1/locations/children/$childId/latest", "GET")
        return if (res.isSuccess) {
            try {
                val body = res.getOrThrow()
                if (body.isBlank()) Result.success(null) else Result.success(JSONObject(body))
            } catch (e: Exception) { Result.failure(e) }
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Get latest location failed"))
        }
    }

    // --- GEOFENCE ZONES ---
    suspend fun createGeofenceZone(
        childId: String,
        name: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float
    ): Result<String> {
        val body = JSONObject().apply {
            put("childId", childId)
            put("name", name)
            put("latitude", latitude)
            put("longitude", longitude)
            put("radiusMeters", radiusMeters)
        }.toString()
        val res = executeRequest("/api/v1/geofences", "POST", body)
        return if (res.isSuccess) {
            try {
                val json = JSONObject(res.getOrThrow())
                Result.success(json.getString("id"))
            } catch (e: Exception) { Result.failure(e) }
        } else Result.failure(res.exceptionOrNull() ?: Exception("Create geofence failed"))
    }

    suspend fun reportGeofenceExit(
        childId: String,
        zoneId: String,
        lat: Double,
        lon: Double,
        acc: Float
    ): Result<Boolean> {
        val body = JSONObject().apply {
            put("childId", childId)
            put("zoneId", zoneId)
            put("latitude", lat)
            put("longitude", lon)
            put("accuracyMeters", acc)
            put("fixTimestamp", java.time.Instant.now().toString())
        }.toString()
        val res = executeRequest("/api/v1/geofences/incidents/exit", "POST", body, requiresAuth = false)
        return if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Report geofence exit failed"))
    }

    // --- EMERGENCY INCIDENTS (SOS / TAMPER) ---
    suspend fun reportEmergencyIncident(
        childId: String,
        deviceId: String,
        incidentType: String, // "MANUAL_SOS", "TAMPER"
        riskLevel: String = "CRITICAL",
        lat: Double? = null,
        lon: Double? = null,
        acc: Float? = null,
        addressText: String? = null
    ): Result<String> {
        val body = JSONObject().apply {
            put("childId", childId)
            put("deviceId", deviceId)
            put("incidentType", incidentType)
            put("riskLevel", riskLevel)
            put("source", if (incidentType == "TAMPER") "ESP32_BLE" else "CHILD_APP")
            lat?.let { put("latitude", it) }
            lon?.let { put("longitude", it) }
            acc?.let { put("accuracyMeters", it) }
            addressText?.let { put("addressText", it) }
            put("startTimestamp", java.time.Instant.now().toString())
        }.toString()

        val res = executeRequest("/api/v1/incidents", "POST", body, requiresAuth = false)
        return if (res.isSuccess) {
            try {
                val json = JSONObject(res.getOrThrow())
                Result.success(json.getString("incidentId"))
            } catch (e: Exception) { Result.failure(e) }
        } else Result.failure(res.exceptionOrNull() ?: Exception("Report emergency failed"))
    }

    // --- BYSTANDER RELAY OBSERVATIONS ---
    suspend fun uploadRelayObservation(
        incidentId: String,
        relayDeviceId: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        rssi: Int,
        hopCount: Int = 1
    ): Result<Boolean> {
        val body = JSONObject().apply {
            put("incidentId", incidentId)
            put("relayDeviceId", relayDeviceId)
            put("latitude", latitude)
            put("longitude", longitude)
            put("accuracyMeters", accuracyMeters)
            put("rssi", rssi)
            put("hopCount", hopCount)
            put("observedAt", java.time.Instant.now().toString())
        }.toString()

        val res = executeRequest("/api/v1/relay/observations", "POST", body, requiresAuth = false)
        return if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Upload relay observation failed"))
    }

    // --- PAIRING & PROVISIONING ---
    suspend fun createPairingSession(childId: String, deviceId: String): Result<JSONObject> {
        val body = JSONObject().apply {
            put("childId", childId)
            put("deviceId", deviceId)
        }.toString()
        val res = executeRequest("/api/v1/pairing/sessions", "POST", body)
        return if (res.isSuccess) {
            try { Result.success(JSONObject(res.getOrThrow())) } catch (e: Exception) { Result.failure(e) }
        } else Result.failure(res.exceptionOrNull() ?: Exception("Create pairing session failed"))
    }

    suspend fun completePairingSession(sessionId: String, challengeResponse: String): Result<JSONObject> {
        val body = JSONObject().apply {
            put("challengeResponse", challengeResponse)
        }.toString()
        val res = executeRequest("/api/v1/pairing/$sessionId/complete", "POST", body)
        return if (res.isSuccess) {
            try { Result.success(JSONObject(res.getOrThrow())) } catch (e: Exception) { Result.failure(e) }
        } else Result.failure(res.exceptionOrNull() ?: Exception("Complete pairing session failed"))
    }
}
