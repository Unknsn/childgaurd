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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

sealed class BleProvisioningState {
    object Idle : BleProvisioningState()
    object Connecting : BleProvisioningState()
    object ServicesDiscovered : BleProvisioningState()
    object WritingConfig : BleProvisioningState()
    data class Success(val version: Int) : BleProvisioningState()
    data class Failed(val reason: String) : BleProvisioningState()
}

/**
 * BleProvisioningManager
 *
 * Implements Feature 1: Secure BLE provisioning of Child ESP32-S3 Wearable Node.
 * Transmits child identifier, parent identifier, parent node network endpoint (IP:Port),
 * endpoint ID, and authentication token over GATT Characteristic 00005afe-0002-...
 */
class BleProvisioningManager(
    private val context: Context,
    private val pairedChildDao: PairedChildDao,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "BleProvisioningMgr"
        val SAFEBAND_SERVICE_UUID: UUID = UUID.fromString("00005afe-0000-1000-8000-00805f9b34fb")
        val PROVISION_CHAR_UUID: UUID = UUID.fromString("00005afe-0002-1000-8000-00805f9b34fb")

        fun buildProvisioningPayload(
            childId: String,
            parentId: String,
            parentNodeIp: String,
            parentNodePort: Int,
            endpointId: String,
            authToken: String,
            version: Int = 1,
            timestampSeconds: Long = System.currentTimeMillis() / 1000L,
            childName: String? = null,
            childAge: Int? = null,
            bloodGroup: String? = null,
            emergencyPhone: String? = null
        ): String {
            require(childId.isNotBlank()) { "Child ID cannot be blank" }
            require(parentId.isNotBlank()) { "Parent ID cannot be blank" }
            val cleanIp = parentNodeIp.trim().ifEmpty { "127.0.0.1" }
            val cleanPort = if (parentNodePort in 1..65535) parentNodePort else 8080
            val cleanEndpointId = endpointId.trim().ifEmpty { "PN-DEFAULT" }
            val cleanToken = authToken.trim().ifEmpty { "TOKEN-DEFAULT" }
            val hmac = SafeBandSecurityManager.computeHmac(
                "$childId:$parentId:$cleanIp:$cleanPort:$cleanEndpointId:$version:$timestampSeconds",
                cleanToken
            ).take(16)

            val base = "PROV:$childId|$parentId|$cleanIp|$cleanPort|$cleanEndpointId|$cleanToken|$version|$timestampSeconds|$hmac"
            return if (childName != null || childAge != null || bloodGroup != null || emergencyPhone != null) {
                val cleanName = childName?.trim()?.ifEmpty { "Child" } ?: "Child"
                val cleanAge = childAge ?: 8
                val cleanBlood = bloodGroup?.trim()?.ifEmpty { "O+" } ?: "O+"
                val cleanPhone = emergencyPhone?.trim()?.ifEmpty { "+91 98765 43210" } ?: "+91 98765 43210"
                "$base|$cleanName|$cleanAge|$cleanBlood|$cleanPhone"
            } else {
                base
            }
        }

        fun buildChildProfilePayload(
            name: String,
            age: Int,
            bloodGroup: String,
            emergencyPhone: String
        ): String {
            val cleanName = name.trim().ifEmpty { "Child" }
            val cleanBlood = bloodGroup.trim().ifEmpty { "O+" }
            val cleanPhone = emergencyPhone.trim().ifEmpty { "+91 98765 43210" }
            return "CHILD:$cleanName|$age|$cleanBlood|$cleanPhone"
        }

        fun parseAckResponse(response: String): Boolean {
            return response.trim().startsWith("ACK:PROV:OK") || response.trim().startsWith("ACK:CHILD:OK")
        }
    }

    private val _state = MutableStateFlow<BleProvisioningState>(BleProvisioningState.Idle)
    val state: StateFlow<BleProvisioningState> = _state.asStateFlow()

    private var activeGatt: BluetoothGatt? = null

    /**
     * Executes provisioning workflow to ESP32 node.
     */
    @SuppressLint("MissingPermission")
    suspend fun provisionChildNode(
        deviceAddress: String,
        childId: String,
        parentId: String,
        parentNodeIp: String,
        parentNodePort: Int,
        parentNodeEndpointId: String,
        authToken: String,
        version: Int = 1,
        childName: String = "Demo Child",
        childAge: Int = 8,
        bloodGroup: String = "O+",
        emergencyPhone: String = "+91 98765 43210"
    ): Boolean {
        _state.value = BleProvisioningState.Connecting
        val payload = buildProvisioningPayload(
            childId = childId,
            parentId = parentId,
            parentNodeIp = parentNodeIp,
            parentNodePort = parentNodePort,
            endpointId = parentNodeEndpointId,
            authToken = authToken,
            version = version,
            childName = childName,
            childAge = childAge,
            bloodGroup = bloodGroup,
            emergencyPhone = emergencyPhone
        )

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _state.value = BleProvisioningState.Failed("Bluetooth is disabled")
            return false
        }

        val device: BluetoothDevice = try {
            bluetoothAdapter.getRemoteDevice(deviceAddress)
        } catch (e: Exception) {
            _state.value = BleProvisioningState.Failed("Invalid device address: $deviceAddress")
            return false
        }

        var provisionSuccess = false

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "Connected to ESP32 wearable. Discovering services...")
                    _state.value = BleProvisioningState.ServicesDiscovered
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "Disconnected from ESP32 wearable.")
                    gatt.close()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(SAFEBAND_SERVICE_UUID)
                    val characteristic = service?.getCharacteristic(PROVISION_CHAR_UUID)
                    if (characteristic != null) {
                        _state.value = BleProvisioningState.WritingConfig
                        val payloadBytes = payload.toByteArray(Charsets.UTF_8)
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
                        _state.value = BleProvisioningState.Failed("Provisioning characteristic not found on node")
                        gatt.disconnect()
                    }
                } else {
                    _state.value = BleProvisioningState.Failed("Service discovery failed with code $status")
                    gatt.disconnect()
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i(TAG, "Provisioning config written successfully to node.")
                    provisionSuccess = true
                    _state.value = BleProvisioningState.Success(version)

                    // Update Room Database
                    coroutineScope.launch {
                        try {
                            pairedChildDao.updateProvisioningStatus(
                                deviceId = childId,
                                isProvisioned = true,
                                provisionedAt = System.currentTimeMillis()
                            )
                            pairedChildDao.updateParentNodeEndpoint(
                                deviceId = childId,
                                ip = parentNodeIp,
                                port = parentNodePort,
                                endpointId = parentNodeEndpointId
                            )
                            Log.i(TAG, "Room Database updated with provisioned endpoint for child $childId")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to update PairedChild in DB", e)
                        } finally {
                            gatt.disconnect()
                        }
                    }
                } else {
                    _state.value = BleProvisioningState.Failed("Write failed with status $status")
                    gatt.disconnect()
                }
            }
        }

        try {
            activeGatt = device.connectGatt(context, false, gattCallback)
        } catch (e: Exception) {
            _state.value = BleProvisioningState.Failed("Connection exception: ${e.message}")
            return false
        }

        // Wait up to 15 seconds for provisioning flow to complete
        val completed = withTimeoutOrNull(15000L) {
            while (_state.value !is BleProvisioningState.Success && _state.value !is BleProvisioningState.Failed) {
                kotlinx.coroutines.delay(200L)
            }
            _state.value is BleProvisioningState.Success
        } ?: false

        return completed
    }

    /**
     * Resets state back to Idle
     */
    @SuppressLint("MissingPermission")
    fun reset() {
        _state.value = BleProvisioningState.Idle
        try {
            activeGatt?.close()
        } catch (ignored: Exception) {}
        activeGatt = null
    }
}
