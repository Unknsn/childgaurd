package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
