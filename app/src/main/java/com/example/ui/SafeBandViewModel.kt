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
import com.example.model.IncidentStage
import com.example.model.RiskLevel
import com.example.model.SafeZone
import com.example.model.SafetyIncident
import com.example.service.AlertNotifier
import com.example.service.BleSafetyManager
import com.example.service.LocationAddressResolver
import com.example.service.LocationSafetyHelper
import com.example.service.MotionAnomalyDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    fun updateChildBioProfile(profile: ChildBioProfile) {
        viewModelScope.launch {
            repository.saveChildBioProfile(profile)
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

    init {
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

        // Collect geofence exit
        viewModelScope.launch {
            locationHelper.isOutsideSafeZone.collect { isOutside ->
                if (appMode.value == AppMode.CHILD) {
                    updateFlag(AlertFlag.GEOFENCE_EXIT, isOutside)
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

        // 2. Emergency / Warning Beacon (MEDIUM or HIGH)
        if (payload.riskLevel == RiskLevel.MEDIUM || payload.riskLevel == RiskLevel.HIGH) {
            resetBeaconWatchdog(payload.deviceId)

            // If the parent already acknowledged/silenced this incident:
            if (_isParentAlertSilenced.value) {
                val currentRisk = activeAlertRiskLevel
                if (currentRisk != null && payload.riskLevel.ordinal > currentRisk.ordinal) {
                    _isParentAlertSilenced.value = false
                } else {
                    _parentIncident.value = _parentIncident.value.copy(
                        lastUpdatedTimestamp = System.currentTimeMillis(),
                        latitude = payload.latitude,
                        longitude = payload.longitude,
                        address = _incomingAlertAddress.value,
                        childBioProfile = payload.childBioProfile ?: _incomingChildProfile.value,
                        emergencyContacts = payload.emergencyContacts.ifEmpty { _incomingChildContacts.value }
                    )
                    return
                }
            }

            val isNewIncident = !_isParentAlertActive.value
            val riskChanged = activeAlertRiskLevel != payload.riskLevel

            _isParentAlertActive.value = true
            activeAlertRiskLevel = payload.riskLevel

            _parentIncident.value = SafetyIncident(
                incidentId = "${payload.deviceId}-${payload.timestamp}",
                deviceId = payload.deviceId,
                stage = IncidentStage.ACTIVE,
                riskLevel = payload.riskLevel,
                startTimestamp = if (isNewIncident) payload.timestamp else _parentIncident.value.startTimestamp,
                lastUpdatedTimestamp = System.currentTimeMillis(),
                latitude = payload.latitude,
                longitude = payload.longitude,
                address = _incomingAlertAddress.value,
                childBioProfile = payload.childBioProfile ?: _incomingChildProfile.value,
                emergencyContacts = payload.emergencyContacts.ifEmpty { _incomingChildContacts.value }
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
            val now = System.currentTimeMillis()
            if (isNewIncident || riskChanged || (now - lastLoggedIncidentTime > 15000L)) {
                lastLoggedIncidentTime = now
                viewModelScope.launch {
                    repository.logEvent(
                        flagType = "BLE_BEACON_RECEIVED",
                        riskLevel = payload.riskLevel.name,
                        outcome = "ALERT_RECEIVED",
                        deviceId = payload.deviceId,
                        details = "Risk ${payload.riskLevel.title} broadcast received. Lat: ${payload.latitude ?: 0.0}, Lon: ${payload.longitude ?: 0.0}"
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
                        details = "Emergency broadcast ended. Alert automatically cleared."
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

    fun reopenAlertSheet() {
        if (_incomingAlert.value != null &&
            (_incomingAlert.value!!.riskLevel == RiskLevel.MEDIUM || _incomingAlert.value!!.riskLevel == RiskLevel.HIGH)
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
            emergencyContacts = simulatedContacts
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
        val flags = _activeFlags.value + AlertFlag.MANUAL_SOS
        _activeFlags.value = flags
        val newRisk = RiskEngine.calculateRisk(flags)
        _currentRiskLevel.value = newRisk

        // Manual SOS is immediate: bypasses confirmation countdown
        cancelCountdown()
        startBleAdvertising(newRisk)

        val loc = locationHelper.currentLocation.value
        _childIncident.value = SafetyIncident(
            incidentId = "${deviceId.value}-${System.currentTimeMillis()}",
            deviceId = deviceId.value,
            stage = IncidentStage.BROADCASTING,
            riskLevel = newRisk,
            startTimestamp = System.currentTimeMillis(),
            lastUpdatedTimestamp = System.currentTimeMillis(),
            latitude = loc?.latitude ?: safeZone.value.latitude,
            longitude = loc?.longitude ?: safeZone.value.longitude,
            address = _childAddress.value,
            childBioProfile = childBioProfile.value,
            emergencyContacts = allContacts.value
        )

        viewModelScope.launch {
            repository.logEvent(
                flagType = AlertFlag.MANUAL_SOS.displayName,
                riskLevel = newRisk.name,
                outcome = "CONFIRMED_IMMEDIATE_SOS",
                deviceId = deviceId.value,
                details = "Manual SOS emergency button triggered by child"
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
                // Start visible countdown to allow "I'm OK, cancel"
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
        _countdownRemainingSeconds.value = 10

        val loc = locationHelper.currentLocation.value
        _childIncident.value = SafetyIncident(
            incidentId = "${deviceId.value}-${System.currentTimeMillis()}",
            deviceId = deviceId.value,
            stage = IncidentStage.CONFIRMATION_PENDING,
            riskLevel = targetRisk,
            startTimestamp = System.currentTimeMillis(),
            lastUpdatedTimestamp = System.currentTimeMillis(),
            latitude = loc?.latitude ?: safeZone.value.latitude,
            longitude = loc?.longitude ?: safeZone.value.longitude,
            address = _childAddress.value,
            childBioProfile = childBioProfile.value,
            emergencyContacts = allContacts.value
        )

        countdownJob = viewModelScope.launch {
            for (i in 10 downTo 1) {
                _countdownRemainingSeconds.value = i
                delay(1000L)
            }
            // Escalation finalized after countdown
            _isCountingDown.value = false
            _currentRiskLevel.value = targetRisk
            startBleAdvertising(targetRisk)

            _childIncident.value = _childIncident.value.copy(
                stage = IncidentStage.BROADCASTING,
                riskLevel = targetRisk,
                lastUpdatedTimestamp = System.currentTimeMillis()
            )

            repository.logEvent(
                flagType = triggerFlag.displayName,
                riskLevel = targetRisk.name,
                outcome = "CONFIRMED_ESCALATED",
                deviceId = deviceId.value,
                details = "Confirmation countdown elapsed without cancellation. Escalated to BLE."
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

    override fun onCleared() {
        super.onCleared()
        alertNotifier.release()
        bleManager.stopAdvertising()
        bleManager.stopScanning()
        motionDetector.stop()
        locationHelper.stopLocationUpdates()
    }
}
