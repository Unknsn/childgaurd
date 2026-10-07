package com.example.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.model.ConnectivityStatus
import com.example.model.ConnectivityTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Connectivity Status Manager (Batch F: Phase 16).
 *
 * Differentiates between:
 * - Active Internet connection (WiFi / Cellular)
 * - BLE Radio availability
 * - Direct nearby BLE peer discovery (proximity)
 * - Relayed multi-hop peer evidence
 *
 * Crucial safety rules:
 * - BLE available != internet available.
 * - BLE beacon observed != wearer device online.
 */
class ConnectivitySafetyManager(private val context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _connectivityStatus = MutableStateFlow(ConnectivityStatus())
    val connectivityStatus: StateFlow<ConnectivityStatus> = _connectivityStatus.asStateFlow()

    fun evaluateConnectivity(
        hasNearbyPeerDirect: Boolean = false,
        hasRelayedPeerEvidence: Boolean = false,
        lastSeenPeerTimestamp: Long = 0L,
        isBleAdvertising: Boolean = false
    ) {
        val isInternet = checkInternetAvailable()
        val isBle = bluetoothAdapter?.isEnabled == true

        val now = System.currentTimeMillis()
        val isPeerFresh = (now - lastSeenPeerTimestamp) < 30_000L

        val tier = when {
            isInternet -> ConnectivityTier.ONLINE
            hasNearbyPeerDirect && isPeerFresh -> ConnectivityTier.NEARBY
            hasRelayedPeerEvidence && isPeerFresh -> ConnectivityTier.RELAYED
            isBle -> ConnectivityTier.OFFLINE
            else -> ConnectivityTier.UNKNOWN
        }

        _connectivityStatus.value = ConnectivityStatus(
            isInternetAvailable = isInternet,
            isBleAvailable = isBle,
            isBleAdvertising = isBleAdvertising,
            hasNearbyPeer = hasNearbyPeerDirect && isPeerFresh,
            hasRelayedPeer = hasRelayedPeerEvidence && isPeerFresh,
            lastSeenPeerTimestamp = lastSeenPeerTimestamp,
            tier = tier
        )
    }

    private fun checkInternetAvailable(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Testing / simulation helper.
     */
    fun simulateConnectivity(
        isInternet: Boolean,
        isBle: Boolean,
        tier: ConnectivityTier
    ) {
        _connectivityStatus.value = ConnectivityStatus(
            isInternetAvailable = isInternet,
            isBleAvailable = isBle,
            isBleAdvertising = false,
            hasNearbyPeer = tier == ConnectivityTier.NEARBY,
            hasRelayedPeer = tier == ConnectivityTier.RELAYED,
            lastSeenPeerTimestamp = System.currentTimeMillis(),
            tier = tier
        )
    }
}
