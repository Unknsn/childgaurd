package com.example.engine

import com.example.model.AlertFlag
import com.example.model.BleBeaconPayload
import com.example.model.GeofenceState
import com.example.model.IncidentStage
import com.example.model.RiskLevel
import com.example.model.RouteState
import com.example.model.ScenarioId
import com.example.model.ScenarioStatus
import com.example.model.SyncStatus
import com.example.model.VerifiedLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Developer & Evaluation Scenario System (Batch G: Phase 17).
 *
 * Implements 7 deterministic evaluation scenarios:
 * 1. NORMAL_DAY
 * 2. SAFE_ZONE_EXIT
 * 3. MOTION_ANOMALY
 * 4. TAMPER_MOVEMENT
 * 5. OFFLINE_EMERGENCY
 * 6. MULTI_RELAY_EMERGENCY
 * 7. MANUAL_SOS
 *
 * Safety Boundary:
 * All scenarios are tagged isSimulation = true.
 * Scenarios NEVER call emergency services, never send real SMS, never make real calls,
 * and never broadcast real emergency BLE advertisements over the air.
 */
data class ScenarioDefinition(
    val id: ScenarioId,
    val title: String,
    val description: String,
    val expectedRisk: RiskLevel,
    val expectedIncidentStage: IncidentStage
)

class EvaluationScenarioRunner(
    private val onSimulateLocation: (Double, Double, GeofenceState, RouteState) -> Unit,
    private val onSimulateRisk: (RiskLevel, Set<AlertFlag>, Boolean) -> Unit,
    private val onSimulateBeacon: (BleBeaconPayload) -> Unit,
    private val onResetState: () -> Unit
) {

    companion object {
        val SCENARIOS: Map<ScenarioId, ScenarioDefinition> = mapOf(
            ScenarioId.NORMAL_DAY to ScenarioDefinition(ScenarioId.NORMAL_DAY, "1. Normal Transit", "Child transit along safe corridor to school without anomalies", RiskLevel.NORMAL, IncidentStage.NONE),
            ScenarioId.SAFE_ZONE_EXIT to ScenarioDefinition(ScenarioId.SAFE_ZONE_EXIT, "2. Safe Zone Exit", "Child exits safe zone with grace period, countdown, and escalation", RiskLevel.HIGH, IncidentStage.ACTIVE),
            ScenarioId.MOTION_ANOMALY to ScenarioDefinition(ScenarioId.MOTION_ANOMALY, "3. Motion Anomaly", "Fall / sudden stillness triggers motion anomaly escalation", RiskLevel.HIGH, IncidentStage.ACTIVE),
            ScenarioId.TAMPER_MOVEMENT to ScenarioDefinition(ScenarioId.TAMPER_MOVEMENT, "4. Tamper + Movement", "Physical band tamper paired with motion triggers high emergency", RiskLevel.HIGH, IncidentStage.ACTIVE),
            ScenarioId.OFFLINE_EMERGENCY to ScenarioDefinition(ScenarioId.OFFLINE_EMERGENCY, "5. Offline Emergency", "Emergency saved locally and queued for peer sync", RiskLevel.HIGH, IncidentStage.ACTIVE),
            ScenarioId.MULTI_RELAY_EMERGENCY to ScenarioDefinition(ScenarioId.MULTI_RELAY_EMERGENCY, "6. Multi-Relay Emergency", "Incident routed across 2 mesh peer relays with duplicate suppression", RiskLevel.HIGH, IncidentStage.ACTIVE),
            ScenarioId.MANUAL_SOS to ScenarioDefinition(ScenarioId.MANUAL_SOS, "7. Manual SOS", "Child holds SOS for 2s triggering immediate CRITICAL escalation", RiskLevel.CRITICAL, IncidentStage.ACTIVE)
        )
    }

    val scenarios: Map<ScenarioId, ScenarioDefinition> = SCENARIOS

    private val _scenarioStatus = MutableStateFlow(ScenarioStatus())
    val scenarioStatus: StateFlow<ScenarioStatus> = _scenarioStatus.asStateFlow()

    private var executionJob: Job? = null

    fun runScenario(scenarioId: ScenarioId, scope: CoroutineScope) {
        stopScenario()

        _scenarioStatus.value = ScenarioStatus(
            activeScenarioId = scenarioId,
            isRunning = true,
            currentStepDescription = "Initializing ${scenarioId.displayName}...",
            progressFraction = 0.05f
        )

        executionJob = scope.launch {
            when (scenarioId) {
                ScenarioId.NORMAL_DAY -> executeNormalDay()
                ScenarioId.SAFE_ZONE_EXIT -> executeSafeZoneExit()
                ScenarioId.MOTION_ANOMALY -> executeMotionAnomaly()
                ScenarioId.TAMPER_MOVEMENT -> executeTamperMovement()
                ScenarioId.OFFLINE_EMERGENCY -> executeOfflineEmergency()
                ScenarioId.MULTI_RELAY_EMERGENCY -> executeMultiRelayEmergency()
                ScenarioId.MANUAL_SOS -> executeManualSos()
            }

            _scenarioStatus.value = _scenarioStatus.value.copy(
                isRunning = false,
                currentStepDescription = "Scenario ${scenarioId.displayName} completed successfully.",
                progressFraction = 1.0f,
                isCompleted = true
            )
        }
    }

    fun stopScenario() {
        executionJob?.cancel()
        executionJob = null
        _scenarioStatus.value = ScenarioStatus()
    }

    fun resetScenario() {
        stopScenario()
        onResetState()
        _scenarioStatus.value = ScenarioStatus(
            currentStepDescription = "All simulation parameters reset to clean baseline."
        )
    }

    // =========================================================================
    // Scenario 1: Normal Day
    // =========================================================================
    private suspend fun executeNormalDay() {
        updateStep("1/4: At home safe perimeter", 0.25f)
        onSimulateLocation(37.7749, -122.4194, GeofenceState.SAFE, RouteState.ON_ROUTE)
        onSimulateRisk(RiskLevel.NORMAL, emptySet(), false)
        delay(600L)

        updateStep("2/4: Transit along safe corridor passing Bus Stop", 0.50f)
        onSimulateLocation(37.7760, -122.4194, GeofenceState.SAFE, RouteState.ON_ROUTE)
        delay(600L)

        updateStep("3/4: Safe arrival at school safe zone", 0.75f)
        onSimulateLocation(37.7780, -122.4194, GeofenceState.SAFE, RouteState.ON_ROUTE)
        delay(600L)

        updateStep("4/4: All parameters normal, 0 safety flags active", 1.0f)
        onSimulateRisk(RiskLevel.NORMAL, emptySet(), false)
    }

    // =========================================================================
    // Scenario 2: Safe Zone Exit
    // =========================================================================
    private suspend fun executeSafeZoneExit() {
        updateStep("1/5: Inside safe zone perimeter", 0.20f)
        onSimulateLocation(37.7749, -122.4194, GeofenceState.SAFE, RouteState.ON_ROUTE)
        onSimulateRisk(RiskLevel.NORMAL, emptySet(), false)
        delay(600L)

        updateStep("2/5: Approaching outer perimeter boundary", 0.40f)
        onSimulateLocation(37.7758, -122.4194, GeofenceState.APPROACHING, RouteState.APPROACHING_EDGE)
        delay(600L)

        updateStep("3/5: Geometrical boundary exit - 15s grace confirmation started", 0.60f)
        onSimulateLocation(37.7768, -122.4194, GeofenceState.EXIT_PENDING, RouteState.ROUTE_DEVIATION)
        delay(600L)

        updateStep("4/5: Grace period elapsed - Safe Zone Exit confirmed", 0.80f)
        onSimulateLocation(37.7785, -122.4194, GeofenceState.OUTSIDE, RouteState.ROUTE_DEVIATION)
        onSimulateRisk(RiskLevel.MEDIUM, setOf(AlertFlag.GEOFENCE_EXIT), false)
        delay(600L)

        updateStep("5/5: Escalation countdown elapsed - Guardian emergency triggered", 1.0f)
        onSimulateRisk(RiskLevel.HIGH, setOf(AlertFlag.GEOFENCE_EXIT, AlertFlag.ROUTE_DEVIATION), false)
    }

    // =========================================================================
    // Scenario 3: Motion Anomaly
    // =========================================================================
    private suspend fun executeMotionAnomaly() {
        updateStep("1/3: Normal walking activity", 0.33f)
        onSimulateRisk(RiskLevel.NORMAL, emptySet(), false)
        delay(600L)

        updateStep("2/3: High shock impact detected (>32 m/s²)", 0.66f)
        onSimulateRisk(RiskLevel.LOW, setOf(AlertFlag.MOTION_ANOMALY), false)
        delay(600L)

        updateStep("3/3: Sustained stillness - Low risk logged in local audit trail", 1.0f)
        onSimulateRisk(RiskLevel.LOW, setOf(AlertFlag.MOTION_ANOMALY), false)
    }

    // =========================================================================
    // Scenario 4: Tamper + Movement
    // =========================================================================
    private suspend fun executeTamperMovement() {
        updateStep("1/3: Band securely fastened on wrist", 0.33f)
        onSimulateRisk(RiskLevel.NORMAL, emptySet(), false)
        delay(600L)

        updateStep("2/3: Band clasp severed / Tamper detected (40 pts)", 0.66f)
        onSimulateRisk(RiskLevel.MEDIUM, setOf(AlertFlag.TAMPER), false)
        delay(600L)

        updateStep("3/3: Tamper (40) + Motion anomaly (20) compound = 60 pts -> HIGH Emergency", 1.0f)
        onSimulateRisk(RiskLevel.HIGH, setOf(AlertFlag.TAMPER, AlertFlag.MOTION_ANOMALY), false)
    }

    // =========================================================================
    // Scenario 5: Offline Emergency
    // =========================================================================
    private suspend fun executeOfflineEmergency() {
        updateStep("1/3: Device in offline mesh mode (No internet / cellular)", 0.33f)
        onSimulateRisk(RiskLevel.NORMAL, emptySet(), false)
        delay(600L)

        updateStep("2/3: Geofence exit occurred offline", 0.66f)
        onSimulateLocation(37.7785, -122.4194, GeofenceState.OUTSIDE, RouteState.ROUTE_DEVIATION)
        onSimulateRisk(RiskLevel.MEDIUM, setOf(AlertFlag.GEOFENCE_EXIT), false)
        delay(600L)

        updateStep("3/3: SafetyEvent saved locally (LOCAL_ONLY), queued for peer sync (PENDING_SYNC)", 1.0f)
    }

    // =========================================================================
    // Scenario 6: Multi-Relay Emergency
    // =========================================================================
    private suspend fun executeMultiRelayEmergency() {
        val now = System.currentTimeMillis()
        updateStep("1/3: Child Wearer broadcasts emergency beacon (Hop 0)", 0.33f)
        val initialPayload = BleBeaconPayload(
            deviceId = "SB-8041",
            riskLevel = RiskLevel.HIGH,
            timestamp = now,
            latitude = 37.7790,
            longitude = -122.4180,
            ephemeralId = "EP-4411",
            isSimulation = true,
            hopCount = 0
        )
        onSimulateBeacon(initialPayload)
        delay(600L)

        updateStep("2/3: Peer Relay Phone A forwards beacon (Hop 1)", 0.66f)
        val hop1Payload = initialPayload.copy(
            ephemeralId = "EP-4411",
            hopCount = 1
        )
        onSimulateBeacon(hop1Payload)
        delay(600L)

        updateStep("3/3: Peer Relay Phone B forwards beacon (Hop 2) - Duplicate suppressed, 1 Incident retained", 1.0f)
        val hop2Payload = initialPayload.copy(
            ephemeralId = "EP-4411",
            hopCount = 2
        )
        onSimulateBeacon(hop2Payload)
    }

    // =========================================================================
    // Scenario 7: Manual SOS
    // =========================================================================
    private suspend fun executeManualSos() {
        updateStep("1/3: Child wearing band in safe state", 0.33f)
        onSimulateRisk(RiskLevel.NORMAL, emptySet(), false)
        delay(600L)

        updateStep("2/3: SOS pressed and held for 2 seconds", 0.66f)
        delay(600L)

        updateStep("3/3: Immediate CRITICAL escalation (100 pts) - 0s delay, countdown bypassed", 1.0f)
        onSimulateRisk(RiskLevel.CRITICAL, setOf(AlertFlag.MANUAL_SOS), true)
    }

    private fun updateStep(description: String, progress: Float) {
        _scenarioStatus.value = _scenarioStatus.value.copy(
            currentStepDescription = description,
            progressFraction = progress
        )
    }
}
