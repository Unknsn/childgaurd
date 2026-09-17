package com.example.engine

import com.example.model.AlertFlag
import com.example.model.RiskLevel

/**
 * Shared Risk Engine logic:
 * - Low: exactly one flag active -> local log entry only, no BLE broadcast.
 * - Medium: two flags active at once (e.g. geofence exit + motion anomaly) -> start BLE emergency advertising.
 * - High: SOS pressed, OR three flags active at once -> BLE advertising + Notify contact available.
 * - Escalations pass through the confirmation countdown before being finalized, except manual SOS which is immediate.
 *
 * EXPLICITLY OUT OF SCOPE (future hardware/cloud phase):
 * - Dedicated wearable hardware with a physical tamper switch
 * - Long-range relay via fixed hubs
 * - Cloud-based escalation to authorities
 * - Multi-hop alert propagation beyond direct BLE range
 */
object RiskEngine {

    fun calculateRisk(activeFlags: Set<AlertFlag>): RiskLevel {
        return when {
            activeFlags.contains(AlertFlag.MANUAL_SOS) || activeFlags.size >= 3 -> {
                RiskLevel.HIGH
            }
            activeFlags.size == 2 -> {
                RiskLevel.MEDIUM
            }
            activeFlags.size == 1 -> {
                RiskLevel.LOW
            }
            else -> {
                RiskLevel.NORMAL
            }
        }
    }

    fun requiresConfirmationCountdown(targetRisk: RiskLevel, triggeredBySos: Boolean): Boolean {
        if (triggeredBySos) return false // SOS is immediate
        return targetRisk == RiskLevel.MEDIUM || targetRisk == RiskLevel.HIGH
    }

    fun shouldBroadcastBle(riskLevel: RiskLevel): Boolean {
        return riskLevel == RiskLevel.MEDIUM || riskLevel == RiskLevel.HIGH
    }
}
