package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.local.SafeBandDatabase
import com.example.data.preferences.SafeBandPreferences
import com.example.data.repository.SafetyRepository
import com.example.model.AlertFlag
import com.example.model.BleBeaconPayload
import com.example.model.IncidentObservation
import com.example.model.IncidentStage
import com.example.model.MonitoringServiceState
import com.example.model.NodeConnectionState
import com.example.model.RiskLevel
import com.example.model.SafetyIncident
import com.example.model.SafetyNode
import com.example.model.SyncStatus
import com.example.model.TrustRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Always-On Foreground Service for BLE Safety Monitoring (Phases 9, 10, 11, 12, 15, 16, 20).
 *
 * Responsibilities:
 * - Maintains a persistent Android Foreground Service with low-importance user notification.
 * - Manages single scanner lifecycle independent of UI/Activity/Composable state.
 * - Operates when app is backgrounded or screen is locked.
 * - Listens for physical ESP32-S3 and simulated SafeBand beacons.
 * - Deduplicates repeated physical BLE advertisements (100 SOS -> 1 incident).
 * - Fires high-priority emergency notifications on CRITICAL SOS events.
 * - Routes events into SafetyRepository (Room DB).
 */
class BleSafetyMonitorService : Service() {

    companion object {
        private const val TAG = "BleSafetyMonitorService"
        const val CHANNEL_ID_MONITORING = "safeband_monitoring_channel"
        const val CHANNEL_ID_EMERGENCY = "safeband_emergency_alerts"
        const val NOTIFICATION_ID_FOREGROUND = 1001
        const val NOTIFICATION_ID_EMERGENCY = 2001

        const val ACTION_START_MONITORING = "com.example.safeband.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.example.safeband.STOP_MONITORING"
        const val ACTION_SHOW_INCIDENT = "com.example.safeband.SHOW_INCIDENT"
        const val EXTRA_INCIDENT_ID = "extra_incident_id"

        private val _serviceState = MutableStateFlow(MonitoringServiceState.DISABLED)
        val serviceState: StateFlow<MonitoringServiceState> = _serviceState.asStateFlow()

        private val _latestBeacon = MutableStateFlow<BleBeaconPayload?>(null)
        val latestBeacon: StateFlow<BleBeaconPayload?> = _latestBeacon.asStateFlow()

        private val _activeEmergencyIncident = MutableStateFlow<SafetyIncident?>(null)
        val activeEmergencyIncident: StateFlow<SafetyIncident?> = _activeEmergencyIncident.asStateFlow()

        private val _discoveredNodes = MutableStateFlow<Map<String, SafetyNode>>(emptyMap())
        val discoveredNodes: StateFlow<Map<String, SafetyNode>> = _discoveredNodes.asStateFlow()

        fun isRunning(): Boolean = _serviceState.value == MonitoringServiceState.ACTIVE

        fun start(context: Context) {
            val intent = Intent(context, BleSafetyMonitorService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start BleSafetyMonitorService", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BleSafetyMonitorService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop BleSafetyMonitorService", e)
            }
        }

        /**
         * For unit testing and developer evaluation: inject a beacon into the service pipeline.
         */
        fun injectTestBeacon(context: Context, payload: BleBeaconPayload) {
            val instance = activeInstance
            if (instance != null) {
                instance.processBeacon(payload)
            } else {
                _latestBeacon.value = payload
            }
        }

        @Volatile
        private var activeInstance: BleSafetyMonitorService? = null
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var repository: SafetyRepository? = null

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scanner: BluetoothLeScanner? = null
    private var currentScanCallback: ScanCallback? = null

    private var beaconWatchdogJob: Job? = null
    private var lastLoggedIncidentTime = 0L

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_ON -> {
                        Log.i(TAG, "Bluetooth turned ON: restoring BLE scanner safely")
                        startBleScanner()
                    }
                    BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {
                        Log.w(TAG, "Bluetooth turned OFF: scanner paused with ERROR state")
                        stopBleScanner()
                        _serviceState.value = MonitoringServiceState.ERROR
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        Log.i(TAG, "BleSafetyMonitorService created")

        val database = SafeBandDatabase.getDatabase(applicationContext)
        val preferences = SafeBandPreferences(applicationContext)
        repository = SafetyRepository(
            database.safetyEventDao(),
            database.trustedContactDao(),
            preferences
        )

        createNotificationChannels()
        registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_MONITORING
        Log.i(TAG, "onStartCommand action=$action")

        if (action == ACTION_STOP_MONITORING) {
            stopMonitoring()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundWithNotification()
        startBleScanner()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        activeInstance = null
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (_: Exception) {}

        stopBleScanner()
        beaconWatchdogJob?.cancel()
        serviceScope.cancel()
        _serviceState.value = MonitoringServiceState.DISABLED
        Log.i(TAG, "BleSafetyMonitorService destroyed")
    }

    // =========================================================================
    // Foreground Service & Notification Management
    // =========================================================================

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java) ?: return

            // 1. Persistent Monitoring Channel (Low importance, non-intrusive)
            val monitoringChannel = NotificationChannel(
                CHANNEL_ID_MONITORING,
                "SafeBand Safety Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent background safety monitoring for child beacons"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(monitoringChannel)

            // 2. High-Importance Emergency Alerts Channel
            val emergencyChannel = NotificationChannel(
                CHANNEL_ID_EMERGENCY,
                "SAFEband Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Emergency SOS alerts from child wearable node"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 150, 800, 150, 1000)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(emergencyChannel)
        }
    }

    private fun startForegroundWithNotification() {
        _serviceState.value = MonitoringServiceState.STARTING

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_MONITORING)
            .setContentTitle("SafeBand Guardian")
            .setContentText("Safety monitoring active • Listening for child safety beacons")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Safety monitoring active\nListening for child safety beacons (range ~30m)"))
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+
                startForeground(
                    NOTIFICATION_ID_FOREGROUND,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID_FOREGROUND, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
            _serviceState.value = MonitoringServiceState.ERROR
        }
    }

    private fun postEmergencyNotification(incident: SafetyIncident) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_SHOW_INCIDENT
            putExtra(EXTRA_INCIDENT_ID, incident.incidentId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        // Lockscreen safe version (No child PII or medical details exposed)
        val publicNotification = NotificationCompat.Builder(this, CHANNEL_ID_EMERGENCY)
            .setContentTitle("SafeBand")
            .setContentText("SafeBand emergency alert received.")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .build()

        val emergencyNotification = NotificationCompat.Builder(this, CHANNEL_ID_EMERGENCY)
            .setContentTitle("Child SOS Alert")
            .setContentText("Emergency SOS received from physical SafeBand child node (${incident.deviceId}).")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Emergency SOS received from physical SafeBand child node (${incident.deviceId}).\n" +
                    "Tap to view incident details, signal proximity, and emergency options."
                )
            )
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 800, 150, 800, 150, 1000))
            .setAutoCancel(true)
            .setPublicVersion(publicNotification)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_EMERGENCY, emergencyNotification)
    }

    private fun clearEmergencyNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(NOTIFICATION_ID_EMERGENCY)
    }

    // =========================================================================
    // Single Scanner Lifecycle (Phase 11)
    // =========================================================================

    @SuppressLint("MissingPermission")
    private fun startBleScanner() {
        // If already ACTIVE, do not start duplicate scanner (Phase 11)
        if (_serviceState.value == MonitoringServiceState.ACTIVE && currentScanCallback != null) {
            Log.d(TAG, "Scanner is already ACTIVE; skipping duplicate start")
            return
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.w(TAG, "Bluetooth not available or disabled")
            _serviceState.value = MonitoringServiceState.ERROR
            return
        }

        // Permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasScan = ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            if (!hasScan) {
                Log.w(TAG, "Missing BLUETOOTH_SCAN permission")
                _serviceState.value = MonitoringServiceState.ERROR
                return
            }
        }

        scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "BluetoothLeScanner is null")
            _serviceState.value = MonitoringServiceState.ERROR
            return
        }

        stopBleScanner()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filters = emptyList<ScanFilter>()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                result?.let { handleScanResult(it) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                super.onBatchScanResults(results)
                results?.forEach { handleScanResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.e(TAG, "BLE scan failed with error: $errorCode")
                _serviceState.value = MonitoringServiceState.ERROR
            }
        }

        try {
            scanner?.startScan(filters, scanSettings, callback)
            currentScanCallback = callback
            _serviceState.value = MonitoringServiceState.ACTIVE
            Log.i(TAG, "BLE Guardian Scanner ACTIVE (Always-On Foreground Service)")
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting scanner in service", e)
            _serviceState.value = MonitoringServiceState.ERROR
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScanner() {
        try {
            currentScanCallback?.let {
                scanner?.stopScan(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scanner", e)
        } finally {
            currentScanCallback = null
            if (_serviceState.value == MonitoringServiceState.ACTIVE) {
                _serviceState.value = MonitoringServiceState.PAUSED
            }
        }
    }

    private fun stopMonitoring() {
        stopBleScanner()
        _serviceState.value = MonitoringServiceState.DISABLED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    // =========================================================================
    // Packet Processing & Event Deduplication (Phase 15, 20)
    // =========================================================================

    private fun handleScanResult(result: ScanResult) {
        val record = result.scanRecord ?: return

        var rawData: ByteArray? = record.getManufacturerSpecificData(BleSafetyManager.MANUFACTURER_ID)
        if (rawData == null) rawData = record.getManufacturerSpecificData(0xFE5A)
        if (rawData == null) {
            val mData = record.manufacturerSpecificData
            if (mData != null) {
                for (i in 0 until mData.size()) {
                    val candidate = mData.valueAt(i)
                    if (candidate != null && candidate.size >= 10 && candidate[0] == BleSafetyManager.MAGIC_BYTE_1 && candidate[1] == BleSafetyManager.MAGIC_BYTE_2) {
                        rawData = candidate
                        break
                    }
                }
            }
        }
        if (rawData == null) {
            val bytes = record.bytes
            if (bytes != null && bytes.size >= 12) {
                for (i in 0 until bytes.size - 9) {
                    if (bytes[i] == BleSafetyManager.MAGIC_BYTE_1 && bytes[i + 1] == BleSafetyManager.MAGIC_BYTE_2) {
                        val len = minOf(bytes.size - i, 22)
                        val extracted = ByteArray(len)
                        System.arraycopy(bytes, i, extracted, 0, len)
                        rawData = extracted
                        break
                    }
                }
            }
        }

        if (rawData == null) return

        val payload = BleSafetyManager.decodePayload(rawData) ?: return
        processBeacon(payload.copy(isSimulation = false), result.rssi)
    }

    fun processBeacon(payload: BleBeaconPayload, rssi: Int = -70) {
        _latestBeacon.value = payload

        // Update node registry
        val now = System.currentTimeMillis()
        val nodes = _discoveredNodes.value.toMutableMap()
        nodes[payload.deviceId] = SafetyNode(
            nodeId = payload.deviceId,
            ephemeralId = payload.ephemeralId,
            role = TrustRole.OWNER,
            rssi = rssi,
            lastSeenTimestamp = now,
            trustLevel = TrustRole.OWNER,
            connectionState = NodeConnectionState.NEARBY
        )
        _discoveredNodes.value = nodes

        // 1. Safe / Normal Beacon: Resolve ongoing incident if active
        if (payload.riskLevel == RiskLevel.NORMAL || payload.riskLevel == RiskLevel.LOW) {
            val currentInc = _activeEmergencyIncident.value
            if (currentInc != null && (currentInc.deviceId == payload.deviceId || currentInc.deviceId == payload.ephemeralId)) {
                beaconWatchdogJob?.cancel()
                beaconWatchdogJob = null
                _activeEmergencyIncident.value = currentInc.copy(
                    stage = IncidentStage.RESOLVED,
                    lastUpdatedTimestamp = now,
                    resolutionReason = "Child device broadcasted safe/normal status"
                )
                clearEmergencyNotification()
                serviceScope.launch {
                    repository?.logEvent(
                        flagType = "BLE_BEACON_RESOLVED",
                        riskLevel = payload.riskLevel.name,
                        outcome = "CANCELLED_BY_CHILD",
                        deviceId = payload.deviceId,
                        details = "Child device returned to safe status. Incident resolved automatically."
                    )
                }
            }
            return
        }

        // 2. Emergency Beacon (MEDIUM, HIGH, or CRITICAL)
        if (payload.riskLevel == RiskLevel.MEDIUM || payload.riskLevel == RiskLevel.HIGH || payload.riskLevel == RiskLevel.CRITICAL) {
            resetWatchdog(payload.deviceId)

            val obsId = if (payload.ephemeralId.isNotBlank()) "${payload.ephemeralId}-${payload.timestamp}" else "${payload.deviceId}-${payload.timestamp}"
            val newObs = IncidentObservation(
                observationId = obsId,
                sourceNodeId = payload.ephemeralId.ifEmpty { payload.deviceId },
                timestamp = now,
                riskLevel = payload.riskLevel,
                hopCount = payload.hopCount,
                rssi = rssi,
                syncStatus = SyncStatus.LOCAL_ONLY
            )

            val currentInc = _activeEmergencyIncident.value
            val isSameActiveIncident = currentInc != null &&
                    currentInc.stage == IncidentStage.ACTIVE &&
                    (currentInc.deviceId == payload.deviceId || currentInc.deviceId == payload.ephemeralId) &&
                    (now - currentInc.startTimestamp < 900_000L) // 15-minute incident deduplication window

            if (isSameActiveIncident) {
                // Deduplication: 100 SOS advertisements -> ONE logical incident (Phase 20)
                val updatedObservations = if (currentInc.observations.none { it.observationId == obsId }) {
                    currentInc.observations + newObs
                } else currentInc.observations

                val highestRisk = if (payload.riskLevel.ordinal > currentInc.riskLevel.ordinal) payload.riskLevel else currentInc.riskLevel

                _activeEmergencyIncident.value = currentInc.copy(
                    lastUpdatedTimestamp = now,
                    riskLevel = highestRisk,
                    observations = updatedObservations,
                    observationCount = updatedObservations.size
                )

                // Rate-limit repository logging to once every 15s or on risk level escalation
                if (payload.riskLevel.ordinal > currentInc.riskLevel.ordinal || (now - lastLoggedIncidentTime > 15_000L)) {
                    lastLoggedIncidentTime = now
                    serviceScope.launch {
                        repository?.logEvent(
                            flagType = "BLE_BEACON_RECEIVED",
                            riskLevel = payload.riskLevel.name,
                            outcome = "ALERT_RECEIVED",
                            deviceId = payload.deviceId,
                            details = "Emergency beacon updated (Observations: ${updatedObservations.size})",
                            syncStatus = SyncStatus.LOCAL_ONLY,
                            observationId = obsId,
                            hopCount = payload.hopCount
                        )
                    }
                }
            } else {
                // New Emergency Incident
                val incidentId = "INC-${payload.deviceId}-${now}"
                val triggerFlags = if (payload.riskLevel == RiskLevel.CRITICAL) setOf(AlertFlag.MANUAL_SOS) else emptySet()

                val newIncident = SafetyIncident(
                    incidentId = incidentId,
                    deviceId = payload.deviceId,
                    stage = IncidentStage.ACTIVE,
                    riskLevel = payload.riskLevel,
                    triggerFlags = triggerFlags,
                    startTimestamp = now,
                    lastUpdatedTimestamp = now,
                    latitude = if (payload.isSimulation) payload.latitude else null,
                    longitude = if (payload.isSimulation) payload.longitude else null,
                    isSimulation = payload.isSimulation,
                    observations = listOf(newObs),
                    observationCount = 1,
                    sourceNodes = listOf(payload.deviceId)
                )

                _activeEmergencyIncident.value = newIncident
                lastLoggedIncidentTime = now

                // Fire High-Priority Emergency Notification (Phase 16)
                postEmergencyNotification(newIncident)

                serviceScope.launch {
                    repository?.logEvent(
                        flagType = if (payload.riskLevel == RiskLevel.CRITICAL) "MANUAL_SOS" else "BLE_BEACON_RECEIVED",
                        riskLevel = payload.riskLevel.name,
                        outcome = "ALERT_RECEIVED",
                        deviceId = payload.deviceId,
                        details = "Emergency SOS beacon detected by background safety monitor.",
                        syncStatus = SyncStatus.LOCAL_ONLY,
                        observationId = obsId,
                        hopCount = payload.hopCount
                    )
                }
            }
        }
    }

    private fun resetWatchdog(deviceId: String) {
        beaconWatchdogJob?.cancel()
        beaconWatchdogJob = serviceScope.launch {
            delay(10_000L) // 10s of silence
            val inc = _activeEmergencyIncident.value
            if (inc != null && inc.deviceId == deviceId && inc.stage == IncidentStage.ACTIVE) {
                _activeEmergencyIncident.value = inc.copy(
                    stage = IncidentStage.RESOLVED,
                    lastUpdatedTimestamp = System.currentTimeMillis(),
                    resolutionReason = "Emergency broadcast timed out"
                )
                clearEmergencyNotification()
                repository?.logEvent(
                    flagType = "BLE_BEACON_TIMEOUT",
                    riskLevel = RiskLevel.NORMAL.name,
                    outcome = "ALERT_RESOLVED_AUTO",
                    deviceId = deviceId,
                    details = "Emergency broadcast ended. Alert cleared."
                )
            }
        }
    }
}
