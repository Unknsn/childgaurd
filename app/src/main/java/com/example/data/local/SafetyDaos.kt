package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SafetyEventDao {
    @Query("SELECT * FROM safety_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<SafetyEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: SafetyEvent): Long

    @Query("DELETE FROM safety_events")
    suspend fun clearAllEvents()

    @Query("SELECT * FROM safety_events WHERE syncStatus = :status ORDER BY timestamp DESC")
    fun getEventsBySyncStatus(status: String): Flow<List<SafetyEvent>>

    @Query("SELECT * FROM safety_events WHERE syncStatus = 'PENDING_SYNC' ORDER BY timestamp ASC")
    suspend fun getPendingSyncEvents(): List<SafetyEvent>

    @Query("UPDATE safety_events SET syncStatus = :newStatus WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, newStatus: String)

    @Query("SELECT * FROM safety_events WHERE observationId = :obsId LIMIT 1")
    suspend fun findByObservationId(obsId: String): SafetyEvent?

    @Query("SELECT COUNT(*) FROM safety_events WHERE observationId = :obsId")
    suspend fun countObservationId(obsId: String): Int

    @Query("UPDATE safety_events SET syncStatus = 'EXPIRED' WHERE syncStatus = 'PENDING_SYNC' AND timestamp < :cutoffTimestamp")
    suspend fun expireOldPendingEvents(cutoffTimestamp: Long): Int
}

@Dao
interface TrustedContactDao {
    @Query("SELECT * FROM trusted_contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<TrustedContact>>

    @Query("SELECT * FROM trusted_contacts")
    suspend fun getContactsSnapshot(): List<TrustedContact>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: TrustedContact): Long

    @Query("DELETE FROM trusted_contacts WHERE id = :id")
    suspend fun deleteContact(id: Long)
}

// =============================================================================
// Feature A: Pickup & Handover DAOs
// =============================================================================

@Dao
interface PickupHandoverDao {
    @Query("SELECT * FROM pickup_authorizations ORDER BY createdAt DESC")
    fun getAllAuthorizations(): Flow<List<PickupAuthorization>>

    @Query("SELECT * FROM pickup_authorizations WHERE status = 'ACTIVE' AND expiresAt > :currentTime ORDER BY expiresAt ASC")
    fun getActiveAuthorizations(currentTime: Long = System.currentTimeMillis()): Flow<List<PickupAuthorization>>

    @Query("SELECT * FROM pickup_authorizations WHERE verificationToken = :token LIMIT 1")
    suspend fun findByVerificationToken(token: String): PickupAuthorization?

    @Query("SELECT * FROM pickup_authorizations WHERE authId = :authId LIMIT 1")
    suspend fun findByAuthId(authId: String): PickupAuthorization?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuthorization(auth: PickupAuthorization): Long

    @Query("UPDATE pickup_authorizations SET status = :newStatus WHERE authId = :authId")
    suspend fun updateAuthorizationStatus(authId: String, newStatus: String)

    @Query("SELECT * FROM handover_records ORDER BY timestamp DESC")
    fun getAllHandoverRecords(): Flow<List<HandoverRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHandoverRecord(record: HandoverRecord): Long
}

// =============================================================================
// Feature B: Emergency Escalation DAOs
// =============================================================================

@Dao
interface EscalationDao {
    @Query("SELECT * FROM escalation_configs WHERE id = 1 LIMIT 1")
    fun getEscalationConfig(): Flow<EscalationConfigEntity?>

    @Query("SELECT * FROM escalation_configs WHERE id = 1 LIMIT 1")
    suspend fun getEscalationConfigSnapshot(): EscalationConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEscalationConfig(config: EscalationConfigEntity): Long

    @Query("SELECT * FROM escalation_records WHERE incidentId = :incidentId ORDER BY timestamp ASC")
    fun getRecordsForIncident(incidentId: String): Flow<List<EscalationRecord>>

    @Query("SELECT * FROM escalation_records WHERE incidentId = :incidentId ORDER BY timestamp ASC")
    suspend fun getRecordsSnapshotForIncident(incidentId: String): List<EscalationRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEscalationRecord(record: EscalationRecord): Long

    @Query("UPDATE escalation_records SET status = :status, acknowledgedBy = :acknowledgedBy, acknowledgedAt = :acknowledgedAt WHERE incidentId = :incidentId AND status = 'DISPATCHED'")
    suspend fun markIncidentAcknowledged(incidentId: String, status: String, acknowledgedBy: String, acknowledgedAt: Long)
}

// =============================================================================
// Feature C: School Safe Zone DAOs
// =============================================================================

@Dao
interface SchoolDao {
    @Query("SELECT * FROM school_profiles ORDER BY schoolName ASC")
    fun getAllSchools(): Flow<List<SchoolProfile>>

    @Query("SELECT * FROM school_profiles WHERE isActive = 1 LIMIT 1")
    fun getActiveSchool(): Flow<SchoolProfile?>

    @Query("SELECT * FROM school_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveSchoolSnapshot(): SchoolProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchool(school: SchoolProfile): Long

    @Update
    suspend fun updateSchool(school: SchoolProfile)

    @Query("DELETE FROM school_profiles WHERE schoolId = :schoolId")
    suspend fun deleteSchool(schoolId: String)

    @Query("SELECT * FROM school_schedule_exceptions WHERE schoolId = :schoolId")
    fun getExceptionsForSchool(schoolId: String): Flow<List<SchoolScheduleException>>

    @Query("SELECT * FROM school_schedule_exceptions WHERE schoolId = :schoolId")
    suspend fun getExceptionsSnapshotForSchool(schoolId: String): List<SchoolScheduleException>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertException(exception: SchoolScheduleException): Long

    @Query("DELETE FROM school_schedule_exceptions WHERE id = :id")
    suspend fun deleteException(id: Long)

    @Query("SELECT * FROM school_verification_logs ORDER BY timestamp DESC")
    fun getAllVerificationLogs(): Flow<List<SchoolVerificationLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVerificationLog(log: SchoolVerificationLog): Long
}

// =============================================================================
// Feature D: Supervised Journey DAOs
// =============================================================================

@Dao
interface JourneyDao {
    @Query("SELECT * FROM supervised_journeys WHERE status = 'ACTIVE' LIMIT 1")
    fun getActiveJourney(): Flow<SupervisedJourney?>

    @Query("SELECT * FROM supervised_journeys WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveJourneySnapshot(): SupervisedJourney?

    @Query("SELECT * FROM supervised_journeys ORDER BY startTimestamp DESC")
    fun getAllJourneys(): Flow<List<SupervisedJourney>>

    @Query("SELECT * FROM supervised_journeys WHERE journeyId = :journeyId LIMIT 1")
    suspend fun findJourneyById(journeyId: String): SupervisedJourney?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJourney(journey: SupervisedJourney): Long

    @Query("UPDATE supervised_journeys SET status = :status, actualArrivalTimestamp = :arrivalTimestamp WHERE journeyId = :journeyId")
    suspend fun updateJourneyStatus(journeyId: String, status: String, arrivalTimestamp: Long? = null)

    @Query("SELECT * FROM journey_events WHERE journeyId = :journeyId ORDER BY timestamp ASC")
    fun getEventsForJourney(journeyId: String): Flow<List<JourneyEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJourneyEvent(event: JourneyEvent): Long
}

// =============================================================================
// Feature E: Emergency Coordination DAOs
// =============================================================================

@Dao
interface CoordinationDao {
    @Query("SELECT * FROM incident_coordination_records WHERE incidentId = :incidentId ORDER BY assignedTimestamp DESC LIMIT 1")
    fun getCoordinationForIncident(incidentId: String): Flow<IncidentCoordinationRecord?>

    @Query("SELECT * FROM incident_coordination_records WHERE incidentId = :incidentId ORDER BY assignedTimestamp DESC LIMIT 1")
    suspend fun getCoordinationSnapshotForIncident(incidentId: String): IncidentCoordinationRecord?

    @Query("SELECT * FROM incident_coordination_records ORDER BY assignedTimestamp DESC")
    fun getAllCoordinations(): Flow<List<IncidentCoordinationRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoordination(record: IncidentCoordinationRecord): Long

    @Query("UPDATE incident_coordination_records SET status = :status, currentMilestone = :milestone WHERE incidentId = :incidentId")
    suspend fun updateMilestone(incidentId: String, status: String, milestone: String)

    @Query("UPDATE incident_coordination_records SET status = 'SAFE_RESOLVED', resolutionReason = :reason, resolvedTimestamp = :timestamp WHERE incidentId = :incidentId")
    suspend fun resolveCoordination(incidentId: String, reason: String, timestamp: Long = System.currentTimeMillis())
}
