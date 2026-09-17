package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppMode
import com.example.model.SafeZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    }

    val appMode: Flow<AppMode> = context.dataStore.data.map { preferences ->
        val modeStr = preferences[KEY_APP_MODE]
        try {
            if (modeStr != null) AppMode.valueOf(modeStr) else AppMode.UNSELECTED
        } catch (_: Exception) {
            AppMode.UNSELECTED
        }
    }

    val deviceId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_DEVICE_ID] ?: run {
            val generated = "SB-" + UUID.randomUUID().toString().take(4).uppercase()
            generated
        }
    }

    val safeZone: Flow<SafeZone> = context.dataStore.data.map { preferences ->
        SafeZone(
            latitude = preferences[KEY_SAFE_ZONE_LAT] ?: 37.7749,
            longitude = preferences[KEY_SAFE_ZONE_LON] ?: -122.4194,
            radiusMeters = preferences[KEY_SAFE_ZONE_RADIUS] ?: 150f,
            name = preferences[KEY_SAFE_ZONE_NAME] ?: "Home Safe Zone"
        )
    }

    suspend fun setAppMode(mode: AppMode) {
        context.dataStore.edit { preferences ->
            preferences[KEY_APP_MODE] = mode.name
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
}
