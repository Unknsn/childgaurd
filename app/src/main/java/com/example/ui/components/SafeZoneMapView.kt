package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    onMapLocationSelected: (Double, Double) -> Unit = { _, _ -> },
    onUseCurrentLocation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isSatelliteView by remember { mutableStateOf(false) }

    val streetTileUrl = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    val satelliteTileUrl = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"

    val tileUrl = if (isSatelliteView) satelliteTileUrl else streetTileUrl

    val htmlContent = remember(safeZone.latitude, safeZone.longitude, safeZone.radiusMeters, tileUrl, childLat, childLon) {
        generateMapHtml(
            lat = safeZone.latitude,
            lon = safeZone.longitude,
            radiusMeters = safeZone.radiusMeters,
            zoneName = safeZone.name,
            tileUrl = tileUrl,
            childLat = childLat,
            childLon = childLon,
            isOutside = isChildOutside
        )
    }

    LaunchedEffect(htmlContent) {
        webViewRef?.loadDataWithBaseURL("https://openstreetmap.org", htmlContent, "text/html", "UTF-8", null)
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
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false

                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun onMapClicked(lat: Double, lon: Double) {
                                    onMapLocationSelected(lat, lon)
                                }
                            }, "AndroidBridge")

                            webViewClient = object : WebViewClient() {}
                            loadDataWithBaseURL("https://openstreetmap.org", htmlContent, "text/html", "UTF-8", null)
                            webViewRef = this
                        }
                    },
                    update = { view ->
                        webViewRef = view
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay Badge: Current Geofence Status
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isChildOutside) Color(0xDDDC2626) else Color(0xDD065F46),
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
                            color = if (isChildOutside) Color.White else Color(0xFF34D399),
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isChildOutside) "CHILD OUTSIDE ZONE" else "CHILD IN SAFE ZONE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Resolved Physical Address Banner
            resolvedAddress?.let { addr ->
                Surface(
                    color = Color(0xFF1E293B),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
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
                                text = "Safe Zone Physical Address:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = addr.formattedSummary(),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Lat: %.5f, Lon: %.5f • Radius: %.0f meters".format(safeZone.latitude, safeZone.longitude, safeZone.radiusMeters),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.65f),
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
    isOutside: Boolean
): String {
    val childMarkerScript = if (childLat != null && childLon != null) {
        val childColor = if (isOutside) "#EF4444" else "#10B981"
        """
        var childMarker = L.circleMarker([$childLat, $childLon], {
            radius: 10,
            fillColor: '$childColor',
            color: '#FFFFFF',
            weight: 3,
            opacity: 1,
            fillOpacity: 0.95
        }).addTo(map);
        childMarker.bindPopup("<b>👶 Child Device</b><br>Lat: $childLat, Lon: $childLon");
        """
    } else ""

    return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        <style>
            html, body {
                margin: 0;
                padding: 0;
                width: 100%;
                height: 100%;
                background-color: #0F172A;
            }
            #map {
                width: 100%;
                height: 100%;
            }
            .leaflet-container {
                background: #0F172A;
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
            var map = L.map('map', {
                center: [$lat, $lon],
                zoom: 15,
                zoomControl: true,
                attributionControl: false
            });

            L.tileLayer('$tileUrl', {
                maxZoom: 19
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
            centerMarker.bindPopup("<b>🛡️ Safe Zone</b><br>$zoneName<br>Radius: ${radiusMeters.toInt()}m").openPopup();

            $childMarkerScript

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
        </script>
    </body>
    </html>
    """.trimIndent()
}
