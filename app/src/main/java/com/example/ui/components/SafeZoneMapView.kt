package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.GeoAddress
import com.example.model.SafeZone

/**
 * Real interactive Street Map powered by Leaflet & OpenStreetMap.
 * Displays real-world streets, roads, areas, safe zone boundary circle,
 * center pin, and live child location pin with tap-to-set capability.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SafeZoneMapView(
    safeZone: SafeZone,
    childLat: Double? = null,
    childLon: Double? = null,
    isChildOutside: Boolean = false,
    resolvedAddress: GeoAddress? = null,
    verifiedLocation: com.example.model.VerifiedLocation? = null,
    geofenceState: com.example.model.GeofenceState = if (isChildOutside) com.example.model.GeofenceState.OUTSIDE else com.example.model.GeofenceState.SAFE,
    routeWaypoints: List<com.example.model.RouteWaypoint> = emptyList(),
    corridorRadiusMeters: Float = 60f,
    onMapLocationSelected: (Double, Double) -> Unit = { _, _ -> },
    onUseCurrentLocation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isSatelliteView by remember { mutableStateOf(false) }

    val streetTileUrl = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    val satelliteTileUrl = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"

    val tileUrl = if (isSatelliteView) satelliteTileUrl else streetTileUrl

    val htmlContent = remember(safeZone.latitude, safeZone.longitude, safeZone.radiusMeters, tileUrl, childLat, childLon, routeWaypoints, corridorRadiusMeters) {
        generateMapHtml(
            lat = safeZone.latitude,
            lon = safeZone.longitude,
            radiusMeters = safeZone.radiusMeters,
            zoneName = safeZone.name,
            tileUrl = tileUrl,
            childLat = childLat,
            childLon = childLon,
            isOutside = isChildOutside,
            routeWaypoints = routeWaypoints,
            corridorRadiusMeters = corridorRadiusMeters
        )
    }

    LaunchedEffect(htmlContent) {
        webViewRef?.loadDataWithBaseURL("file:///android_asset/leaflet/", htmlContent, "text/html", "UTF-8", null)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Interactive Map Header & Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF10B981).copy(alpha = 0.2f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PinDrop,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier
                                .padding(5.dp)
                                .size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Live Safe Zone Map",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Tap anywhere on map to reposition safe zone",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 11.sp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Toggle Street / Satellite View
                    IconButton(
                        onClick = { isSatelliteView = !isSatelliteView },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Toggle Layer",
                            tint = if (isSatelliteView) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Use GPS button
                    IconButton(
                        onClick = onUseCurrentLocation,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "My GPS Location",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // WebView Interactive Map Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(310.dp)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = true
                            settings.allowContentAccess = true
                            settings.userAgentString = "SafeBandGuardian/1.1 (Android; com.aistudio.safeband.xqmt)"
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = false
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false

                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun onMapClicked(lat: Double, lon: Double) {
                                    onMapLocationSelected(lat, lon)
                                }
                            }, "AndroidBridge")

                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    view?.evaluateJavascript("if (typeof map !== 'undefined' && map) { map.invalidateSize(true); }", null)
                                }
                            }
                            loadDataWithBaseURL("file:///android_asset/leaflet/", htmlContent, "text/html", "UTF-8", null)
                            webViewRef = this
                        }
                    },
                    update = { view ->
                        webViewRef = view
                    },
                    onRelease = { view ->
                        view.stopLoading()
                        view.destroy()
                        webViewRef = null
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay Badge: Current Geofence Status
                val badgeColor = when (geofenceState) {
                    com.example.model.GeofenceState.OUTSIDE -> Color(0xDDDC2626)
                    com.example.model.GeofenceState.EXIT_PENDING -> Color(0xDDF97316)
                    com.example.model.GeofenceState.APPROACHING -> Color(0xDDFBBF24)
                    com.example.model.GeofenceState.REENTERED -> Color(0xDD047857)
                    com.example.model.GeofenceState.SAFE -> Color(0xDD065F46)
                }
                val badgeText = when (geofenceState) {
                    com.example.model.GeofenceState.OUTSIDE -> "OUTSIDE SAFE ZONE"
                    com.example.model.GeofenceState.EXIT_PENDING -> "EXIT PENDING (CONFIRMING)"
                    com.example.model.GeofenceState.APPROACHING -> "APPROACHING BOUNDARY"
                    com.example.model.GeofenceState.REENTERED -> "RE-ENTERED SAFE ZONE"
                    com.example.model.GeofenceState.SAFE -> "CHILD IN SAFE ZONE"
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = badgeColor,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Phase 6: Verified Location Bar (Never claiming live when stale or unverified)
            Surface(
                color = Color(0xFF0F172A),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = if (verifiedLocation?.confidence == com.example.model.LocationConfidence.HIGH) Color(0xFF10B981) else Color(0xFF38BDF8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (verifiedLocation != null)
                                "LAST VERIFIED: ${verifiedLocation.getRelativeTimeString()}"
                            else
                                "LOCATION: Last known safe coordinates",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp
                        )
                    }

                    if (verifiedLocation != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (verifiedLocation.confidence) {
                                com.example.model.LocationConfidence.HIGH -> Color(0xFF065F46)
                                com.example.model.LocationConfidence.MEDIUM -> Color(0xFF92400E)
                                else -> Color(0xFF334155)
                            }
                        ) {
                            Text(
                                text = "${verifiedLocation.confidence.displayName} (±${verifiedLocation.accuracyMeters.toInt()}m)",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Structured Physical Address Card
            Surface(
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
            ) {
                var isExpanded by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Safe Zone Physical Address",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                        }

                        if (resolvedAddress != null && (resolvedAddress.street.isNotBlank() || resolvedAddress.fullAddress.length > 50)) {
                            Text(
                                text = if (isExpanded) "Show Less" else "View Full Address",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clickable { isExpanded = !isExpanded }
                                    .padding(start = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (resolvedAddress != null) {
                        val primaryText = when {
                            resolvedAddress.street.isNotBlank() -> resolvedAddress.street
                            resolvedAddress.area.isNotBlank() -> resolvedAddress.area
                            else -> resolvedAddress.fullAddress.take(45)
                        }
                        val secondaryParts = mutableListOf<String>()
                        if (resolvedAddress.area.isNotBlank() && resolvedAddress.street.isNotBlank()) secondaryParts.add(resolvedAddress.area)
                        if (resolvedAddress.city.isNotBlank()) secondaryParts.add(resolvedAddress.city)
                        if (resolvedAddress.pinCode.isNotBlank()) secondaryParts.add(resolvedAddress.pinCode)
                        val secondaryText = secondaryParts.joinToString(", ")

                        Text(
                            text = primaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = if (isExpanded) 4 else 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (secondaryText.isNotBlank()) {
                            Text(
                                text = secondaryText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Normal,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = if (isExpanded) 3 else 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isExpanded && resolvedAddress.fullAddress.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = resolvedAddress.fullAddress,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.65f),
                                lineHeight = 16.sp
                            )
                        }
                    } else {
                        Text(
                            text = "Resolving physical address...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Structured Lat / Lon / Radius Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lat: %.5f".format(java.util.Locale.US, safeZone.latitude),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Lon: %.5f".format(java.util.Locale.US, safeZone.longitude),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Radius: %.0f m".format(java.util.Locale.US, safeZone.radiusMeters),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF34D399),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

private fun generateMapHtml(
    lat: Double,
    lon: Double,
    radiusMeters: Float,
    zoneName: String,
    tileUrl: String,
    childLat: Double?,
    childLon: Double?,
    isOutside: Boolean,
    routeWaypoints: List<com.example.model.RouteWaypoint> = emptyList(),
    corridorRadiusMeters: Float = 60f
): String {
    val childMarkerScript = if (childLat != null && childLon != null) {
        val childColor = if (isOutside) "#EF4444" else "#10B981"
        """
        var childMarker = L.circleMarker([$childLat, $childLon], {
            radius: 9,
            fillColor: '$childColor',
            color: '#FFFFFF',
            weight: 3,
            opacity: 1,
            fillOpacity: 0.95
        }).addTo(map);
        childMarker.bindPopup("<b>Child Device Location</b><br>Active Status");
        """
    } else ""

    val routeScript = if (routeWaypoints.size >= 2) {
        val pointsJs = routeWaypoints.joinToString(", ") { "[${it.latitude}, ${it.longitude}]" }
        """
        var routePts = [$pointsJs];
        var corridorBand = L.polyline(routePts, {
            color: '#0284C7',
            weight: 20,
            opacity: 0.25
        }).addTo(map);
        var routeLine = L.polyline(routePts, {
            color: '#38BDF8',
            weight: 4,
            dashArray: '6, 8'
        }).addTo(map);
        """
    } else ""

    return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
        <link rel="stylesheet" href="leaflet.css" />
        <script src="leaflet.js"></script>
        <style>
            html, body {
                margin: 0;
                padding: 0;
                width: 100%;
                height: 100%;
                min-height: 100%;
                background-color: #0F172A;
                overflow: hidden;
            }
            #map {
                position: absolute;
                top: 0;
                bottom: 0;
                left: 0;
                right: 0;
                width: 100%;
                height: 100%;
                min-height: 280px;
                background-color: #0F172A;
            }
            .leaflet-container {
                background: #0F172A;
                width: 100%;
                height: 100%;
            }
            .pulse-circle {
                border-radius: 50%;
                box-shadow: 0 0 15px rgba(16, 185, 129, 0.7);
            }
        </style>
    </head>
    <body>
        <div id="map"></div>
        <script>
            var map;
            function initMap() {
                if (typeof L === 'undefined') {
                    setTimeout(initMap, 100);
                    return;
                }
                map = L.map('map', {
                    center: [$lat, $lon],
                    zoom: 15,
                    zoomControl: true,
                    attributionControl: false
                });

                L.tileLayer('$tileUrl', {
                    maxZoom: 19,
                    subdomains: ['a', 'b', 'c']
                }).addTo(map);

                // Safe Zone Circle
                var circle = L.circle([$lat, $lon], {
                    color: '#059669',
                    fillColor: '#10B981',
                    fillOpacity: 0.25,
                    weight: 3,
                    radius: $radiusMeters
                }).addTo(map);

                // Safe Zone Center Pin
                var centerMarker = L.marker([$lat, $lon]).addTo(map);
                centerMarker.bindPopup("<b>🛡️ Safe Zone</b><br>Radius: ${radiusMeters.toInt()}m").openPopup();

                $childMarkerScript
                $routeScript

                // Click listener to set new safe zone coordinates
                map.on('click', function(e) {
                    var clickedLat = e.latlng.lat;
                    var clickedLon = e.latlng.lng;
                    circle.setLatLng([clickedLat, clickedLon]);
                    centerMarker.setLatLng([clickedLat, clickedLon]);
                    if (window.AndroidBridge && window.AndroidBridge.onMapClicked) {
                        window.AndroidBridge.onMapClicked(clickedLat, clickedLon);
                    }
                });

                function invalidate() {
                    if (typeof map !== 'undefined' && map) {
                        map.invalidateSize(true);
                    }
                }
                setTimeout(invalidate, 100);
                setTimeout(invalidate, 300);
                setTimeout(invalidate, 800);
                window.addEventListener('resize', invalidate);
                window.addEventListener('load', invalidate);
            }

            if (document.readyState === 'complete' || document.readyState === 'interactive') {
                initMap();
            } else {
                window.addEventListener('DOMContentLoaded', initMap);
            }
        </script>
    </body>
    </html>
    """.trimIndent()
}
