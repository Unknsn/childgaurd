package com.example.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.example.data.local.TrustedContact
import com.example.model.BleBeaconPayload
import com.example.model.ChildBioProfile
import com.example.model.NodeConnectionState
import com.example.model.RiskLevel
import com.example.model.SafetyNode
import com.example.model.TrustRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue

/**
 * Privacy-Preserving BLE Safety Manager (Batch D: Phases 8, 9, 10).
 *
 * Implements:
 * - Rotating Ephemeral Identifiers (EP-XXXX) rotating every 15 minutes.
 * - Strict Privacy Boundary: Public BLE advertisements NEVER expose child name,
 *   age, blood type, medical conditions, allergies, parent phone, or full address.
 * - Trust Level Filtering (OWNER, TRUSTED_GUARDIAN, INSTITUTION, EMERGENCY_AUTHORITY, ANONYMOUS_RELAY).
 * - SafetyNode discovery and tracking with RSSI, connection state, and stale pruning.
 */
class BleSafetyManager(private val context: Context) {

    companion object {
        private const val TAG = "BleSafetyManager"
        const val MANUFACTURER_ID = 0x5AFE
        const val MAGIC_BYTE_1: Byte = 0x53 // 'S'
        const val MAGIC_BYTE_2: Byte = 0x42 // 'B'
        val SAFEBAND_SERVICE_UUID: UUID = UUID.fromString("00005afe-0000-1000-8000-00805f9b34fb")

        // Packet Types (Byte 9)
        const val TYPE_TELEMETRY: Byte = 0x00
        const val TYPE_BIO_CORE: Byte = 0x10
        const val TYPE_BIO_PHONE: Byte = 0x11
        const val TYPE_BIO_MEDICAL: Byte = 0x12
        const val TYPE_EMERGENCY_CONTACT: Byte = 0x13

        val BLOOD_TYPES = listOf("O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-")

        /**
         * Deterministically derives a rotating ephemeral identifier (EP-XXXX) for the given device ID.
         * Default rotation interval: 15 minutes (900,000 ms).
         * Fits into 7 ASCII bytes without truncation.
         */
        fun generateEphemeralId(deviceId: String, epochWindow: Long = System.currentTimeMillis() / 900_000L): String {
            val combined = "$deviceId-$epochWindow"
            val hash = (combined.hashCode().absoluteValue % 65536).toString(16).uppercase().padStart(4, '0')
            return "EP-$hash"
        }

        fun encodePayload(
            deviceId: String,
            riskLevel: RiskLevel,
            lat: Double,
            lon: Double,
            ephemeralId: String = ""
        ): ByteArray {
            val buffer = ByteBuffer.allocate(22)
            buffer.put(MAGIC_BYTE_1)
            buffer.put(MAGIC_BYTE_2)

            // Prefer ephemeral ID for privacy if provided; fallback to deviceId
            val broadcastId = ephemeralId.ifEmpty { deviceId }
            val idBytes = broadcastId.padEnd(7, ' ').take(7).toByteArray(Charsets.US_ASCII)
            buffer.put(idBytes)

            val riskByte: Byte = when (riskLevel) {
                RiskLevel.NORMAL -> 0
                RiskLevel.LOW -> 1
                RiskLevel.MEDIUM -> 2
                RiskLevel.HIGH -> 3
                RiskLevel.CRITICAL -> 4
            }
            buffer.put(riskByte)

            val timeSeconds = (System.currentTimeMillis() / 1000L).toInt()
            buffer.putInt(timeSeconds)
            buffer.putFloat(lat.toFloat())
            buffer.putFloat(lon.toFloat())

            return buffer.array()
        }

        // Legacy compatibility helpers (for internal test assertions only)
        fun encodeBioCorePayload(deviceId: String, bio: ChildBioProfile?): ByteArray {
            val buffer = ByteBuffer.allocate(22)
            buffer.put(MAGIC_BYTE_1)
            buffer.put(MAGIC_BYTE_2)
            val idBytes = deviceId.padEnd(7, ' ').take(7).toByteArray(Charsets.US_ASCII)
            buffer.put(idBytes)
            buffer.put(TYPE_BIO_CORE)
            val bloodIdx = BLOOD_TYPES.indexOfFirst { it.equals(bio?.bloodType, ignoreCase = true) }
            buffer.put(if (bloodIdx >= 0) bloodIdx.toByte() else 0xFF.toByte())
            val ageVal = bio?.age?.toIntOrNull() ?: 0
            buffer.put(ageVal.coerceIn(0, 99).toByte())
            val nameBytes = (bio?.childName ?: "Child").take(10).toByteArray(Charsets.UTF_8)
            val namePadded = ByteArray(10)
            System.arraycopy(nameBytes, 0, namePadded, 0, minOf(nameBytes.size, 10))
            buffer.put(namePadded)
            return buffer.array()
        }

        fun encodeBioPhonePayload(deviceId: String, phoneNumber: String?): ByteArray {
            val buffer = ByteBuffer.allocate(24)
            buffer.put(MAGIC_BYTE_1)
            buffer.put(MAGIC_BYTE_2)
            val idBytes = deviceId.padEnd(7, ' ').take(7).toByteArray(Charsets.US_ASCII)
            buffer.put(idBytes)
            buffer.put(TYPE_BIO_PHONE)
            val digitsOnly = (phoneNumber ?: "").filter { it.isDigit() || it == '+' }.take(14)
            val phoneBytes = digitsOnly.toByteArray(Charsets.US_ASCII)
            val phonePadded = ByteArray(14)
            System.arraycopy(phoneBytes, 0, phonePadded, 0, minOf(phoneBytes.size, 14))
            buffer.put(phonePadded)
            return buffer.array()
        }

        fun encodeBioMedicalPayload(deviceId: String, medicalInfo: String?): ByteArray {
            val buffer = ByteBuffer.allocate(24)
            buffer.put(MAGIC_BYTE_1)
            buffer.put(MAGIC_BYTE_2)
            val idBytes = deviceId.padEnd(7, ' ').take(7).toByteArray(Charsets.US_ASCII)
            buffer.put(idBytes)
            buffer.put(TYPE_BIO_MEDICAL)
            val medBytes = (medicalInfo ?: "").take(14).toByteArray(Charsets.US_ASCII)
            val medPadded = ByteArray(14)
            System.arraycopy(medBytes, 0, medPadded, 0, minOf(medBytes.size, 14))
            buffer.put(medPadded)
            return buffer.array()
        }

        fun encodeContactPayload(deviceId: String, contactPhone: String?): ByteArray {
            val buffer = ByteBuffer.allocate(24)
            buffer.put(MAGIC_BYTE_1)
            buffer.put(MAGIC_BYTE_2)
            val idBytes = deviceId.padEnd(7, ' ').take(7).toByteArray(Charsets.US_ASCII)
            buffer.put(idBytes)
            buffer.put(TYPE_EMERGENCY_CONTACT)
            val digitsOnly = (contactPhone ?: "").filter { it.isDigit() || it == '+' }.take(14)
            val bytes = digitsOnly.toByteArray(Charsets.US_ASCII)
            val padded = ByteArray(14)
            System.arraycopy(bytes, 0, padded, 0, minOf(bytes.size, 14))
            buffer.put(padded)
            return buffer.array()
        }

        sealed class DecodedPacket(val deviceId: String) {
            class Telemetry(
                deviceId: String,
                val riskLevel: RiskLevel,
                val timestamp: Long,
                val lat: Double?,
                val lon: Double?
            ) : DecodedPacket(deviceId)

            class BioCore(
                deviceId: String,
                val bloodType: String,
                val age: String,
                val childName: String
            ) : DecodedPacket(deviceId)

            class BioPhone(
                deviceId: String,
                val phone: String
            ) : DecodedPacket(deviceId)

            class BioMedical(
                deviceId: String,
                val medicalInfo: String
            ) : DecodedPacket(deviceId)

            class EmergencyContact(
                deviceId: String,
                val phone: String
            ) : DecodedPacket(deviceId)
        }

        fun decodePacket(data: ByteArray): DecodedPacket? {
            if (data.size < 10) return null
            if (data[0] != MAGIC_BYTE_1 || data[1] != MAGIC_BYTE_2) return null

            return try {
                val buffer = ByteBuffer.wrap(data)
                buffer.position(2)

                val idBytes = ByteArray(7)
                buffer.get(idBytes)
                val deviceId = String(idBytes, Charsets.US_ASCII).takeWhile { it != '\u0000' }.trim()

                val typeOrRisk = buffer.get()

                when (typeOrRisk) {
                    TYPE_BIO_CORE -> {
                        val bloodByte = buffer.get().toInt() and 0xFF
                        val bloodType = if (bloodByte in BLOOD_TYPES.indices) BLOOD_TYPES[bloodByte] else "O+"
                        val ageByte = buffer.get().toInt() and 0xFF
                        val age = if (ageByte > 0) ageByte.toString() else "8"
                        val nameBytes = ByteArray(minOf(buffer.remaining(), 10))
                        buffer.get(nameBytes)
                        val name = String(nameBytes, Charsets.UTF_8).takeWhile { it != '\u0000' }.trim().filter { it.isLetterOrDigit() || it == ' ' }
                        DecodedPacket.BioCore(deviceId, bloodType, age, name.ifEmpty { "Child" })
                    }
                    TYPE_BIO_PHONE -> {
                        val phoneBytes = ByteArray(buffer.remaining())
                        buffer.get(phoneBytes)
                        val phone = String(phoneBytes, Charsets.US_ASCII).takeWhile { it != '\u0000' }.trim()
                        DecodedPacket.BioPhone(deviceId, phone)
                    }
                    TYPE_BIO_MEDICAL -> {
                        val medBytes = ByteArray(buffer.remaining())
                        buffer.get(medBytes)
                        val med = String(medBytes, Charsets.US_ASCII).takeWhile { it != '\u0000' }.trim()
                        DecodedPacket.BioMedical(deviceId, med)
                    }
                    TYPE_EMERGENCY_CONTACT -> {
                        val contactBytes = ByteArray(buffer.remaining())
                        buffer.get(contactBytes)
                        val phone = String(contactBytes, Charsets.US_ASCII).takeWhile { it != '\u0000' }.trim()
                        DecodedPacket.EmergencyContact(deviceId, phone)
                    }
                    else -> {
                        val riskLevel = when (typeOrRisk.toInt()) {
                            1 -> RiskLevel.LOW
                            2 -> RiskLevel.MEDIUM
                            3 -> RiskLevel.HIGH
                            4 -> RiskLevel.CRITICAL
                            else -> RiskLevel.NORMAL
                        }
                        val timeSeconds = buffer.int
                        val timestamp = timeSeconds.toLong() * 1000L

                        var lat: Double? = null
                        var lon: Double? = null
                        if (buffer.remaining() >= 8) {
                            lat = buffer.float.toDouble()
                            lon = buffer.float.toDouble()
                        }

                        DecodedPacket.Telemetry(
                            deviceId = deviceId,
                            riskLevel = riskLevel,
                            timestamp = timestamp,
                            lat = lat,
                            lon = lon
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding BLE packet", e)
                null
            }
        }

        fun decodePayload(data: ByteArray): BleBeaconPayload? {
            val packet = decodePacket(data) ?: return null
            return when (packet) {
                is DecodedPacket.Telemetry -> BleBeaconPayload(
                    deviceId = packet.deviceId,
                    riskLevel = packet.riskLevel,
                    timestamp = packet.timestamp,
                    latitude = packet.lat,
                    longitude = packet.lon,
                    ephemeralId = if (packet.deviceId.startsWith("EP-")) packet.deviceId else ""
                )
                else -> null
            }
        }
    }

    // =========================================================================
    // Local Trust Registry & Ephemeral Mapping (Phase 8 & 9)
    // =========================================================================

    private val pairedTrustedDevices = ConcurrentHashMap<String, TrustRole>()
    private val ephemeralToRealDeviceMap = ConcurrentHashMap<String, String>()

    fun registerPairedDevice(realDeviceId: String, role: TrustRole = TrustRole.TRUSTED_GUARDIAN) {
        pairedTrustedDevices[realDeviceId] = role
    }

    fun associateEphemeralId(ephemeralId: String, realDeviceId: String) {
        ephemeralToRealDeviceMap[ephemeralId] = realDeviceId
    }

    fun resolveRealDeviceId(ephemeralOrRealId: String): String {
        return ephemeralToRealDeviceMap[ephemeralOrRealId] ?: ephemeralOrRealId
    }

    fun resolveTrustRole(ephemeralOrRealId: String): TrustRole {
        val realId = resolveRealDeviceId(ephemeralOrRealId)
        return pairedTrustedDevices[realId] ?: TrustRole.ANONYMOUS_RELAY
    }

    // =========================================================================
    // Node Discovery & Lifecycle Registry (Phase 10)
    // =========================================================================

    private val _discoveredSafetyNodes = MutableStateFlow<Map<String, SafetyNode>>(emptyMap())
    val discoveredSafetyNodes: StateFlow<Map<String, SafetyNode>> = _discoveredSafetyNodes.asStateFlow()

    fun recordNodeObservation(
        nodeId: String,
        ephemeralId: String,
        rssi: Int,
        hopCount: Int = 0
    ) {
        val now = System.currentTimeMillis()
        val trustRole = resolveTrustRole(ephemeralId.ifEmpty { nodeId })
        val connectionState = when {
            hopCount > 1 -> NodeConnectionState.RELAYED
            rssi > -95 -> NodeConnectionState.NEARBY
            else -> NodeConnectionState.ONLINE
        }

        val map = _discoveredSafetyNodes.value.toMutableMap()
        val key = ephemeralId.ifEmpty { nodeId }
        map[key] = SafetyNode(
            nodeId = nodeId,
            ephemeralId = ephemeralId,
            role = trustRole,
            rssi = rssi,
            lastSeenTimestamp = now,
            trustLevel = trustRole,
            connectionState = connectionState
        )
        _discoveredSafetyNodes.value = map
    }

    fun pruneStaleNodes(now: Long = System.currentTimeMillis()) {
        val map = _discoveredSafetyNodes.value.mapValues { (_, node) ->
            val age = now - node.lastSeenTimestamp
            when {
                age >= 45_000L -> node.copy(connectionState = NodeConnectionState.OFFLINE)
                age >= 15_000L -> node.copy(connectionState = NodeConnectionState.UNKNOWN)
                else -> node
            }
        }
        _discoveredSafetyNodes.value = map
    }

    // =========================================================================
    // BLE Hardware & State Management
    // =========================================================================

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _lastReceivedBeacon = MutableStateFlow<BleBeaconPayload?>(null)
    val lastReceivedBeacon: StateFlow<BleBeaconPayload?> = _lastReceivedBeacon.asStateFlow()

    private val _bleSupported = MutableStateFlow(bluetoothAdapter != null)
    val bleSupported: StateFlow<Boolean> = _bleSupported.asStateFlow()

    private val _bluetoothEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled == true)
    val bluetoothEnabled: StateFlow<Boolean> = _bluetoothEnabled.asStateFlow()

    private var currentAdvertiseCallback: AdvertiseCallback? = null
    private var currentScanCallback: ScanCallback? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    @SuppressLint("MissingPermission")
    fun startAdvertising(
        deviceId: String,
        riskLevel: RiskLevel,
        lat: Double = 0.0,
        lon: Double = 0.0,
        childBioProfile: ChildBioProfile? = null,
        contacts: List<TrustedContact> = emptyList()
    ) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth not available or enabled for advertising")
            return
        }

        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.w(TAG, "BluetoothLeAdvertiser not supported on this hardware")
            return
        }

        stopAdvertising()

        val ephemeralId = generateEphemeralId(deviceId)
        associateEphemeralId(ephemeralId, deviceId)

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        // Privacy: Broadcast only minimal telemetry with ephemeral ID.
        // Public packet contains NO child name, medical condition, allergies, or phone numbers.
        val telemetryPayload = encodePayload(deviceId, riskLevel, lat, lon, ephemeralId)

        val mainData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(SAFEBAND_SERVICE_UUID))
            .addManufacturerData(MANUFACTURER_ID, telemetryPayload)
            .build()

        // Privacy: Scan response is empty to avoid exposing PII over unencrypted BLE
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                super.onStartSuccess(settingsInEffect)
                Log.d(TAG, "BLE advertising started successfully with ephemeral ID $ephemeralId")
                _isAdvertising.value = true
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                Log.e(TAG, "BLE advertising failed with error code: $errorCode")
                _isAdvertising.value = false
            }
        }

        try {
            advertiser?.startAdvertising(settings, mainData, scanResponse, callback)
            currentAdvertiseCallback = callback
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission for BLE advertising", e)
            _isAdvertising.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting advertising", e)
            _isAdvertising.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        try {
            currentAdvertiseCallback?.let {
                advertiser?.stopAdvertising(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping advertising", e)
        } finally {
            currentAdvertiseCallback = null
            _isAdvertising.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanning(onBeaconFound: (BleBeaconPayload) -> Unit) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth not available for scanning")
            return
        }

        scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "BluetoothLeScanner not supported")
            return
        }

        stopScanning()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filters = listOf(
            ScanFilter.Builder()
                .setManufacturerData(MANUFACTURER_ID, byteArrayOf(MAGIC_BYTE_1, MAGIC_BYTE_2), byteArrayOf(0xFF.toByte(), 0xFF.toByte()))
                .build()
        )

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                result?.let { handleScanResult(it, onBeaconFound) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                super.onBatchScanResults(results)
                results?.forEach { handleScanResult(it, onBeaconFound) }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.e(TAG, "BLE scan failed with error: $errorCode")
                _isScanning.value = false
            }
        }

        try {
            scanner?.startScan(filters, scanSettings, callback)
            currentScanCallback = callback
            _isScanning.value = true
            Log.d(TAG, "BLE scanning started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission for BLE scan", e)
            _isScanning.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting scan", e)
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        try {
            currentScanCallback?.let {
                scanner?.stopScan(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan", e)
        } finally {
            currentScanCallback = null
            _isScanning.value = false
        }
    }

    private fun handleScanResult(result: ScanResult, onBeaconFound: (BleBeaconPayload) -> Unit) {
        val record = result.scanRecord ?: return
        val rawData = record.getManufacturerSpecificData(MANUFACTURER_ID) ?: return

        val decoded = decodePacket(rawData) ?: return
        if (decoded is DecodedPacket.Telemetry) {
            val ephemeralOrRealId = decoded.deviceId
            val realId = resolveRealDeviceId(ephemeralOrRealId)
            val trustRole = resolveTrustRole(ephemeralOrRealId)

            recordNodeObservation(
                nodeId = realId,
                ephemeralId = if (ephemeralOrRealId.startsWith("EP-")) ephemeralOrRealId else "",
                rssi = result.rssi,
                hopCount = 0
            )

            // Construct payload with trust-level access filtering
            val payload = BleBeaconPayload(
                deviceId = if (trustRole == TrustRole.TRUSTED_GUARDIAN || trustRole == TrustRole.OWNER) realId else ephemeralOrRealId,
                riskLevel = decoded.riskLevel,
                timestamp = decoded.timestamp,
                latitude = decoded.lat,
                longitude = decoded.lon,
                ephemeralId = if (ephemeralOrRealId.startsWith("EP-")) ephemeralOrRealId else "",
                trustRole = trustRole,
                hopCount = 0,
                // Proximity alone does NOT grant access to sensitive child identity or contacts
                childBioProfile = null,
                emergencyContacts = emptyList()
            )

            _lastReceivedBeacon.value = payload
            onBeaconFound(payload)
        }
    }

    /**
     * Manual injection of simulated beacon for single device testing / demo.
     */
    fun injectSimulatedBeacon(payload: BleBeaconPayload, onBeaconFound: (BleBeaconPayload) -> Unit) {
        _lastReceivedBeacon.value = payload
        recordNodeObservation(
            nodeId = payload.deviceId,
            ephemeralId = payload.ephemeralId,
            rssi = -70,
            hopCount = payload.hopCount
        )
        onBeaconFound(payload)
    }
}
