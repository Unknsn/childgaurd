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
import com.example.model.RiskLevel
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

/**
 * Manages Bluetooth Low Energy (BLE) direct peer communication between Child and Parent phones.
 * Fully offline: No internet, no backend, no cloud.
 *
 * Broadcasts telemetry, child medical & bio identity, and emergency contacts
 * using multi-packet manufacturer advertising over manufacturer id 0x5AFE.
 */
class BleSafetyManager(private val context: Context) {

    companion object {
        private const val TAG = "BleSafetyManager"
        const val MANUFACTURER_ID = 0x5AFE
        const val MAGIC_BYTE_1: Byte = 0x53 // 'S'
        const val MAGIC_BYTE_2: Byte = 0x42 // 'B'
        val SAFEBAND_SERVICE_UUID: UUID = UUID.fromString("00005afe-0000-1000-8000-00805f9b34fb")

        // Packet Types (Byte 9)
        // 0..3: Legacy/Telemetry (NORMAL, LOW, MEDIUM, HIGH)
        const val TYPE_BIO_CORE: Byte = 0x10
        const val TYPE_BIO_PHONE: Byte = 0x11
        const val TYPE_BIO_MEDICAL: Byte = 0x12
        const val TYPE_EMERGENCY_CONTACT: Byte = 0x13

        val BLOOD_TYPES = listOf("O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-")

        fun encodePayload(
            deviceId: String,
            riskLevel: RiskLevel,
            lat: Double,
            lon: Double
        ): ByteArray {
            val buffer = ByteBuffer.allocate(22)
            buffer.put(MAGIC_BYTE_1)
            buffer.put(MAGIC_BYTE_2)

            val idBytes = deviceId.padEnd(7, ' ').take(7).toByteArray(Charsets.US_ASCII)
            buffer.put(idBytes)

            val riskByte: Byte = when (riskLevel) {
                RiskLevel.NORMAL -> 0
                RiskLevel.LOW -> 1
                RiskLevel.MEDIUM -> 2
                RiskLevel.HIGH -> 3
            }
            buffer.put(riskByte)

            val timeSeconds = (System.currentTimeMillis() / 1000L).toInt()
            buffer.putInt(timeSeconds)
            buffer.putFloat(lat.toFloat())
            buffer.putFloat(lon.toFloat())

            return buffer.array()
        }

        fun encodeBioCorePayload(
            deviceId: String,
            bio: ChildBioProfile?
        ): ByteArray {
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

        fun encodeBioPhonePayload(
            deviceId: String,
            phoneNumber: String?
        ): ByteArray {
            val buffer = ByteBuffer.allocate(22)
            buffer.put(MAGIC_BYTE_1)
            buffer.put(MAGIC_BYTE_2)

            val idBytes = deviceId.padEnd(7, ' ').take(7).toByteArray(Charsets.US_ASCII)
            buffer.put(idBytes)
            buffer.put(TYPE_BIO_PHONE)

            val digitsOnly = (phoneNumber ?: "").filter { it.isDigit() || it == '+' }.take(12)
            val phoneBytes = digitsOnly.toByteArray(Charsets.US_ASCII)
            val phonePadded = ByteArray(12)
            System.arraycopy(phoneBytes, 0, phonePadded, 0, minOf(phoneBytes.size, 12))
            buffer.put(phonePadded)

            return buffer.array()
        }

        fun encodeBioMedicalPayload(
            deviceId: String,
            medicalInfo: String?
        ): ByteArray {
            val buffer = ByteBuffer.allocate(22)
            buffer.put(MAGIC_BYTE_1)
            buffer.put(MAGIC_BYTE_2)

            val idBytes = deviceId.padEnd(7, ' ').take(7).toByteArray(Charsets.US_ASCII)
            buffer.put(idBytes)
            buffer.put(TYPE_BIO_MEDICAL)

            val medBytes = (medicalInfo ?: "").take(12).toByteArray(Charsets.US_ASCII)
            val medPadded = ByteArray(12)
            System.arraycopy(medBytes, 0, medPadded, 0, minOf(medBytes.size, 12))
            buffer.put(medPadded)

            return buffer.array()
        }

        fun encodeContactPayload(
            deviceId: String,
            contactPhone: String?
        ): ByteArray {
            val buffer = ByteBuffer.allocate(22)
            buffer.put(MAGIC_BYTE_1)
            buffer.put(MAGIC_BYTE_2)

            val idBytes = deviceId.padEnd(7, ' ').take(7).toByteArray(Charsets.US_ASCII)
            buffer.put(idBytes)
            buffer.put(TYPE_EMERGENCY_CONTACT)

            val digitsOnly = (contactPhone ?: "").filter { it.isDigit() || it == '+' }.take(12)
            val bytes = digitsOnly.toByteArray(Charsets.US_ASCII)
            val padded = ByteArray(12)
            System.arraycopy(bytes, 0, padded, 0, minOf(bytes.size, 12))
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
                val deviceId = String(idBytes, Charsets.US_ASCII).trim()

                val typeOrRisk = buffer.get()

                when (typeOrRisk) {
                    TYPE_BIO_CORE -> {
                        val bloodByte = buffer.get().toInt() and 0xFF
                        val bloodType = if (bloodByte in BLOOD_TYPES.indices) BLOOD_TYPES[bloodByte] else "O+"
                        val ageByte = buffer.get().toInt() and 0xFF
                        val age = if (ageByte > 0) ageByte.toString() else "8"
                        val nameBytes = ByteArray(minOf(buffer.remaining(), 10))
                        buffer.get(nameBytes)
                        val name = String(nameBytes, Charsets.UTF_8).trim().filter { it.isLetterOrDigit() || it == ' ' }
                        DecodedPacket.BioCore(deviceId, bloodType, age, name.ifEmpty { "Child" })
                    }
                    TYPE_BIO_PHONE -> {
                        val phoneBytes = ByteArray(buffer.remaining())
                        buffer.get(phoneBytes)
                        val phone = String(phoneBytes, Charsets.US_ASCII).trim()
                        DecodedPacket.BioPhone(deviceId, phone)
                    }
                    TYPE_BIO_MEDICAL -> {
                        val medBytes = ByteArray(buffer.remaining())
                        buffer.get(medBytes)
                        val med = String(medBytes, Charsets.US_ASCII).trim()
                        DecodedPacket.BioMedical(deviceId, med)
                    }
                    TYPE_EMERGENCY_CONTACT -> {
                        val contactBytes = ByteArray(buffer.remaining())
                        buffer.get(contactBytes)
                        val phone = String(contactBytes, Charsets.US_ASCII).trim()
                        DecodedPacket.EmergencyContact(deviceId, phone)
                    }
                    else -> {
                        // Standard Telemetry (riskLevel 0..3)
                        val riskLevel = when (typeOrRisk.toInt()) {
                            1 -> RiskLevel.LOW
                            2 -> RiskLevel.MEDIUM
                            3 -> RiskLevel.HIGH
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
                    longitude = packet.lon
                )
                else -> null
            }
        }
    }

    private class AggregatedChildData(
        val deviceId: String,
        var riskLevel: RiskLevel = RiskLevel.NORMAL,
        var timestamp: Long = System.currentTimeMillis(),
        var latitude: Double? = null,
        var longitude: Double? = null,
        var childName: String = "",
        var age: String = "",
        var bloodType: String = "",
        var primaryParentPhone: String = "",
        var medicalConditions: String = "",
        var allergies: String = "",
        val emergencyContacts: MutableList<TrustedContact> = mutableListOf()
    ) {
        fun toPayload(): BleBeaconPayload {
            val bio = if (childName.isNotBlank() || primaryParentPhone.isNotBlank() || bloodType.isNotBlank()) {
                ChildBioProfile(
                    childName = childName.ifEmpty { "Child ($deviceId)" },
                    age = age.ifEmpty { "8" },
                    bloodType = bloodType.ifEmpty { "O+" },
                    primaryParentPhone = primaryParentPhone,
                    secondaryContactPhone = emergencyContacts.firstOrNull { it.phoneNumber != primaryParentPhone }?.phoneNumber ?: "",
                    medicalConditions = medicalConditions.ifEmpty { "None Reported" },
                    allergies = allergies.ifEmpty { "None Reported" },
                    emergencyNotes = "Transmitted live via SafeBand BLE from child node $deviceId"
                )
            } else null

            return BleBeaconPayload(
                deviceId = deviceId,
                riskLevel = riskLevel,
                timestamp = timestamp,
                latitude = latitude,
                longitude = longitude,
                childBioProfile = bio,
                emergencyContacts = emergencyContacts.toList()
            )
        }
    }

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
    private var rotationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val childDataCache = ConcurrentHashMap<String, AggregatedChildData>()

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

        stopAdvertising() // Stop previous if any

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        val telemetryPayload = encodePayload(deviceId, riskLevel, lat, lon)
        val bioCorePayload = encodeBioCorePayload(deviceId, childBioProfile)

        val mainData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(SAFEBAND_SERVICE_UUID))
            .addManufacturerData(MANUFACTURER_ID, telemetryPayload)
            .build()

        // Include bio data in Scan Response so both telemetry and child bio arrive simultaneously!
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(MANUFACTURER_ID, bioCorePayload)
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                super.onStartSuccess(settingsInEffect)
                Log.d(TAG, "BLE advertising started successfully")
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

            // If in active emergency (MEDIUM or HIGH), rotate supplemental packets (Parent Phone, Medical, Contacts)
            if (riskLevel == RiskLevel.MEDIUM || riskLevel == RiskLevel.HIGH) {
                startRotationSequence(deviceId, riskLevel, lat, lon, childBioProfile, contacts)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission for BLE advertising", e)
            _isAdvertising.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting advertising", e)
            _isAdvertising.value = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRotationSequence(
        deviceId: String,
        riskLevel: RiskLevel,
        lat: Double,
        lon: Double,
        childBioProfile: ChildBioProfile?,
        contacts: List<TrustedContact>
    ) {
        rotationJob?.cancel()
        rotationJob = scope.launch {
            val supplementalPayloads = mutableListOf<ByteArray>()
            supplementalPayloads.add(encodeBioPhonePayload(deviceId, childBioProfile?.primaryParentPhone))
            if (!childBioProfile?.medicalConditions.isNullOrBlank()) {
                supplementalPayloads.add(encodeBioMedicalPayload(deviceId, childBioProfile?.medicalConditions))
            }
            contacts.take(2).forEach { contact ->
                supplementalPayloads.add(encodeContactPayload(deviceId, contact.phoneNumber))
            }

            var idx = 0
            while (isActive && _isAdvertising.value && supplementalPayloads.isNotEmpty()) {
                delay(600L)
                if (!isActive || !_isAdvertising.value) break

                val extraPayload = supplementalPayloads[idx % supplementalPayloads.size]
                idx++

                // Rotate scan response
                val newScanResp = AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .setIncludeTxPowerLevel(false)
                    .addManufacturerData(MANUFACTURER_ID, extraPayload)
                    .build()

                try {
                    // Update advertisement with supplemental payload
                    advertiser?.stopAdvertising(currentAdvertiseCallback ?: continue)
                    val settings = AdvertiseSettings.Builder()
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                        .setConnectable(false)
                        .setTimeout(0)
                        .build()

                    val mainData = AdvertiseData.Builder()
                        .setIncludeDeviceName(false)
                        .setIncludeTxPowerLevel(false)
                        .addServiceUuid(ParcelUuid(SAFEBAND_SERVICE_UUID))
                        .addManufacturerData(MANUFACTURER_ID, encodePayload(deviceId, riskLevel, lat, lon))
                        .build()

                    advertiser?.startAdvertising(settings, mainData, newScanResp, currentAdvertiseCallback)
                } catch (e: Exception) {
                    Log.w(TAG, "Packet rotation tick exception: ${e.message}")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        rotationJob?.cancel()
        rotationJob = null
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
            Log.w(TAG, "Bluetooth not available or enabled for scanning")
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
        val aggregated = childDataCache.getOrPut(decoded.deviceId) { AggregatedChildData(deviceId = decoded.deviceId) }

        when (decoded) {
            is DecodedPacket.Telemetry -> {
                aggregated.riskLevel = decoded.riskLevel
                aggregated.timestamp = decoded.timestamp
                if (decoded.lat != null && decoded.lon != null) {
                    aggregated.latitude = decoded.lat
                    aggregated.longitude = decoded.lon
                }
            }
            is DecodedPacket.BioCore -> {
                if (decoded.childName.isNotBlank()) aggregated.childName = decoded.childName
                if (decoded.age.isNotBlank()) aggregated.age = decoded.age
                if (decoded.bloodType.isNotBlank()) aggregated.bloodType = decoded.bloodType
            }
            is DecodedPacket.BioPhone -> {
                if (decoded.phone.isNotBlank()) aggregated.primaryParentPhone = decoded.phone
            }
            is DecodedPacket.BioMedical -> {
                if (decoded.medicalInfo.isNotBlank()) aggregated.medicalConditions = decoded.medicalInfo
            }
            is DecodedPacket.EmergencyContact -> {
                if (decoded.phone.isNotBlank() && aggregated.emergencyContacts.none { it.phoneNumber == decoded.phone }) {
                    aggregated.emergencyContacts.add(
                        TrustedContact(
                            name = "Child's Contact",
                            phoneNumber = decoded.phone,
                            relationship = "Transmitted by Child"
                        )
                    )
                }
            }
        }

        val payload = aggregated.toPayload()
        _lastReceivedBeacon.value = payload
        onBeaconFound(payload)
    }

    /**
     * Manual injection of simulated beacon for single device testing / demo.
     */
    fun injectSimulatedBeacon(payload: BleBeaconPayload, onBeaconFound: (BleBeaconPayload) -> Unit) {
        _lastReceivedBeacon.value = payload
        onBeaconFound(payload)
    }
}
