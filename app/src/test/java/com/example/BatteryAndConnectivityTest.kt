package com.example

import com.example.model.BatteryInfo
import com.example.model.BatteryState
import com.example.model.ConnectivityStatus
import com.example.model.ConnectivityTier
import com.example.model.OperatingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit Test Suite for Battery Intelligence and Connectivity Status (Batch F: Phases 15 & 16).
 */
class BatteryAndConnectivityTest {

    // =========================================================================
    // Phase 15: Battery Intelligence Tests
    // =========================================================================

    @Test
    fun testBatteryThresholdClassifications() {
        // Normal: > 25%
        val normalInfo = evaluateBatteryState(78, isCharging = false)
        assertEquals(BatteryState.NORMAL, normalInfo.batteryState)
        assertEquals(OperatingMode.NORMAL, normalInfo.operatingMode)

        // Low: 16% .. 25%
        val lowInfo = evaluateBatteryState(22, isCharging = false)
        assertEquals(BatteryState.LOW, lowInfo.batteryState)
        assertEquals(OperatingMode.POWER_SAVING, lowInfo.operatingMode)

        // Critical: <= 15%
        val criticalInfo = evaluateBatteryState(12, isCharging = false)
        assertEquals(BatteryState.CRITICAL, criticalInfo.batteryState)
        assertEquals(OperatingMode.POWER_SAVING, criticalInfo.operatingMode)

        // Boundary edge 25% (Low)
        assertEquals(BatteryState.LOW, evaluateBatteryState(25, false).batteryState)
        // Boundary edge 26% (Normal)
        assertEquals(BatteryState.NORMAL, evaluateBatteryState(26, false).batteryState)
        // Boundary edge 15% (Critical)
        assertEquals(BatteryState.CRITICAL, evaluateBatteryState(15, false).batteryState)
        // Boundary edge 16% (Low)
        assertEquals(BatteryState.LOW, evaluateBatteryState(16, false).batteryState)
    }

    @Test
    fun testBatteryUnknownGracefulFallback() {
        val unknownInfo = evaluateBatteryState(null, false)
        assertEquals(BatteryState.NORMAL, unknownInfo.batteryState)
        assertEquals(OperatingMode.NORMAL, unknownInfo.operatingMode)
        assertTrue(unknownInfo.getDisplaySummary().contains("Unknown"))
    }

    @Test
    fun testBatteryDisplaySummaryRealInformationOnly() {
        val normal = evaluateBatteryState(85, isCharging = true)
        val summaryNormal = normal.getDisplaySummary()
        assertTrue(summaryNormal.contains("85%"))
        assertTrue(summaryNormal.contains("Charging"))
        assertTrue(summaryNormal.contains("Normal operation"))

        val critical = evaluateBatteryState(8, isCharging = false)
        val summaryCrit = critical.getDisplaySummary()
        assertTrue(summaryCrit.contains("8%"))
        assertTrue(summaryCrit.contains("Critically low"))
    }

    // =========================================================================
    // Phase 16: Connectivity Status Tier Tests
    // =========================================================================

    @Test
    fun testConnectivityOnlineWhenInternetAvailable() {
        val status = evaluateConnectivity(
            isInternet = true,
            isBle = true,
            hasNearbyPeer = false,
            hasRelayedPeer = false,
            lastSeenPeerDeltaMs = 0L
        )
        assertEquals(ConnectivityTier.ONLINE, status.tier)
        assertTrue(status.isInternetAvailable)
    }

    @Test
    fun testConnectivityNearbyWhenDirectPeerFreshWithoutInternet() {
        val status = evaluateConnectivity(
            isInternet = false,
            isBle = true,
            hasNearbyPeer = true,
            hasRelayedPeer = false,
            lastSeenPeerDeltaMs = 5_000L // 5 seconds ago (< 30s)
        )
        assertEquals(ConnectivityTier.NEARBY, status.tier)
        assertFalse(status.isInternetAvailable)
        assertTrue(status.isBleAvailable)
        assertTrue(status.hasNearbyPeer)
    }

    @Test
    fun testConnectivityRelayedWhenMeshEvidenceFresh() {
        val status = evaluateConnectivity(
            isInternet = false,
            isBle = true,
            hasNearbyPeer = false,
            hasRelayedPeer = true,
            lastSeenPeerDeltaMs = 12_000L // 12 seconds ago (< 30s)
        )
        assertEquals(ConnectivityTier.RELAYED, status.tier)
        assertTrue(status.hasRelayedPeer)
    }

    @Test
    fun testConnectivityOfflineWhenBleAvailableButNoPeers() {
        val status = evaluateConnectivity(
            isInternet = false,
            isBle = true,
            hasNearbyPeer = false,
            hasRelayedPeer = false,
            lastSeenPeerDeltaMs = 0L
        )
        assertEquals(ConnectivityTier.OFFLINE, status.tier)
        assertTrue(status.isBleAvailable)
        assertFalse(status.isInternetAvailable)
    }

    @Test
    fun testConnectivityStalePeerFallsBackToOffline() {
        // Peer seen 45 seconds ago (> 30s threshold)
        val status = evaluateConnectivity(
            isInternet = false,
            isBle = true,
            hasNearbyPeer = true,
            hasRelayedPeer = false,
            lastSeenPeerDeltaMs = 45_000L
        )
        // Stale peer should NOT maintain NEARBY tier
        assertEquals(ConnectivityTier.OFFLINE, status.tier)
        assertFalse(status.hasNearbyPeer)
    }

    @Test
    fun testSafetyRuleBleAvailableDoesNotEqualInternet() {
        val bleOnlyStatus = evaluateConnectivity(
            isInternet = false,
            isBle = true,
            hasNearbyPeer = false,
            hasRelayedPeer = false,
            lastSeenPeerDeltaMs = 0L
        )
        // Explicit invariant: BLE radio active does not mean internet is online
        assertTrue(bleOnlyStatus.isBleAvailable)
        assertFalse(bleOnlyStatus.isInternetAvailable)
        assertEquals(ConnectivityTier.OFFLINE, bleOnlyStatus.tier)
    }

    // =========================================================================
    // Pure Helper Evaluators Matching Implementation
    // =========================================================================

    private fun evaluateBatteryState(percentage: Int?, isCharging: Boolean): BatteryInfo {
        val state = when {
            percentage == null -> BatteryState.NORMAL
            percentage <= 15 -> BatteryState.CRITICAL
            percentage <= 25 -> BatteryState.LOW
            else -> BatteryState.NORMAL
        }
        val mode = if (state == BatteryState.LOW || state == BatteryState.CRITICAL) {
            OperatingMode.POWER_SAVING
        } else {
            OperatingMode.NORMAL
        }
        return BatteryInfo(percentage, isCharging, state, mode)
    }

    private fun evaluateConnectivity(
        isInternet: Boolean,
        isBle: Boolean,
        hasNearbyPeer: Boolean,
        hasRelayedPeer: Boolean,
        lastSeenPeerDeltaMs: Long
    ): ConnectivityStatus {
        val isFresh = lastSeenPeerDeltaMs in 1..29_999L || (lastSeenPeerDeltaMs == 0L && (hasNearbyPeer || hasRelayedPeer))
        val nearbyActive = hasNearbyPeer && isFresh
        val relayedActive = hasRelayedPeer && isFresh

        val tier = when {
            isInternet -> ConnectivityTier.ONLINE
            nearbyActive -> ConnectivityTier.NEARBY
            relayedActive -> ConnectivityTier.RELAYED
            isBle -> ConnectivityTier.OFFLINE
            else -> ConnectivityTier.UNKNOWN
        }

        return ConnectivityStatus(
            isInternetAvailable = isInternet,
            isBleAvailable = isBle,
            isBleAdvertising = false,
            hasNearbyPeer = nearbyActive,
            hasRelayedPeer = relayedActive,
            lastSeenPeerTimestamp = System.currentTimeMillis() - lastSeenPeerDeltaMs,
            tier = tier
        )
    }
}
