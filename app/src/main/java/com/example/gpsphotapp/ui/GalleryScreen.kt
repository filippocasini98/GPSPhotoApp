package com.example.gpsphotapp.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.gpsphotapp.AppViewModel
import com.example.gpsphotapp.ExportManager
import com.example.gpsphotapp.R
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    vm: AppViewModel,
    sessionDir: File,
    onBack: () -> Unit
) {
    val photos = remember { mutableStateListOf<File>() }
    var fullscreenPhoto by remember { mutableStateOf<File?>(null) }
    var pendingDelete by remember { mutableStateOf<File?>(null) }
    var editTarget by remember { mutableStateOf<File?>(null) }

    fun refresh() {
        photos.clear()
        photos.addAll(
            sessionDir.listFiles()
                ?.filter {
                    val ext = it.extension.lowercase()
                    ext == "jpg" || ext == "jpeg"
                }
                ?.sortedBy { it.name }
                ?: emptyList()
        )
    }

    LaunchedEffect(sessionDir.absolutePath) { refresh() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.gallery_title_fmt, photos.size),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
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
        if (photos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No photos in this session",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(photos, key = { it.absolutePath }) { photo ->
                    AsyncImage(
                        model = photo,
                        contentDescription = photo.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .combinedClickable(
                                onClick = { fullscreenPhoto = photo },
                                onLongClick = { pendingDelete = photo }
                            ),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }

    fullscreenPhoto?.let { photo ->
        AlertDialog(
            onDismissRequest = { fullscreenPhoto = null },
            title = { Text(photo.name, style = MaterialTheme.typography.titleSmall) },
            text = {
                AsyncImage(
                    model = photo,
                    contentDescription = photo.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
            },
            confirmButton = {
                Button(
                    onClick = { pendingDelete = photo },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        editTarget = photo
                        fullscreenPhoto = null
                    }) { Text(stringResource(R.string.edit)) }
                    OutlinedButton(onClick = { fullscreenPhoto = null }) { Text(stringResource(R.string.close)) }
                }
            }
        )
    }

    pendingDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_photo)) },
            text = { Text(stringResource(R.string.delete_photo_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        if (fullscreenPhoto == photo) fullscreenPhoto = null
                        pendingDelete = null
                        vm.deletePhoto(sessionDir, photo) { refresh() }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    editTarget?.let { photo ->
        EditPointDialog(
            vm = vm,
            sessionDir = sessionDir,
            photo = photo,
            onDismiss = { editTarget = null },
            onSaved = { editTarget = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditPointDialog(
    vm: AppViewModel,
    sessionDir: File,
    photo: File,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    // Valori correnti del punto dal CSV di sessione.
    val point = remember(photo.absolutePath) {
        ExportManager.readPoints(sessionDir).firstOrNull { it.filename == photo.name }
    }
    var selectedLabel by remember { mutableStateOf(point?.label ?: vm.labels.firstOrNull().orEmpty()) }
    var extraValue by remember { mutableStateOf(point?.extra ?: "") }
    var saveFailed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_point)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    photo.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = extraValue,
                    onValueChange = { extraValue = it },
                    label = { Text(stringResource(R.string.optional_numeric_value)) },
                    singleLine = true
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // L'etichetta corrente compare anche se non è più tra
                    // quelle configurate.
                    val options = (vm.labels + selectedLabel).distinct().filter { it.isNotBlank() }
                    options.forEach { label ->
                        FilterChip(
                            selected = selectedLabel == label,
                            onClick = { selectedLabel = label },
                            label = { Text(label) }
                        )
                    }
                }
                if (saveFailed) {
                    Text(
                        stringResource(R.string.edit_point_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                vm.updatePoint(
                    sessionDir,
                    photo.name,
                    selectedLabel.ifBlank { "Generic" },
                    extraValue
                ) { ok ->
                    if (ok) onSaved() else saveFailed = true
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
