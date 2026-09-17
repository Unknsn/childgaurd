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
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.example.model.BleBeaconPayload
import com.example.model.RiskLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Manages Bluetooth Low Energy (BLE) direct peer communication between Child and Parent phones.
 * No internet, no backend, no cloud.
 *
 * Payload is broadcast via Manufacturer Data using manufacturer id 0x5AFE.
 */
class BleSafetyManager(private val context: Context) {

    companion object {
        private const val TAG = "BleSafetyManager"
        const val MANUFACTURER_ID = 0x5AFE
        const val MAGIC_BYTE_1: Byte = 0x53 // 'S'
        const val MAGIC_BYTE_2: Byte = 0x42 // 'B'
        val SAFEBAND_SERVICE_UUID: UUID = UUID.fromString("00005afe-0000-1000-8000-00805f9b34fb")

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

        fun decodePayload(data: ByteArray): BleBeaconPayload? {
            if (data.size < 14) return null
            if (data[0] != MAGIC_BYTE_1 || data[1] != MAGIC_BYTE_2) return null

            return try {
                val buffer = ByteBuffer.wrap(data)
                buffer.position(2)

                val idBytes = ByteArray(7)
                buffer.get(idBytes)
                val deviceId = String(idBytes, Charsets.US_ASCII).trim()

                val riskByte = buffer.get().toInt()
                val riskLevel = when (riskByte) {
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

                BleBeaconPayload(
                    deviceId = deviceId,
                    riskLevel = riskLevel,
                    timestamp = timestamp,
                    latitude = lat,
                    longitude = lon
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding BLE payload", e)
                null
            }
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

    @SuppressLint("MissingPermission")
    fun startAdvertising(
        deviceId: String,
        riskLevel: RiskLevel,
        lat: Double = 0.0,
        lon: Double = 0.0
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

        val payload = encodePayload(deviceId, riskLevel, lat, lon)

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(SAFEBAND_SERVICE_UUID))
            .addManufacturerData(MANUFACTURER_ID, payload)
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
            advertiser?.startAdvertising(settings, data, callback)
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

        decodePayload(rawData)?.let { payload ->
            _lastReceivedBeacon.value = payload
            onBeaconFound(payload)
        }
    }

    /**
     * Manual injection of simulated beacon for single device testing / demo.
     */
    fun injectSimulatedBeacon(payload: BleBeaconPayload, onBeaconFound: (BleBeaconPayload) -> Unit) {
        _lastReceivedBeacon.value = payload
        onBeaconFound(payload)
    }
}
