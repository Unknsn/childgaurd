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
}

@Dao
interface TrustedContactDao {
    @Query("SELECT * FROM trusted_contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<TrustedContact>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: TrustedContact): Long

    @Query("DELETE FROM trusted_contacts WHERE id = :id")
    suspend fun deleteContact(id: Long)
}
