package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SafetyEvent::class, TrustedContact::class],
    version = 2,
    exportSchema = false
)
abstract class SafeBandDatabase : RoomDatabase() {
    abstract fun safetyEventDao(): SafetyEventDao
    abstract fun trustedContactDao(): TrustedContactDao

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

        fun getDatabase(context: Context): SafeBandDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SafeBandDatabase::class.java,
                    "safeband_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
