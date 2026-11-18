package com.example.core.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.core.security.SafeBandSecurityManager
import com.example.data.local.PairedChildDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

sealed class TamperMaintenanceState {
    object Idle : TamperMaintenanceState()
    object Transmitting : TamperMaintenanceState()
    data class Active(val remainingSeconds: Long) : TamperMaintenanceState()
    object Cleared : TamperMaintenanceState()
    data class Failed(val error: String) : TamperMaintenanceState()
}

/**
 * BleTamperMaintenanceManager
 *
 * Implements Feature 3: Parent-Authorized Strap Maintenance Mode for ESP32-S3 Wearable Node.
 * Transmits maintenance authorization to characteristic 00005afe-0003-... to prevent
 * false tamper emergency escalation during authorized strap adjustment or battery service.
 */
class BleTamperMaintenanceManager(
    private val context: Context,
    private val pairedChildDao: PairedChildDao,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "BleTamperMaintMgr"
        val SAFEBAND_SERVICE_UUID: UUID = UUID.fromString("00005afe-0000-1000-8000-00805f9b34fb")
        val MAINTENANCE_CHAR_UUID: UUID = UUID.fromString("00005afe-0003-1000-8000-00805f9b34fb")
        val TAMPER_CHAR_UUID: UUID = UUID.fromString("00005afe-0004-1000-8000-00805f9b34fb")

        @Volatile
        private var sequenceCounter: Long = 1000L

        fun getNextSequence(): Long = synchronized(this) { ++sequenceCounter }

        fun buildMaintenanceCommand(
            durationSeconds: Long,
            authToken: String,
            seq: Long = getNextSequence()
        ): String {
            val cleanDuration = durationSeconds.coerceIn(10L, 3600L)
            val cleanToken = authToken.trim().ifEmpty { "DEFAULT" }
            val hmac = SafeBandSecurityManager.computeHmac("$cleanDuration:$seq", cleanToken).take(16)
            return "MAINT:AUTH:$cleanDuration:$cleanToken:$seq:$hmac"
        }

        fun buildCancelCommand(): String = "MAINT:CANCEL"

        fun parseAckResponse(response: String): Boolean {
            return response.trim().startsWith("ACK:MAINT")
        }
    }

    private val _state = MutableStateFlow<TamperMaintenanceState>(TamperMaintenanceState.Idle)
    val state: StateFlow<TamperMaintenanceState> = _state.asStateFlow()

    private var activeGatt: BluetoothGatt? = null

    @SuppressLint("MissingPermission")
    suspend fun authorizeMaintenance(
        deviceAddress: String,
        childId: String,
        durationSeconds: Long,
        authToken: String
    ): Boolean {
        _state.value = TamperMaintenanceState.Transmitting
        val command = buildMaintenanceCommand(durationSeconds, authToken)

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _state.value = TamperMaintenanceState.Failed("Bluetooth is disabled")
            return false
        }

        val device: BluetoothDevice = try {
            bluetoothAdapter.getRemoteDevice(deviceAddress)
        } catch (e: Exception) {
            _state.value = TamperMaintenanceState.Failed("Invalid device address: $deviceAddress")
            return false
        }

        var writeSuccess = false

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "Connected to ESP32 for maintenance authorization. Discovering services...")
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    gatt.close()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(SAFEBAND_SERVICE_UUID)
                    val characteristic = service?.getCharacteristic(MAINTENANCE_CHAR_UUID)
                    if (characteristic != null) {
                        val payloadBytes = command.toByteArray(Charsets.UTF_8)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeCharacteristic(
                                characteristic,
                                payloadBytes,
                                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            characteristic.value = payloadBytes
                            @Suppress("DEPRECATION")
                            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            @Suppress("DEPRECATION")
                            gatt.writeCharacteristic(characteristic)
                        }
                    } else {
                        _state.value = TamperMaintenanceState.Failed("Maintenance characteristic not found")
                        gatt.disconnect()
                    }
                } else {
                    _state.value = TamperMaintenanceState.Failed("Service discovery failed with code $status")
                    gatt.disconnect()
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i(TAG, "Maintenance command written successfully to node.")
                    writeSuccess = true
                    _state.value = TamperMaintenanceState.Active(durationSeconds)

                    coroutineScope.launch {
                        try {
                            val expiry = System.currentTimeMillis() + (durationSeconds * 1000L)
                            pairedChildDao.updateTamperStatus(
                                deviceId = childId,
                                status = "AUTHORIZED_DISCONNECTION",
                                authorizedUntil = expiry
                            )
                            Log.i(TAG, "Room Database updated with maintenance status for child $childId")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to update tamper status in Room DB", e)
                        } finally {
                            gatt.disconnect()
                        }
                    }
                } else {
                    _state.value = TamperMaintenanceState.Failed("Characteristic write failed with code $status")
                    gatt.disconnect()
                }
            }
        }

        try {
            activeGatt = device.connectGatt(context, false, gattCallback)
        } catch (e: Exception) {
            _state.value = TamperMaintenanceState.Failed("Connection error: ${e.message}")
            return false
        }

        val completed = withTimeoutOrNull(15000L) {
            while (_state.value !is TamperMaintenanceState.Active && _state.value !is TamperMaintenanceState.Failed) {
                kotlinx.coroutines.delay(200L)
            }
            _state.value is TamperMaintenanceState.Active
        } ?: false

        return completed
    }

    @SuppressLint("MissingPermission")
    suspend fun cancelMaintenance(deviceAddress: String, childId: String): Boolean {
        _state.value = TamperMaintenanceState.Transmitting
        val command = buildCancelCommand()

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _state.value = TamperMaintenanceState.Failed("Bluetooth is disabled")
            return false
        }

        val device: BluetoothDevice = try {
            bluetoothAdapter.getRemoteDevice(deviceAddress)
        } catch (e: Exception) {
            _state.value = TamperMaintenanceState.Failed("Invalid device address")
            return false
        }

        var writeSuccess = false

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    gatt.close()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(SAFEBAND_SERVICE_UUID)
                    val characteristic = service?.getCharacteristic(MAINTENANCE_CHAR_UUID)
                    if (characteristic != null) {
                        val payloadBytes = command.toByteArray(Charsets.UTF_8)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeCharacteristic(
                                characteristic,
                                payloadBytes,
                                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            characteristic.value = payloadBytes
                            @Suppress("DEPRECATION")
                            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            @Suppress("DEPRECATION")
                            gatt.writeCharacteristic(characteristic)
                        }
                    } else {
                        gatt.disconnect()
                    }
                } else {
                    gatt.disconnect()
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    writeSuccess = true
                    _state.value = TamperMaintenanceState.Cleared
                    coroutineScope.launch {
                        try {
                            pairedChildDao.updateTamperStatus(
                                deviceId = childId,
                                status = "PROTECTED",
                                authorizedUntil = 0L
                            )
                        } finally {
                            gatt.disconnect()
                        }
                    }
                } else {
                    gatt.disconnect()
                }
            }
        }

        try {
            activeGatt = device.connectGatt(context, false, gattCallback)
        } catch (e: Exception) {
            return false
        }

        val completed = withTimeoutOrNull(10000L) {
            while (_state.value !is TamperMaintenanceState.Cleared && _state.value !is TamperMaintenanceState.Failed) {
                kotlinx.coroutines.delay(200L)
            }
            _state.value is TamperMaintenanceState.Cleared
        } ?: false

        return completed
    }

    @SuppressLint("MissingPermission")
    fun reset() {
        _state.value = TamperMaintenanceState.Idle
        try {
            activeGatt?.close()
        } catch (ignored: Exception) {}
        activeGatt = null
    }
}
