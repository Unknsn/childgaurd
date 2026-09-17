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
import com.example.model.AppMode
import com.example.ui.SafeBandViewModel
import com.example.ui.components.PermissionRationaleDialog
import com.example.ui.screens.AlertHistoryScreen
import com.example.ui.screens.ChildHomeScreen
import com.example.ui.screens.ModeSelectionScreen
import com.example.ui.screens.ParentHomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

enum class AppDestination {
    HOME,
    SETTINGS,
    HISTORY
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

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = Pair(currentMode, currentDestination),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ScreenTransition"
            ) { (mode, destination) ->
                when {
                    mode == AppMode.UNSELECTED -> {
                        ModeSelectionScreen(
                            onSelectMode = { selected ->
                                viewModel.selectAppMode(selected)
                            }
                        )
                    }

                    destination == AppDestination.SETTINGS -> {
                        SettingsScreen(
                            viewModel = viewModel,
                            onNavigateBack = { currentDestination = AppDestination.HOME }
                        )
                    }

                    destination == AppDestination.HISTORY -> {
                        AlertHistoryScreen(
                            viewModel = viewModel,
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
                            onNavigateToSettings = { currentDestination = AppDestination.SETTINGS }
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
