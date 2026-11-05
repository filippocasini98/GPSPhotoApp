package com.example.gpsphotapp.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gpsphotapp.AppViewModel
import com.example.gpsphotapp.ExportManager
import com.example.gpsphotapp.R
import com.example.gpsphotapp.SessionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    vm: AppViewModel,
    padding: PaddingValues,
    onOpenGallery: (File) -> Unit,
    onOpenMap: (File) -> Unit,
    onResumeSession: (SessionInfo) -> Boolean
) {
    val sessions = remember { mutableStateListOf<SessionInfo>() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var renameTarget by remember { mutableStateOf<SessionInfo?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<SessionInfo?>(null) }
    var resumeTarget by remember { mutableStateOf<SessionInfo?>(null) }
    var exporting by remember { mutableStateOf(false) }

    fun refresh() {
        sessions.clear()
        sessions.addAll(vm.loadSessions())
    }
    LaunchedEffect(vm.isAutoRecording, vm.isManualSession) { refresh() }

    fun runExport(sessionDir: File, type: ExportType) {
        if (exporting) return
        exporting = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                when (type) {
                    ExportType.GPX -> ExportManager.exportGpx(context, sessionDir)
                    ExportType.KML -> ExportManager.exportKml(context, sessionDir)
                    ExportType.GEOJSON -> ExportManager.exportGeoJson(context, sessionDir)
                    ExportType.ZIP -> ExportManager.exportZip(context, sessionDir)
                    ExportType.CSV -> ExportManager.exportCsv(context, sessionDir)
                    ExportType.GPKG -> ExportManager.exportGpkg(context, sessionDir)
                }
            }
            exporting = false
            when (result) {
                is ExportManager.ExportResult.Success -> {
                    if (!ExportManager.share(context, result.file, result.mime)) {
                        Toast.makeText(context, R.string.export_failed, Toast.LENGTH_SHORT).show()
                    }
                }
                is ExportManager.ExportResult.NoData ->
                    Toast.makeText(context, R.string.export_no_data, Toast.LENGTH_SHORT).show()
                is ExportManager.ExportResult.Error ->
                    Toast.makeText(context, R.string.export_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (sessions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.no_sessions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(sessions, key = { it.dir.absolutePath }) { session ->
                val isActive = session.dir == vm.currentAutoSessionDir ||
                        session.dir == vm.currentManualSessionDir
                val resumable = !isActive &&
                        !(session.isAuto && vm.isAutoRecording) &&
                        !(!session.isAuto && vm.isManualSession)
                SessionCard(
                    session = session,
                    isActive = isActive,
                    resumable = resumable,
                    onGallery = { onOpenGallery(session.dir) },
                    onMap = { onOpenMap(session.dir) },
                    onResume = { resumeTarget = session },
                    onRename = {
                        renameTarget = session
                        renameText = session.name
                    },
                    onDelete = { deleteTarget = session },
                    onExport = { type -> runExport(session.dir, type) }
                )
            }
        }
    }

    resumeTarget?.let { session ->
        AlertDialog(
            onDismissRequest = { resumeTarget = null },
            title = { Text(stringResource(R.string.resume_closed_title)) },
            text = { Text(stringResource(R.string.resume_closed_body, session.name)) },
            confirmButton = {
                Button(onClick = {
                    val ok = onResumeSession(session)
                    resumeTarget = null
                    if (!ok) {
                        Toast.makeText(context, R.string.resume_failed, Toast.LENGTH_SHORT).show()
                    }
                    refresh()
                }) { Text(stringResource(R.string.resume)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { resumeTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    renameTarget?.let { session ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.rename_session)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.session_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.renameSession(session, renameText)
                    renameTarget = null
                    refresh()
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    deleteTarget?.let { session ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(session.name) },
            text = { Text(stringResource(R.string.delete_session_body)) },
            confirmButton = {
                Button(onClick = {
                    vm.deleteSession(session)
                    deleteTarget = null
                    refresh()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

internal enum class ExportType { GPX, KML, GEOJSON, ZIP, CSV, GPKG }

@Composable
private fun SessionCard(
    session: SessionInfo,
    isActive: Boolean,
    resumable: Boolean,
    onGallery: () -> Unit,
    onMap: () -> Unit,
    onResume: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExport: (ExportType) -> Unit
) {
    var exportOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(session.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(session.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isActive) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                stringResource(R.string.session_active_chip),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    )
                    Spacer(Modifier.size(6.dp))
                }
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            stringResource(if (session.isAuto) R.string.auto else R.string.manual),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (session.isAuto)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            }

            Text(
                stringResource(R.string.photos_count_fmt, session.photoCount),
                style = MaterialTheme.typography.bodyMedium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onGallery, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Gallery")
                }
                OutlinedButton(onClick = onMap, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.map_title))
                }
            }

            if (resumable) {
                Button(
                    onClick = onResume,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.resume))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    OutlinedButton(onClick = { exportOpen = true }, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.IosShare, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.export))
                    }
                    DropdownMenu(expanded = exportOpen, onDismissRequest = { exportOpen = false }) {
                        listOf(
                            "GPX" to ExportType.GPX,
                            "KML" to ExportType.KML,
                            "GeoJSON" to ExportType.GEOJSON,
                            "ZIP" to ExportType.ZIP,
                            "CSV" to ExportType.CSV,
                            "GeoPackage" to ExportType.GPKG
                        ).forEach { (label, type) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    exportOpen = false
                                    onExport(type)
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRename, enabled = !isActive) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rename))
                }
                IconButton(onClick = onDelete, enabled = !isActive) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = if (isActive) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
