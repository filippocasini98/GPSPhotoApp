package com.example.gpsphotapp

import android.content.Context
import android.location.Location
import android.os.Environment
import android.util.Log
import mil.nga.geopackage.GeoPackage
import mil.nga.geopackage.GeoPackageFactory
import mil.nga.geopackage.GeoPackageManager
import mil.nga.geopackage.BoundingBox
import mil.nga.geopackage.features.columns.GeometryColumns
import mil.nga.geopackage.features.user.FeatureColumn
import mil.nga.geopackage.features.user.FeatureRow
import mil.nga.geopackage.features.user.FeatureTableMetadata
import mil.nga.geopackage.geom.GeoPackageGeometryData
import mil.nga.sf.Point
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Equivalente Android di DataManager.swift su iOS.
 * Centralizza la directory di storage dell'app, la scrittura su CSV e GeoPackage.
 */
object DataManager {

    fun getAppStorageDir(context: Context): File {
        // getExternalFilesDir può restituire null se lo storage esterno non è
        // disponibile: in quel caso si ripiega sullo storage interno.
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        val dir = File(base, "GPSPhotoApp")
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e("DataManager", "Cannot create storage dir ${dir.absolutePath}")
        }
        return dir
    }

    // ---- CSV ----

    fun initCsv(file: File) {
        try {
            if (!file.exists()) {
                FileOutputStream(file).use {
                    val header =
                        "filename,latitude,longitude,altitude,accuracy_m,timestamp,label,extra_value\n"
                    it.write(header.toByteArray())
                }
            }
        } catch (e: Exception) {
            Log.e("CSV", "Init Error", e)
        }
    }

    fun csvEscape(s: String): String {
        return if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else s
    }

    fun isoTimestamp(timeMs: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return fmt.format(Date(timeMs))
    }

    fun writeToCsv(
        file: File,
        filename: String,
        loc: Location?,
        label: String,
        extra: String = ""
    ) {
        try {
            if (!file.exists()) initCsv(file)
            val timeToFormat = loc?.time ?: System.currentTimeMillis()
            val timestamp = isoTimestamp(timeToFormat)
            val lat = loc?.latitude?.toString() ?: ""
            val lng = loc?.longitude?.toString() ?: ""
            val alt = loc?.altitude?.toString() ?: ""
            val acc = loc?.accuracy?.toString() ?: ""
            val line =
                "${csvEscape(filename)},$lat,$lng,$alt,$acc,$timestamp,${csvEscape(label)},${csvEscape(extra)}\n"
            FileOutputStream(file, true).use { it.write(line.toByteArray()) }
        } catch (e: Exception) {
            Log.e("CSV", "Write Error", e)
        }
    }

    // ---- GeoPackage ----
    //
    // Durante una sessione il database resta aperto nello storage interno del
    // GeoPackageManager e ogni foto è un semplice INSERT. Il file data.gpkg
    // nella cartella di sessione viene rigenerato con un export atomico
    // (file temporaneo + rename) ogni FLUSH_EVERY inserimenti e alla chiusura
    // della sessione. Tutte le chiamate arrivano dal dispatcher di salvataggio
    // single-thread di AppViewModel; il lock protegge solo la mappa.

    private const val GPKG_FILENAME = "data.gpkg"
    private const val GPKG_TMP_FILENAME = "data.tmp.gpkg"
    private const val FLUSH_EVERY = 20

    private class LiveSession(
        val databaseName: String,
        var geoPackage: GeoPackage,
        var insertsSinceFlush: Int = 0
    )

    private val liveSessions = mutableMapOf<String, LiveSession>()

    private fun gpkgManager(context: Context): GeoPackageManager =
        GeoPackageFactory.getManager(context)

    fun openSessionGeoPackage(context: Context, targetDir: File) {
        val key = targetDir.absolutePath
        synchronized(liveSessions) {
            if (liveSessions.containsKey(key)) return
        }
        try {
            val manager = gpkgManager(context)
            val databaseName = "live_${targetDir.name}"
            if (manager.databases().contains(databaseName)) manager.delete(databaseName)
            // Residuo di un export interrotto da un crash.
            File(targetDir, GPKG_TMP_FILENAME).takeIf { it.exists() }?.delete()
            val gpkgFile = File(targetDir, GPKG_FILENAME)
            val existed = gpkgFile.exists()
            if (existed) {
                manager.importGeoPackage(databaseName, gpkgFile)
            } else {
                manager.create(databaseName)
            }
            val gpkg: GeoPackage = manager.open(databaseName)
            if (!gpkg.featureTables.contains("points")) {
                createPointsTable(gpkg)
            }
            val live = LiveSession(databaseName, gpkg)
            synchronized(liveSessions) { liveSessions[key] = live }
            if (!existed) flushSession(context, targetDir, live)
        } catch (e: Exception) {
            Log.e("GPKG", "Open session error", e)
        }
    }

    fun closeSessionGeoPackage(context: Context, targetDir: File) {
        val live = synchronized(liveSessions) {
            liveSessions.remove(targetDir.absolutePath)
        } ?: return
        try {
            val manager = gpkgManager(context)
            live.geoPackage.close()
            exportAtomically(manager, live.databaseName, targetDir)
            manager.delete(live.databaseName)
        } catch (e: Exception) {
            Log.e("GPKG", "Close session error", e)
        }
    }

    fun writeToGeoPackage(
        context: Context,
        targetDir: File,
        filename: String,
        loc: Location?,
        label: String,
        extra: String
    ) {
        try {
            val live = synchronized(liveSessions) {
                liveSessions[targetDir.absolutePath]
            }
            if (live != null) {
                insertPoint(live.geoPackage, filename, loc, label, extra)
                live.insertsSinceFlush++
                if (live.insertsSinceFlush >= FLUSH_EVERY) {
                    flushSession(context, targetDir, live)
                }
            } else {
                writeOneShot(context, targetDir, filename, loc, label, extra)
            }
        } catch (e: Exception) {
            Log.e("GPKG", "Write Error", e)
        }
    }

    fun initGeoPackage(context: Context, targetDir: File) {
        try {
            val gpkgFile = File(targetDir, GPKG_FILENAME)
            if (gpkgFile.exists()) return

            val manager = gpkgManager(context)
            val databaseName = "init_${System.currentTimeMillis()}"
            manager.create(databaseName)
            try {
                val gpkg: GeoPackage = manager.open(databaseName)
                try {
                    createPointsTable(gpkg)
                } finally {
                    gpkg.close()
                }
                exportAtomically(manager, databaseName, targetDir)
            } finally {
                manager.delete(databaseName)
            }
        } catch (e: Exception) {
            Log.e("GPKG", "Init Error", e)
        }
    }

    // Percorso di riserva per scritture arrivate a sessione già chiusa
    // (es. callback di uno scatto ancora in volo al momento dello stop).
    private fun writeOneShot(
        context: Context,
        targetDir: File,
        filename: String,
        loc: Location?,
        label: String,
        extra: String
    ) {
        val gpkgFile = File(targetDir, GPKG_FILENAME)
        if (!gpkgFile.exists()) initGeoPackage(context, targetDir)
        val manager = gpkgManager(context)
        val databaseName = "oneshot_${System.currentTimeMillis()}"
        if (manager.databases().contains(databaseName)) manager.delete(databaseName)
        manager.importGeoPackage(databaseName, gpkgFile)
        try {
            val gpkg = manager.open(databaseName)
            try {
                insertPoint(gpkg, filename, loc, label, extra)
            } finally {
                gpkg.close()
            }
            exportAtomically(manager, databaseName, targetDir)
        } finally {
            manager.delete(databaseName)
        }
    }

    // L'export della libreria copia il file del database, quindi il GeoPackage
    // va chiuso prima di esportare e riaperto dopo.
    private fun flushSession(context: Context, targetDir: File, live: LiveSession) {
        val manager = gpkgManager(context)
        live.geoPackage.close()
        exportAtomically(manager, live.databaseName, targetDir)
        live.geoPackage = manager.open(live.databaseName)
        live.insertsSinceFlush = 0
    }

    private fun exportAtomically(
        manager: GeoPackageManager,
        databaseName: String,
        targetDir: File
    ) {
        val tmp = File(targetDir, GPKG_TMP_FILENAME)
        if (tmp.exists()) tmp.delete()
        manager.exportGeoPackage(databaseName, tmp)
        val dest = File(targetDir, GPKG_FILENAME)
        if (!tmp.renameTo(dest)) {
            Log.e("GPKG", "Rename ${tmp.name} -> ${dest.name} failed")
        }
    }

    private fun createPointsTable(gpkg: GeoPackage) {
        val gc = GeometryColumns().apply {
            tableName = "points"
            columnName = "geom"
            geometryType = mil.nga.sf.GeometryType.POINT
            srsId = 4326
            z = 0.toByte()
            m = 0.toByte()
        }
        val cols = listOf(
            FeatureColumn.createPrimaryKeyColumn("id"),
            FeatureColumn.createColumn("filename", mil.nga.geopackage.db.GeoPackageDataType.TEXT, false, null),
            FeatureColumn.createColumn("latitude", mil.nga.geopackage.db.GeoPackageDataType.DOUBLE, false, null),
            FeatureColumn.createColumn("longitude", mil.nga.geopackage.db.GeoPackageDataType.DOUBLE, false, null),
            FeatureColumn.createColumn("altitude", mil.nga.geopackage.db.GeoPackageDataType.DOUBLE, false, null),
            FeatureColumn.createColumn("accuracy_m", mil.nga.geopackage.db.GeoPackageDataType.DOUBLE, false, null),
            FeatureColumn.createColumn("timestamp", mil.nga.geopackage.db.GeoPackageDataType.TEXT, false, null),
            FeatureColumn.createColumn("label", mil.nga.geopackage.db.GeoPackageDataType.TEXT, false, null),
            FeatureColumn.createColumn("extra_value", mil.nga.geopackage.db.GeoPackageDataType.TEXT, false, null)
        )
        val bbox = BoundingBox(-180.0, -90.0, 180.0, 90.0)
        val metadata = FeatureTableMetadata.create(gc, cols, bbox)
        gpkg.createFeatureTable(metadata)
    }

    /**
     * Ricostruisce data.gpkg a partire dai punti del CSV. Usato dall'export
     * quando il GeoPackage manca (sessioni vecchie o scritture fallite).
     */
    fun rebuildGeoPackageFromPoints(
        context: Context,
        sessionDir: File,
        points: List<ExportManager.SessionPoint>
    ): Boolean {
        // Se la sessione è live non si tocca: il file verrà riscritto dal flush.
        synchronized(liveSessions) {
            if (liveSessions.containsKey(sessionDir.absolutePath)) return true
        }
        return try {
            val manager = gpkgManager(context)
            val databaseName = "rebuild_${System.currentTimeMillis()}"
            if (manager.databases().contains(databaseName)) manager.delete(databaseName)
            manager.create(databaseName)
            try {
                val gpkg = manager.open(databaseName)
                try {
                    createPointsTable(gpkg)
                    val dao = gpkg.getFeatureDao("points")
                    points.forEach { p ->
                        val row: FeatureRow = dao.newRow()
                        if (p.latitude != null && p.longitude != null) {
                            val point = Point(p.longitude, p.latitude)
                            val geomData = GeoPackageGeometryData(4326)
                            geomData.geometry = point
                            row.geometry = geomData
                            row.setValue("latitude", p.latitude)
                            row.setValue("longitude", p.longitude)
                            p.altitude?.let { row.setValue("altitude", it) }
                            p.accuracy?.let { row.setValue("accuracy_m", it) }
                        }
                        row.setValue("filename", p.filename)
                        row.setValue("timestamp", p.timestamp)
                        row.setValue("label", p.label)
                        row.setValue("extra_value", p.extra)
                        dao.insert(row)
                    }
                } finally {
                    gpkg.close()
                }
                exportAtomically(manager, databaseName, sessionDir)
            } finally {
                manager.delete(databaseName)
            }
            true
        } catch (e: Exception) {
            Log.e("GPKG", "Rebuild Error", e)
            false
        }
    }

    // ---- Modifica di sessioni esistenti ----
    //
    // Aggiorna o elimina un punto (identificato dal nome file della foto) sia
    // nel CSV sia nel GeoPackage. Se la sessione è ancora aperta usa il
    // database live e forza un flush; altrimenti fa un ciclo one-shot
    // import → modifica → export atomico.

    fun updatePoint(
        context: Context,
        sessionDir: File,
        filename: String,
        newLabel: String,
        newExtra: String
    ): Boolean {
        val csvOk = rewriteCsv(sessionDir) { fields ->
            if (fields[0] == filename && fields.size >= 8) {
                fields.toMutableList().apply {
                    this[6] = newLabel
                    this[7] = newExtra
                }
            } else fields
        }
        val gpkgOk = editGeoPackage(context, sessionDir) { gpkg ->
            val dao = gpkg.getFeatureDao("points")
            val rows = mutableListOf<FeatureRow>()
            val cursor = dao.queryForEq("filename", filename)
            try {
                while (cursor.moveToNext()) rows.add(cursor.row)
            } finally {
                cursor.close()
            }
            rows.forEach { row ->
                row.setValue("label", newLabel)
                row.setValue("extra_value", newExtra)
                dao.update(row)
            }
        }
        return csvOk && gpkgOk
    }

    fun deletePoint(context: Context, sessionDir: File, filename: String): Boolean {
        val csvOk = rewriteCsv(sessionDir) { fields ->
            if (fields[0] == filename) null else fields
        }
        val gpkgOk = editGeoPackage(context, sessionDir) { gpkg ->
            gpkg.getFeatureDao("points").delete("filename = ?", arrayOf(filename))
        }
        return csvOk && gpkgOk
    }

    // transform: record -> record modificato, o null per eliminarlo.
    // L'header (primo record) è sempre conservato. Scrittura atomica.
    private fun rewriteCsv(
        sessionDir: File,
        transform: (List<String>) -> List<String>?
    ): Boolean {
        val csv = File(sessionDir, "summary.csv")
        if (!csv.exists()) return false
        return try {
            val records = ExportManager.parseCsvRecords(csv.readText())
            if (records.isEmpty()) return false
            val sb = StringBuilder()
            records.forEachIndexed { index, fields ->
                val out = (if (index == 0) fields else transform(fields)) ?: return@forEachIndexed
                sb.append(out.joinToString(",") { csvEscape(it) }).append('\n')
            }
            val tmp = File(sessionDir, "summary.tmp.csv")
            tmp.writeText(sb.toString())
            tmp.renameTo(csv)
        } catch (e: Exception) {
            Log.e("CSV", "Rewrite Error", e)
            false
        }
    }

    private fun editGeoPackage(
        context: Context,
        sessionDir: File,
        edit: (GeoPackage) -> Unit
    ): Boolean {
        return try {
            val live = synchronized(liveSessions) { liveSessions[sessionDir.absolutePath] }
            if (live != null) {
                edit(live.geoPackage)
                flushSession(context, sessionDir, live)
            } else {
                val gpkgFile = File(sessionDir, GPKG_FILENAME)
                // Sessione senza GeoPackage: nulla da modificare, non è un errore.
                if (!gpkgFile.exists()) return true
                val manager = gpkgManager(context)
                val databaseName = "edit_${System.currentTimeMillis()}"
                if (manager.databases().contains(databaseName)) manager.delete(databaseName)
                manager.importGeoPackage(databaseName, gpkgFile)
                try {
                    val gpkg = manager.open(databaseName)
                    try {
                        edit(gpkg)
                    } finally {
                        gpkg.close()
                    }
                    exportAtomically(manager, databaseName, sessionDir)
                } finally {
                    manager.delete(databaseName)
                }
            }
            true
        } catch (e: Exception) {
            Log.e("GPKG", "Edit Error", e)
            false
        }
    }

    private fun insertPoint(
        gpkg: GeoPackage,
        filename: String,
        loc: Location?,
        label: String,
        extra: String
    ) {
        val dao = gpkg.getFeatureDao("points")
        val row: FeatureRow = dao.newRow()
        if (loc != null) {
            val point = Point(loc.longitude, loc.latitude)
            val geomData = GeoPackageGeometryData(4326)
            geomData.geometry = point
            row.geometry = geomData
            row.setValue("latitude", loc.latitude)
            row.setValue("longitude", loc.longitude)
            row.setValue("altitude", loc.altitude)
            row.setValue("accuracy_m", loc.accuracy.toDouble())
        }
        val timeToFormat = loc?.time ?: System.currentTimeMillis()
        row.setValue("filename", filename)
        row.setValue("timestamp", isoTimestamp(timeToFormat))
        row.setValue("label", label)
        row.setValue("extra_value", extra)
        dao.insert(row)
    }
}
