package com.example.data.repository

import com.example.data.local.CoordinationDao
import com.example.data.local.EscalationConfigEntity
import com.example.data.local.EscalationDao
import com.example.data.local.EscalationRecord
import com.example.data.local.HandoverRecord
import com.example.data.local.IncidentCoordinationRecord
import com.example.data.local.JourneyDao
import com.example.data.local.JourneyEvent
import com.example.data.local.PickupAuthorization
import com.example.data.local.PickupHandoverDao
import com.example.data.local.SchoolDao
import com.example.data.local.SchoolProfile
import com.example.data.local.SchoolScheduleException
import com.example.data.local.SchoolVerificationLog
import com.example.data.local.SupervisedJourney
import com.example.data.local.SafetyEvent
import com.example.data.local.SafetyEventDao
import com.example.data.local.TrustedContact
import com.example.data.local.TrustedContactDao
import com.example.data.preferences.SafeBandPreferences
import com.example.model.AppMode
import com.example.model.SafeZone
import com.example.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

class SafetyRepository(
    private val eventDao: SafetyEventDao,
    private val contactDao: TrustedContactDao,
    private val preferences: SafeBandPreferences,
    private val pickupDao: PickupHandoverDao? = null,
    private val escalationDao: EscalationDao? = null,
    private val schoolDao: SchoolDao? = null,
    private val journeyDao: JourneyDao? = null,
    private val coordinationDao: CoordinationDao? = null
) {
    val allEvents: Flow<List<SafetyEvent>> = eventDao.getAllEvents()
    val allContacts: Flow<List<TrustedContact>> = contactDao.getAllContacts()
    val appMode: Flow<AppMode> = preferences.appMode
    val deviceId: Flow<String> = preferences.deviceId
    val safeZone: Flow<SafeZone> = preferences.safeZone
    val childBioProfile: Flow<com.example.model.ChildBioProfile> = preferences.childBioProfile
    val trustedRoute: Flow<com.example.model.TrustedRoute> = preferences.trustedRoute
    val safetyMonitoringEnabled: Flow<Boolean> = preferences.safetyMonitoringEnabled

    // Feature A: Pickup & Handover
    val allAuthorizations: Flow<List<PickupAuthorization>> = pickupDao?.getAllAuthorizations() ?: flowOf(emptyList())
    val activeAuthorizations: Flow<List<PickupAuthorization>> = pickupDao?.getActiveAuthorizations() ?: flowOf(emptyList())
    val allHandoverRecords: Flow<List<HandoverRecord>> = pickupDao?.getAllHandoverRecords() ?: flowOf(emptyList())

    // Feature B: Escalation
    val escalationConfig: Flow<EscalationConfigEntity?> = escalationDao?.getEscalationConfig() ?: flowOf(EscalationConfigEntity())

    // Feature C: School
    val allSchools: Flow<List<SchoolProfile>> = schoolDao?.getAllSchools() ?: flowOf(emptyList())
    val activeSchool: Flow<SchoolProfile?> = schoolDao?.getActiveSchool() ?: flowOf(null)
    val schoolVerificationLogs: Flow<List<SchoolVerificationLog>> = schoolDao?.getAllVerificationLogs() ?: flowOf(emptyList())

    // Feature D: Journey
    val activeJourney: Flow<SupervisedJourney?> = journeyDao?.getActiveJourney() ?: flowOf(null)
    val allJourneys: Flow<List<SupervisedJourney>> = journeyDao?.getAllJourneys() ?: flowOf(emptyList())

    // Feature E: Coordination
    val allCoordinations: Flow<List<IncidentCoordinationRecord>> = coordinationDao?.getAllCoordinations() ?: flowOf(emptyList())


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

    // =========================================================================
    // Feature A: Safe Pickup and Handover Verification
    // =========================================================================

    suspend fun createPickupAuthorization(
        childName: String,
        authorizedPersonName: String,
        relationship: String,
        phoneNumber: String,
        validityMinutes: Int,
        isOneTime: Boolean = true,
        notes: String = ""
    ): PickupAuthorization {
        val authId = "AUTH-${UUID.randomUUID().toString().take(8).uppercase()}"
        val tokenChars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        val code = (1..6).map { tokenChars.random() }.joinToString("")
        val now = System.currentTimeMillis()
        val expiresAt = now + (validityMinutes * 60 * 1000L)
        val signature = "SIG-${(authId + authorizedPersonName + expiresAt).hashCode().toString(16).uppercase()}"

        val auth = PickupAuthorization(
            authId = authId,
            childName = childName.trim(),
            authorizedPersonName = authorizedPersonName.trim(),
            relationship = relationship.trim(),
            phoneNumber = phoneNumber.trim(),
            notes = notes.trim(),
            createdAt = now,
            expiresAt = expiresAt,
            isOneTime = isOneTime,
            verificationToken = code,
            status = "ACTIVE",
            parentSignature = signature
        )
        pickupDao?.insertAuthorization(auth)
        return auth
    }

    suspend fun verifyPickupToken(token: String): PickupAuthorization? {
        val cleanToken = token.trim().uppercase()
        val auth = pickupDao?.findByVerificationToken(cleanToken) ?: return null
        val now = System.currentTimeMillis()
        if (auth.status != "ACTIVE" || now >= auth.expiresAt) {
            if (auth.status == "ACTIVE" && now >= auth.expiresAt) {
                pickupDao.updateAuthorizationStatus(auth.authId, "EXPIRED")
            }
            return null
        }
        return auth
    }

    suspend fun approveHandover(
        auth: PickupAuthorization,
        parentName: String = "Primary Parent",
        locationSummary: String = "Designated Pickup Zone",
        notes: String = ""
    ): HandoverRecord {
        val handoverId = "HO-${UUID.randomUUID().toString().take(8).uppercase()}"
        val record = HandoverRecord(
            handoverId = handoverId,
            authId = auth.authId,
            childName = auth.childName,
            pickupPersonName = auth.authorizedPersonName,
            relationship = auth.relationship,
            timestamp = System.currentTimeMillis(),
            status = "APPROVED",
            decisionBy = parentName,
            locationSummary = locationSummary,
            notes = notes
        )
        pickupDao?.insertHandoverRecord(record)
        if (auth.isOneTime) {
            pickupDao?.updateAuthorizationStatus(auth.authId, "USED")
        }
        logEvent(
            flagType = "HANDOVER_APPROVED",
            riskLevel = "NORMAL",
            outcome = "CONFIRMED_ESCALATED",
            deviceId = "LOCAL_APP",
            details = "Handover approved for ${auth.authorizedPersonName} (${auth.relationship})"
        )
        return record
    }

    suspend fun rejectHandover(
        auth: PickupAuthorization,
        parentName: String = "Primary Parent",
        reason: String = "Parent declined handover"
    ): HandoverRecord {
        val handoverId = "HO-${UUID.randomUUID().toString().take(8).uppercase()}"
        val record = HandoverRecord(
            handoverId = handoverId,
            authId = auth.authId,
            childName = auth.childName,
            pickupPersonName = auth.authorizedPersonName,
            relationship = auth.relationship,
            timestamp = System.currentTimeMillis(),
            status = "REJECTED",
            decisionBy = parentName,
            locationSummary = "Pickup Location",
            notes = reason
        )
        pickupDao?.insertHandoverRecord(record)
        logEvent(
            flagType = "HANDOVER_REJECTED",
            riskLevel = "MEDIUM",
            outcome = "CANCELLED_BY_USER",
            deviceId = "LOCAL_APP",
            details = "Handover rejected for ${auth.authorizedPersonName}: $reason"
        )
        return record
    }

    suspend fun revokePickupAuthorization(authId: String) {
        pickupDao?.updateAuthorizationStatus(authId, "REVOKED")
    }

    // =========================================================================
    // Feature B: Emergency Escalation System
    // =========================================================================

    suspend fun saveEscalationConfig(config: EscalationConfigEntity) {
        escalationDao?.saveEscalationConfig(config)
    }

    suspend fun getEscalationConfigSnapshot(): EscalationConfigEntity {
        return escalationDao?.getEscalationConfigSnapshot() ?: EscalationConfigEntity()
    }

    fun getEscalationRecords(incidentId: String): Flow<List<EscalationRecord>> {
        return escalationDao?.getRecordsForIncident(incidentId) ?: flowOf(emptyList())
    }

    suspend fun recordEscalationStep(
        incidentId: String,
        tier: Int,
        recipientName: String,
        recipientContact: String
    ): Long {
        val record = EscalationRecord(
            incidentId = incidentId,
            timestamp = System.currentTimeMillis(),
            tier = tier,
            recipientName = recipientName,
            recipientContact = recipientContact,
            status = "DISPATCHED"
        )
        return escalationDao?.insertEscalationRecord(record) ?: -1L
    }

    suspend fun acknowledgeEscalation(incidentId: String, responderName: String) {
        escalationDao?.markIncidentAcknowledged(
            incidentId = incidentId,
            status = "ACKNOWLEDGED",
            acknowledgedBy = responderName,
            acknowledgedAt = System.currentTimeMillis()
        )
    }

    // =========================================================================
    // Feature C: School Arrival & Departure Verification
    // =========================================================================

    suspend fun saveSchoolProfile(school: SchoolProfile): Long {
        return schoolDao?.insertSchool(school) ?: -1L
    }

    suspend fun deleteSchool(schoolId: String) {
        schoolDao?.deleteSchool(schoolId)
    }

    suspend fun addSchoolHolidayException(schoolId: String, epochDay: Long, reason: String) {
        schoolDao?.insertException(
            SchoolScheduleException(
                schoolId = schoolId,
                dateEpochDay = epochDay,
                reason = reason,
                isHoliday = true
            )
        )
    }

    suspend fun getSchoolExceptionsSnapshot(schoolId: String): List<SchoolScheduleException> {
        return schoolDao?.getExceptionsSnapshotForSchool(schoolId) ?: emptyList()
    }

    suspend fun logSchoolVerification(schoolId: String, eventType: String, confidence: String, details: String) {
        val log = SchoolVerificationLog(
            schoolId = schoolId,
            timestamp = System.currentTimeMillis(),
            eventType = eventType,
            locationConfidence = confidence,
            details = details
        )
        schoolDao?.insertVerificationLog(log)
    }

    // =========================================================================
    // Feature D: Journey Safety Mode
    // =========================================================================

    suspend fun startSupervisedJourney(
        name: String,
        startLabel: String,
        startLat: Double,
        startLon: Double,
        destLabel: String,
        destLat: Double,
        destLon: Double,
        destRadiusMeters: Float = 100f,
        corridorRadiusMeters: Float = 80f,
        expectedDurationMinutes: Int = 30,
        guardianContactsToNotify: String = ""
    ): SupervisedJourney {
        val journeyId = "JRN-${UUID.randomUUID().toString().take(8).uppercase()}"
        val now = System.currentTimeMillis()
        val journey = SupervisedJourney(
            journeyId = journeyId,
            name = name.trim().ifEmpty { "Supervised Journey" },
            startLabel = startLabel.trim(),
            startLat = startLat,
            startLon = startLon,
            destLabel = destLabel.trim(),
            destLat = destLat,
            destLon = destLon,
            destRadiusMeters = destRadiusMeters,
            corridorRadiusMeters = corridorRadiusMeters,
            expectedDurationMinutes = expectedDurationMinutes,
            startTimestamp = now,
            expectedArrivalTimestamp = now + (expectedDurationMinutes * 60 * 1000L),
            guardianContactsToNotify = guardianContactsToNotify,
            status = "ACTIVE"
        )
        journeyDao?.insertJourney(journey)
        recordJourneyEvent(journeyId, "STARTED", startLat, startLon, "Journey departed from $startLabel")
        return journey
    }

    suspend fun recordJourneyEvent(
        journeyId: String,
        eventType: String,
        latitude: Double?,
        longitude: Double?,
        details: String,
        isAlert: Boolean = false
    ) {
        journeyDao?.insertJourneyEvent(
            JourneyEvent(
                journeyId = journeyId,
                timestamp = System.currentTimeMillis(),
                eventType = eventType,
                latitude = latitude,
                longitude = longitude,
                details = details,
                isSafetyAlert = isAlert
            )
        )
    }

    suspend fun completeSupervisedJourney(journeyId: String) {
        val now = System.currentTimeMillis()
        journeyDao?.updateJourneyStatus(journeyId, "COMPLETED", now)
        recordJourneyEvent(journeyId, "ARRIVED", null, null, "Child safely arrived at destination.")
    }

    suspend fun stopSupervisedJourney(journeyId: String) {
        journeyDao?.updateJourneyStatus(journeyId, "STOPPED", null)
        recordJourneyEvent(journeyId, "STOPPED", null, null, "Journey supervision ended by guardian.")
    }

    fun getJourneyEvents(journeyId: String): Flow<List<JourneyEvent>> {
        return journeyDao?.getEventsForJourney(journeyId) ?: flowOf(emptyList())
    }

    // =========================================================================
    // Feature E: Emergency Response Coordination
    // =========================================================================

    fun getIncidentCoordination(incidentId: String): Flow<IncidentCoordinationRecord?> {
        return coordinationDao?.getCoordinationForIncident(incidentId) ?: flowOf(null)
    }

    suspend fun claimIncidentResponse(
        incidentId: String,
        responderName: String,
        responderPhone: String,
        responderRole: String
    ): IncidentCoordinationRecord {
        val record = IncidentCoordinationRecord(
            incidentId = incidentId,
            responderName = responderName.trim(),
            responderPhone = responderPhone.trim(),
            responderRole = responderRole.trim(),
            assignedTimestamp = System.currentTimeMillis(),
            status = "CLAIMED",
            currentMilestone = "$responderName ($responderRole) acknowledged incident and is responding"
        )
        coordinationDao?.insertCoordination(record)
        acknowledgeEscalation(incidentId, responderName)
        return record
    }

    suspend fun updateCoordinationMilestone(incidentId: String, status: String, milestone: String) {
        coordinationDao?.updateMilestone(incidentId, status, milestone)
    }

    suspend fun resolveIncidentCoordination(incidentId: String, reason: String) {
        coordinationDao?.resolveCoordination(incidentId, reason, System.currentTimeMillis())
    }
}
