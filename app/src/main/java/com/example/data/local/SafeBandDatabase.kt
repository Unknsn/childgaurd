package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SafetyEvent::class,
        TrustedContact::class,
        PickupAuthorization::class,
        HandoverRecord::class,
        EscalationConfigEntity::class,
        EscalationRecord::class,
        SchoolProfile::class,
        SchoolScheduleException::class,
        SchoolVerificationLog::class,
        SupervisedJourney::class,
        JourneyEvent::class,
        IncidentCoordinationRecord::class
    ],
    version = 3,
    exportSchema = false
)
abstract class SafeBandDatabase : RoomDatabase() {
    abstract fun safetyEventDao(): SafetyEventDao
    abstract fun trustedContactDao(): TrustedContactDao
    abstract fun pickupHandoverDao(): PickupHandoverDao
    abstract fun escalationDao(): EscalationDao
    abstract fun schoolDao(): SchoolDao
    abstract fun journeyDao(): JourneyDao
    abstract fun coordinationDao(): CoordinationDao

    companion object {
        @Volatile
        private var INSTANCE: SafeBandDatabase? = null

        /**
         * Non-destructive migration from Schema v1 to v2 (Batch C).
         * Adds offline store-and-forward columns without altering or deleting existing records.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE safety_events ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                db.execSQL("ALTER TABLE safety_events ADD COLUMN observationId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE safety_events ADD COLUMN hopCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Non-destructive migration from Schema v2 to v3 (SafeBand Guardian Next Major Release).
         * Adds tables for Safe Pickup & Handover, Emergency Escalation Chain,
         * School Safe Zone Verification, Journey Safety Mode, and Emergency Coordination.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Feature A: Pickup & Handover
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pickup_authorizations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        authId TEXT NOT NULL,
                        childName TEXT NOT NULL,
                        authorizedPersonName TEXT NOT NULL,
                        relationship TEXT NOT NULL,
                        phoneNumber TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        expiresAt INTEGER NOT NULL,
                        isOneTime INTEGER NOT NULL,
                        verificationToken TEXT NOT NULL,
                        status TEXT NOT NULL,
                        parentSignature TEXT NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS handover_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        handoverId TEXT NOT NULL,
                        authId TEXT NOT NULL,
                        childName TEXT NOT NULL,
                        pickupPersonName TEXT NOT NULL,
                        relationship TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        decisionBy TEXT NOT NULL,
                        locationSummary TEXT NOT NULL,
                        notes TEXT NOT NULL
                    )
                """.trimIndent())

                // Feature B: Emergency Escalation
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS escalation_configs (
                        id INTEGER PRIMARY KEY NOT NULL,
                        tier1DelaySeconds INTEGER NOT NULL,
                        tier2DelaySeconds INTEGER NOT NULL,
                        autoEscalateEnabled INTEGER NOT NULL,
                        tier1Role TEXT NOT NULL,
                        tier2Role TEXT NOT NULL,
                        tier3Role TEXT NOT NULL,
                        notifyPoliceClaimForbidden INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS escalation_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        incidentId TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        tier INTEGER NOT NULL,
                        recipientName TEXT NOT NULL,
                        recipientContact TEXT NOT NULL,
                        status TEXT NOT NULL,
                        acknowledgedBy TEXT,
                        acknowledgedAt INTEGER
                    )
                """.trimIndent())

                // Feature C: School Safe Zone
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS school_profiles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        schoolId TEXT NOT NULL,
                        schoolName TEXT NOT NULL,
                        address TEXT NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        radiusMeters REAL NOT NULL,
                        expectedArrivalStartMinutes INTEGER NOT NULL,
                        expectedArrivalEndMinutes INTEGER NOT NULL,
                        expectedDepartureStartMinutes INTEGER NOT NULL,
                        expectedDepartureEndMinutes INTEGER NOT NULL,
                        schoolDaysMask INTEGER NOT NULL,
                        isActive INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS school_schedule_exceptions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        schoolId TEXT NOT NULL,
                        dateEpochDay INTEGER NOT NULL,
                        reason TEXT NOT NULL,
                        isHoliday INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS school_verification_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        schoolId TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        eventType TEXT NOT NULL,
                        locationConfidence TEXT NOT NULL,
                        details TEXT NOT NULL
                    )
                """.trimIndent())

                // Feature D: Journey Safety Mode
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS supervised_journeys (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        journeyId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        startLabel TEXT NOT NULL,
                        startLat REAL NOT NULL,
                        startLon REAL NOT NULL,
                        destLabel TEXT NOT NULL,
                        destLat REAL NOT NULL,
                        destLon REAL NOT NULL,
                        destRadiusMeters REAL NOT NULL,
                        corridorRadiusMeters REAL NOT NULL,
                        expectedDurationMinutes INTEGER NOT NULL,
                        startTimestamp INTEGER NOT NULL,
                        expectedArrivalTimestamp INTEGER NOT NULL,
                        actualArrivalTimestamp INTEGER,
                        guardianContactsToNotify TEXT NOT NULL,
                        status TEXT NOT NULL,
                        waypointsJson TEXT NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS journey_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        journeyId TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        eventType TEXT NOT NULL,
                        latitude REAL,
                        longitude REAL,
                        details TEXT NOT NULL,
                        isSafetyAlert INTEGER NOT NULL
                    )
                """.trimIndent())

                // Feature E: Emergency Response Coordination
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS incident_coordination_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        incidentId TEXT NOT NULL,
                        responderName TEXT NOT NULL,
                        responderPhone TEXT NOT NULL,
                        responderRole TEXT NOT NULL,
                        assignedTimestamp INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        currentMilestone TEXT NOT NULL,
                        resolutionReason TEXT,
                        resolvedTimestamp INTEGER
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): SafeBandDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SafeBandDatabase::class.java,
                    "safeband_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
