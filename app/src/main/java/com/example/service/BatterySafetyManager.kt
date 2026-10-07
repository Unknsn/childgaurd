package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.example.model.BatteryInfo
import com.example.model.BatteryState
import com.example.model.OperatingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Battery Intelligence Manager (Batch F: Phase 15).
 *
 * Uses Android system BatteryManager to evaluate real battery levels,
 * charging state, threshold classifications, and power-saving operating modes.
 *
 * Rules:
 * - Real battery level only; no invented percentages.
 * - NORMAL (>25%), LOW (16%..25%), CRITICAL (<=15%).
 * - Enters POWER_SAVING operating mode automatically on LOW or CRITICAL battery.
 */
class BatterySafetyManager(private val context: Context) {

    private val _batteryInfo = MutableStateFlow(BatteryInfo())
    val batteryInfo: StateFlow<BatteryInfo> = _batteryInfo.asStateFlow()

    private var receiver: BroadcastReceiver? = null

    init {
        registerBatteryReceiver()
    }

    fun registerBatteryReceiver() {
        if (receiver != null) return

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    processBatteryIntent(intent)
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val initialIntent = context.registerReceiver(receiver, filter)
        if (initialIntent != null) {
            processBatteryIntent(initialIntent)
        }
    }

    fun unregisterBatteryReceiver() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {}
            receiver = null
        }
    }

    /**
     * Parses battery percentage and status from Intent.ACTION_BATTERY_CHANGED.
     */
    fun processBatteryIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val percentage = if (level >= 0 && scale > 0) {
            ((level.toFloat() / scale.toFloat()) * 100f).toInt().coerceIn(0, 100)
        } else {
            null
        }

        updateBatteryState(percentage, isCharging)
    }

    /**
     * Evaluates BatteryState and OperatingMode deterministically.
     */
    fun updateBatteryState(percentage: Int?, isCharging: Boolean) {
        val batteryState = when {
            percentage == null -> BatteryState.NORMAL
            percentage <= 15 -> BatteryState.CRITICAL
            percentage <= 25 -> BatteryState.LOW
            else -> BatteryState.NORMAL
        }

        val operatingMode = if (batteryState == BatteryState.LOW || batteryState == BatteryState.CRITICAL) {
            OperatingMode.POWER_SAVING
        } else {
            OperatingMode.NORMAL
        }

        _batteryInfo.value = BatteryInfo(
            percentage = percentage,
            isCharging = isCharging,
            batteryState = batteryState,
            operatingMode = operatingMode
        )
    }

    /**
     * Helper for tests or manual evaluation injection.
     */
    fun simulateBatteryState(percentage: Int, isCharging: Boolean = false) {
        updateBatteryState(percentage, isCharging)
    }
}
