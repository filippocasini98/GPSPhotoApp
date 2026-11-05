package com.example.gpsphotapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.gpsphotapp.ExportManager
import com.example.gpsphotapp.R
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

data class MapPinUi(val point: ExportManager.SessionPoint)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    sessionDir: File,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<MapPinUi?>(null) }
    val pins = remember(sessionDir.absolutePath) {
        ExportManager.readPoints(sessionDir)
            .filter { it.latitude != null && it.longitude != null }
            .map { MapPinUi(it) }
    }

    remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = context.cacheDir
            osmdroidTileCache = File(context.cacheDir, "osmdroid-tiles").apply { mkdirs() }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.map_title), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (pins.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No geotagged points in this session",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        setUseDataConnection(true)
                        minZoomLevel = 2.0
                        maxZoomLevel = 19.0
                        overlays.clear()

                        pins.forEach { pin ->
                            val lat = pin.point.latitude ?: return@forEach
                            val lon = pin.point.longitude ?: return@forEach
                            val marker = Marker(this).apply {
                                position = GeoPoint(lat, lon)
                                title = pin.point.label
                                subDescription = pin.point.filename
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                setOnMarkerClickListener { _, _ ->
                                    selected = pin
                                    true
                                }
                            }
                            overlays.add(marker)
                        }

                        val lats = pins.mapNotNull { it.point.latitude }
                        val lons = pins.mapNotNull { it.point.longitude }
                        if (lats.size == 1) {
                            controller.setCenter(GeoPoint(lats[0], lons[0]))
                            controller.setZoom(17.0)
                        } else {
                            val pad = 0.0005
                            post {
                                runCatching {
                                    zoomToBoundingBox(
                                        BoundingBox(
                                            lats.max() + pad,
                                            lons.max() + pad,
                                            lats.min() - pad,
                                            lons.min() - pad
                                        ),
                                        false,
                                        80
                                    )
                                }
                            }
                        }
                        invalidate()
                    }
                }
            )

            selected?.let { pin ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AsyncImage(
                            model = File(sessionDir, pin.point.filename),
                            contentDescription = pin.point.filename,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black),
                            contentScale = ContentScale.Crop
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                            Text(pin.point.label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                            Text(pin.point.filename, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                String.format("%.6f, %.6f", pin.point.latitude, pin.point.longitude),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(pin.point.timestamp, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
