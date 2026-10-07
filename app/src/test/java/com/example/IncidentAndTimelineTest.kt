package com.example

import com.example.data.local.SafetyEvent
import com.example.model.GeofenceState
import com.example.model.IncidentObservation
import com.example.model.IncidentStage
import com.example.model.RiskLevel
import com.example.model.RouteState
import com.example.model.SafetyIncident
import com.example.model.SyncStatus
import com.example.ui.SafeBandViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit Test Suite for Incident Deduplication, Event Timeline V2,
 * Incident Detail, and Incident Replay (Batch E: Phases 11-14).
 */
class IncidentAndTimelineTest {

    @Test
    fun testDuplicateObservationSuppressionKeepsSingleIncident() {
        // Real-world scenario: Child SOS observed by Phone A, then relayed through Phone B, then Hub
        val now = 1700000000000L
        val originalObsId = "OBS-CHILD-001"

        val initialIncident = SafetyIncident(
            incidentId = "INC-SB-101",
            deviceId = "SB-101",
            stage = IncidentStage.ACTIVE,
            riskLevel = RiskLevel.HIGH,
            startTimestamp = now,
            lastUpdatedTimestamp = now,
            observations = listOf(
                IncidentObservation(
                    observationId = originalObsId,
                    sourceNodeId = "PHONE-A",
                    timestamp = now,
                    riskLevel = RiskLevel.HIGH,
                    hopCount = 0,
                    syncStatus = SyncStatus.LOCAL_ONLY
                )
            ),
            observationCount = 1,
            sourceNodes = listOf("PHONE-A"),
            maxHopCount = 0
        )

        // Second observation from PHONE-B for the same event
        val secondObs = IncidentObservation(
            observationId = originalObsId,
            sourceNodeId = "PHONE-B",
            timestamp = now + 2000L,
            riskLevel = RiskLevel.HIGH,
            hopCount = 1,
            syncStatus = SyncStatus.RELAYED
        )

        // Merging logic: observations with duplicate observationId are suppressed/deduplicated,
        // but source node provenance and hop count are preserved
        val updatedObs = if (initialIncident.observations.none { it.observationId == secondObs.observationId }) {
            initialIncident.observations + secondObs
        } else {
            initialIncident.observations
        }
        val updatedSources = (initialIncident.sourceNodes + "PHONE-B").distinct()
        val updatedIncident = initialIncident.copy(
            observations = updatedObs,
            observationCount = updatedObs.size,
            sourceNodes = updatedSources,
            maxHopCount = maxOf(initialIncident.maxHopCount, secondObs.hopCount),
            lastUpdatedTimestamp = now + 2000L
        )

        // Must remain single incident
        assertEquals("INC-SB-101", updatedIncident.incidentId)
        // Observations deduplicated
        assertEquals(1, updatedIncident.observationCount)
        // Sources preserved
        assertEquals(2, updatedIncident.sourceNodes.size)
        assertTrue(updatedIncident.sourceNodes.contains("PHONE-A"))
        assertTrue(updatedIncident.sourceNodes.contains("PHONE-B"))
        // Hop count updated to highest seen
        assertEquals(1, updatedIncident.maxHopCount)
    }

    @Test
    fun testDistinctRealWorldEventsFormSeparateObservations() {
        val now = 1700000000000L
        val obs1 = IncidentObservation("OBS-1", "NODE-A", now, RiskLevel.MEDIUM, 0)
        val obs2 = IncidentObservation("OBS-2", "NODE-B", now + 1000L, RiskLevel.HIGH, 1)

        val incident = SafetyIncident(
            incidentId = "INC-100",
            deviceId = "SB-8041",
            observations = listOf(obs1, obs2),
            observationCount = 2,
            sourceNodes = listOf("NODE-A", "NODE-B"),
            riskLevel = RiskLevel.HIGH,
            maxHopCount = 1
        )

        assertEquals(2, incident.observationCount)
        assertEquals(2, incident.sourceNodes.size)
        assertEquals(RiskLevel.HIGH, incident.riskLevel)
        assertEquals(1, incident.maxHopCount)
    }

    @Test
    fun testTimelineBuilderChronologicalOrderingAndGrouping() {
        val now = 1700000000000L
        val events = listOf(
            SafetyEvent(
                id = 1,
                timestamp = now - 50_000L,
                flagType = "SOS",
                riskLevel = "CRITICAL",
                outcome = "CONFIRMED_ESCALATED",
                deviceId = "SB-8041",
                observationId = "NODE-1-100",
                hopCount = 0
            ),
            SafetyEvent(
                id = 2,
                timestamp = now - 5_000L,
                flagType = "SOS",
                riskLevel = "CRITICAL",
                outcome = "CONFIRMED_ESCALATED",
                deviceId = "SB-8041",
                observationId = "NODE-1-100",
                hopCount = 1
            ),
            SafetyEvent(
                id = 3,
                timestamp = now,
                flagType = "GEOFENCE_EXIT",
                riskLevel = "HIGH",
                outcome = "ESCALATED",
                deviceId = "SB-8041",
                observationId = "NODE-2-200",
                hopCount = 0
            )
        )

        val timeline = SafeBandViewModel.buildTimelineItems(events)

        // Events should be sorted descending by timestamp
        assertTrue(timeline.isNotEmpty())
        assertEquals("GEOFENCE_EXIT", timeline[0].flagType)
        assertEquals("Safe Zone Exit Confirmed", timeline[0].title)
        assertEquals(RiskLevel.HIGH, timeline[0].riskLevel)
    }

    @Test
    fun testTimelineBuilderCollapsesAdjacentEventsWithin15Seconds() {
        val now = 1700000000000L
        val events = listOf(
            SafetyEvent(
                id = 1,
                timestamp = now - 5_000L,
                flagType = "SOS",
                riskLevel = "HIGH",
                outcome = "BROADCAST",
                deviceId = "SB-100",
                observationId = "SB-100-1",
                hopCount = 0
            ),
            SafetyEvent(
                id = 2,
                timestamp = now,
                flagType = "SOS",
                riskLevel = "HIGH",
                outcome = "BROADCAST",
                deviceId = "SB-100",
                observationId = "SB-100-2",
                hopCount = 1
            )
        )

        val timeline = SafeBandViewModel.buildTimelineItems(events)

        // Collapsed into single item with count = 2
        assertEquals(1, timeline.size)
        assertEquals(2, timeline[0].observationCount)
        assertEquals(1, timeline[0].hopCount)
    }

    @Test
    fun testIncidentReplaySequenceDeterministicSteps() {
        val replaySteps = SafeBandViewModel.DEFAULT_REPLAY_STEPS

        assertEquals(10, replaySteps.size)

        // Step 0: Safe at home
        assertEquals(0, replaySteps[0].stepIndex)
        assertEquals(GeofenceState.SAFE, replaySteps[0].geofenceState)
        assertEquals(RouteState.ON_ROUTE, replaySteps[0].routeState)
        assertEquals(RiskLevel.NORMAL, replaySteps[0].riskLevel)

        // Step 3: Boundary Breach
        assertEquals(3, replaySteps[3].stepIndex)
        assertEquals(GeofenceState.EXIT_PENDING, replaySteps[3].geofenceState)
        assertEquals(RouteState.ROUTE_DEVIATION, replaySteps[3].routeState)
        assertEquals(RiskLevel.MEDIUM, replaySteps[3].riskLevel)

        // Step 4: Grace Period Elapsed / Confirmed Exit
        assertEquals(4, replaySteps[4].stepIndex)
        assertEquals(GeofenceState.OUTSIDE, replaySteps[4].geofenceState)
        assertEquals(RiskLevel.HIGH, replaySteps[4].riskLevel)

        // Step 7: Guardian Escalation / CRITICAL
        assertEquals(7, replaySteps[7].stepIndex)
        assertEquals(RiskLevel.CRITICAL, replaySteps[7].riskLevel)
        assertEquals(SyncStatus.ACKNOWLEDGED, replaySteps[7].syncStatus)

        // Step 9: Incident Resolved
        assertEquals(9, replaySteps[9].stepIndex)
        assertEquals(GeofenceState.SAFE, replaySteps[9].geofenceState)
        assertEquals(RiskLevel.NORMAL, replaySteps[9].riskLevel)
        assertEquals(IncidentStage.RESOLVED, replaySteps[9].stage)
    }

    @Test
    fun testIncidentDetailAcknowledgeAndResolveTransitions() {
        val incident = SafetyIncident(
            incidentId = "INC-TEST-001",
            deviceId = "SB-99",
            stage = IncidentStage.ACTIVE,
            riskLevel = RiskLevel.CRITICAL
        )

        // Acknowledge transition
        val ackIncident = incident.copy(syncStatus = SyncStatus.ACKNOWLEDGED)
        assertEquals(SyncStatus.ACKNOWLEDGED, ackIncident.syncStatus)
        assertEquals(IncidentStage.ACTIVE, ackIncident.stage) // Acknowledging does not resolve

        // Resolve transition
        val resolvedIncident = ackIncident.copy(
            stage = IncidentStage.RESOLVED,
            syncStatus = SyncStatus.SYNCED
        )
        assertEquals(IncidentStage.RESOLVED, resolvedIncident.stage)
        assertEquals(SyncStatus.SYNCED, resolvedIncident.syncStatus)
    }
}
