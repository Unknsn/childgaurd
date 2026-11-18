package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.RelayForwardingRecord
import com.example.data.local.TrustedContact
import com.example.model.BleBeaconPayload
import com.example.model.ChildBioProfile
import com.example.model.GeoAddress
import com.example.model.RiskLevel
import com.example.ui.SafeBandViewModel
import com.example.ui.components.EmergencyAlertSheet
import com.example.ui.components.PermissionRationaleDialog
import com.example.ui.components.RelayEmergencyAlertSheet
import com.example.ui.screens.AlertHistoryScreen
import com.example.ui.screens.ChildHomeScreen
import com.example.ui.screens.ChildProvisioningScreen
import com.example.ui.screens.ChildSettingsScreen
import com.example.ui.screens.FirstLaunchScreen
import com.example.ui.screens.ModeSelectionScreen
import com.example.ui.screens.ParentHomeScreen
import com.example.ui.screens.SafeZoneConfigScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TrustedContactsScreen
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ScreensVisualScreenshotTest
 *
 * Automated visual regression suite rendering 13 core screens and sheets
 * with Robolectric Native Graphics on Pixel 8 form factor.
 * Generates verified PNG screenshot artifacts into app/src/test/screenshots/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class ScreensVisualScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var viewModel: SafeBandViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<Application>()
        viewModel = SafeBandViewModel(app)
    }

    @Test
    fun screenshot_01_mode_selection_screen() {
        composeTestRule.setContent {
            MyApplicationTheme {
                ModeSelectionScreen(onSelectMode = {})
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/01_mode_selection_screen.png")
    }

    @Test
    fun screenshot_02_first_launch_screen() {
        composeTestRule.setContent {
            MyApplicationTheme {
                FirstLaunchScreen(viewModel = viewModel, onNavigateToChildProvisioning = {})
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/02_first_launch_screen.png")
    }

    @Test
    fun screenshot_03_child_home_normal() {
        composeTestRule.setContent {
            MyApplicationTheme {
                ChildHomeScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onNavigateToHistory = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/03_child_home_normal.png")
    }

    @Test
    fun screenshot_04_parent_home_dashboard() {
        composeTestRule.setContent {
            MyApplicationTheme {
                ParentHomeScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/04_parent_home_dashboard.png")
    }

    @Test
    fun screenshot_05_child_provisioning_screen() {
        composeTestRule.setContent {
            MyApplicationTheme {
                ChildProvisioningScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onProvisioningComplete = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/05_child_provisioning_screen.png")
    }

    @Test
    fun screenshot_06_settings_screen() {
        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/06_settings_screen.png")
    }

    @Test
    fun screenshot_07_child_settings_screen() {
        composeTestRule.setContent {
            MyApplicationTheme {
                ChildSettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onUnlockSuccess = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/07_child_settings_screen.png")
    }

    @Test
    fun screenshot_08_alert_history_screen() {
        composeTestRule.setContent {
            MyApplicationTheme {
                AlertHistoryScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/08_alert_history_screen.png")
    }

    @Test
    fun screenshot_09_trusted_contacts_screen() {
        composeTestRule.setContent {
            MyApplicationTheme {
                TrustedContactsScreen(viewModel = viewModel)
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/09_trusted_contacts_screen.png")
    }

    @Test
    fun screenshot_10_safe_zone_config_screen() {
        composeTestRule.setContent {
            MyApplicationTheme {
                SafeZoneConfigScreen(viewModel = viewModel)
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/10_safe_zone_config_screen.png")
    }

    @Test
    fun screenshot_11_emergency_alert_sheet() {
        val payload = BleBeaconPayload(
            deviceId = "SB-DEMO-01",
            riskLevel = RiskLevel.CRITICAL,
            timestamp = System.currentTimeMillis(),
            latitude = 28.6139,
            longitude = 77.2090,
            childBioProfile = ChildBioProfile(
                childName = "Aarav Sharma",
                age = "8",
                bloodType = "O+",
                primaryParentPhone = "+91 98765 43210"
            ),
            address = GeoAddress(
                fullAddress = "Near India Gate, Central Delhi, 110001",
                street = "Rajpath",
                area = "Central Delhi",
                city = "New Delhi",
                state = "Delhi",
                pinCode = "110001",
                latitude = 28.6139,
                longitude = 77.2090
            )
        )
        val contacts = listOf(
            TrustedContact(name = "Rajesh Sharma", phoneNumber = "+91 98765 43210", relationship = "Father"),
            TrustedContact(name = "Priya Sharma", phoneNumber = "+91 98111 22334", relationship = "Mother")
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                EmergencyAlertSheet(
                    payload = payload,
                    elapsedSeconds = 12,
                    contacts = contacts,
                    onDismiss = {},
                    onNotifyContact = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/11_emergency_alert_sheet.png")
    }

    @Test
    fun screenshot_12_relay_emergency_alert_sheet() {
        val record = RelayForwardingRecord(
            incidentId = "INC-RELAY-99",
            deviceId = "SB-EPHEMERAL-77",
            eventType = "SOS",
            detectionTimestamp = System.currentTimeMillis(),
            relayLatitude = 28.6139,
            relayLongitude = 77.2090,
            relayAccuracy = 12.5f,
            forwardStatus = "DELIVERED_BACKEND"
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                RelayEmergencyAlertSheet(
                    record = record,
                    onDismiss = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/12_relay_emergency_alert_sheet.png")
    }

    @Test
    fun screenshot_13_permission_rationale_dialog() {
        composeTestRule.setContent {
            MyApplicationTheme {
                PermissionRationaleDialog(
                    title = "Bluetooth & Location Permission",
                    rationale = "SafeBand Guardian requires Bluetooth and Location access to maintain continuous local BLE mesh connectivity with your child's wearable band and detect geofence boundaries.",
                    onConfirm = {},
                    onDismiss = {},
                    iconType = "bluetooth"
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/13_permission_rationale_dialog.png")
    }

    @Test
    fun screenshot_14_child_home_emergency() {
        viewModel.triggerManualSos()
        composeTestRule.setContent {
            MyApplicationTheme {
                ChildHomeScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onNavigateToHistory = {}
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/14_child_home_emergency.png")
    }

    @Test
    fun test_sos_cancellation_requires_confirmation_dialog() {
        viewModel.triggerManualSos()
        assertTrue("SOS must be active after triggering", viewModel.activeFlags.value.contains(com.example.model.AlertFlag.MANUAL_SOS))

        composeTestRule.setContent {
            MyApplicationTheme {
                ChildHomeScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onNavigateToHistory = {}
                )
            }
        }

        // Tap the SOS button while active
        composeTestRule.onNodeWithTag("manual_sos_button").performClick()

        // Verify confirmation dialog is displayed
        composeTestRule.onNodeWithText("Cancel Emergency Alert?").assertIsDisplayed()

        // Click "Keep Alert Active" (safe default)
        composeTestRule.onNodeWithText("Keep Alert Active").performClick()

        // SOS must remain ACTIVE
        assertTrue("SOS must remain active after dismissing cancellation dialog", viewModel.activeFlags.value.contains(com.example.model.AlertFlag.MANUAL_SOS))

        // Tap again to show dialog and confirm cancellation
        composeTestRule.onNodeWithTag("manual_sos_button").performClick()
        composeTestRule.onNodeWithText("Cancel Emergency Alert?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Yes, Cancel Alert").performClick()

        // SOS must now be cancelled
        assertFalse("SOS must be cancelled after confirming cancellation", viewModel.activeFlags.value.contains(com.example.model.AlertFlag.MANUAL_SOS))
    }
}
