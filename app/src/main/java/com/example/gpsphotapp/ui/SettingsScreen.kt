package com.example.gpsphotapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gpsphotapp.AppViewModel
import com.example.gpsphotapp.PhotoQuality
import com.example.gpsphotapp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    rawSupported: Boolean,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleMedium) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection(
                title = stringResource(R.string.settings_capture),
                subtitle = stringResource(R.string.settings_capture_subtitle)
            ) {
                SettingInfoRow(
                    title = "Burst shots",
                    value = burstLabel(vm.burstCount),
                    description = "Take multiple consecutive shots per point. Useful when you want a higher chance of a sharp frame."
                )
                Slider(
                    value = vm.burstCount.toFloat(),
                    onValueChange = {
                        vm.burstCount = it.toInt().coerceIn(1, 5)
                        vm.persistSettings()
                    },
                    valueRange = 1f..5f,
                    steps = 3
                )

                Text("Photo quality", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Choose whether to favor speed or quality of capture.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PhotoQuality.entries.forEach { quality ->
                    QualityOption(
                        title = quality.label,
                        description = qualityDescription(quality),
                        selected = vm.photoQuality == quality,
                        onClick = {
                            vm.photoQuality = quality
                            vm.persistSettings()
                        }
                    )
                }
                if (rawSupported) {
                    SettingsToggle(
                        title = "RAW + JPEG",
                        description = "Save both the processed file and the RAW file. Heavier on storage.",
                        checked = vm.rawPlusJpeg
                    ) {
                        vm.rawPlusJpeg = it
                        vm.persistSettings()
                    }
                } else {
                    InfoCallout("RAW not available on this camera/device.")
                }
            }

            SettingsSection(
                title = stringResource(R.string.settings_feedback),
                subtitle = stringResource(R.string.settings_feedback_subtitle)
            ) {
                SettingsToggle(
                    title = "Haptic on capture",
                    description = "Brief vibration when a photo is saved.",
                    checked = vm.hapticEnabled
                ) {
                    vm.hapticEnabled = it
                    vm.persistSettings()
                }
                SettingsToggle(
                    title = "Shutter sound",
                    description = "Confirmation sound when a photo is captured.",
                    checked = vm.soundEnabled
                ) {
                    vm.soundEnabled = it
                    vm.persistSettings()
                }
            }

            SettingsSection(
                title = stringResource(R.string.settings_overlays),
                subtitle = stringResource(R.string.settings_overlays_subtitle)
            ) {
                SettingsToggle(
                    title = "Grid",
                    description = "Rule-of-thirds grid for composition.",
                    checked = vm.showGrid
                ) {
                    vm.showGrid = it
                    vm.persistSettings()
                }
                SettingsToggle(
                    title = "Level",
                    description = "Horizon level to keep shots straight.",
                    checked = vm.showLevel
                ) {
                    vm.showLevel = it
                    vm.persistSettings()
                }
            }

            SettingsSection(
                title = stringResource(R.string.settings_safety),
                subtitle = stringResource(R.string.settings_safety_subtitle)
            ) {
                SettingsToggle(
                    title = "Auto-stop on low battery",
                    description = "Stops the auto recording when battery is very low.",
                    checked = vm.autoStopOnLowBattery
                ) {
                    vm.autoStopOnLowBattery = it
                    vm.persistSettings()
                }
                SettingsToggle(
                    title = "Auto-stop on low storage",
                    description = "Stops the auto recording when free space is low.",
                    checked = vm.autoStopOnLowStorage
                ) {
                    vm.autoStopOnLowStorage = it
                    vm.persistSettings()
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}

@Composable
private fun SettingInfoRow(title: String, value: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QualityOption(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsToggle(title: String, description: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(12.dp))
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}

@Composable
private fun InfoCallout(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(12.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

private fun burstLabel(value: Int): String = if (value == 1) "1 photo" else "$value photos"

private fun qualityDescription(quality: PhotoQuality): String = when (quality) {
    PhotoQuality.SPEED -> "Faster, useful when capturing often or with moving subjects."
    PhotoQuality.BALANCED -> "Recommended balance between speed and quality."
    PhotoQuality.HIGH -> "Maximum quality, slower per shot."
}
