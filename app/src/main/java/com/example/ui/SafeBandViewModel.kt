package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.SafeBandDatabase
import com.example.data.local.SafetyEvent
import com.example.data.local.TrustedContact
import com.example.data.preferences.SafeBandPreferences
import com.example.data.repository.SafetyRepository
import com.example.engine.RiskEngine
import com.example.model.AlertFlag
import com.example.model.AppMode
import com.example.model.BleBeaconPayload
import com.example.model.ChildBioProfile
import com.example.model.GeoAddress
import com.example.model.GeofenceState
import com.example.model.IncidentStage
import com.example.model.RiskLevel
import com.example.model.RouteState
import com.example.model.SafeZone
import com.example.model.SafetyIncident
import com.example.model.SafetyNode
import com.example.model.SyncStatus
import com.example.model.TrustedRoute
import com.example.model.BatteryInfo
import com.example.model.BatteryState
import com.example.model.ConnectivityStatus
import com.example.model.ConnectivityTier
import com.example.model.IncidentObservation
import com.example.model.IncidentReplayState
import com.example.model.LocationConfidence
import com.example.model.OperatingMode
import com.example.model.ReplayStep
import com.example.model.ScenarioId
import com.example.model.ScenarioStatus
import com.example.model.TimelineItem
import com.example.model.VerifiedLocation
import com.example.service.AlertNotifier
import com.example.service.BatterySafetyManager
import com.example.service.BleSafetyManager
import com.example.service.ConnectivitySafetyManager
import com.example.service.LocationAddressResolver
import com.example.service.LocationSafetyHelper
import com.example.service.MotionAnomalyDetector
import com.example.engine.EvaluationScenarioRunner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SafeBandViewModel(application: Application) : AndroidViewModel(application) {

    private val database = SafeBandDatabase.getDatabase(application)
    private val preferences = SafeBandPreferences(application)
    private val repository = SafetyRepository(
        database.safetyEventDao(),
        database.trustedContactDao(),
        preferences
    )

    val bleManager = BleSafetyManager(application)
    val motionDetector = MotionAnomalyDetector(application, viewModelScope)
    val locationHelper = LocationSafetyHelper(application)
    val alertNotifier = AlertNotifier(application, viewModelScope)
    val batteryManager = BatterySafetyManager(application)
    val connectivityManager = ConnectivitySafetyManager(application)

    val batteryInfo: StateFlow<BatteryInfo> = batteryManager.batteryInfo
    val connectivityStatus: StateFlow<ConnectivityStatus> = connectivityManager.connectivityStatus

    // Persistent state
    val appMode: StateFlow<AppMode> = repository.appMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppMode.UNSELECTED
    )

    val deviceId: StateFlow<String> = repository.deviceId.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "SB-8041"
    )

    val safeZone: StateFlow<SafeZone> = repository.safeZone.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SafeZone()
    )

    val allEvents: StateFlow<List<SafetyEvent>> = repository.allEvents.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allContacts: StateFlow<List<TrustedContact>> = repository.allContacts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val childBioProfile: StateFlow<ChildBioProfile> = repository.childBioProfile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChildBioProfile()
    )

    val trustedRoute: StateFlow<TrustedRoute> = repository.trustedRoute.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TrustedRoute()
    )

    val geofenceState: StateFlow<GeofenceState> = locationHelper.geofenceState
    val distanceToBoundary: StateFlow<Float?> = locationHelper.distanceToBoundaryMeters
    val distanceFromCenter: StateFlow<Float?> = locationHelper.distanceFromCenterMeters
    val verifiedLocation: StateFlow<VerifiedLocation?> = locationHelper.verifiedLocation
    val routeState: StateFlow<RouteState> = locationHelper.routeState
    val distanceToRouteCorridor: StateFlow<Float?> = locationHelper.distanceToRouteCorridorMeters

    fun updateChildBioProfile(profile: ChildBioProfile) {
        viewModelScope.launch {
            repository.saveChildBioProfile(profile)
        }
    }

    fun saveTrustedRoute(route: TrustedRoute) {
        viewModelScope.launch {
            repository.saveTrustedRoute(route)
        }
    }

    // Child Mode State
    private val _activeFlags = MutableStateFlow<Set<AlertFlag>>(emptySet())
    val activeFlags: StateFlow<Set<AlertFlag>> = _activeFlags.asStateFlow()

    private val _currentRiskLevel = MutableStateFlow(RiskLevel.NORMAL)
    val currentRiskLevel: StateFlow<RiskLevel> = _currentRiskLevel.asStateFlow()

    private val _isCountingDown = MutableStateFlow(false)
    val isCountingDown: StateFlow<Boolean> = _isCountingDown.asStateFlow()

    private val _countdownRemainingSeconds = MutableStateFlow(10)
    val countdownRemainingSeconds: StateFlow<Int> = _countdownRemainingSeconds.asStateFlow()

    private var countdownJob: Job? = null

    // Parent Mode State
    private val _incomingAlert = MutableStateFlow<BleBeaconPayload?>(null)
    val incomingAlert: StateFlow<BleBeaconPayload?> = _incomingAlert.asStateFlow()

    // Incoming Child Details & Contacts from alerting child device
    private val _incomingChildProfile = MutableStateFlow<ChildBioProfile?>(null)
    val incomingChildProfile: StateFlow<ChildBioProfile?> = _incomingChildProfile.asStateFlow()

    private val _incomingChildContacts = MutableStateFlow<List<TrustedContact>>(emptyList())
    val incomingChildContacts: StateFlow<List<TrustedContact>> = _incomingChildContacts.asStateFlow()

    // Resolved Physical Addresses (Street, Area, City, State, PIN code)
    private val _childAddress = MutableStateFlow<GeoAddress?>(null)
    val childAddress: StateFlow<GeoAddress?> = _childAddress.asStateFlow()

    private val _incomingAlertAddress = MutableStateFlow<GeoAddress?>(null)
    val incomingAlertAddress: StateFlow<GeoAddress?> = _incomingAlertAddress.asStateFlow()

    private val _safeZoneAddress = MutableStateFlow<GeoAddress?>(null)
    val safeZoneAddress: StateFlow<GeoAddress?> = _safeZoneAddress.asStateFlow()

    private val _isParentAlertActive = MutableStateFlow(false)
    val isParentAlertActive: StateFlow<Boolean> = _isParentAlertActive.asStateFlow()

    private val _isParentAlertSilenced = MutableStateFlow(false)
    val isParentAlertSilenced: StateFlow<Boolean> = _isParentAlertSilenced.asStateFlow()

    private val _alertDurationSeconds = MutableStateFlow(0L)
    val alertDurationSeconds: StateFlow<Long> = _alertDurationSeconds.asStateFlow()
    private var alertTimerJob: Job? = null

    private var activeAlertRiskLevel: RiskLevel? = null
    private var beaconWatchdogJob: Job? = null
    private var lastLoggedIncidentTime = 0L
    private var cancellationAdvertisingJob: Job? = null

    // Central Authoritative Incident State Model
    private val _childIncident = MutableStateFlow(SafetyIncident(deviceId = "SB-8041", stage = IncidentStage.NONE))
    val childIncident: StateFlow<SafetyIncident> = _childIncident.asStateFlow()

    private val _parentIncident = MutableStateFlow(SafetyIncident())
    val parentIncident: StateFlow<SafetyIncident> = _parentIncident.asStateFlow()

    private val _nearbyNodes = MutableStateFlow<Map<String, BleBeaconPayload>>(emptyMap())
    val nearbyNodes: StateFlow<Map<String, BleBeaconPayload>> = _nearbyNodes.asStateFlow()
    val discoveredSafetyNodes: StateFlow<Map<String, SafetyNode>> = bleManager.discoveredSafetyNodes

    // Batch E: Event Timeline V2 Items Flow
    val timelineItems: StateFlow<List<TimelineItem>> = repository.allEvents.map { events ->
        buildTimelineItems(events)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Batch E: Selected Incident Detail (Phase 13)
    private val _selectedIncidentDetail = MutableStateFlow<SafetyIncident?>(null)
    val selectedIncidentDetail: StateFlow<SafetyIncident?> = _selectedIncidentDetail.asStateFlow()

    // Batch E: Incident Replay Engine (Phase 14)
    private val _replayState = MutableStateFlow(IncidentReplayState())
    val replayState: StateFlow<IncidentReplayState> = _replayState.asStateFlow()
    private var replayJob: Job? = null

    // Batch G: Developer / Evaluation Scenario Runner (Phase 17)
    val scenarioRunner = EvaluationScenarioRunner(
        onSimulateLocation = { lat, lon, gState, rState ->
            locationHelper.simulateGeofenceState(gState)
            locationHelper.simulateRouteState(rState)
            locationHelper.simulateVerifiedLocation(
                VerifiedLocation(
                    latitude = lat,
                    longitude = lon,
                    source = "SIMULATION",
                    accuracyMeters = 8f,
                    confidence = LocationConfidence.HIGH,
                    isSimulation = true
                )
            )
        },
        onSimulateRisk = { risk, flags, isSos ->
            if (isSos) {
                triggerManualSos()
            } else {
                _activeFlags.value = flags
                _currentRiskLevel.value = risk
                if (risk == RiskLevel.MEDIUM || risk == RiskLevel.HIGH || risk == RiskLevel.CRITICAL) {
                    _childIncident.value = _childIncident.value.copy(
                        stage = IncidentStage.ACTIVE,
                        riskLevel = risk,
                        isSimulation = true
                    )
                    simulateIncomingEmergency(risk)
                }
            }
        },
        onSimulateBeacon = { payload ->
            bleManager.injectSimulatedBeacon(payload) { received ->
                onBeaconReceived(received)
            }
        },
        onResetState = {
            _activeFlags.value = emptySet()
            _currentRiskLevel.value = RiskLevel.NORMAL
            locationHelper.simulateGeofenceState(GeofenceState.SAFE)
            locationHelper.simulateRouteState(RouteState.ON_ROUTE)
            _isParentAlertActive.value = false
            _isParentAlertSilenced.value = false
            _incomingAlert.value = null
            alertNotifier.stopAlert()
        }
    )

    init {
        // Evaluate connectivity on peer node updates
        viewModelScope.launch {
            discoveredSafetyNodes.collect { nodes ->
                val now = System.currentTimeMillis()
                val direct = nodes.values.any { it.isNearby && (now - it.lastSeenTimestamp < 30_000L) }
                val relayed = nodes.values.any { it.connectionState == com.example.model.NodeConnectionState.RELAYED && (now - it.lastSeenTimestamp < 30_000L) }
                val lastSeen = nodes.values.maxOfOrNull { it.lastSeenTimestamp } ?: 0L
                connectivityManager.evaluateConnectivity(
                    hasNearbyPeerDirect = direct,
                    hasRelayedPeerEvidence = relayed,
                    lastSeenPeerTimestamp = lastSeen,
                    isBleAdvertising = bleManager.isAdvertising.value
                )
            }
        }
        // Seed default emergency services (Police 112, Childline India 1098)
        viewModelScope.launch {
            repository.seedDefaultEmergencyServicesIfNecessary()
        }

        // Continually resolve Safe Zone physical address
        viewModelScope.launch {
            safeZone.collect { sz ->
                val addr = LocationAddressResolver.resolveAddress(getApplication(), sz.latitude, sz.longitude)
                _safeZoneAddress.value = addr
            }
        }

        // Continually resolve Child location address
        viewModelScope.launch {
            locationHelper.currentLocation.collect { loc ->
                if (loc != null) {
                    val addr = LocationAddressResolver.resolveAddress(getApplication(), loc.latitude, loc.longitude)
                    _childAddress.value = addr
                }
            }
        }

        // Collect motion detector anomaly
        viewModelScope.launch {
            motionDetector.isAnomalyActive.collect { isAnomaly ->
                if (appMode.value == AppMode.CHILD) {
                    updateFlag(AlertFlag.MOTION_ANOMALY, isAnomaly)
                }
            }
        }

        // Collect geofence exit (backward compatible)
        viewModelScope.launch {
            locationHelper.isOutsideSafeZone.collect { isOutside ->
                if (appMode.value == AppMode.CHILD) {
                    updateFlag(AlertFlag.GEOFENCE_EXIT, isOutside)
                }
            }
        }

        // Propagate trusted route to locationHelper
        viewModelScope.launch {
            trustedRoute.collect { route ->
                locationHelper.setTrustedRoute(route)
            }
        }

        // Collect route corridor deviation
        viewModelScope.launch {
            locationHelper.routeState.collect { rState ->
                if (appMode.value == AppMode.CHILD) {
                    val isDeviated = rState == RouteState.ROUTE_DEVIATION
                    updateFlag(AlertFlag.ROUTE_DEVIATION, isDeviated)
                }
            }
        }

        // Collect Geofence Engine state transitions for audit logging
        viewModelScope.launch {
            locationHelper.geofenceState.collect { gState ->
                if (appMode.value == AppMode.CHILD) {
                    when (gState) {
                        GeofenceState.REENTERED -> {
                            repository.logEvent(
                                flagType = "GEOFENCE_REENTERED",
                                riskLevel = RiskLevel.NORMAL.name,
                                outcome = "CANCELLED_BY_USER",
                                deviceId = deviceId.value,
                                details = "Child safely re-entered designated safe zone boundary."
                            )
                        }
                        GeofenceState.OUTSIDE -> {
                            repository.logEvent(
                                flagType = "GEOFENCE_CONFIRMED_EXIT",
                                riskLevel = RiskLevel.MEDIUM.name,
                                outcome = "CONFIRMED_ESCALATED",
                                deviceId = deviceId.value,
                                details = "Safe zone boundary exit confirmed by distance, accuracy, and time verification."
                            )
                        }
                        else -> {}
                    }
                }
            }
        }

        // Seed default trusted contact once on first startup
        viewModelScope.launch {
            repository.seedInitialGuardianIfEmpty()
        }
    }

    fun selectAppMode(mode: AppMode) {
        viewModelScope.launch {
            repository.setAppMode(mode)
            if (mode == AppMode.CHILD) {
                startChildSensors()
            } else if (mode == AppMode.PARENT) {
                stopChildSensors()
                startParentScanning()
            }
        }
    }

    fun setCustomDeviceId(id: String) {
        viewModelScope.launch {
            repository.setDeviceId(id)
        }
    }

    fun onPermissionsGranted() {
        if (appMode.value == AppMode.CHILD) {
            startChildSensors()
        } else if (appMode.value == AppMode.PARENT) {
            startParentScanning()
        }
    }

    fun startChildSensors() {
        motionDetector.start()
        locationHelper.startLocationUpdates(safeZone.value)
    }

    fun stopChildSensors() {
        motionDetector.stop()
        locationHelper.stopLocationUpdates()
        bleManager.stopAdvertising()
        cancelCountdown()
        _childIncident.value = SafetyIncident(deviceId = deviceId.value, stage = IncidentStage.NONE)
    }

    fun startParentScanning() {
        bleManager.startScanning { payload ->
            onBeaconReceived(payload)
        }
    }

    fun stopParentScanning() {
        bleManager.stopScanning()
        beaconWatchdogJob?.cancel()
        beaconWatchdogJob = null
        dismissParentAlert()
        _isParentAlertSilenced.value = false
    }

    private fun onBeaconReceived(payload: BleBeaconPayload) {
        // Track in nearbyNodes registry
        val nodes = _nearbyNodes.value.toMutableMap()
        nodes[payload.deviceId] = payload
        _nearbyNodes.value = nodes

        // Always store latest beacon so parent UI displays latest telemetry
        _incomingAlert.value = payload

        // Update incoming child bio profile and contacts from the alerting child node
        if (payload.childBioProfile != null) {
            _incomingChildProfile.value = payload.childBioProfile
        }
        if (payload.emergencyContacts.isNotEmpty()) {
            _incomingChildContacts.value = payload.emergencyContacts
        }

        // Asynchronously resolve physical address for incoming beacon
        if (payload.latitude != null && payload.longitude != null) {
            viewModelScope.launch {
                val addr = LocationAddressResolver.resolveAddress(getApplication(), payload.latitude, payload.longitude)
                _incomingAlertAddress.value = addr
            }
        }

        // 1. Normal or Low Risk Beacon -> Child cancelled emergency or returned to safe state
        if (payload.riskLevel == RiskLevel.NORMAL || payload.riskLevel == RiskLevel.LOW) {
            val currentActiveDeviceId = _parentIncident.value.deviceId
            if (currentActiveDeviceId.isEmpty() || currentActiveDeviceId == payload.deviceId) {
                beaconWatchdogJob?.cancel()
                beaconWatchdogJob = null

                if (_isParentAlertActive.value || _isParentAlertSilenced.value) {
                    _isParentAlertActive.value = false
                    _isParentAlertSilenced.value = false
                    activeAlertRiskLevel = null
                    _parentIncident.value = _parentIncident.value.copy(
                        stage = IncidentStage.CANCELLED,
                        lastUpdatedTimestamp = System.currentTimeMillis(),
                        resolutionReason = "Child device broadcasted safe/normal status"
                    )
                    alertNotifier.stopAlert()
                    alertTimerJob?.cancel()
                    alertTimerJob = null

                    viewModelScope.launch {
                        repository.logEvent(
                            flagType = "BLE_BEACON_RESOLVED",
                            riskLevel = payload.riskLevel.name,
                            outcome = "CANCELLED_BY_CHILD",
                            deviceId = payload.deviceId,
                            details = "Child device broadcasted safe/normal status. Alert dismissed."
                        )
                    }
                }
            }
            return
        }

        // 2. Emergency / Warning Beacon (MEDIUM, HIGH, or CRITICAL)
        if (payload.riskLevel == RiskLevel.MEDIUM || payload.riskLevel == RiskLevel.HIGH || payload.riskLevel == RiskLevel.CRITICAL) {
            resetBeaconWatchdog(payload.deviceId)

            val now = System.currentTimeMillis()
            val obsId = if (payload.ephemeralId.isNotBlank()) "${payload.ephemeralId}-${payload.timestamp}" else "${payload.deviceId}-${payload.timestamp}"
            val sourceNode = payload.ephemeralId.ifEmpty { payload.deviceId }
            val newObs = IncidentObservation(
                observationId = obsId,
                sourceNodeId = sourceNode,
                timestamp = now,
                riskLevel = payload.riskLevel,
                hopCount = payload.hopCount,
                rssi = -70,
                syncStatus = if (payload.hopCount > 0) SyncStatus.RELAYED else SyncStatus.LOCAL_ONLY
            )

            // If the parent already acknowledged/silenced this incident:
            if (_isParentAlertSilenced.value) {
                val currentRisk = activeAlertRiskLevel
                if (currentRisk != null && payload.riskLevel.ordinal > currentRisk.ordinal) {
                    _isParentAlertSilenced.value = false
                } else {
                    val currentInc = _parentIncident.value
                    val updatedObs = if (currentInc.observations.none { it.observationId == obsId }) {
                        currentInc.observations + newObs
                    } else currentInc.observations
                    val updatedSources = (currentInc.sourceNodes + sourceNode).distinct()

                    _parentIncident.value = currentInc.copy(
                        lastUpdatedTimestamp = now,
                        latitude = payload.latitude ?: currentInc.latitude,
                        longitude = payload.longitude ?: currentInc.longitude,
                        address = _incomingAlertAddress.value ?: currentInc.address,
                        childBioProfile = payload.childBioProfile ?: _incomingChildProfile.value ?: currentInc.childBioProfile,
                        emergencyContacts = payload.emergencyContacts.ifEmpty { _incomingChildContacts.value }.ifEmpty { currentInc.emergencyContacts },
                        observations = updatedObs,
                        observationCount = updatedObs.size,
                        sourceNodes = updatedSources,
                        maxHopCount = maxOf(currentInc.maxHopCount, payload.hopCount)
                    )
                    return
                }
            }

            val isNewIncident = !_isParentAlertActive.value
            val riskChanged = activeAlertRiskLevel != payload.riskLevel

            _isParentAlertActive.value = true
            activeAlertRiskLevel = payload.riskLevel

            val currentInc = _parentIncident.value
            val isSameActiveIncident = !isNewIncident &&
                (currentInc.deviceId == payload.deviceId || currentInc.deviceId == payload.ephemeralId || currentInc.incidentId.contains(payload.deviceId)) &&
                (now - currentInc.startTimestamp < 900_000L)

            val updatedObservations = if (isSameActiveIncident) {
                if (currentInc.observations.none { it.observationId == obsId }) {
                    currentInc.observations + newObs
                } else currentInc.observations
            } else {
                listOf(newObs)
            }

            val updatedSourceNodes = (if (isSameActiveIncident) currentInc.sourceNodes else emptyList())
                .toMutableSet()
                .apply { add(sourceNode) }
                .toList()

            val highestRisk = if (isSameActiveIncident) {
                if (payload.riskLevel.ordinal > currentInc.riskLevel.ordinal) payload.riskLevel else currentInc.riskLevel
            } else {
                payload.riskLevel
            }

            val incidentId = if (isSameActiveIncident) currentInc.incidentId else "INC-${payload.deviceId}-${payload.timestamp}"

            _parentIncident.value = SafetyIncident(
                incidentId = incidentId,
                deviceId = payload.deviceId,
                stage = IncidentStage.ACTIVE,
                riskLevel = highestRisk,
                startTimestamp = if (isSameActiveIncident) currentInc.startTimestamp else payload.timestamp,
                lastUpdatedTimestamp = now,
                latitude = payload.latitude ?: currentInc.latitude,
                longitude = payload.longitude ?: currentInc.longitude,
                verifiedLocation = locationHelper.verifiedLocation.value ?: currentInc.verifiedLocation,
                address = _incomingAlertAddress.value ?: currentInc.address,
                childBioProfile = payload.childBioProfile ?: _incomingChildProfile.value ?: currentInc.childBioProfile,
                emergencyContacts = payload.emergencyContacts.ifEmpty { _incomingChildContacts.value }.ifEmpty { currentInc.emergencyContacts },
                isSimulation = payload.isSimulation,
                observations = updatedObservations,
                observationCount = updatedObservations.size,
                sourceNodes = updatedSourceNodes,
                maxHopCount = maxOf(if (isSameActiveIncident) currentInc.maxHopCount else 0, payload.hopCount),
                syncStatus = if (payload.hopCount > 0) SyncStatus.RELAYED else SyncStatus.LOCAL_ONLY
            )

            // Start or escalate siren & vibration ONLY on a new incident or risk escalation
            if (isNewIncident || riskChanged) {
                alertNotifier.startAlert(payload.riskLevel)
            }

            // Start elapsed time timer ONLY if not already running
            if (alertTimerJob == null || !alertTimerJob!!.isActive) {
                _alertDurationSeconds.value = 0L
                alertTimerJob = viewModelScope.launch {
                    val startMs = System.currentTimeMillis()
                    while (true) {
                        delay(1000L)
                        _alertDurationSeconds.value = (System.currentTimeMillis() - startMs) / 1000L
                    }
                }
            }

            // Rate-limit database logging to once every 15s or on risk changes
            if (isNewIncident || riskChanged || (now - lastLoggedIncidentTime > 15000L)) {
                lastLoggedIncidentTime = now
                viewModelScope.launch {
                    repository.logEvent(
                        flagType = "BLE_BEACON_RECEIVED",
                        riskLevel = payload.riskLevel.name,
                        outcome = "ALERT_RECEIVED",
                        deviceId = payload.deviceId,
                        details = "Risk ${payload.riskLevel.title} broadcast received. Lat: ${payload.latitude ?: 0.0}, Lon: ${payload.longitude ?: 0.0}",
                        syncStatus = if (payload.hopCount > 0) SyncStatus.RELAYED else SyncStatus.LOCAL_ONLY,
                        observationId = obsId,
                        hopCount = payload.hopCount
                    )
                }
            }
        }
    }

    private fun resetBeaconWatchdog(deviceId: String) {
        beaconWatchdogJob?.cancel()
        beaconWatchdogJob = viewModelScope.launch {
            delay(7000L) // 7 seconds of no emergency packets
            if (_isParentAlertActive.value || _isParentAlertSilenced.value) {
                val currentActiveDeviceId = _parentIncident.value.deviceId
                if (currentActiveDeviceId.isEmpty() || currentActiveDeviceId == deviceId) {
                    _isParentAlertActive.value = false
                    _isParentAlertSilenced.value = false
                    activeAlertRiskLevel = null
                    _parentIncident.value = _parentIncident.value.copy(
                        stage = IncidentStage.RESOLVED,
                        lastUpdatedTimestamp = System.currentTimeMillis(),
                        resolutionReason = "Emergency broadcast timed out"
                    )
                    alertNotifier.stopAlert()
                    alertTimerJob?.cancel()
                    alertTimerJob = null

                    repository.logEvent(
                        flagType = "BLE_BEACON_TIMEOUT",
                        riskLevel = RiskLevel.NORMAL.name,
                        outcome = "ALERT_RESOLVED_AUTO",
                        deviceId = deviceId,
                        details = "Emergency broadcast ended. Alert automatically cleared.",
                        syncStatus = SyncStatus.SYNCED
                    )
                }
            }
        }
    }

    fun dismissParentAlert() {
        _isParentAlertActive.value = false
        _isParentAlertSilenced.value = true
        _parentIncident.value = _parentIncident.value.copy(
            stage = IncidentStage.SILENCED,
            lastUpdatedTimestamp = System.currentTimeMillis()
        )
        alertNotifier.stopAlert()
        alertTimerJob?.cancel()
        alertTimerJob = null
    }

    fun acknowledgeIncident(incidentId: String = "") {
        _isParentAlertSilenced.value = true
        alertNotifier.stopAlert()
        _parentIncident.value = _parentIncident.value.copy(
            stage = IncidentStage.SILENCED,
            lastUpdatedTimestamp = System.currentTimeMillis()
        )
        viewModelScope.launch {
            repository.logEvent(
                flagType = "INCIDENT_ACKNOWLEDGED",
                riskLevel = activeAlertRiskLevel?.name ?: RiskLevel.NORMAL.name,
                outcome = "ACKNOWLEDGED",
                deviceId = _parentIncident.value.deviceId,
                details = "Incident acknowledged by guardian.",
                syncStatus = SyncStatus.ACKNOWLEDGED
            )
        }
    }

    fun resolveIncident(incidentId: String = "", reason: String = "Resolved by guardian") {
        _isParentAlertActive.value = false
        _isParentAlertSilenced.value = false
        activeAlertRiskLevel = null
        alertNotifier.stopAlert()
        alertTimerJob?.cancel()
        alertTimerJob = null
        _parentIncident.value = _parentIncident.value.copy(
            stage = IncidentStage.RESOLVED,
            lastUpdatedTimestamp = System.currentTimeMillis(),
            resolutionReason = reason
        )
        viewModelScope.launch {
            repository.logEvent(
                flagType = "INCIDENT_RESOLVED",
                riskLevel = RiskLevel.NORMAL.name,
                outcome = "RESOLVED_BY_GUARDIAN",
                deviceId = _parentIncident.value.deviceId,
                details = reason,
                syncStatus = SyncStatus.SYNCED
            )
        }
    }

    fun reopenAlertSheet() {
        if (_incomingAlert.value != null &&
            (_incomingAlert.value!!.riskLevel == RiskLevel.MEDIUM ||
             _incomingAlert.value!!.riskLevel == RiskLevel.HIGH ||
             _incomingAlert.value!!.riskLevel == RiskLevel.CRITICAL)
        ) {
            _isParentAlertActive.value = true
            _isParentAlertSilenced.value = false
            _parentIncident.value = _parentIncident.value.copy(
                stage = IncidentStage.ACTIVE,
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Simulate incoming emergency for single-device parent testing / evaluation.
     * Generates alerting child data (Aarav Sharma) with emergency contacts (Father, Mother, Police 112, Childline 1098).
     */
    fun simulateIncomingEmergency(riskLevel: RiskLevel = RiskLevel.HIGH) {
        _isParentAlertSilenced.value = false
        val simLat = safeZone.value.latitude + 0.005
        val simLon = safeZone.value.longitude + 0.005

        val simulatedChild = ChildBioProfile(
            childName = "Aarav Sharma",
            age = "9",
            bloodType = "B+",
            primaryParentPhone = "+91 98765 43210",
            secondaryContactPhone = "+91 98111 22334",
            medicalConditions = "Asthma (Carries Salbutamol Inhaler)",
            allergies = "Severe Peanut Allergy",
            emergencyNotes = "Wearing SafeBand ID. In emergency, call parent or 112 immediately."
        )

        val simulatedContacts = listOf(
            TrustedContact(id = 101, name = "Rajesh Sharma (Father)", phoneNumber = "+91 98765 43210", relationship = "Father"),
            TrustedContact(id = 102, name = "Sunita Sharma (Mother)", phoneNumber = "+91 98111 22334", relationship = "Mother"),
            TrustedContact(id = 103, name = "Police / National Emergency", phoneNumber = "112", relationship = "Emergency Hotline"),
            TrustedContact(id = 104, name = "Childline India", phoneNumber = "1098", relationship = "Child Care Helpline")
        )

        _incomingChildProfile.value = simulatedChild
        _incomingChildContacts.value = simulatedContacts

        val payload = BleBeaconPayload(
            deviceId = "SB-CHLD",
            riskLevel = riskLevel,
            timestamp = System.currentTimeMillis(),
            latitude = simLat,
            longitude = simLon,
            childBioProfile = simulatedChild,
            emergencyContacts = simulatedContacts,
            isSimulation = true
        )

        viewModelScope.launch {
            val addr = LocationAddressResolver.resolveAddress(getApplication(), simLat, simLon)
            _incomingAlertAddress.value = addr
        }

        bleManager.injectSimulatedBeacon(payload) { p ->
            onBeaconReceived(p)
        }
    }

    private fun broadcastCancellationBriefly() {
        cancellationAdvertisingJob?.cancel()
        startBleAdvertising(RiskLevel.NORMAL)
        cancellationAdvertisingJob = viewModelScope.launch {
            delay(4000L) // Broadcast NORMAL for 4 seconds so parent immediately clears
            if (!RiskEngine.shouldBroadcastBle(_currentRiskLevel.value)) {
                bleManager.stopAdvertising()
            }
        }
    }

    // Flag & Risk Management for Child Mode
    fun triggerManualSos() {
        cancellationAdvertisingJob?.cancel()
        countdownJob?.cancel()
        _isCountingDown.value = false
        val flags = _activeFlags.value + AlertFlag.MANUAL_SOS
        _activeFlags.value = flags
        val newRisk = RiskLevel.CRITICAL
        _currentRiskLevel.value = newRisk

        // Manual SOS is immediate: ALWAYS bypasses confirmation countdown immediately
        startBleAdvertising(newRisk)

        val loc = locationHelper.currentLocation.value
        val verified = locationHelper.verifiedLocation.value
        _childIncident.value = SafetyIncident(
            incidentId = "${deviceId.value}-${System.currentTimeMillis()}",
            deviceId = deviceId.value,
            stage = IncidentStage.BROADCASTING,
            riskLevel = newRisk,
            startTimestamp = System.currentTimeMillis(),
            lastUpdatedTimestamp = System.currentTimeMillis(),
            latitude = loc?.latitude ?: safeZone.value.latitude,
            longitude = loc?.longitude ?: safeZone.value.longitude,
            verifiedLocation = verified,
            address = _childAddress.value,
            childBioProfile = childBioProfile.value,
            emergencyContacts = allContacts.value,
            isSimulation = false
        )

        viewModelScope.launch {
            repository.logEvent(
                flagType = AlertFlag.MANUAL_SOS.displayName,
                riskLevel = newRisk.name,
                outcome = "CONFIRMED_IMMEDIATE_SOS",
                deviceId = deviceId.value,
                details = "Manual SOS emergency button triggered by child - immediate CRITICAL escalation without countdown"
            )
        }
    }

    fun cancelManualSos() {
        val flags = _activeFlags.value - AlertFlag.MANUAL_SOS
        _activeFlags.value = flags
        val newRisk = RiskEngine.calculateRisk(flags)
        _currentRiskLevel.value = newRisk

        if (!RiskEngine.shouldBroadcastBle(newRisk)) {
            broadcastCancellationBriefly()
            _childIncident.value = _childIncident.value.copy(
                stage = IncidentStage.CANCELLED,
                riskLevel = newRisk,
                lastUpdatedTimestamp = System.currentTimeMillis(),
                resolutionReason = "Manual SOS cancelled by child"
            )
        } else {
            _childIncident.value = _childIncident.value.copy(
                riskLevel = newRisk,
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
        }

        viewModelScope.launch {
            repository.logEvent(
                flagType = AlertFlag.MANUAL_SOS.displayName,
                riskLevel = newRisk.name,
                outcome = "CANCELLED_BY_USER",
                deviceId = deviceId.value,
                details = "Child manual SOS cleared"
            )
        }
    }

    fun updateFlag(flag: AlertFlag, isActive: Boolean) {
        if (flag == AlertFlag.MANUAL_SOS && isActive) {
            triggerManualSos()
            return
        }

        val current = _activeFlags.value.toMutableSet()
        if (isActive) {
            current.add(flag)
        } else {
            current.remove(flag)
        }
        _activeFlags.value = current

        val evaluatedRisk = RiskEngine.calculateRisk(current)

        // If risk level changes
        if (evaluatedRisk != _currentRiskLevel.value) {
            val isEscalation = evaluatedRisk.ordinal > _currentRiskLevel.value.ordinal

            if (isEscalation && RiskEngine.requiresConfirmationCountdown(evaluatedRisk, false)) {
                // Start visible 15-second countdown to allow "I'm OK, cancel"
                startConfirmationCountdown(evaluatedRisk, flag)
            } else if (!isEscalation) {
                // Downgrade
                _currentRiskLevel.value = evaluatedRisk
                if (!RiskEngine.shouldBroadcastBle(evaluatedRisk)) {
                    if (bleManager.isAdvertising.value) {
                        broadcastCancellationBriefly()
                    } else {
                        bleManager.stopAdvertising()
                    }
                    _childIncident.value = _childIncident.value.copy(
                        stage = if (evaluatedRisk == RiskLevel.NORMAL) IncidentStage.NONE else IncidentStage.WARNING,
                        riskLevel = evaluatedRisk,
                        lastUpdatedTimestamp = System.currentTimeMillis(),
                        resolutionReason = "Sensor anomaly cleared"
                    )
                }
                cancelCountdown()
            } else {
                // Low risk (1 flag) -> logged locally only, no broadcast
                _currentRiskLevel.value = evaluatedRisk
                _childIncident.value = _childIncident.value.copy(
                    stage = IncidentStage.WARNING,
                    riskLevel = evaluatedRisk,
                    lastUpdatedTimestamp = System.currentTimeMillis()
                )
                viewModelScope.launch {
                    repository.logEvent(
                        flagType = flag.displayName,
                        riskLevel = evaluatedRisk.name,
                        outcome = "LOGGED_LOCAL",
                        deviceId = deviceId.value,
                        details = "Single flag active. Low risk logged locally without BLE broadcast."
                    )
                }
            }
        }
    }

    private fun startConfirmationCountdown(targetRisk: RiskLevel, triggerFlag: AlertFlag) {
        cancellationAdvertisingJob?.cancel()
        countdownJob?.cancel()
        _isCountingDown.value = true
        _countdownRemainingSeconds.value = RiskEngine.CONFIRMATION_COUNTDOWN_SECONDS

        val loc = locationHelper.currentLocation.value
        val verified = locationHelper.verifiedLocation.value
        _childIncident.value = SafetyIncident(
            incidentId = "${deviceId.value}-${System.currentTimeMillis()}",
            deviceId = deviceId.value,
            stage = IncidentStage.CONFIRMATION_PENDING,
            riskLevel = targetRisk,
            startTimestamp = System.currentTimeMillis(),
            lastUpdatedTimestamp = System.currentTimeMillis(),
            latitude = loc?.latitude ?: safeZone.value.latitude,
            longitude = loc?.longitude ?: safeZone.value.longitude,
            verifiedLocation = verified,
            address = _childAddress.value,
            childBioProfile = childBioProfile.value,
            emergencyContacts = allContacts.value,
            isSimulation = false
        )

        countdownJob = viewModelScope.launch {
            var currentTargetRisk = targetRisk
            for (i in RiskEngine.CONFIRMATION_COUNTDOWN_SECONDS downTo 1) {
                _countdownRemainingSeconds.value = i

                // 1. Active location & confidence re-evaluation
                val freshLoc = locationHelper.currentLocation.value
                val freshVerified = locationHelper.verifiedLocation.value
                if (freshLoc != null) {
                    _childIncident.value = _childIncident.value.copy(
                        latitude = freshLoc.latitude,
                        longitude = freshLoc.longitude,
                        verifiedLocation = freshVerified,
                        lastUpdatedTimestamp = System.currentTimeMillis()
                    )
                }

                // 2. Detect re-entry during countdown
                val gState = locationHelper.geofenceState.value
                val rState = locationHelper.routeState.value
                if ((gState == GeofenceState.REENTERED || gState == GeofenceState.SAFE) &&
                    (rState == RouteState.ON_ROUTE || rState == RouteState.UNKNOWN) &&
                    !_activeFlags.value.contains(AlertFlag.MANUAL_SOS) &&
                    !_activeFlags.value.contains(AlertFlag.TAMPER)
                ) {
                    // Child safely re-entered inside boundary
                    _isCountingDown.value = false
                    _currentRiskLevel.value = RiskLevel.NORMAL
                    _childIncident.value = _childIncident.value.copy(
                        stage = IncidentStage.RESOLVED,
                        riskLevel = RiskLevel.NORMAL,
                        lastUpdatedTimestamp = System.currentTimeMillis(),
                        resolutionReason = "Safe boundary re-entry detected during 15s confirmation window"
                    )
                    repository.logEvent(
                        flagType = "CONFIRMATION_RESOLVED_REENTRY",
                        riskLevel = RiskLevel.NORMAL.name,
                        outcome = "RESOLVED_SAFE_REENTRY",
                        deviceId = deviceId.value,
                        details = "Child safely re-entered safe zone boundary within 15s window. Escalation aborted."
                    )
                    return@launch
                }

                // 3. Detect worsening risk / compounding signals
                val active = _activeFlags.value
                if (active.contains(AlertFlag.MANUAL_SOS)) {
                    _isCountingDown.value = false
                    triggerManualSos()
                    return@launch
                }

                val freshlyEvaluatedRisk = RiskEngine.calculateRisk(active)
                if (freshlyEvaluatedRisk.ordinal > currentTargetRisk.ordinal) {
                    currentTargetRisk = freshlyEvaluatedRisk
                    _childIncident.value = _childIncident.value.copy(
                        riskLevel = currentTargetRisk,
                        lastUpdatedTimestamp = System.currentTimeMillis()
                    )
                }

                delay(1000L)
            }

            // Escalation finalized after 15s confirmation countdown
            _isCountingDown.value = false
            _currentRiskLevel.value = currentTargetRisk
            startBleAdvertising(currentTargetRisk)

            _childIncident.value = _childIncident.value.copy(
                stage = IncidentStage.BROADCASTING,
                riskLevel = currentTargetRisk,
                lastUpdatedTimestamp = System.currentTimeMillis()
            )

            repository.logEvent(
                flagType = triggerFlag.displayName,
                riskLevel = currentTargetRisk.name,
                outcome = "CONFIRMED_ESCALATED",
                deviceId = deviceId.value,
                details = "15-second confirmation window elapsed without cancellation. Escalated to BLE broadcast."
            )
        }
    }

    /**
     * "I'm OK, cancel" button pressed by child during confirmation window.
     */
    fun cancelConfirmationCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _isCountingDown.value = false

        // Clear sensor flags that caused anomaly
        motionDetector.clearAnomaly()
        val flags = _activeFlags.value - AlertFlag.MOTION_ANOMALY
        _activeFlags.value = flags

        val newRisk = RiskEngine.calculateRisk(flags)
        _currentRiskLevel.value = newRisk
        if (!RiskEngine.shouldBroadcastBle(newRisk)) {
            if (bleManager.isAdvertising.value) {
                broadcastCancellationBriefly()
            } else {
                bleManager.stopAdvertising()
            }
        }

        _childIncident.value = _childIncident.value.copy(
            stage = IncidentStage.CANCELLED,
            riskLevel = newRisk,
            lastUpdatedTimestamp = System.currentTimeMillis(),
            resolutionReason = "False alarm prevented: Child confirmed 'I am OK'"
        )

        viewModelScope.launch {
            repository.logEvent(
                flagType = "CONFIRMATION_WINDOW",
                riskLevel = RiskLevel.NORMAL.name,
                outcome = "CANCELLED_BY_USER",
                deviceId = deviceId.value,
                details = "False alarm prevented: Child confirmed 'I am OK' during countdown."
            )
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _isCountingDown.value = false
    }

    private fun startBleAdvertising(riskLevel: RiskLevel) {
        val loc = locationHelper.currentLocation.value
        bleManager.startAdvertising(
            deviceId = deviceId.value,
            riskLevel = riskLevel,
            lat = loc?.latitude ?: safeZone.value.latitude,
            lon = loc?.longitude ?: safeZone.value.longitude,
            childBioProfile = childBioProfile.value,
            contacts = allContacts.value
        )
    }

    // Simulation Toggles for Child Mode
    fun toggleSimulatedMotionAnomaly() {
        val currentlyActive = _activeFlags.value.contains(AlertFlag.MOTION_ANOMALY)
        if (currentlyActive) {
            motionDetector.clearAnomaly()
            updateFlag(AlertFlag.MOTION_ANOMALY, false)
        } else {
            motionDetector.triggerSimulatedAnomaly()
            updateFlag(AlertFlag.MOTION_ANOMALY, true)
        }
    }

    fun toggleSimulatedGeofenceExit() {
        val currentlyActive = _activeFlags.value.contains(AlertFlag.GEOFENCE_EXIT)
        val newOutside = !currentlyActive
        locationHelper.simulateOutsideSafeZone(newOutside)
        updateFlag(AlertFlag.GEOFENCE_EXIT, newOutside)
    }

    // Safe Zone Settings
    fun saveSafeZone(lat: Double, lon: Double, radius: Float, name: String) {
        viewModelScope.launch {
            val updated = SafeZone(lat, lon, radius, name)
            repository.saveSafeZone(updated)
            locationHelper.startLocationUpdates(updated)
        }
    }

    // Contacts
    fun addTrustedContact(name: String, phone: String, relationship: String) {
        viewModelScope.launch {
            repository.addContact(name, phone, relationship)
        }
    }

    fun deleteTrustedContact(id: Long) {
        viewModelScope.launch {
            repository.deleteContact(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    /**
     * Creates an ACTION_SENDTO SMS Intent with pre-filled details.
     * Includes physical address (Street, Area, City, State, PIN code),
     * coordinates, Google Maps link, child medical info, and Indian emergency numbers.
     */
    fun createEmergencySmsIntent(
        context: Context,
        contactPhoneNumber: String,
        payload: BleBeaconPayload,
        bioProfile: ChildBioProfile? = incomingChildProfile.value ?: payload.childBioProfile ?: childBioProfile.value,
        address: GeoAddress? = incomingAlertAddress.value ?: payload.address ?: childAddress.value
    ): Intent {
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timeStr = timeFormat.format(Date(payload.timestamp))
        val coordsStr = if (payload.latitude != null && payload.longitude != null) {
            "Lat: %.5f, Lon: %.5f".format(payload.latitude, payload.longitude)
        } else {
            "Safe Zone Vicinity"
        }
        val mapsLink = if (payload.latitude != null && payload.longitude != null) {
            "https://maps.google.com/?q=%.5f,%.5f".format(payload.latitude, payload.longitude)
        } else ""

        val addressSection = if (address != null && address.formattedSummary().isNotBlank()) {
            """
            📍 PHYSICAL ADDRESS:
            ${address.formattedSummary()}
            """.trimIndent()
        } else {
            "📍 AREA: Near Safe Zone Vicinity"
        }

        val profile = bioProfile ?: ChildBioProfile()

        val body = """
            🚨 SAFEBAND EMERGENCY ALERT 🚨
            Child: ${profile.childName} (Age: ${profile.age})
            Alert Status: ${payload.riskLevel.title} at $timeStr
            Device ID: ${payload.deviceId}

            $addressSection
            Coordinates: $coordsStr
            ${if (mapsLink.isNotBlank()) "Maps Link: $mapsLink" else ""}

            📋 CRITICAL MEDICAL & BIO DATA:
            • Blood Type: ${profile.bloodType}
            • Primary Guardian Phone: ${profile.primaryParentPhone}
            • Secondary Phone: ${profile.secondaryContactPhone}
            • Medical Conditions: ${profile.medicalConditions}
            • Known Allergies: ${profile.allergies}
            • Emergency Instructions: ${profile.emergencyNotes}

            🚨 EMERGENCY HELPLINES (INDIA):
            • Police / National Emergency: 112
            • Childline India (Child Care): 1098

            Please check on the child or dispatch assistance immediately!
        """.trimIndent()

        return Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${contactPhoneNumber.replace(" ", "")}")
            putExtra("sms_body", body)
        }
    }

    // =========================================================================
    // Batch E: Incident Detail (Phase 13)
    // =========================================================================

    fun viewIncidentDetail(incident: SafetyIncident) {
        _selectedIncidentDetail.value = incident
    }

    fun closeIncidentDetail() {
        _selectedIncidentDetail.value = null
    }

    // =========================================================================
    // Batch E: Incident Replay Engine (Phase 14)
    // =========================================================================

    val replaySteps: List<ReplayStep> = DEFAULT_REPLAY_STEPS

    fun startIncidentReplay(incident: SafetyIncident? = null) {
        replayJob?.cancel()
        _replayState.value = IncidentReplayState(
            isPlaying = true,
            currentStepIndex = 0,
            totalSteps = replaySteps.size,
            currentStep = replaySteps[0],
            isCompleted = false
        )
        replayJob = viewModelScope.launch {
            for (i in 0 until replaySteps.size) {
                _replayState.value = _replayState.value.copy(
                    isPlaying = true,
                    currentStepIndex = i,
                    currentStep = replaySteps[i]
                )
                delay(1200L)
            }
            _replayState.value = _replayState.value.copy(
                isPlaying = false,
                isCompleted = true
            )
        }
    }

    fun pauseIncidentReplay() {
        replayJob?.cancel()
        _replayState.value = _replayState.value.copy(isPlaying = false)
    }

    fun resumeIncidentReplay() {
        val currentIndex = _replayState.value.currentStepIndex
        if (currentIndex >= replaySteps.size - 1) {
            startIncidentReplay()
            return
        }
        replayJob?.cancel()
        replayJob = viewModelScope.launch {
            _replayState.value = _replayState.value.copy(isPlaying = true)
            for (i in (currentIndex + 1) until replaySteps.size) {
                _replayState.value = _replayState.value.copy(
                    currentStepIndex = i,
                    currentStep = replaySteps[i]
                )
                delay(1200L)
            }
            _replayState.value = _replayState.value.copy(
                isPlaying = false,
                isCompleted = true
            )
        }
    }

    fun stepForwardIncidentReplay() {
        val nextIndex = (_replayState.value.currentStepIndex + 1).coerceAtMost(replaySteps.size - 1)
        _replayState.value = _replayState.value.copy(
            isPlaying = false,
            currentStepIndex = nextIndex,
            currentStep = replaySteps[nextIndex],
            isCompleted = (nextIndex == replaySteps.size - 1)
        )
    }

    fun resetIncidentReplay() {
        replayJob?.cancel()
        _replayState.value = IncidentReplayState(
            totalSteps = replaySteps.size,
            currentStep = replaySteps[0]
        )
    }

    // =========================================================================
    // Batch G: Scenario Execution Controls (Phase 17)
    // =========================================================================

    fun runScenario(scenarioId: ScenarioId) {
        scenarioRunner.runScenario(scenarioId, viewModelScope)
    }

    fun resetScenario() {
        scenarioRunner.resetScenario()
    }

    // =========================================================================
    // Pure Helper: Event Timeline V2 Builder (Phase 12)
    // =========================================================================

    companion object {
        val DEFAULT_REPLAY_STEPS: List<ReplayStep> = listOf(
            ReplayStep(0, "1. Home Departure", "Child departing home waypoint within designated safe zone", GeofenceState.SAFE, RouteState.ON_ROUTE, RiskLevel.NORMAL, IncidentStage.NONE, 37.7749, -122.4194),
            ReplayStep(1, "2. Bus Stop Transit", "Child transit along designated corridor passing bus stop", GeofenceState.SAFE, RouteState.ON_ROUTE, RiskLevel.NORMAL, IncidentStage.NONE, 37.7760, -122.4194),
            ReplayStep(2, "3. School Area Arrival", "Approaching perimeter boundary edge near school", GeofenceState.APPROACHING, RouteState.APPROACHING_EDGE, RiskLevel.LOW, IncidentStage.NONE, 37.7780, -122.4194),
            ReplayStep(3, "4. Boundary Breach", "Route corridor deviation detected heading outside perimeter", GeofenceState.EXIT_PENDING, RouteState.ROUTE_DEVIATION, RiskLevel.MEDIUM, IncidentStage.CONFIRMATION_PENDING, 37.7790, -122.4180),
            ReplayStep(4, "5. Grace Period Elapsed", "15s confirmation window elapsed, safe zone exit confirmed", GeofenceState.OUTSIDE, RouteState.ROUTE_DEVIATION, RiskLevel.HIGH, IncidentStage.CONFIRMED, 37.7800, -122.4170),
            ReplayStep(5, "6. Beacon Broadcasting", "Wearer node begins emergency beacon broadcasting", GeofenceState.OUTSIDE, RouteState.ROUTE_DEVIATION, RiskLevel.HIGH, IncidentStage.BROADCASTING, 37.7805, -122.4165),
            ReplayStep(6, "7. Peer Relay 1 (Phone A)", "Observed and forwarded by peer relay Phone A (Hop 1)", GeofenceState.OUTSIDE, RouteState.ROUTE_DEVIATION, RiskLevel.HIGH, IncidentStage.ACTIVE, 37.7810, -122.4160, SyncStatus.RELAYED, 2),
            ReplayStep(7, "8. Guardian Escalation", "Guardian device receives alert, siren activates", GeofenceState.OUTSIDE, RouteState.ROUTE_DEVIATION, RiskLevel.CRITICAL, IncidentStage.ACTIVE, 37.7812, -122.4158, SyncStatus.ACKNOWLEDGED, 3),
            ReplayStep(8, "9. Return to Route", "Child re-enters designated route corridor perimeter", GeofenceState.REENTERED, RouteState.ON_ROUTE, RiskLevel.LOW, IncidentStage.ACTIVE, 37.7770, -122.4190, SyncStatus.SYNCED, 3),
            ReplayStep(9, "10. Incident Resolved", "Child safely inside safe zone, marked resolved", GeofenceState.SAFE, RouteState.ON_ROUTE, RiskLevel.NORMAL, IncidentStage.RESOLVED, 37.7750, -122.4194, SyncStatus.SYNCED, 3)
        )

        fun buildTimelineItems(events: List<SafetyEvent>): List<TimelineItem> {
            if (events.isEmpty()) return emptyList()

            val mapped = events.sortedByDescending { it.timestamp }.map { event ->
                val risk = try {
                    RiskLevel.valueOf(event.riskLevel)
                } catch (_: Exception) {
                    RiskLevel.NORMAL
                }
                val sync = try {
                    SyncStatus.valueOf(event.syncStatus)
                } catch (_: Exception) {
                    SyncStatus.LOCAL_ONLY
                }
                val title = when (event.flagType) {
                    "SOS" -> "Manual SOS Triggered"
                    "GEOFENCE", "GEOFENCE_EXIT" -> "Safe Zone Exit Confirmed"
                    "MOTION", "MOTION_ANOMALY" -> "Motion Anomaly Detected"
                    "ROUTE", "ROUTE_DEVIATION" -> "Route Corridor Deviation"
                    "TAMPER" -> "Band Tamper / Clasp Cut"
                    "BOUNDARY", "BOUNDARY_VIOLATION" -> "Boundary Violation"
                    "BLE_BEACON_RECEIVED" -> "BLE Emergency Beacon Received"
                    "BLE_BEACON_RESOLVED" -> "Safe Beacon Received (Resolved)"
                    "CONFIRMATION_RESOLVED_REENTRY" -> "Safe Boundary Re-Entry"
                    else -> event.flagType
                }
                TimelineItem(
                    id = "${event.id}-${event.timestamp}",
                    timestamp = event.timestamp,
                    title = title,
                    description = event.details.ifEmpty { "${event.flagType} (${event.outcome})" },
                    riskLevel = risk,
                    flagType = event.flagType,
                    outcome = event.outcome,
                    syncStatus = sync,
                    observationCount = 1,
                    sourceNode = if (event.observationId.contains("-")) event.observationId.substringBefore("-") else event.deviceId,
                    hopCount = event.hopCount,
                    isSimulation = event.outcome.contains("SIMULAT"),
                    rawEventId = event.id
                )
            }

            // Collapse adjacent events of identical flagType and sourceNode within 15 seconds
            val result = mutableListOf<TimelineItem>()
            for (item in mapped) {
                val last = result.lastOrNull()
                if (last != null && last.flagType == item.flagType && last.sourceNode == item.sourceNode &&
                    kotlin.math.abs(last.timestamp - item.timestamp) < 15_000L
                ) {
                    result[result.size - 1] = last.copy(
                        observationCount = last.observationCount + 1,
                        hopCount = maxOf(last.hopCount, item.hopCount)
                    )
                } else {
                    result.add(item)
                }
            }
            return result
        }
    }

    override fun onCleared() {
        super.onCleared()
        alertNotifier.release()
        bleManager.stopAdvertising()
        bleManager.stopScanning()
        motionDetector.stop()
        locationHelper.stopLocationUpdates()
        batteryManager.unregisterBatteryReceiver()
        scenarioRunner.stopScenario()
        replayJob?.cancel()
    }
}
