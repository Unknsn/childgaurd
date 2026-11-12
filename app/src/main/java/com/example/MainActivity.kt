package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.AppMode
import com.example.ui.SafeBandViewModel
import com.example.ui.components.PermissionRationaleDialog
import com.example.ui.screens.AlertHistoryScreen
import com.example.ui.screens.AppUpdateScreen
import com.example.ui.screens.ChildHomeScreen
import com.example.ui.screens.ChildProvisioningScreen
import com.example.ui.screens.ChildSettingsScreen
import com.example.ui.screens.FirstLaunchScreen
import com.example.ui.screens.JourneySafetyScreen
import com.example.ui.screens.ModeSelectionScreen
import com.example.ui.screens.ParentHomeScreen
import com.example.ui.screens.PickupManagementScreen
import com.example.ui.screens.SchoolManagementScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

enum class AppDestination {
    HOME,
    SETTINGS,
    HISTORY,
    UPDATE,
    PICKUP,
    SCHOOL,
    JOURNEY
}

class MainActivity : ComponentActivity() {

    private val viewModel: SafeBandViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                SafeBandApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun SafeBandApp(viewModel: SafeBandViewModel) {
    val context = LocalContext.current
    val currentMode by viewModel.appMode.collectAsState()
    var currentDestination by remember { mutableStateOf(AppDestination.HOME) }
    var isChildProvisioning by remember { mutableStateOf(false) }

    // Permission handling state
    var showRationaleDialog by remember { mutableStateOf(false) }
    var pendingPermissions by remember { mutableStateOf<Array<String>>(emptyArray()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        viewModel.onPermissionsGranted()
    }

    // Determine required permissions based on SDK version
    fun getRequiredPermissions(): List<String> {
        val list = mutableListOf<String>()

        // Location for geofence & BLE hardware
        list.add(Manifest.permission.ACCESS_FINE_LOCATION)
        list.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        // Bluetooth permissions for Android 12+ (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_SCAN)
            list.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        // Notification permission for Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return list
    }

    // Check permissions on role selected
    LaunchedEffect(currentMode) {
        if (currentMode != AppMode.UNSELECTED) {
            val missing = getRequiredPermissions().filter { perm ->
                ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                pendingPermissions = missing.toTypedArray()
                showRationaleDialog = true
            } else {
                viewModel.onPermissionsGranted()
            }
        }
    }

    // Hardware state observer & refresh on app resume
    val isBtHardwareEnabled by viewModel.isBluetoothHardwareEnabled.collectAsState()
    val isLocHardwareEnabled by viewModel.isLocationHardwareEnabled.collectAsState()
    var showHardwarePrompt by remember { mutableStateOf(true) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshHardwarePrerequisites()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (currentDestination != AppDestination.HOME) {
        androidx.activity.compose.BackHandler {
            currentDestination = AppDestination.HOME
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = Triple(currentMode, currentDestination, isChildProvisioning),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ScreenTransition"
            ) { (mode, destination, provisioning) ->
                when {
                    mode == AppMode.UNSELECTED -> {
                        if (provisioning) {
                            ChildProvisioningScreen(
                                viewModel = viewModel,
                                onNavigateBack = { isChildProvisioning = false },
                                onProvisioningComplete = {
                                    isChildProvisioning = false
                                    currentDestination = AppDestination.HOME
                                }
                            )
                        } else {
                            FirstLaunchScreen(
                                viewModel = viewModel,
                                onNavigateToChildProvisioning = { isChildProvisioning = true }
                            )
                        }
                    }

                    destination == AppDestination.SETTINGS -> {
                        if (mode == AppMode.CHILD) {
                            ChildSettingsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { currentDestination = AppDestination.HOME },
                                onUnlockSuccess = { currentDestination = AppDestination.HOME }
                            )
                        } else {
                            SettingsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { currentDestination = AppDestination.HOME },
                                onNavigateToUpdate = { currentDestination = AppDestination.UPDATE },
                                onNavigateToPickup = { currentDestination = AppDestination.PICKUP },
                                onNavigateToSchool = { currentDestination = AppDestination.SCHOOL },
                                onNavigateToJourney = { currentDestination = AppDestination.JOURNEY }
                            )
                        }
                    }

                    destination == AppDestination.UPDATE -> {
                        AppUpdateScreen(
                            viewModel = viewModel,
                            onNavigateBack = { currentDestination = AppDestination.HOME }
                        )
                    }

                    destination == AppDestination.PICKUP -> {
                        PickupManagementScreen(
                            viewModel = viewModel,
                            onNavigateBack = { currentDestination = AppDestination.HOME }
                        )
                    }

                    destination == AppDestination.SCHOOL -> {
                        SchoolManagementScreen(
                            viewModel = viewModel,
                            onNavigateBack = { currentDestination = AppDestination.HOME }
                        )
                    }

                    destination == AppDestination.JOURNEY -> {
                        JourneySafetyScreen(
                            viewModel = viewModel,
                            onNavigateBack = { currentDestination = AppDestination.HOME }
                        )
                    }

                    destination == AppDestination.HISTORY -> {
                        AlertHistoryScreen(
                            viewModel = viewModel,
                            onNavigateBack = { currentDestination = AppDestination.HOME },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }

                    mode == AppMode.CHILD -> {
                        ChildHomeScreen(
                            viewModel = viewModel,
                            onNavigateToSettings = { currentDestination = AppDestination.SETTINGS },
                            onNavigateToHistory = { currentDestination = AppDestination.HISTORY }
                        )
                    }

                    mode == AppMode.PARENT -> {
                        ParentHomeScreen(
                            viewModel = viewModel,
                            onNavigateToSettings = { currentDestination = AppDestination.SETTINGS },
                            onNavigateToUpdate = { currentDestination = AppDestination.UPDATE },
                            onNavigateToPickup = { currentDestination = AppDestination.PICKUP },
                            onNavigateToSchool = { currentDestination = AppDestination.SCHOOL },
                            onNavigateToJourney = { currentDestination = AppDestination.JOURNEY }
                        )
                    }
                }
            }

            // Permission Rationale Dialog
            if (showRationaleDialog) {
                PermissionRationaleDialog(
                    title = "Sensors & Bluetooth Permission",
                    rationale = "SafeBand requires Bluetooth and Location access to monitor for safe zone boundary exits and communicate directly between child and parent devices. All computation is strictly on-device without internet.",
                    onConfirm = {
                        showRationaleDialog = false
                        permissionLauncher.launch(pendingPermissions)
                    },
                    onDismiss = {
                        showRationaleDialog = false
                    }
                )
            }

            // Hardware Prerequisite Dialog (Bluetooth & Location Enforcer)
            if (currentMode != AppMode.UNSELECTED && showHardwarePrompt && !showRationaleDialog) {
                if (!isBtHardwareEnabled) {
                    PermissionRationaleDialog(
                        title = "Bluetooth Is Turned Off",
                        rationale = "SafeBand requires Bluetooth Low Energy to scan for child wearable bands and receive emergency SOS signals. Please enable Bluetooth to keep protection active.",
                        onConfirm = {
                            showHardwarePrompt = false
                            try {
                                context.startActivity(viewModel.getEnableBluetoothIntent())
                            } catch (_: Exception) {}
                        },
                        onDismiss = { showHardwarePrompt = false },
                        iconType = "bluetooth"
                    )
                } else if (!isLocHardwareEnabled) {
                    PermissionRationaleDialog(
                        title = "Location Services Turned Off",
                        rationale = "Android requires device Location to be turned ON for Bluetooth beacon discovery and geofence tracking. Please enable Location in system settings.",
                        onConfirm = {
                            showHardwarePrompt = false
                            try {
                                context.startActivity(viewModel.getEnableLocationIntent())
                            } catch (_: Exception) {}
                        },
                        onDismiss = { showHardwarePrompt = false },
                        iconType = "location"
                    )
                }
            }
        }
    }
}

// Greeting function retained for testing compatibility
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme { Greeting("Android") }
}
