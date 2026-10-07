package com.example.engine

import com.example.model.AlertFlag
import com.example.model.RiskLevel

/**
 * Risk Engine V2 (Batch B: Phases 3, 4, 5).
 *
 * Deterministic weighted scoring system with temporal signal decay:
 * - MANUAL_SOS: 100 points (Immediate CRITICAL override - 0s delay, bypasses confirmation countdown)
 * - TAMPER: 40 points (Physical band tamper / cut attempt)
 * - GEOFENCE_EXIT: 30 points (Perimeter boundary breach)
 * - ROUTE_DEVIATION: 25 points (Corridor deviation outside safe transit route)
 * - BOUNDARY_VIOLATION: 25 points (Repeated boundary violations / oscillations)
 * - MOTION_ANOMALY: 20 points (Abrupt fall, sustained stillness, or violent movement)
 *
 * Temporal Decay:
 * - Age < 60s: Full weight (100%)
 * - 60s <= Age < 120s: Decayed weight (50%)
 * - Age >= 120s: Expired (0%)
 *
 * Thresholds:
 * - Score >= 80 -> CRITICAL (Emergency pathway active)
 * - Score >= 55 -> HIGH (Guardian & trusted escalation)
 * - Score >= 26 -> MEDIUM (Guardian notification / BLE broadcast)
 * - Score >= 1  -> LOW (Local awareness only, no broadcast)
 * - Score == 0  -> NORMAL (All parameters safe)
 */
object RiskEngine {

    // Default weights for safety signals
    const val WEIGHT_MANUAL_SOS = 100
    const val WEIGHT_TAMPER = 40
    const val WEIGHT_GEOFENCE_EXIT = 30
    const val WEIGHT_ROUTE_DEVIATION = 25
    const val WEIGHT_BOUNDARY_VIOLATION = 25
    const val WEIGHT_MOTION_ANOMALY = 20

    const val SCORE_THRESHOLD_CRITICAL = 80
    const val SCORE_THRESHOLD_HIGH = 55
    const val SCORE_THRESHOLD_MEDIUM = 26
    const val SCORE_THRESHOLD_LOW = 1

    const val DECAY_HALF_LIFE_MS = 60_000L // 60 seconds full weight
    const val DECAY_EXPIRY_MS = 120_000L   // 120 seconds completely expired

    const val CONFIRMATION_COUNTDOWN_SECONDS = 15

    fun getSignalWeight(flag: AlertFlag): Int {
        return when (flag) {
            AlertFlag.MANUAL_SOS -> WEIGHT_MANUAL_SOS
            AlertFlag.TAMPER -> WEIGHT_TAMPER
            AlertFlag.GEOFENCE_EXIT -> WEIGHT_GEOFENCE_EXIT
            AlertFlag.ROUTE_DEVIATION -> WEIGHT_ROUTE_DEVIATION
            AlertFlag.BOUNDARY_VIOLATION -> WEIGHT_BOUNDARY_VIOLATION
            AlertFlag.MOTION_ANOMALY -> WEIGHT_MOTION_ANOMALY
        }
    }

    /**
     * Backward-compatible risk calculation from a simple set of active flags.
     * All provided flags are assumed to be newly active (timestamp = now).
     */
    fun calculateRisk(activeFlags: Set<AlertFlag>): RiskLevel {
        if (activeFlags.isEmpty()) return RiskLevel.NORMAL
        val now = System.currentTimeMillis()
        val signalsMap = activeFlags.associateWith { now }
        return calculateRiskWithTimestamp(signalsMap, now)
    }

    /**
     * Risk Engine V2 calculation taking into account signal timestamps and temporal decay.
     */
    fun calculateRiskWithTimestamp(
        activeSignals: Map<AlertFlag, Long>,
        now: Long = System.currentTimeMillis()
    ): RiskLevel {
        if (activeSignals.isEmpty()) return RiskLevel.NORMAL

        // CRITICAL RULE: Manual SOS ALWAYS becomes CRITICAL immediately.
        // No confirmation countdown, no waiting for additional evidence, no location requirement.
        val sosTimestamp = activeSignals[AlertFlag.MANUAL_SOS]
        if (sosTimestamp != null) {
            val age = (now - sosTimestamp).coerceAtLeast(0L)
            if (age < DECAY_EXPIRY_MS) {
                return RiskLevel.CRITICAL
            }
        }

        val totalScore = calculateScore(activeSignals, now)

        return when {
            totalScore >= SCORE_THRESHOLD_CRITICAL -> RiskLevel.CRITICAL
            totalScore >= SCORE_THRESHOLD_HIGH -> RiskLevel.HIGH
            totalScore >= SCORE_THRESHOLD_MEDIUM -> RiskLevel.MEDIUM
            totalScore >= SCORE_THRESHOLD_LOW -> RiskLevel.LOW
            else -> RiskLevel.NORMAL
        }
    }

    /**
     * Computes cumulative score with temporal decay.
     */
    fun calculateScore(
        activeSignals: Map<AlertFlag, Long>,
        now: Long = System.currentTimeMillis()
    ): Int {
        var score = 0
        for ((flag, timestamp) in activeSignals) {
            val baseWeight = getSignalWeight(flag)
            val age = (now - timestamp).coerceAtLeast(0L)
            val decayedWeight = when {
                age < DECAY_HALF_LIFE_MS -> baseWeight
                age < DECAY_EXPIRY_MS -> baseWeight / 2
                else -> 0
            }
            score += decayedWeight
        }
        return score
    }

    /**
     * Determines if a risk transition requires the 15-second confirmation countdown.
     * Manual SOS ALWAYS bypasses the confirmation countdown (returns false).
     */
    fun requiresConfirmationCountdown(targetRisk: RiskLevel, triggeredBySos: Boolean): Boolean {
        if (triggeredBySos) return false // Immediate bypass for manual SOS
        return targetRisk == RiskLevel.MEDIUM || targetRisk == RiskLevel.HIGH || targetRisk == RiskLevel.CRITICAL
    }

    /**
     * Determines whether the given risk level warrants BLE beacon broadcasting.
     */
    fun shouldBroadcastBle(riskLevel: RiskLevel): Boolean {
        return riskLevel == RiskLevel.MEDIUM || riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL
    }
}
