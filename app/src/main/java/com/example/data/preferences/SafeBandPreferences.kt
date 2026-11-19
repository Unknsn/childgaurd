package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.security.SafeBandSecurityManager
import com.example.model.AppMode
import com.example.model.SafeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "safeband_preferences")

class SafeBandPreferences(private val context: Context) {

    companion object {
        private val KEY_APP_MODE = stringPreferencesKey("app_mode")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_SAFE_ZONE_LAT = doublePreferencesKey("safe_zone_lat")
        private val KEY_SAFE_ZONE_LON = doublePreferencesKey("safe_zone_lon")
        private val KEY_SAFE_ZONE_RADIUS = floatPreferencesKey("safe_zone_radius")
        private val KEY_SAFE_ZONE_NAME = stringPreferencesKey("safe_zone_name")

        // Child Bio & Medical Data Keys
        private val KEY_BIO_CHILD_NAME = stringPreferencesKey("bio_child_name")
        private val KEY_BIO_AGE = stringPreferencesKey("bio_age")
        private val KEY_BIO_BLOOD_TYPE = stringPreferencesKey("bio_blood_type")
        private val KEY_BIO_PRIMARY_PHONE = stringPreferencesKey("bio_primary_phone")
        private val KEY_BIO_SECONDARY_PHONE = stringPreferencesKey("bio_secondary_phone")
        private val KEY_BIO_MEDICAL = stringPreferencesKey("bio_medical_conditions")
        private val KEY_BIO_ALLERGIES = stringPreferencesKey("bio_allergies")
        private val KEY_BIO_NOTES = stringPreferencesKey("bio_emergency_notes")

        // Route Corridor Keys (Phase 2)
        private val KEY_ROUTE_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("route_enabled")
        private val KEY_ROUTE_NAME = stringPreferencesKey("route_name")
        private val KEY_ROUTE_CORRIDOR_METERS = floatPreferencesKey("route_corridor_meters")
        private val KEY_ROUTE_WAYPOINTS = stringPreferencesKey("route_waypoints")

        // Background Safety Monitoring (Phase 13)
        private val KEY_SAFETY_MONITORING_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("safety_monitoring_enabled")

        // Parent Node Endpoint Configuration
        private val KEY_PARENT_NODE_IP = stringPreferencesKey("parent_node_ip")
        private val KEY_PARENT_NODE_PORT = androidx.datastore.preferences.core.intPreferencesKey("parent_node_port")
        private val KEY_PARENT_NODE_ENDPOINT_ID = stringPreferencesKey("parent_node_endpoint_id")
        private val KEY_PARENT_NODE_AUTH_TOKEN = stringPreferencesKey("parent_node_auth_token")

        // Community Relay Settings
        private val KEY_RELAY_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("community_relay_enabled")
        private val KEY_AUTO_FORWARD_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("auto_forward_enabled")

        // Backend Cloud / Tunnel Endpoint
        private val KEY_BACKEND_BASE_URL = stringPreferencesKey("backend_base_url")
        private val KEY_BACKEND_ACCESS_TOKEN = stringPreferencesKey("backend_access_token")
        private val KEY_BACKEND_REFRESH_TOKEN = stringPreferencesKey("backend_refresh_token")
        private val KEY_ASSOCIATED_CHILD_ID = stringPreferencesKey("associated_child_id")

        // Guardian Master PIN for Child Mode Lockdown
        private val KEY_MASTER_PIN = stringPreferencesKey("master_guardian_pin")
        const val DEFAULT_CONFIGURED_MASTER_PIN = "998877"

        @Volatile
        private var cachedAppMode: AppMode = AppMode.UNSELECTED

        @Volatile
        private var cachedMasterPin: String = DEFAULT_CONFIGURED_MASTER_PIN

        @Volatile
        private var cachedCommunityRelayEnabled: Boolean = true

        @Volatile
        private var cachedParentNodeIp: String = "192.168.1.100"

        @Volatile
        private var cachedParentNodePort: Int = 8080

        @Volatile
        private var cachedParentNodeEndpointId: String = "PN-HUB-01"

        @Volatile
        private var cachedParentNodeAuthToken: String = "SB-TOKEN-SECURE"

        @Volatile
        private var cachedBackendBaseUrl: String = "http://10.0.2.2:8080"

        @Volatile
        private var cachedBackendAccessToken: String? = null

        @Volatile
        private var cachedBackendRefreshToken: String? = null

        @Volatile
        private var cachedAssociatedChildId: String? = null
    }

    fun isCommunityRelayEnabledDirect(): Boolean = cachedCommunityRelayEnabled
    fun getParentNodeIpDirect(): String = cachedParentNodeIp
    fun getParentNodePortDirect(): Int = cachedParentNodePort
    fun getParentNodeEndpointIdDirect(): String = cachedParentNodeEndpointId
    fun getParentNodeAuthTokenDirect(): String = cachedParentNodeAuthToken
    fun getBackendBaseUrlDirect(): String = cachedBackendBaseUrl
    fun getBackendAccessTokenDirect(): String? = cachedBackendAccessToken
    fun getBackendRefreshTokenDirect(): String? = cachedBackendRefreshToken
    fun getAssociatedChildIdDirect(): String? = cachedAssociatedChildId

    fun setCommunityRelayEnabledDirect(enabled: Boolean) {
        cachedCommunityRelayEnabled = enabled
    }

    val backendBaseUrl: Flow<String> = context.dataStore.data.map { preferences ->
        val url = preferences[KEY_BACKEND_BASE_URL] ?: "http://10.0.2.2:8080"
        cachedBackendBaseUrl = url
        url
    }

    suspend fun setBackendBaseUrl(url: String) {
        val sanitized = url.trim().removeSuffix("/")
        cachedBackendBaseUrl = sanitized
        context.dataStore.edit { it[KEY_BACKEND_BASE_URL] = sanitized }
    }

    suspend fun setBackendTokens(accessToken: String?, refreshToken: String?) {
        cachedBackendAccessToken = accessToken
        cachedBackendRefreshToken = refreshToken
        context.dataStore.edit { prefs ->
            if (accessToken != null) prefs[KEY_BACKEND_ACCESS_TOKEN] = accessToken else prefs.remove(KEY_BACKEND_ACCESS_TOKEN)
            if (refreshToken != null) prefs[KEY_BACKEND_REFRESH_TOKEN] = refreshToken else prefs.remove(KEY_BACKEND_REFRESH_TOKEN)
        }
    }

    suspend fun setAssociatedChildId(childId: String?) {
        cachedAssociatedChildId = childId
        context.dataStore.edit { prefs ->
            if (childId != null) prefs[KEY_ASSOCIATED_CHILD_ID] = childId else prefs.remove(KEY_ASSOCIATED_CHILD_ID)
        }
    }

    val safetyMonitoringEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SAFETY_MONITORING_ENABLED] ?: true
    }

    val parentNodeIp: Flow<String> = context.dataStore.data.map { preferences ->
        val ip = preferences[KEY_PARENT_NODE_IP] ?: "192.168.1.100"
        cachedParentNodeIp = ip
        ip
    }

    val parentNodePort: Flow<Int> = context.dataStore.data.map { preferences ->
        val port = preferences[KEY_PARENT_NODE_PORT] ?: 8080
        cachedParentNodePort = port
        port
    }

    val parentNodeEndpointId: Flow<String> = context.dataStore.data.map { preferences ->
        val endpointId = preferences[KEY_PARENT_NODE_ENDPOINT_ID] ?: "PN-HUB-01"
        cachedParentNodeEndpointId = endpointId
        endpointId
    }

    val parentNodeAuthToken: Flow<String> = context.dataStore.data.map { preferences ->
        val raw = preferences[KEY_PARENT_NODE_AUTH_TOKEN] ?: "SB-TOKEN-SECURE"
        val token = SafeBandSecurityManager.decrypt(raw).ifEmpty { raw }
        cachedParentNodeAuthToken = token
        token
    }

    val communityRelayEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        val enabled = preferences[KEY_RELAY_ENABLED] ?: true
        cachedCommunityRelayEnabled = enabled
        enabled
    }

    val autoForwardEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_FORWARD_ENABLED] ?: true
    }

    suspend fun saveParentNodeEndpoint(ip: String, port: Int, endpointId: String = "PN-HUB-01", token: String = "SB-TOKEN-SECURE") {
        cachedParentNodeIp = ip.trim()
        cachedParentNodePort = port
        cachedParentNodeEndpointId = endpointId.trim()
        cachedParentNodeAuthToken = token.trim()
        val encryptedToken = SafeBandSecurityManager.encrypt(token.trim())
        try {
            context.dataStore.edit { preferences ->
                preferences[KEY_PARENT_NODE_IP] = ip.trim()
                preferences[KEY_PARENT_NODE_PORT] = port
                preferences[KEY_PARENT_NODE_ENDPOINT_ID] = endpointId.trim()
                preferences[KEY_PARENT_NODE_AUTH_TOKEN] = encryptedToken
            }
        } catch (_: Exception) {}
    }

    suspend fun setCommunityRelayEnabled(enabled: Boolean) {
        cachedCommunityRelayEnabled = enabled
        try {
            context.dataStore.edit { preferences ->
                preferences[KEY_RELAY_ENABLED] = enabled
            }
        } catch (_: Exception) {}
    }

    suspend fun setAutoForwardEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_FORWARD_ENABLED] = enabled
        }
    }

    val masterPin: Flow<String> = context.dataStore.data.map { preferences ->
        val pin = preferences[KEY_MASTER_PIN] ?: DEFAULT_CONFIGURED_MASTER_PIN
        cachedMasterPin = pin
        pin
    }

    suspend fun setMasterPin(pin: String) {
        val sanitized = pin.trim().filter { it.isDigit() }
        if (sanitized.length == 6) {
            cachedMasterPin = sanitized
            context.dataStore.edit { preferences ->
                preferences[KEY_MASTER_PIN] = sanitized
            }
        }
    }

    fun getCachedMasterPin(): String = cachedMasterPin

    fun setCachedMasterPin(pin: String) {
        cachedMasterPin = pin.trim().filter { it.isDigit() }
    }

    fun setCachedAppMode(mode: AppMode) {
        cachedAppMode = mode
    }

    fun resetCache() {
        cachedAppMode = AppMode.UNSELECTED
        cachedMasterPin = DEFAULT_CONFIGURED_MASTER_PIN
    }

    val appMode: Flow<AppMode> = context.dataStore.data.map { preferences ->
        val modeStr = preferences[KEY_APP_MODE]
        val mode = try {
            if (modeStr != null) AppMode.valueOf(modeStr) else AppMode.UNSELECTED
        } catch (_: Exception) {
            AppMode.UNSELECTED
        }
        cachedAppMode = mode
        mode
    }

    suspend fun setAppMode(mode: AppMode) {
        cachedAppMode = mode
        context.dataStore.edit { preferences ->
            preferences[KEY_APP_MODE] = mode.name
        }
    }

    fun getCachedAppMode(): AppMode = cachedAppMode

    val deviceId: Flow<String> = context.dataStore.data.map { preferences ->
        val existing = preferences[KEY_DEVICE_ID]
        if (existing != null) {
            existing
        } else {
            val generated = "SB-" + UUID.randomUUID().toString().take(4).uppercase()
            CoroutineScope(Dispatchers.IO).launch {
                context.dataStore.edit { prefs ->
                    if (prefs[KEY_DEVICE_ID] == null) {
                        prefs[KEY_DEVICE_ID] = generated
                    }
                }
            }
            generated
        }
    }

    suspend fun getOrCreateDeviceId(): String {
        val prefs = context.dataStore.data.first()
        val existing = prefs[KEY_DEVICE_ID]
        if (existing != null) return existing
        val generated = "SB-" + UUID.randomUUID().toString().take(4).uppercase()
        context.dataStore.edit { it[KEY_DEVICE_ID] = generated }
        return generated
    }

    val safeZone: Flow<SafeZone> = context.dataStore.data.map { preferences ->
        SafeZone(
            latitude = preferences[KEY_SAFE_ZONE_LAT] ?: 37.7749,
            longitude = preferences[KEY_SAFE_ZONE_LON] ?: -122.4194,
            radiusMeters = preferences[KEY_SAFE_ZONE_RADIUS] ?: 150f,
            name = preferences[KEY_SAFE_ZONE_NAME] ?: "Home Safe Zone"
        )
    }

    val childBioProfile: Flow<com.example.model.ChildBioProfile> = context.dataStore.data.map { preferences ->
        com.example.model.ChildBioProfile(
            childName = preferences[KEY_BIO_CHILD_NAME] ?: "Leo",
            age = preferences[KEY_BIO_AGE] ?: "8",
            bloodType = preferences[KEY_BIO_BLOOD_TYPE] ?: "O+",
            primaryParentPhone = preferences[KEY_BIO_PRIMARY_PHONE] ?: "+1 (555) 019-2834",
            secondaryContactPhone = preferences[KEY_BIO_SECONDARY_PHONE] ?: "+1 (555) 014-9821",
            medicalConditions = preferences[KEY_BIO_MEDICAL] ?: "Asthma (Carries Inhaler)",
            allergies = preferences[KEY_BIO_ALLERGIES] ?: "Severe Peanut & Penicillin Allergy",
            emergencyNotes = preferences[KEY_BIO_NOTES] ?: "Wears medical ID band. In emergency call parents immediately."
        )
    }


    suspend fun setSafetyMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SAFETY_MONITORING_ENABLED] = enabled
        }
    }

    suspend fun setDeviceId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DEVICE_ID] = id
        }
    }

    suspend fun saveSafeZone(zone: SafeZone) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SAFE_ZONE_LAT] = zone.latitude
            preferences[KEY_SAFE_ZONE_LON] = zone.longitude
            preferences[KEY_SAFE_ZONE_RADIUS] = zone.radiusMeters
            preferences[KEY_SAFE_ZONE_NAME] = zone.name
        }
    }

        suspend fun saveChildBioProfile(profile: com.example.model.ChildBioProfile) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BIO_CHILD_NAME] = profile.childName
            preferences[KEY_BIO_AGE] = profile.age
            preferences[KEY_BIO_BLOOD_TYPE] = profile.bloodType
            preferences[KEY_BIO_PRIMARY_PHONE] = profile.primaryParentPhone
            preferences[KEY_BIO_SECONDARY_PHONE] = profile.secondaryContactPhone
            preferences[KEY_BIO_MEDICAL] = profile.medicalConditions
            preferences[KEY_BIO_ALLERGIES] = profile.allergies
            preferences[KEY_BIO_NOTES] = profile.emergencyNotes
        }
    }

    val trustedRoute: Flow<com.example.model.TrustedRoute> = context.dataStore.data.map { preferences ->
        val isEnabled = preferences[KEY_ROUTE_ENABLED] ?: false
        val name = preferences[KEY_ROUTE_NAME] ?: "Home to School Route"
        val corridorMeters = preferences[KEY_ROUTE_CORRIDOR_METERS] ?: 60f
        val rawWaypoints = preferences[KEY_ROUTE_WAYPOINTS]

        val waypoints = if (!rawWaypoints.isNullOrBlank()) {
            decodeWaypoints(rawWaypoints)
        } else {
            // Default demo route based on default safe zone
            val baseLat = preferences[KEY_SAFE_ZONE_LAT] ?: 37.7749
            val baseLon = preferences[KEY_SAFE_ZONE_LON] ?: -122.4194
            listOf(
                com.example.model.RouteWaypoint("Home", baseLat, baseLon),
                com.example.model.RouteWaypoint("Bus Stop", baseLat + 0.002, baseLon + 0.002),
                com.example.model.RouteWaypoint("School", baseLat + 0.005, baseLon + 0.005)
            )
        }

        com.example.model.TrustedRoute(
            id = "default_route",
            name = name,
            waypoints = waypoints,
            corridorRadiusMeters = corridorMeters,
            isEnabled = isEnabled
        )
    }

    suspend fun saveTrustedRoute(route: com.example.model.TrustedRoute) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ROUTE_ENABLED] = route.isEnabled
            preferences[KEY_ROUTE_NAME] = route.name
            preferences[KEY_ROUTE_CORRIDOR_METERS] = route.corridorRadiusMeters
            preferences[KEY_ROUTE_WAYPOINTS] = encodeWaypoints(route.waypoints)
        }
    }

    private fun encodeWaypoints(waypoints: List<com.example.model.RouteWaypoint>): String {
        return waypoints.joinToString("|") { "${it.name}:${it.latitude},${it.longitude}" }
    }

    private fun decodeWaypoints(raw: String): List<com.example.model.RouteWaypoint> {
        return try {
            raw.split("|").mapNotNull { part ->
                val tokens = part.split(":")
                if (tokens.size == 2) {
                    val name = tokens[0]
                    val coords = tokens[1].split(",")
                    if (coords.size == 2) {
                        val lat = coords[0].toDoubleOrNull() ?: 0.0
                        val lon = coords[1].toDoubleOrNull() ?: 0.0
                        com.example.model.RouteWaypoint(name, lat, lon)
                    } else null
                } else null
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
