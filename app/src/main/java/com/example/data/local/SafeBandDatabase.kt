package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SafetyEvent::class, TrustedContact::class],
    version = 1,
    exportSchema = false
)
abstract class SafeBandDatabase : RoomDatabase() {
    abstract fun safetyEventDao(): SafetyEventDao
    abstract fun trustedContactDao(): TrustedContactDao

    companion object {
        @Volatile
        private var INSTANCE: SafeBandDatabase? = null

        fun getDatabase(context: Context): SafeBandDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SafeBandDatabase::class.java,
                    "safeband_database"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
