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
    fun testSequentialMigration1To6PreservesPreexistingData() {
        val dbFile = context.getDatabasePath("test_migration.db")
        dbFile.delete()

        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test_migration.db")
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Schema v1 tables
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS safety_events (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            flagType TEXT NOT NULL,
                            riskLevel TEXT NOT NULL,
                            outcome TEXT NOT NULL,
                            durationSeconds INTEGER NOT NULL,
                            locationSummary TEXT NOT NULL,
                            deviceId TEXT NOT NULL DEFAULT 'SB-001'
                        )
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS trusted_contacts (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            phoneNumber TEXT NOT NULL,
                            relationship TEXT NOT NULL,
                            isEmergencyContact INTEGER NOT NULL
                        )
                    """.trimIndent())
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        var db = helper.writableDatabase

        // 1. Insert Schema v1 data
        db.execSQL("INSERT INTO safety_events (timestamp, flagType, riskLevel, outcome, durationSeconds, locationSummary, deviceId) VALUES (1000, 'SOS', 'CRITICAL', 'CONFIRMED', 15, 'School', 'SB-100')")
        db.execSQL("INSERT INTO trusted_contacts (name, phoneNumber, relationship, isEmergencyContact) VALUES ('Mom', '+15551234', 'Parent', 1)")

        // 2. Migrate 1 -> 2
        SafeBandDatabase.MIGRATION_1_2.migrate(db)
        val v2Cursor = db.query("SELECT syncStatus, observationId, hopCount FROM safety_events WHERE deviceId = 'SB-100'")
        assertTrue(v2Cursor.moveToFirst())
        assertEquals("LOCAL_ONLY", v2Cursor.getString(0))
        assertEquals(0, v2Cursor.getInt(2))
        v2Cursor.close()

        // 3. Migrate 2 -> 3
        SafeBandDatabase.MIGRATION_2_3.migrate(db)
        db.execSQL("INSERT INTO pickup_authorizations (authId, childName, authorizedPersonName, relationship, phoneNumber, notes, createdAt, expiresAt, isOneTime, verificationToken, status, parentSignature) VALUES ('AUTH-01', 'Leo', 'Uncle Bob', 'Family', '+15559999', 'None', 1000, 2000, 1, 'TOK123', 'PENDING', 'SIG')")
        db.execSQL("INSERT INTO school_profiles (schoolId, schoolName, address, latitude, longitude, radiusMeters, expectedArrivalStartMinutes, expectedArrivalEndMinutes, expectedDepartureStartMinutes, expectedDepartureEndMinutes, schoolDaysMask, isActive) VALUES ('SCH-01', 'Greenwood High', '123 Main St', 37.7749, -122.4194, 250.0, 480, 510, 900, 930, 31, 1)")

        // 4. Migrate 3 -> 4
        SafeBandDatabase.MIGRATION_3_4.migrate(db)
        db.execSQL("INSERT INTO paired_children (deviceId, childName, age, bloodType, primaryGuardianPhone, secondaryGuardianPhone, medicalConditions, allergies, emergencyNotes) VALUES ('SB-100', 'Leo', '8', 'O+', '+15551234', '+15555678', 'Asthma', 'Peanuts', 'None')")

        // 5. Migrate 4 -> 5
        SafeBandDatabase.MIGRATION_4_5.migrate(db)
        db.execSQL("INSERT INTO parent_accounts (userId, googleSub, email, displayName, createdAt, lastLoginAt, authStatus) VALUES ('USER-01', 'SUB-01', 'mom@gmail.com', 'Anita', 1000, 1000, 'AUTHENTICATED')")
        db.execSQL("INSERT INTO provisioning_challenges (challengeId, childDeviceId, oneTimeCode, expiresAt, createdAt, status, parentUserId) VALUES ('CH-01', 'SB-100', '123456', 9999999, 1000, 'PENDING', 'USER-01')")
        db.execSQL("INSERT INTO family_guardians (guardianId, parentUserId, name, phoneNumber, relationship, role, status, createdAt) VALUES ('G-01', 'USER-01', 'Grandma', '+15558888', 'Family', 'TRUSTED_GUARDIAN', 'ACTIVE', 1000)")

        // 6. Migrate 5 -> 6 (v3.4.0: Parent Endpoint, Tamper Status & Relay Forwarding)
        SafeBandDatabase.MIGRATION_5_6.migrate(db)
        db.execSQL("INSERT INTO relay_forwarding_records (incidentId, deviceId, eventType, detectionTimestamp, relayLatitude, relayLongitude, relayAccuracy, forwardStatus, targetEndpoint) VALUES ('INC-001', 'SB-100', 'SOS', 2000, 37.7749, -122.4194, 15.0, 'QUEUED', 'http://192.168.1.100:8080/api/relay/alert')")

        // 7. Verify preexisting data from v1 through v6 is preserved
        val eventCursor = db.query("SELECT * FROM safety_events WHERE deviceId = 'SB-100'")
        assertTrue("Preexisting v1 safety event must be preserved", eventCursor.moveToFirst())
        assertEquals("SOS", eventCursor.getString(eventCursor.getColumnIndexOrThrow("flagType")))
        eventCursor.close()

        val contactCursor = db.query("SELECT * FROM trusted_contacts WHERE name = 'Mom'")
        assertTrue("Preexisting v1 trusted contact must be preserved", contactCursor.moveToFirst())
        assertEquals("+15551234", contactCursor.getString(contactCursor.getColumnIndexOrThrow("phoneNumber")))
        contactCursor.close()

        val childCursor = db.query("SELECT * FROM paired_children WHERE deviceId = 'SB-100'")
        assertTrue("Preexisting v4 paired child must be preserved", childCursor.moveToFirst())
        assertEquals("Leo", childCursor.getString(childCursor.getColumnIndexOrThrow("childName")))
        assertEquals("Asthma", childCursor.getString(childCursor.getColumnIndexOrThrow("medicalConditions")))
        assertEquals("192.168.1.100", childCursor.getString(childCursor.getColumnIndexOrThrow("parentNodeIp")))
        assertEquals(8080, childCursor.getInt(childCursor.getColumnIndexOrThrow("parentNodePort")))
        assertEquals("PROTECTED", childCursor.getString(childCursor.getColumnIndexOrThrow("tamperStatus")))
        assertEquals(0, childCursor.getInt(childCursor.getColumnIndexOrThrow("isProvisioned")))
        childCursor.close()

        val parentCursor = db.query("SELECT * FROM parent_accounts WHERE userId = 'USER-01'")
        assertTrue("v5 parent account must be created and queryable", parentCursor.moveToFirst())
        assertEquals("mom@gmail.com", parentCursor.getString(parentCursor.getColumnIndexOrThrow("email")))
        parentCursor.close()

        val challengeCursor = db.query("SELECT * FROM provisioning_challenges WHERE challengeId = 'CH-01'")
        assertTrue("v5 provisioning challenge must be created", challengeCursor.moveToFirst())
        assertEquals("123456", challengeCursor.getString(challengeCursor.getColumnIndexOrThrow("oneTimeCode")))
        challengeCursor.close()

        val relayCursor = db.query("SELECT * FROM relay_forwarding_records WHERE incidentId = 'INC-001'")
        assertTrue("v6 relay forwarding record must be created and queryable", relayCursor.moveToFirst())
        assertEquals("QUEUED", relayCursor.getString(relayCursor.getColumnIndexOrThrow("forwardStatus")))
        assertEquals("SOS", relayCursor.getString(relayCursor.getColumnIndexOrThrow("eventType")))
        relayCursor.close()

        db.close()
        dbFile.delete()
    }

    @Test
    fun testRoomDatabaseSchemaV6InstantiatesAllDaos() {
        val roomDb = Room.inMemoryDatabaseBuilder(context, SafeBandDatabase::class.java)
            .allowMainThreadQueries()
            .addMigrations(
                SafeBandDatabase.MIGRATION_1_2,
                SafeBandDatabase.MIGRATION_2_3,
                SafeBandDatabase.MIGRATION_3_4,
                SafeBandDatabase.MIGRATION_4_5,
                SafeBandDatabase.MIGRATION_5_6
            )
            .build()

        assertNotNull(roomDb.safetyEventDao())
        assertNotNull(roomDb.trustedContactDao())
        assertNotNull(roomDb.pickupHandoverDao())
        assertNotNull(roomDb.escalationDao())
        assertNotNull(roomDb.schoolDao())
        assertNotNull(roomDb.journeyDao())
        assertNotNull(roomDb.coordinationDao())
        assertNotNull(roomDb.pairedChildDao())
        assertNotNull(roomDb.playbookDao())
        assertNotNull(roomDb.parentAccountDao())
        assertNotNull(roomDb.provisioningDao())
        assertNotNull(roomDb.familyGuardianDao())
        assertNotNull(roomDb.relayForwardingDao())

        val readableDb = roomDb.openHelper.readableDatabase
        assertTrue(readableDb.isOpen)

        val cursor = readableDb.query("SELECT name FROM sqlite_master WHERE type='table'")
        val tableNames = mutableListOf<String>()
        while (cursor.moveToNext()) {
            tableNames.add(cursor.getString(0))
        }
        cursor.close()

        assertTrue("parent_accounts table must exist in Room schema v6", tableNames.contains("parent_accounts"))
        assertTrue("provisioning_challenges table must exist in Room schema v6", tableNames.contains("provisioning_challenges"))
        assertTrue("family_guardians table must exist in Room schema v6", tableNames.contains("family_guardians"))
        assertTrue("relay_forwarding_records table must exist in Room schema v6", tableNames.contains("relay_forwarding_records"))
        assertTrue("safety_events table must exist", tableNames.contains("safety_events"))
        assertTrue("paired_children table must exist", tableNames.contains("paired_children"))

        roomDb.close()
    }
}
