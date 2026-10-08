package com.example.data.repository

import com.example.data.local.SafetyEvent
import com.example.data.local.SafetyEventDao
import com.example.data.local.TrustedContact
import com.example.data.local.TrustedContactDao
import com.example.data.preferences.SafeBandPreferences
import com.example.model.AppMode
import com.example.model.SafeZone
import com.example.model.SyncStatus
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
    val childBioProfile: Flow<com.example.model.ChildBioProfile> = preferences.childBioProfile
    val trustedRoute: Flow<com.example.model.TrustedRoute> = preferences.trustedRoute
    val safetyMonitoringEnabled: Flow<Boolean> = preferences.safetyMonitoringEnabled

    suspend fun setSafetyMonitoringEnabled(enabled: Boolean) {
        preferences.setSafetyMonitoringEnabled(enabled)
    }

    /**
     * Logs a safety event with offline store-and-forward tracking and duplicate suppression.
     */
    suspend fun logEvent(
        flagType: String,
        riskLevel: String,
        outcome: String,
        deviceId: String,
        details: String = "",
        syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
        observationId: String = "",
        hopCount: Int = 0
    ): Long {
        // Prevent duplicate observation insertion
        if (observationId.isNotBlank()) {
            val existing = eventDao.findByObservationId(observationId)
            if (existing != null) {
                return existing.id
            }
        }
        val event = SafetyEvent(
            flagType = flagType,
            riskLevel = riskLevel,
            outcome = outcome,
            deviceId = deviceId,
            details = details,
            syncStatus = syncStatus.name,
            observationId = observationId,
            hopCount = hopCount
        )
        return eventDao.insertEvent(event)
    }

    suspend fun clearHistory() {
        eventDao.clearAllEvents()
    }

    // =========================================================================
    // Store-and-Forward Lifecycle Transitions (Phase 7)
    // =========================================================================

    suspend fun updateEventSyncStatus(id: Long, status: SyncStatus) {
        eventDao.updateSyncStatus(id, status.name)
    }

    suspend fun markEventPendingSync(id: Long) {
        eventDao.updateSyncStatus(id, SyncStatus.PENDING_SYNC.name)
    }

    suspend fun markEventRelayed(id: Long) {
        eventDao.updateSyncStatus(id, SyncStatus.RELAYED.name)
    }

    suspend fun markEventAcknowledged(id: Long) {
        eventDao.updateSyncStatus(id, SyncStatus.ACKNOWLEDGED.name)
    }

    suspend fun markEventSynced(id: Long) {
        eventDao.updateSyncStatus(id, SyncStatus.SYNCED.name)
    }

    suspend fun expireOldPendingEvents(cutoffTimestamp: Long): Int {
        return eventDao.expireOldPendingEvents(cutoffTimestamp)
    }

    suspend fun getPendingSyncEvents(): List<SafetyEvent> {
        return eventDao.getPendingSyncEvents()
    }

    fun getEventsBySyncStatus(status: SyncStatus): Flow<List<SafetyEvent>> {
        return eventDao.getEventsBySyncStatus(status.name)
    }

    suspend fun isObservationStored(observationId: String): Boolean {
        if (observationId.isBlank()) return false
        return eventDao.countObservationId(observationId) > 0
    }

    // =========================================================================
    // Contact Management
    // =========================================================================

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

    suspend fun saveChildBioProfile(profile: com.example.model.ChildBioProfile) {
        preferences.saveChildBioProfile(profile)
    }

    suspend fun saveTrustedRoute(route: com.example.model.TrustedRoute) {
        preferences.saveTrustedRoute(route)
    }

    suspend fun seedDefaultEmergencyServicesIfNecessary() {
        val existing = contactDao.getContactsSnapshot()
        if (existing.none { it.phoneNumber == "112" }) {
            contactDao.insertContact(
                TrustedContact(
                    name = "Police / National Emergency",
                    phoneNumber = "112",
                    relationship = "Emergency (Police/Fire/Ambulance)"
                )
            )
        }
        if (existing.none { it.phoneNumber == "1098" }) {
            contactDao.insertContact(
                TrustedContact(
                    name = "Childline India (Child Protection)",
                    phoneNumber = "1098",
                    relationship = "24x7 Child Care Helpline"
                )
            )
        }
    }

    suspend fun seedInitialGuardianIfEmpty() {
        val existing = contactDao.getContactsSnapshot()
        val hasGuardian = existing.any { it.phoneNumber != "112" && it.phoneNumber != "1098" }
        if (!hasGuardian) {
            contactDao.insertContact(
                TrustedContact(
                    name = "Mom / Dad (Primary Guardian)",
                    phoneNumber = "+91 98765 43210",
                    relationship = "Primary Guardian"
                )
            )
        }
    }
}
