package com.example

import com.example.engine.EvaluationScenarioRunner
import com.example.model.AlertFlag
import com.example.model.BleBeaconPayload
import com.example.model.GeofenceState
import com.example.model.RiskLevel
import com.example.model.RouteState
import com.example.model.ScenarioId
import com.example.model.SyncStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit Test Suite for Developer / Evaluation Scenario System (Batch G: Phase 17).
 * Verifies all 7 deterministic scenarios, state transitions, simulation isolation, and clean reset.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EvaluationScenarioTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun testAllSevenScenariosDefinedWithDeterministicMetadata() {
        val scenarioIds = ScenarioId.values()
        assertEquals(7, scenarioIds.size)

        val runner = createScenarioRunner({ _, _, _, _ -> }, { _, _, _ -> }, {}, {})
        val scenarios = runner.scenarios

        assertEquals(7, scenarios.size)

        // Scenario 1: Normal Day
        val normal = scenarios[ScenarioId.NORMAL_DAY]
        assertNotNull(normal)
        assertEquals(RiskLevel.NORMAL, normal?.expectedRisk)

        // Scenario 2: Safe Zone Exit
        val exit = scenarios[ScenarioId.SAFE_ZONE_EXIT]
        assertNotNull(exit)
        assertEquals(RiskLevel.HIGH, exit?.expectedRisk)

        // Scenario 3: Motion Anomaly
        val motion = scenarios[ScenarioId.MOTION_ANOMALY]
        assertNotNull(motion)
        assertEquals(RiskLevel.HIGH, motion?.expectedRisk)

        // Scenario 4: Tamper + Movement
        val tamper = scenarios[ScenarioId.TAMPER_MOVEMENT]
        assertNotNull(tamper)
        assertEquals(RiskLevel.HIGH, tamper?.expectedRisk)

        // Scenario 5: Offline Emergency
        val offline = scenarios[ScenarioId.OFFLINE_EMERGENCY]
        assertNotNull(offline)
        assertEquals(RiskLevel.HIGH, offline?.expectedRisk)

        // Scenario 6: Multi-Relay Emergency
        val multiRelay = scenarios[ScenarioId.MULTI_RELAY_EMERGENCY]
        assertNotNull(multiRelay)
        assertEquals(RiskLevel.HIGH, multiRelay?.expectedRisk)

        // Scenario 7: Manual SOS
        val manualSos = scenarios[ScenarioId.MANUAL_SOS]
        assertNotNull(manualSos)
        assertEquals(RiskLevel.CRITICAL, manualSos?.expectedRisk)
    }

    @Test
    fun testNormalDayScenarioExecution() = runTest(testDispatcher) {
        var observedRisk: RiskLevel? = null
        var observedGeofence: GeofenceState? = null

        val runner = createScenarioRunner(
            onSimulateLocation = { _, _, gState, _ -> observedGeofence = gState },
            onSimulateRisk = { risk, _, _ -> observedRisk = risk },
            onSimulateBeacon = {},
            onResetState = {}
        )

        runner.runScenario(ScenarioId.NORMAL_DAY, this)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(RiskLevel.NORMAL, observedRisk)
        assertEquals(GeofenceState.SAFE, observedGeofence)
        assertTrue(runner.scenarioStatus.value.isCompleted)
        assertFalse(runner.scenarioStatus.value.isRunning)
    }

    @Test
    fun testSafeZoneExitScenarioEscalation() = runTest(testDispatcher) {
        val geofenceStates = mutableListOf<GeofenceState>()
        val riskLevels = mutableListOf<RiskLevel>()

        val runner = createScenarioRunner(
            onSimulateLocation = { _, _, gState, _ -> geofenceStates.add(gState) },
            onSimulateRisk = { risk, _, _ -> riskLevels.add(risk) },
            onSimulateBeacon = {},
            onResetState = {}
        )

        runner.runScenario(ScenarioId.SAFE_ZONE_EXIT, this)
        testDispatcher.scheduler.advanceUntilIdle()

        // Verifies step-by-step progression: APPROACHING -> EXIT_PENDING -> OUTSIDE
        assertTrue(geofenceStates.contains(GeofenceState.APPROACHING))
        assertTrue(geofenceStates.contains(GeofenceState.EXIT_PENDING))
        assertTrue(geofenceStates.contains(GeofenceState.OUTSIDE))

        // Reaches HIGH risk
        assertTrue(riskLevels.contains(RiskLevel.HIGH))
    }

    @Test
    fun testTamperAndMovementTriggersHighRisk() = runTest(testDispatcher) {
        var maxRisk: RiskLevel = RiskLevel.NORMAL
        val observedFlags = mutableSetOf<AlertFlag>()

        val runner = createScenarioRunner(
            onSimulateLocation = { _, _, _, _ -> },
            onSimulateRisk = { risk, flags, _ ->
                if (risk.ordinal > maxRisk.ordinal) maxRisk = risk
                observedFlags.addAll(flags)
            },
            onSimulateBeacon = {},
            onResetState = {}
        )

        runner.runScenario(ScenarioId.TAMPER_MOVEMENT, this)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(RiskLevel.HIGH, maxRisk)
        assertTrue(observedFlags.contains(AlertFlag.TAMPER))
        assertTrue(observedFlags.contains(AlertFlag.MOTION_ANOMALY))
    }

    @Test
    fun testMultiRelayScenarioPayloadsAreTaggedSimulation() = runTest(testDispatcher) {
        val simulatedBeacons = mutableListOf<BleBeaconPayload>()

        val runner = createScenarioRunner(
            onSimulateLocation = { _, _, _, _ -> },
            onSimulateRisk = { _, _, _ -> },
            onSimulateBeacon = { beacon -> simulatedBeacons.add(beacon) },
            onResetState = {}
        )

        runner.runScenario(ScenarioId.MULTI_RELAY_EMERGENCY, this)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(simulatedBeacons.isNotEmpty())
        // Safety invariant: all simulated beacons MUST be tagged isSimulation = true
        simulatedBeacons.forEach { beacon ->
            assertTrue("Beacon must be isolated as simulation", beacon.isSimulation)
        }
        // Verify multi-hop evidence
        val maxHops = simulatedBeacons.maxOf { it.hopCount }
        assertTrue("Multi-relay must have hopCount >= 2", maxHops >= 2)
    }

    @Test
    fun testScenarioResetReturnsToBaseline() {
        var resetCalled = false
        val runner = createScenarioRunner(
            onSimulateLocation = { _, _, _, _ -> },
            onSimulateRisk = { _, _, _ -> },
            onSimulateBeacon = {},
            onResetState = { resetCalled = true }
        )

        runner.resetScenario()

        assertTrue(resetCalled)
        assertFalse(runner.scenarioStatus.value.isRunning)
        assertTrue(runner.scenarioStatus.value.currentStepDescription.contains("clean baseline"))
    }

    private fun createScenarioRunner(
        onSimulateLocation: (Double, Double, GeofenceState, RouteState) -> Unit,
        onSimulateRisk: (RiskLevel, Set<AlertFlag>, Boolean) -> Unit,
        onSimulateBeacon: (BleBeaconPayload) -> Unit,
        onResetState: () -> Unit
    ): EvaluationScenarioRunner {
        return EvaluationScenarioRunner(
            onSimulateLocation = onSimulateLocation,
            onSimulateRisk = onSimulateRisk,
            onSimulateBeacon = onSimulateBeacon,
            onResetState = onResetState
        )
    }
}
