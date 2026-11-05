package com.example.gpsphotapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.gpsphotapp.AppViewModel
import com.example.gpsphotapp.CameraManager
import com.example.gpsphotapp.CaptureMode
import com.example.gpsphotapp.LocationHelper
import com.example.gpsphotapp.R
import com.example.gpsphotapp.ui.theme.RecordingRed

/**
 * Schermata dedicata alla registrazione automatica: preview, stato GPS,
 * modalità tempo/distanza e pulsante di start/stop.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AutoRecordingScreen(
    vm: AppViewModel,
    cameraManager: CameraManager,
    locationHelper: LocationHelper,
    windowSizeClass: WindowSizeClass,
    padding: PaddingValues,
    onNavigateSettings: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var intervalText by rememberSaveable { mutableStateOf(vm.recordingInterval.toString()) }
    var distanceText by rememberSaveable { mutableStateOf(vm.captureDistance.toString()) }
    val isTablet = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded

    val controls: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AnimatedVisibility(
                visible = vm.isAutoRecording,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                ActiveCard(
                    title = stringResource(R.string.auto_session_active),
                    subtitle = stringResource(R.string.photos_count_fmt, vm.autoPhotoCount),
                    detail = vm.currentAutoSessionDir?.name,
                    accent = RecordingRed
                )
            }

            GpsCard(locationHelper)

            SectionTitle(stringResource(R.string.capture_mode))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CaptureMode.entries.forEach { mode ->
                    FilterChip(
                        selected = vm.captureMode == mode,
                        enabled = !vm.isAutoRecording,
                        onClick = {
                            vm.captureMode = mode
                            vm.persistSettings()
                        },
                        label = { Text(mode.label) }
                    )
                }
            }

            if (vm.captureMode == CaptureMode.TIME) {
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = {
                        intervalText = it
                        it.toDoubleOrNull()?.takeIf { v -> v > 0 }?.let { v -> vm.recordingInterval = v }
                    },
                    label = { Text(stringResource(R.string.interval_seconds_label)) },
                    singleLine = true,
                    enabled = !vm.isAutoRecording,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OutlinedTextField(
                    value = distanceText,
                    onValueChange = {
                        distanceText = it
                        it.toDoubleOrNull()?.takeIf { v -> v > 0 }?.let { v -> vm.captureDistance = v }
                    },
                    label = { Text(stringResource(R.string.distance_meters_label)) },
                    singleLine = true,
                    enabled = !vm.isAutoRecording,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = {
                    if (vm.isAutoRecording) {
                        vm.stopAutoRecording()
                    } else {
                        vm.persistSettings()
                        vm.startAutoRecording(cameraManager, locationHelper)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (vm.isAutoRecording) RecordingRed else MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    if (vm.isAutoRecording) Icons.Default.Stop else Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    stringResource(if (vm.isAutoRecording) R.string.stop_auto else R.string.start_auto),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            ExpandableCard(
                title = stringResource(R.string.exposure),
                icon = Icons.Default.Tune
            ) { ExposureContent(cameraManager) }

            ExpandableCard(
                title = stringResource(R.string.manual_focus),
                icon = Icons.Default.CenterFocusStrong
            ) { ManualFocusContent(vm, cameraManager) }

            ToggleRow(
                title = stringResource(R.string.include_gps_csv),
                checked = vm.saveLocation,
                onCheckedChange = {
                    vm.saveLocation = it
                    vm.persistSettings()
                }
            )
            Spacer(Modifier.size(8.dp))
        }
    }

    if (isTablet) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusStrip(vm, onNavigateSettings)
                vm.batteryWarning?.let { WarningBanner(it) }
                vm.storageWarning?.let { WarningBanner(it) }
                vm.captureError?.let { WarningBanner(it) }
                CameraPreviewCard(vm, cameraManager, lifecycleOwner, Modifier.aspectRatio(1f))
                CameraLensChips(cameraManager)
            }
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .verticalScroll(rememberScrollState())
            ) { controls() }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // FIXED TOP: Status, Warnings, Camera Preview
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusStrip(vm, onNavigateSettings)
                vm.batteryWarning?.let { WarningBanner(it) }
                vm.storageWarning?.let { WarningBanner(it) }
                vm.captureError?.let { WarningBanner(it) }
                CameraPreviewCard(vm, cameraManager, lifecycleOwner, Modifier.aspectRatio(4f / 3f))
                CameraLensChips(cameraManager)
            }

            // SCROLLABLE MIDDLE: All settings and GPS info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedVisibility(
                    visible = vm.isAutoRecording,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    ActiveCard(
                        title = stringResource(R.string.auto_session_active),
                        subtitle = stringResource(R.string.photos_count_fmt, vm.autoPhotoCount),
                        detail = vm.currentAutoSessionDir?.name,
                        accent = RecordingRed
                    )
                }

                GpsCard(locationHelper)

                SectionTitle(stringResource(R.string.capture_mode))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CaptureMode.entries.forEach { mode ->
                        FilterChip(
                            selected = vm.captureMode == mode,
                            enabled = !vm.isAutoRecording,
                            onClick = {
                                vm.captureMode = mode
                                vm.persistSettings()
                            },
                            label = { Text(mode.label) }
                        )
                    }
                }

                if (vm.captureMode == CaptureMode.TIME) {
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = {
                            intervalText = it
                            it.toDoubleOrNull()?.takeIf { v -> v > 0 }?.let { v -> vm.recordingInterval = v }
                        },
                        label = { Text(stringResource(R.string.interval_seconds_label)) },
                        singleLine = true,
                        enabled = !vm.isAutoRecording,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = distanceText,
                        onValueChange = {
                            distanceText = it
                            it.toDoubleOrNull()?.takeIf { v -> v > 0 }?.let { v -> vm.captureDistance = v }
                        },
                        label = { Text(stringResource(R.string.distance_meters_label)) },
                        singleLine = true,
                        enabled = !vm.isAutoRecording,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                ExpandableCard(
                    title = stringResource(R.string.exposure),
                    icon = Icons.Default.Tune
                ) { ExposureContent(cameraManager) }

                ExpandableCard(
                    title = stringResource(R.string.manual_focus),
                    icon = Icons.Default.CenterFocusStrong
                ) { ManualFocusContent(vm, cameraManager) }

                ToggleRow(
                    title = stringResource(R.string.include_gps_csv),
                    checked = vm.saveLocation,
                    onCheckedChange = {
                        vm.saveLocation = it
                        vm.persistSettings()
                    }
                )
                Spacer(Modifier.size(8.dp))
            }

            // FIXED BOTTOM: Start/Stop Button
            Surface(
                tonalElevation = 1.dp,
                shadowElevation = 4.dp
            ) {
                Button(
                    onClick = {
                        if (vm.isAutoRecording) {
                            vm.stopAutoRecording()
                        } else {
                            vm.persistSettings()
                            vm.startAutoRecording(cameraManager, locationHelper)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (vm.isAutoRecording) RecordingRed else MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        if (vm.isAutoRecording) Icons.Default.Stop else Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(if (vm.isAutoRecording) R.string.stop_auto else R.string.start_auto),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (vm.resumePromptVisible && vm.resumableState != null) {
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
            title = { Text(stringResource(R.string.resume_session_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.resume_session_body,
                        vm.resumableState?.photoCount ?: 0
                    )
                )
            },
            confirmButton = {
                Button(onClick = { vm.resumeSession(cameraManager, locationHelper) }) {
                    Text(stringResource(R.string.resume))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { vm.discardResumableState() }) {
                    Text(stringResource(R.string.discard))
                }
            }
        )
    }
}
