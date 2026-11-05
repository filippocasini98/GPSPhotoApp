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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gpsphotapp.AppViewModel
import com.example.gpsphotapp.CameraManager
import com.example.gpsphotapp.LocationHelper
import com.example.gpsphotapp.R
import com.example.gpsphotapp.ui.theme.ManualOrange

/**
 * Schermata dedicata alla sessione manuale: preview, etichette e
 * cattura punto per punto.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ManualSessionScreen(
    vm: AppViewModel,
    cameraManager: CameraManager,
    locationHelper: LocationHelper,
    windowSizeClass: WindowSizeClass,
    padding: PaddingValues,
    onNavigateSettings: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var showManualCapture by rememberSaveable { mutableStateOf(false) }
    var showManageLabels by rememberSaveable { mutableStateOf(false) }
    val isTablet = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded

    val controls: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AnimatedVisibility(
                visible = vm.isManualSession,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                ActiveCard(
                    title = stringResource(R.string.manual_session_active),
                    subtitle = stringResource(R.string.photos_count_fmt, vm.manualPhotoCount),
                    detail = vm.currentManualSessionDir?.name,
                    accent = ManualOrange
                )
            }

            GpsCard(locationHelper)

            if (vm.isManualSession) {
                Button(
                    onClick = { showManualCapture = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Icon(Icons.Outlined.AddCircle, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.capture_point),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(
                    onClick = { vm.stopManualSession() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.stop_manual))
                }
            } else {
                Button(
                    onClick = { vm.startManualSession() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ManualOrange,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Outlined.Bookmarks, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.start_manual),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
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

            OutlinedButton(
                onClick = { showManageLabels = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text(stringResource(R.string.manage_labels)) }
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

            // SCROLLABLE MIDDLE
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedVisibility(
                    visible = vm.isManualSession,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    ActiveCard(
                        title = stringResource(R.string.manual_session_active),
                        subtitle = stringResource(R.string.photos_count_fmt, vm.manualPhotoCount),
                        detail = vm.currentManualSessionDir?.name,
                        accent = ManualOrange
                    )
                }

                GpsCard(locationHelper)

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

                OutlinedButton(
                    onClick = { showManageLabels = true },
                    modifier = Modifier
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) { Text(stringResource(R.string.manage_labels)) }

                Spacer(Modifier.height(16.dp))
            }

            // FIXED BOTTOM: Capture Point and Stop/Start Buttons
            Surface(
                tonalElevation = 1.dp,
                shadowElevation = 4.dp
            ) {
                if (vm.isManualSession) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { showManualCapture = true },
                            modifier = Modifier
                                .weight(1.5f)
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Icon(Icons.Outlined.AddCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(
                                stringResource(R.string.capture_point),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = { vm.stopManualSession() },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(
                                stringResource(R.string.stop_manual),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = { vm.startManualSession() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ManualOrange,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Outlined.Bookmarks, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(
                            stringResource(R.string.start_manual),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showManualCapture) {
        ManualCaptureDialog(
            labels = vm.labels,
            onDismiss = { showManualCapture = false },
            onSave = { label, value ->
                vm.captureManual(label, value, cameraManager, locationHelper)
                showManualCapture = false
            }
        )
    }

    if (showManageLabels) {
        ManageLabelsDialog(
            labels = vm.labels,
            onDismiss = { showManageLabels = false },
            onAdd = { vm.addLabel(it) },
            onDelete = { vm.removeLabel(it) }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ManualCaptureDialog(
    labels: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var numericValue by rememberSaveable { mutableStateOf("") }
    var selectedLabel by rememberSaveable { mutableStateOf(labels.firstOrNull().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.capture_point)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = numericValue,
                    onValueChange = { numericValue = it },
                    label = { Text(stringResource(R.string.optional_numeric_value)) },
                    singleLine = true
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    labels.forEach { label ->
                        FilterChip(
                            selected = selectedLabel == label,
                            onClick = { selectedLabel = label },
                            label = { Text(label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selectedLabel.ifBlank { "Generic" }, numericValue) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ManageLabelsDialog(
    labels: List<String>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var newLabel by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_labels)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newLabel,
                        onValueChange = { newLabel = it },
                        label = { Text(stringResource(R.string.new_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilledIconButton(onClick = {
                        if (newLabel.isNotBlank()) {
                            onAdd(newLabel)
                            newLabel = ""
                        }
                    }) {
                        Icon(Icons.Outlined.AddCircle, contentDescription = stringResource(R.string.add))
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    labels.forEach { label ->
                        FilterChip(
                            selected = false,
                            onClick = { onDelete(label) },
                            label = { Text(label) }
                        )
                    }
                }
                Text(
                    stringResource(R.string.tap_label_to_remove),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        }
    )
}
