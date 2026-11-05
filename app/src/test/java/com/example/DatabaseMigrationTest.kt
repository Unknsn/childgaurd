package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SafeBandDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatabaseMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun testDatabaseSchemaInstantiationAndTables() {
        val db = Room.inMemoryDatabaseBuilder(context, SafeBandDatabase::class.java)
            .allowMainThreadQueries()
            .addMigrations(SafeBandDatabase.MIGRATION_1_2, SafeBandDatabase.MIGRATION_2_3)
            .build()

        assertNotNull(db.safetyEventDao())
        assertNotNull(db.trustedContactDao())
        assertNotNull(db.pickupHandoverDao())
        assertNotNull(db.escalationDao())
        assertNotNull(db.schoolDao())
        assertNotNull(db.journeyDao())
        assertNotNull(db.coordinationDao())

        // Verify open helper executes migrations without crashing
        val openHelper = db.openHelper
        val readableDb = openHelper.readableDatabase
        assertTrue(readableDb.isOpen)

        // Query sqlite_master to verify newly created tables exist
        val cursor = readableDb.query(
            "SELECT name FROM sqlite_master WHERE type='table'"
        )
        val tableNames = mutableListOf<String>()
        while (cursor.moveToNext()) {
            tableNames.add(cursor.getString(0))
        }
        cursor.close()

        assertTrue("safety_events table must exist", tableNames.contains("safety_events"))
        assertTrue("trusted_contacts table must exist", tableNames.contains("trusted_contacts"))
        assertTrue("pickup_authorizations table must exist", tableNames.contains("pickup_authorizations"))
        assertTrue("handover_records table must exist", tableNames.contains("handover_records"))
        assertTrue("escalation_configs table must exist", tableNames.contains("escalation_configs"))
        assertTrue("escalation_records table must exist", tableNames.contains("escalation_records"))
        assertTrue("school_profiles table must exist", tableNames.contains("school_profiles"))
        assertTrue("school_schedule_exceptions table must exist", tableNames.contains("school_schedule_exceptions"))
        assertTrue("school_verification_logs table must exist", tableNames.contains("school_verification_logs"))
        assertTrue("supervised_journeys table must exist", tableNames.contains("supervised_journeys"))
        assertTrue("journey_events table must exist", tableNames.contains("journey_events"))
        assertTrue("incident_coordination_records table must exist", tableNames.contains("incident_coordination_records"))

        db.close()
    }
}

