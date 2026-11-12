package com.example.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.provider.Settings
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * HardwareSafetyEnforcer
 *
 * Actively monitors hardware radio and sensor states:
 * - Bluetooth Low Energy adapter (Enabled / Disabled)
 * - Location Services GPS provider (Enabled / Disabled)
 *
 * Ensures parents and children are immediately alerted if either life-safety
 * requirement is switched off in system quick settings.
 */
class HardwareSafetyEnforcer(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _isBluetoothEnabled = MutableStateFlow(checkBluetoothStatus())
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _isLocationEnabled = MutableStateFlow(checkLocationStatus())
    val isLocationEnabled: StateFlow<Boolean> = _isLocationEnabled.asStateFlow()

    private val hardwareReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    _isBluetoothEnabled.value = checkBluetoothStatus()
                }
                LocationManager.PROVIDERS_CHANGED_ACTION,
                LocationManager.MODE_CHANGED_ACTION -> {
                    _isLocationEnabled.value = checkLocationStatus()
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        try {
            context.registerReceiver(hardwareReceiver, filter)
        } catch (_: Exception) {}
    }

    fun refreshStatus() {
        _isBluetoothEnabled.value = checkBluetoothStatus()
        _isLocationEnabled.value = checkLocationStatus()
    }

    fun checkBluetoothStatus(): Boolean {
        return try {
            val adapter = bluetoothManager?.adapter ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
            adapter?.isEnabled == true
        } catch (_: Exception) {
            false
        }
    }

    fun checkLocationStatus(): Boolean {
        return try {
            if (locationManager == null) false
            else LocationManagerCompat.isLocationEnabled(locationManager)
        } catch (_: Exception) {
            false
        }
    }

    fun getEnableBluetoothIntent(): Intent {
        return Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun getEnableLocationIntent(): Intent {
        return Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun destroy() {
        try {
            context.unregisterReceiver(hardwareReceiver)
        } catch (_: Exception) {}
    }
}

