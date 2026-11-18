package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.ui.components.SafeBandCard
import com.example.ui.components.SafeBandPrimaryButton
import com.example.ui.components.SafeBandSecondaryButton
import com.example.ui.components.SafeBandStatusBadge
import com.example.ui.theme.SafeBandColor
import com.example.ui.theme.SafeBandShapes
import com.example.ui.theme.SafeBandSpacing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GeofenceState
import com.example.model.LocationConfidence
import com.example.model.RouteState
import com.example.model.RouteWaypoint
import com.example.model.SafeZone
import com.example.model.TrustedRoute
import com.example.model.VerifiedLocation
import com.example.ui.SafeBandViewModel
import com.example.ui.components.SafeZoneMapView

@Composable
fun SafeZoneConfigScreen(
    viewModel: SafeBandViewModel,
    modifier: Modifier = Modifier
) {
    val currentSafeZone by viewModel.safeZone.collectAsState()
    val isChildOutside by viewModel.locationHelper.isOutsideSafeZone.collectAsState()
    val childDistance by viewModel.locationHelper.distanceToSafeZoneMeters.collectAsState()
    val deviceLocation by viewModel.locationHelper.currentLocation.collectAsState()
    val safeZoneAddress by viewModel.safeZoneAddress.collectAsState()

    val geofenceState by viewModel.geofenceState.collectAsState()
    val distanceToBoundary by viewModel.distanceToBoundary.collectAsState()
    val distanceFromCenter by viewModel.distanceFromCenter.collectAsState()
    val verifiedLocation by viewModel.verifiedLocation.collectAsState()
    val routeState by viewModel.routeState.collectAsState()
    val trustedRoute by viewModel.trustedRoute.collectAsState()
    val distanceToRouteCorridor by viewModel.distanceToRouteCorridor.collectAsState()

    var isRouteEnabled by remember(trustedRoute) { mutableStateOf(trustedRoute.isEnabled) }
    var routeCorridorValue by remember(trustedRoute) { mutableFloatStateOf(trustedRoute.corridorRadiusMeters) }
    var routeSavedMessage by remember { mutableStateOf<String?>(null) }

    var nameInput by remember(currentSafeZone) { mutableStateOf(currentSafeZone.name) }
    var latInput by remember(currentSafeZone) { mutableStateOf(currentSafeZone.latitude.toString()) }
    var lonInput by remember(currentSafeZone) { mutableStateOf(currentSafeZone.longitude.toString()) }
    var radiusValue by remember(currentSafeZone) { mutableFloatStateOf(currentSafeZone.radiusMeters) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    val previewZone = remember(latInput, lonInput, radiusValue, nameInput) {
        val lat = latInput.toDoubleOrNull() ?: currentSafeZone.latitude
        val lon = lonInput.toDoubleOrNull() ?: currentSafeZone.longitude
        SafeZone(
            latitude = lat,
            longitude = lon,
            radiusMeters = radiusValue,
            name = nameInput.ifEmpty { "Designated Safe Zone" }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Radar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Geofence Safe Zone Map",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Interactive OpenStreetMap • Live boundary & street addresses",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Real Interactive OpenStreetMap Safe Zone View
        SafeZoneMapView(
            safeZone = previewZone,
            childLat = deviceLocation?.latitude,
            childLon = deviceLocation?.longitude,
            isChildOutside = isChildOutside,
            resolvedAddress = safeZoneAddress,
            verifiedLocation = verifiedLocation,
            geofenceState = geofenceState,
            routeWaypoints = if (isRouteEnabled) trustedRoute.waypoints else emptyList(),
            corridorRadiusMeters = routeCorridorValue,
            onMapLocationSelected = { lat, lon ->
                latInput = "%.5f".format(java.util.Locale.US, lat)
                lonInput = "%.5f".format(java.util.Locale.US, lon)
            },
            onUseCurrentLocation = {
                deviceLocation?.let { loc ->
                    latInput = "%.5f".format(java.util.Locale.US, loc.latitude)
                    lonInput = "%.5f".format(java.util.Locale.US, loc.longitude)
                }
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Configuration Form Card
        SafeBandCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "ZONE PARAMETERS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = SafeBandColor.Primary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("Safe Zone Label") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("safe_zone_name_input"),
                shape = SafeBandShapes.medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = latInput,
                    onValueChange = { latInput = it },
                    label = { Text("Latitude") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("safe_zone_lat_input"),
                    shape = SafeBandShapes.medium
                )

                Spacer(modifier = Modifier.width(10.dp))

                OutlinedTextField(
                    value = lonInput,
                    onValueChange = { lonInput = it },
                    label = { Text("Longitude") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("safe_zone_lon_input"),
                    shape = SafeBandShapes.medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Use Current Location Button
            SafeBandSecondaryButton(
                text = "Pin To My Current Location",
                icon = Icons.Default.MyLocation,
                onClick = {
                    deviceLocation?.let { loc ->
                        latInput = "%.5f".format(java.util.Locale.US, loc.latitude)
                        lonInput = "%.5f".format(java.util.Locale.US, loc.longitude)
                    } ?: run {
                        latInput = "37.77490"
                        lonInput = "-122.41940"
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("use_current_location_button")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Radius Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Boundary Radius:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                SafeBandStatusBadge(
                    text = "${radiusValue.toInt()} meters",
                    color = SafeBandColor.Primary
                )
            }

            Slider(
                value = radiusValue,
                onValueChange = { radiusValue = it },
                valueRange = 50f..1000f,
                steps = 18,
                modifier = Modifier.testTag("radius_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = SafeBandColor.Primary,
                    activeTrackColor = SafeBandColor.Primary
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Save Changes Button
            SafeBandPrimaryButton(
                text = "Save Safe Zone",
                icon = Icons.Default.Check,
                onClick = {
                    val lat = latInput.toDoubleOrNull() ?: currentSafeZone.latitude
                    val lon = lonInput.toDoubleOrNull() ?: currentSafeZone.longitude
                    viewModel.saveSafeZone(lat, lon, radiusValue, nameInput)
                    saveMessage = "Safe Zone saved successfully!"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_safe_zone_button")
            )

            saveMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.labelMedium,
                    color = SafeBandColor.Success,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Geofence Engine Status & Accuracy Card
        SafeBandCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GEOFENCE ENGINE TELEMETRY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = SafeBandColor.Primary,
                    letterSpacing = 1.sp
                )

                val geofenceBadgeColor = when (geofenceState) {
                    com.example.model.GeofenceState.SAFE -> SafeBandColor.Success
                    com.example.model.GeofenceState.APPROACHING -> SafeBandColor.Warning
                    com.example.model.GeofenceState.EXIT_PENDING -> SafeBandColor.WarningDark
                    com.example.model.GeofenceState.OUTSIDE -> SafeBandColor.Emergency
                    com.example.model.GeofenceState.REENTERED -> SafeBandColor.Success
                }

                SafeBandStatusBadge(
                    text = geofenceState.displayName,
                    color = geofenceBadgeColor
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val centerDistStr = distanceFromCenter?.let { "%.0f m".format(it) } ?: "Awaiting GPS fix"
            val boundDistStr = distanceToBoundary?.let { dist ->
                if (dist > 0) "+%.0f m (Outside)".format(dist) else "%.0f m (Inside)".format(dist)
            } ?: "Awaiting GPS fix"

            Text(
                text = "• Distance from Safe Zone Center: $centerDistStr\n• Distance relative to boundary: $boundDistStr\n• GPS Accuracy: ±${verifiedLocation?.accuracyMeters?.toInt() ?: 15}m (${verifiedLocation?.confidence?.displayName ?: "Medium"})\n• Grace Period: 15s Confirmation Window active",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Trusted Route Corridor Card
        SafeBandCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TRUSTED ROUTE CORRIDOR",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = SafeBandColor.Primary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Home → Bus Stop → School Safety Corridor",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                androidx.compose.material3.Switch(
                    checked = isRouteEnabled,
                    onCheckedChange = { isRouteEnabled = it }
                )
            }

            if (isRouteEnabled) {
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Corridor Radius: ${routeCorridorValue.toInt()} meters",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Slider(
                    value = routeCorridorValue,
                    onValueChange = { routeCorridorValue = it },
                    valueRange = 30f..150f,
                    steps = 11,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = SafeBandColor.Primary,
                        activeTrackColor = SafeBandColor.Primary
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Current Route Status: ${routeState.displayName}" +
                        (distanceToRouteCorridor?.let { " (%.0f m from center-line)".format(it) } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = when (routeState) {
                        com.example.model.RouteState.ON_ROUTE -> SafeBandColor.Success
                        com.example.model.RouteState.APPROACHING_EDGE -> SafeBandColor.Warning
                        com.example.model.RouteState.ROUTE_DEVIATION -> SafeBandColor.Emergency
                        com.example.model.RouteState.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SafeBandSecondaryButton(
                    text = "Save Route Corridor Settings",
                    onClick = {
                        val updatedRoute = trustedRoute.copy(
                            isEnabled = isRouteEnabled,
                            corridorRadiusMeters = routeCorridorValue
                        )
                        viewModel.saveTrustedRoute(updatedRoute)
                        routeSavedMessage = "Trusted route corridor saved!"
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                routeSavedMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelSmall,
                        color = SafeBandColor.Success,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
