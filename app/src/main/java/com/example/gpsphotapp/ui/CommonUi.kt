package com.example.gpsphotapp.ui

import android.location.LocationManager
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPointFactory
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.gpsphotapp.AppViewModel
import com.example.gpsphotapp.CameraManager
import com.example.gpsphotapp.LocationHelper
import com.example.gpsphotapp.R
import com.example.gpsphotapp.ui.theme.LiveGreen
import com.example.gpsphotapp.ui.theme.ManualOrange
import com.example.gpsphotapp.ui.theme.RecordingRed
import kotlin.math.roundToInt

@Composable
internal fun StatusStrip(vm: AppViewModel, onSettingsClick: (() -> Unit)? = null) {
    val battery = (vm.batteryLevel() * 100).roundToInt()
    val storageBytes = vm.availableStorageBytes()
    val storageText = if (storageBytes >= 1_000_000_000L)
        String.format("%.1f GB", storageBytes / 1_000_000_000f)
    else
        "${storageBytes / 1_000_000} MB"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusPill(
            icon = if (battery < 20) Icons.Default.Battery0Bar else Icons.Default.BatteryFull,
            text = if (battery >= 0) "$battery%" else "—",
            warning = battery in 0..19
        )
        StatusPill(
            icon = Icons.Default.Storage,
            text = storageText,
            warning = storageBytes < 500_000_000L
        )
        
        if (onSettingsClick != null) {
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, warning: Boolean) {
    val container = if (warning) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val content = if (warning) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
        Text(text, color = content, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun WarningBanner(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
internal fun CameraPreviewCard(
    vm: AppViewModel,
    cameraManager: CameraManager,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    modifier: Modifier = Modifier
) {
    var preview by remember { mutableStateOf<PreviewView?>(null) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black)
            .pointerInput(cameraManager.currentZoom) {
                detectTransformGestures { _, _, zoom, _ ->
                    cameraManager.setZoom(cameraManager.currentZoom * zoom)
                }
            }
            .pointerInput(preview) {
                detectTapGestures { offset ->
                    val view = preview ?: return@detectTapGestures
                    val factory: MeteringPointFactory = view.meteringPointFactory
                    val point = factory.createPoint(offset.x, offset.y)
                    val action = FocusMeteringAction.Builder(point).build()
                    cameraManager.tapToFocus(action)
                }
            }
    ) {
        if (cameraManager.permissionGranted) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        cameraManager.attach(lifecycleOwner, this)
                        preview = this
                    }
                }
            )
            if (vm.showGrid) GridOverlay()
            if (vm.showLevel) LevelOverlay()

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (vm.isAutoRecording) StatusChip(stringResource(R.string.rec_auto), RecordingRed)
                if (vm.isManualSession) StatusChip(stringResource(R.string.rec_manual), ManualOrange)
            }

            val activeLens = cameraManager.availableCameras.firstOrNull { it.id == cameraManager.currentCameraId }
            val baseZoom = activeLens?.label?.removeSuffix("x")?.toFloatOrNull() ?: 1f
            val effectiveZoom = baseZoom * cameraManager.currentZoom
            Text(
                text = String.format("%.1fx", effectiveZoom),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xAA000000))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Camera permission required",
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun CameraLensChips(cameraManager: CameraManager) {
    if (cameraManager.availableCameras.size > 1) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cameraManager.availableCameras.forEach { lens ->
                FilterChip(
                    selected = lens.id == cameraManager.currentCameraId,
                    onClick = { cameraManager.switchCamera(lens) },
                    label = { Text(lens.label) }
                )
            }
        }
    }
}

@Composable
internal fun StatusChip(text: String, color: Color) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold
    )
}

@Composable
internal fun ActiveCard(title: String, subtitle: String, detail: String?, accent: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                detail?.let { Text(stringResource(R.string.folder_fmt, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
internal fun GpsCard(locationHelper: LocationHelper) {
    val loc = locationHelper.location
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.MyLocation,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (loc != null) MaterialTheme.colorScheme.primary else Color.Gray
            )

            if (loc != null) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${stringResource(R.string.latitude_fmt, loc.latitude)}, ${stringResource(R.string.longitude_fmt, loc.longitude)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "±${"%.1f".format(loc.accuracy)}m • ${(loc.provider ?: "UNK").uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val good = loc.accuracy < 10f
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = (if (good) LiveGreen else MaterialTheme.colorScheme.error).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (good) "GOOD" else "FAIR",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (good) LiveGreen else MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Text(
                    text = if (!locationHelper.isGpsHardwareAvailable()) "NO GPS HW"
                    else stringResource(R.string.gps_waiting),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun ExpandableCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures { open = !open } },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    if (open) "−" else "+",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            AnimatedVisibility(open) {
                Column(modifier = Modifier.padding(top = 12.dp)) { content() }
            }
        }
    }
}

@Composable
internal fun ManualFocusContent(vm: AppViewModel, cameraManager: CameraManager) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Manual", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = vm.isManualFocus,
                onCheckedChange = {
                    vm.isManualFocus = it
                    vm.persistSettings()
                    cameraManager.setFocus(vm.focusDistance, it)
                }
            )
        }
        if (vm.isManualFocus) {
            Slider(
                value = vm.focusDistance,
                onValueChange = {
                    vm.focusDistance = it
                    cameraManager.setFocus(it, true)
                },
                valueRange = 0f..10f
            )
            Text(
                String.format("Distance %.1f", vm.focusDistance),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun ExposureContent(cameraManager: CameraManager) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (cameraManager.exposureAutoMode) "Auto" else "Manual", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = !cameraManager.exposureAutoMode,
                onCheckedChange = { manual -> cameraManager.setExposureAuto(!manual) }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("EV", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            Text(
                String.format("%.1f", cameraManager.currentExposureBias),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = cameraManager.currentExposureBias,
            onValueChange = { cameraManager.setExposureBias(it) },
            valueRange = cameraManager.minExposureBias..cameraManager.maxExposureBias
        )
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
internal fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
