package com.example.data.repository

import com.example.data.local.SafetyEvent
import com.example.data.local.SafetyEventDao
import com.example.data.local.TrustedContact
import com.example.data.local.TrustedContactDao
import com.example.data.preferences.SafeBandPreferences
import com.example.model.AppMode
import com.example.model.SafeZone
import kotlinx.coroutines.flow.Flow

class SafetyRepository(
    private val eventDao: SafetyEventDao,
    private val contactDao: TrustedContactDao,
    private val preferences: SafeBandPreferences
) {
    val allEvents: Flow<List<SafetyEvent>> = eventDao.getAllEvents()
    val allContacts: Flow<List<TrustedContact>> = contactDao.getAllContacts()
    val appMode: Flow<AppMode> = preferences.appMode
    val deviceId: Flow<String> = preferences.deviceId
    val safeZone: Flow<SafeZone> = preferences.safeZone

    suspend fun logEvent(
        flagType: String,
        riskLevel: String,
        outcome: String,
        deviceId: String,
        details: String = ""
    ): Long {
        val event = SafetyEvent(
            flagType = flagType,
            riskLevel = riskLevel,
            outcome = outcome,
            deviceId = deviceId,
            details = details
        )
        return eventDao.insertEvent(event)
    }

    suspend fun clearHistory() {
        eventDao.clearAllEvents()
    }

    suspend fun addContact(name: String, phoneNumber: String, relationship: String): Long {
        val contact = TrustedContact(
            name = name.trim(),
            phoneNumber = phoneNumber.trim(),
            relationship = relationship.trim().ifEmpty { "Guardian" }
        )
        return contactDao.insertContact(contact)
    }

    suspend fun deleteContact(id: Long) {
        contactDao.deleteContact(id)
    }

    suspend fun setAppMode(mode: AppMode) {
        preferences.setAppMode(mode)
    }

    suspend fun setDeviceId(id: String) {
        preferences.setDeviceId(id)
    }

    suspend fun saveSafeZone(zone: SafeZone) {
        preferences.saveSafeZone(zone)
    }
}
