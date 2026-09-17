package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "safety_events")
data class SafetyEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val flagType: String, // e.g. "SOS", "MOTION", "GEOFENCE", "BLE_RECEIVED"
    val riskLevel: String, // "NORMAL", "LOW", "MEDIUM", "HIGH"
    val outcome: String, // "CONFIRMED_ESCALATED", "CANCELLED_BY_USER", "LOGGED_LOCAL", "ALERT_RECEIVED", "SIMULATED"
    val deviceId: String,
    val details: String = ""
)

@Entity(tableName = "trusted_contacts")
data class TrustedContact(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phoneNumber: String,
    val relationship: String = "Guardian"
)
