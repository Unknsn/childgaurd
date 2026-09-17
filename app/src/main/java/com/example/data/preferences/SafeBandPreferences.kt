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

        // Child Bio & Medical Data Keys
        private val KEY_BIO_CHILD_NAME = stringPreferencesKey("bio_child_name")
        private val KEY_BIO_AGE = stringPreferencesKey("bio_age")
        private val KEY_BIO_BLOOD_TYPE = stringPreferencesKey("bio_blood_type")
        private val KEY_BIO_PRIMARY_PHONE = stringPreferencesKey("bio_primary_phone")
        private val KEY_BIO_SECONDARY_PHONE = stringPreferencesKey("bio_secondary_phone")
        private val KEY_BIO_MEDICAL = stringPreferencesKey("bio_medical_conditions")
        private val KEY_BIO_ALLERGIES = stringPreferencesKey("bio_allergies")
        private val KEY_BIO_NOTES = stringPreferencesKey("bio_emergency_notes")
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
}
