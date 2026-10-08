package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.preferences.SafeBandPreferences
import com.example.model.AppMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Device Reboot Receiver (Phase 19).
 *
 * Restores always-on background safety monitoring after device reboot when:
 * - Safety monitoring is enabled in user settings
 * - App is configured in PARENT mode
 * - Required Bluetooth permissions remain granted
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.i("BootCompletedReceiver", "Received $action: checking if safety monitoring should be restored")

            CoroutineScope(Dispatchers.IO).launch {
                val preferences = SafeBandPreferences(context.applicationContext)
                val isMonitoringEnabled = preferences.safetyMonitoringEnabled.first()
                val currentMode = preferences.appMode.first()

                if (isMonitoringEnabled && currentMode == AppMode.PARENT) {
                    val hasBleScan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.BLUETOOTH_SCAN
                        ) == PackageManager.PERMISSION_GRANTED
                    } else true

                    if (hasBleScan) {
                        Log.i("BootCompletedReceiver", "Restoring BleSafetyMonitorService on boot")
                        BleSafetyMonitorService.start(context.applicationContext)
                    } else {
                        Log.w("BootCompletedReceiver", "Cannot restore monitoring on boot: BLUETOOTH_SCAN permission not granted")
                    }
                } else {
                    Log.d("BootCompletedReceiver", "Monitoring not restored: monitoringEnabled=$isMonitoringEnabled, mode=$currentMode")
                }
            }
        }
    }
}
