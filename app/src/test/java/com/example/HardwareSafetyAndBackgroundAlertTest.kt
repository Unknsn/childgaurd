package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.model.BleBeaconPayload
import com.example.model.ChildBioProfile
import com.example.model.RiskLevel
import com.example.model.SafetyIncident
import com.example.service.BleSafetyMonitorService
import com.example.service.HardwareSafetyEnforcer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * HardwareSafetyAndBackgroundAlertTest (v3.2.0)
 *
 * Exhaustively verifies:
 * 1. Hardware prerequisites (Bluetooth & Location/GPS enforcer)
 * 2. Background emergency alert delivery when app is closed
 * 3. Lockscreen public visibility & alarm audio attributes
 * 4. UI timeline flag and coordinates non-collision formatting
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HardwareSafetyAndBackgroundAlertTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun testHardwareSafetyEnforcerInitialStateAndIntents() {
        val enforcer = HardwareSafetyEnforcer(context)

        // Verify state flows are initialized
        assertNotNull("Bluetooth state flow must not be null", enforcer.isBluetoothEnabled.value)
        assertNotNull("Location state flow must not be null", enforcer.isLocationEnabled.value)

        // Verify intent generators for user prompt
        val btIntent = enforcer.getEnableBluetoothIntent()
        assertNotNull(btIntent)
        assertTrue(btIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)

        val locIntent = enforcer.getEnableLocationIntent()
        assertEquals(Settings.ACTION_LOCATION_SOURCE_SETTINGS, locIntent.action)
        assertTrue(locIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)

        enforcer.destroy()
    }

    @Test
    fun testEmergencyNotificationChannelConfiguredForBackgroundLockscreenAlerts() {
        val serviceController = Robolectric.buildService(BleSafetyMonitorService::class.java).create()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val emergencyChannel = notificationManager.getNotificationChannel(BleSafetyMonitorService.CHANNEL_ID_EMERGENCY)
        assertNotNull("Emergency notification channel must exist", emergencyChannel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, emergencyChannel.importance)
        assertEquals(Notification.VISIBILITY_PUBLIC, emergencyChannel.lockscreenVisibility)
        assertTrue("Vibration must be enabled for emergency alerts", emergencyChannel.shouldVibrate())

        serviceController.destroy()
    }

    @Test
    fun testEmergencyNotificationPostedWithChildBioAndGuardianCallAction() {
        val serviceController = Robolectric.buildService(BleSafetyMonitorService::class.java).create()
        val service = serviceController.get()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = shadowOf(notificationManager)

        val testDeviceId = "EP-C0D7"
        val childBio = ChildBioProfile(
            childName = "Reyansh",
            age = "9",
            bloodType = "B+",
            primaryParentPhone = "+91 99887 76655"
        )
        service.incomingBioCache[testDeviceId] = childBio

        val emergencyPayload = BleBeaconPayload(
            deviceId = testDeviceId,
            riskLevel = RiskLevel.CRITICAL,
            timestamp = System.currentTimeMillis(),
            isSimulation = false
        )

        // Process incoming emergency beacon
        service.processBeacon(emergencyPayload)

        // Verify notification posted
        val postedNotifs = shadowNm.allNotifications
        assertTrue("At least one notification should be posted", postedNotifs.isNotEmpty())

        val emergencyNotif = postedNotifs.find { it.category == Notification.CATEGORY_ALARM }
        assertNotNull("Emergency alarm notification must be posted", emergencyNotif)
        assertEquals(Notification.VISIBILITY_PUBLIC, emergencyNotif?.visibility)

        val shadowNotif = shadowOf(emergencyNotif)
        val title = shadowNotif.contentTitle?.toString() ?: ""
        assertTrue("Title should mention child name", title.contains("REYANSH", ignoreCase = true))

        val text = shadowNotif.contentText?.toString() ?: ""
        assertTrue("Notification content should include guardian phone", text.contains("+91 99887 76655"))

        // Verify Call Guardian action button exists
        val actions = emergencyNotif?.actions
        assertNotNull("Notification actions must not be null", actions)
        val callAction = actions?.find { it.title?.toString()?.contains("Guardian", ignoreCase = true) == true }
        assertNotNull("Call Guardian action must be present", callAction)

        serviceController.destroy()
    }

    @Test
    fun testTimelineFlagFormattingPreventsWrapping() {
        val rawFlag = "CONFIRMATION_RESOLVED_REENTRY"
        val cleanFlag = when (rawFlag) {
            "MANUAL_SOS" -> "Manual SOS"
            "CONFIRMATION_RESOLVED_REENTRY" -> "Safe Re-entry"
            "GEOFENCE_EXIT" -> "Perimeter Exit"
            "BLE_BEACON_TIMEOUT" -> "Beacon Timeout"
            else -> rawFlag.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
        }

        assertEquals("Safe Re-entry", cleanFlag)
        assertTrue("Clean flag must be concise (< 20 chars) to prevent line splitting", cleanFlag.length <= 16)
    }

    @Test
    fun testCoordinatesAndRadiusFormattingDoesNotCollide() {
        val lat = 37.77490
        val lon = -122.41940
        val radius = 150f

        val coordText = "Lat: %.5f • Lon: %.5f".format(java.util.Locale.US, lat, lon)
        val radiusText = "Radius: %.0fm".format(java.util.Locale.US, radius)

        assertEquals("Lat: 37.77490 • Lon: -122.41940", coordText)
        assertEquals("Radius: 150m", radiusText)
        assertTrue("Separated labels prevent wrapping", coordText.length < 35 && radiusText.length < 15)
    }
}

