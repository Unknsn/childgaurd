package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SafeBandDatabase
import com.example.data.local.SafetyEvent
import com.example.data.preferences.SafeBandPreferences
import com.example.data.repository.SafetyRepository
import com.example.model.SyncStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit Test Suite for Offline Store-and-Forward Synchronization (Batch C: Phase 7).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StoreAndForwardTest {

    private lateinit var database: SafeBandDatabase
    private lateinit var repository: SafetyRepository
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(context, SafeBandDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val preferences = SafeBandPreferences(context)
        repository = SafetyRepository(
            eventDao = database.safetyEventDao(),
            contactDao = database.trustedContactDao(),
            preferences = preferences
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testOfflineEventPersistence() = runBlocking {
        val eventId = repository.logEvent(
            flagType = "SOS",
            riskLevel = "CRITICAL",
            outcome = "CONFIRMED_IMMEDIATE_SOS",
            deviceId = "SB-OFFLINE",
            details = "Offline emergency triggered without internet connectivity",
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        assertTrue(eventId > 0)

        val allEvents = repository.allEvents.first()
        assertEquals(1, allEvents.size)
        val event = allEvents[0]
        assertEquals("SOS", event.flagType)
        assertEquals("CRITICAL", event.riskLevel)
        assertEquals(SyncStatus.LOCAL_ONLY.name, event.syncStatus)
    }

    @Test
    fun testStoreAndForwardLifecycleProgression() = runBlocking {
        // 1. Initial creation as LOCAL_ONLY
        val eventId = repository.logEvent(
            flagType = "GEOFENCE_EXIT",
            riskLevel = "MEDIUM",
            outcome = "CONFIRMED_ESCALATED",
            deviceId = "SB-8041",
            syncStatus = SyncStatus.LOCAL_ONLY
        )

        // 2. Queue for forwarding: LOCAL_ONLY -> PENDING_SYNC
        repository.markEventPendingSync(eventId)
        var pending = repository.getPendingSyncEvents()
        assertEquals(1, pending.size)
        assertEquals(SyncStatus.PENDING_SYNC.name, pending[0].syncStatus)

        // 3. Observed by peer relay: PENDING_SYNC -> RELAYED
        repository.markEventRelayed(eventId)
        var allEvents = repository.allEvents.first()
        assertEquals(SyncStatus.RELAYED.name, allEvents[0].syncStatus)

        // 4. Guardian acknowledgement: RELAYED -> ACKNOWLEDGED
        repository.markEventAcknowledged(eventId)
        allEvents = repository.allEvents.first()
        assertEquals(SyncStatus.ACKNOWLEDGED.name, allEvents[0].syncStatus)

        // 5. Final synchronization confirmed: ACKNOWLEDGED -> SYNCED
        repository.markEventSynced(eventId)
        allEvents = repository.allEvents.first()
        assertEquals(SyncStatus.SYNCED.name, allEvents[0].syncStatus)

        // Ensure no pending events remain
        pending = repository.getPendingSyncEvents()
        assertTrue(pending.isEmpty())
    }

    @Test
    fun testOldPendingEventsExpiry() = runBlocking {
        val now = System.currentTimeMillis()

        // Fresh pending event (30 seconds old)
        val freshEventId = repository.logEvent(
            flagType = "MOTION",
            riskLevel = "LOW",
            outcome = "LOGGED_LOCAL",
            deviceId = "SB-FRESH",
            syncStatus = SyncStatus.PENDING_SYNC
        )

        // Manually insert an old pending event (5 minutes old)
        val oldEvent = SafetyEvent(
            timestamp = now - 300_000L,
            flagType = "ROUTE",
            riskLevel = "LOW",
            outcome = "LOGGED_LOCAL",
            deviceId = "SB-OLD",
            syncStatus = SyncStatus.PENDING_SYNC.name
        )
        val oldEventId = database.safetyEventDao().insertEvent(oldEvent)

        // Expire pending events older than 2 minutes (120_000L)
        val expiredCount = repository.expireOldPendingEvents(now - 120_000L)
        assertEquals(1, expiredCount)

        // Fresh event remains PENDING_SYNC
        val pendingEvents = repository.getPendingSyncEvents()
        assertEquals(1, pendingEvents.size)
        assertEquals(freshEventId, pendingEvents[0].id)

        // Old event is now EXPIRED
        val allEvents = repository.allEvents.first()
        val expiredEvent = allEvents.find { it.id == oldEventId }
        assertNotNull(expiredEvent)
        assertEquals(SyncStatus.EXPIRED.name, expiredEvent?.syncStatus)
    }

    @Test
    fun testDuplicateObservationSuppression() = runBlocking {
        val observationId = "OBS-9988-12345"

        // First observation from Relay Phone A
        val id1 = repository.logEvent(
            flagType = "BLE_BEACON_RECEIVED",
            riskLevel = "HIGH",
            outcome = "ALERT_RECEIVED",
            deviceId = "SB-CHILD-01",
            details = "Observed by Phone A",
            syncStatus = SyncStatus.RELAYED,
            observationId = observationId,
            hopCount = 1
        )

        // Second duplicate observation from Relay Phone B for the same incident packet
        val id2 = repository.logEvent(
            flagType = "BLE_BEACON_RECEIVED",
            riskLevel = "HIGH",
            outcome = "ALERT_RECEIVED",
            deviceId = "SB-CHILD-01",
            details = "Observed by Phone B",
            syncStatus = SyncStatus.RELAYED,
            observationId = observationId,
            hopCount = 2
        )

        // Must return the existing ID and suppress duplicate row insertion
        assertEquals(id1, id2)
        val allEvents = repository.allEvents.first()
        assertEquals(1, allEvents.size)
        assertEquals("Observed by Phone A", allEvents[0].details)
    }

    @Test
    fun testNonDestructiveSchemaMigrationSql() {
        // Test that migration SQL correctly adds columns with default values
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("test_migration.db")
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        // Create schema v1
                        db.execSQL(
                            """
                            CREATE TABLE safety_events (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                timestamp INTEGER NOT NULL,
                                flagType TEXT NOT NULL,
                                riskLevel TEXT NOT NULL,
                                outcome TEXT NOT NULL,
                                deviceId TEXT NOT NULL,
                                details TEXT NOT NULL
                            )
                            """.trimIndent()
                        )
                        // Insert existing user record under v1
                        db.execSQL(
                            """
                            INSERT INTO safety_events (timestamp, flagType, riskLevel, outcome, deviceId, details)
                            VALUES (1000000, 'SOS', 'HIGH', 'CONFIRMED_ESCALATED', 'SB-100', 'Pre-existing record')
                            """.trimIndent()
                        )
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )

        val db = openHelper.writableDatabase

        // Execute MIGRATION_1_2
        SafeBandDatabase.MIGRATION_1_2.migrate(db)

        // Query migrated record: existing data must be perfectly preserved, with new default columns populated
        val cursor = db.query("SELECT * FROM safety_events WHERE deviceId = 'SB-100'")
        assertTrue(cursor.moveToFirst())

        val idIdx = cursor.getColumnIndex("id")
        val detailsIdx = cursor.getColumnIndex("details")
        val syncStatusIdx = cursor.getColumnIndex("syncStatus")
        val obsIdIdx = cursor.getColumnIndex("observationId")
        val hopCountIdx = cursor.getColumnIndex("hopCount")

        assertEquals("Pre-existing record", cursor.getString(detailsIdx))
        assertEquals("LOCAL_ONLY", cursor.getString(syncStatusIdx))
        assertEquals("", cursor.getString(obsIdIdx))
        assertEquals(0, cursor.getInt(hopCountIdx))

        cursor.close()
        db.close()
    }
}
