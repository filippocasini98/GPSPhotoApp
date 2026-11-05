package com.example.gpsphotapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Equivalente Android di ExportManager.swift su iOS.
 * Esporta una sessione in GPX, KML, GeoJSON, CSV, GeoPackage o ZIP e
 * condivide il file tramite FileProvider.
 *
 * Tutti gli export vengono scritti in una cartella dedicata nella cache
 * dell'app (coperta dal FileProvider), così le cartelle di sessione
 * restano pulite e gli ZIP non includono export precedenti.
 */
object ExportManager {

    data class SessionPoint(
        val filename: String,
        val latitude: Double?,
        val longitude: Double?,
        val altitude: Double?,
        val accuracy: Double?,
        val timestamp: String,
        val label: String,
        val extra: String
    )

    sealed class ExportResult {
        data class Success(val file: File, val mime: String) : ExportResult()
        object NoData : ExportResult()
        data class Error(val message: String) : ExportResult()
    }

    fun readPoints(sessionDir: File): List<SessionPoint> {
        val csv = File(sessionDir, "summary.csv")
        if (!csv.exists()) return emptyList()
        return try {
            parseCsvRecords(csv.readText())
                .drop(1) // header
                .mapNotNull { fields ->
                    if (fields.size < 8) return@mapNotNull null
                    SessionPoint(
                        filename = fields[0],
                        latitude = fields[1].toDoubleOrNull(),
                        longitude = fields[2].toDoubleOrNull(),
                        altitude = fields[3].toDoubleOrNull(),
                        accuracy = fields[4].toDoubleOrNull(),
                        timestamp = fields[5],
                        label = fields[6],
                        extra = fields[7]
                    )
                }
        } catch (e: Exception) {
            Log.e("Export", "readPoints failed", e)
            emptyList()
        }
    }

    // Parser CSV a stato: gestisce virgole, doppi apici e newline dentro
    // campi quotati, che una lettura riga-per-riga spezzerebbe.
    // Non privato: riusato da DataManager per la modifica dei punti.
    internal fun parseCsvRecords(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        fun endField() {
            fields.add(current.toString())
            current.clear()
        }

        fun endRecord() {
            endField()
            if (fields.size > 1 || fields[0].isNotEmpty()) records.add(fields.toList())
            fields.clear()
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < text.length && text[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> endField()
                c == '\n' && !inQuotes -> endRecord()
                c == '\r' && !inQuotes -> { /* parte di CRLF, ignorato */ }
                else -> current.append(c)
            }
            i++
        }
        if (current.isNotEmpty() || fields.isNotEmpty()) endRecord()
        return records
    }

    // ---- GPX ----

    fun exportGpx(context: Context, sessionDir: File): ExportResult {
        val pts = readPoints(sessionDir).filter { it.latitude != null && it.longitude != null }
        if (pts.isEmpty()) return ExportResult.NoData
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"GPSphotapp\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        for (p in pts) {
            val lat = p.latitude ?: continue
            val lon = p.longitude ?: continue
            sb.append(String.format(Locale.US, "  <wpt lat=\"%.7f\" lon=\"%.7f\">\n", lat, lon))
            p.altitude?.let { sb.append(String.format(Locale.US, "    <ele>%s</ele>\n", it.toString())) }
            sb.append("    <time>${xmlEscape(p.timestamp)}</time>\n")
            sb.append("    <name>${xmlEscape(p.label)}</name>\n")
            val desc = if (p.extra.isEmpty()) p.filename else "${p.filename} | ${p.extra}"
            sb.append("    <desc>${xmlEscape(desc)}</desc>\n")
            sb.append("  </wpt>\n")
        }
        sb.append("</gpx>\n")
        return writeExport(context, sb.toString(), "${sessionDir.name}.gpx", "application/gpx+xml")
    }

    // ---- KML ----

    fun exportKml(context: Context, sessionDir: File): ExportResult {
        val pts = readPoints(sessionDir).filter { it.latitude != null && it.longitude != null }
        if (pts.isEmpty()) return ExportResult.NoData
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n<Document>\n")
        sb.append("  <name>${xmlEscape(sessionDir.name)}</name>\n")
        for (p in pts) {
            val lat = p.latitude ?: continue
            val lon = p.longitude ?: continue
            val alt = p.altitude ?: 0.0
            sb.append("  <Placemark>\n")
            sb.append("    <name>${xmlEscape(p.label)}</name>\n")
            sb.append("    <description>${xmlEscape("${p.filename} | ${p.extra}")}</description>\n")
            sb.append("    <TimeStamp><when>${xmlEscape(p.timestamp)}</when></TimeStamp>\n")
            sb.append(String.format(Locale.US, "    <Point><coordinates>%.7f,%.7f,%.2f</coordinates></Point>\n", lon, lat, alt))
            sb.append("  </Placemark>\n")
        }
        sb.append("</Document>\n</kml>\n")
        return writeExport(context, sb.toString(), "${sessionDir.name}.kml", "application/vnd.google-earth.kml+xml")
    }

    // ---- GeoJSON ----

    fun exportGeoJson(context: Context, sessionDir: File): ExportResult {
        val pts = readPoints(sessionDir).filter { it.latitude != null && it.longitude != null }
        if (pts.isEmpty()) return ExportResult.NoData
        return try {
            val features = JSONArray()
            for (p in pts) {
                val lat = p.latitude ?: continue
                val lon = p.longitude ?: continue
                val geometry = JSONObject().apply {
                    put("type", "Point")
                    put("coordinates", JSONArray().apply {
                        put(lon)
                        put(lat)
                        put(p.altitude ?: 0.0)
                    })
                }
                val props = JSONObject().apply {
                    put("filename", p.filename)
                    put("label", p.label)
                    put("timestamp", p.timestamp)
                    put("accuracy", p.accuracy ?: JSONObject.NULL)
                    put("extra_value", p.extra)
                }
                features.put(JSONObject().apply {
                    put("type", "Feature")
                    put("geometry", geometry)
                    put("properties", props)
                })
            }
            val coll = JSONObject().apply {
                put("type", "FeatureCollection")
                put("features", features)
            }
            writeExport(context, coll.toString(2), "${sessionDir.name}.geojson", "application/geo+json")
        } catch (e: Exception) {
            Log.e("Export", "exportGeoJson failed", e)
            ExportResult.Error(e.message ?: "GeoJSON export failed")
        }
    }

    // ---- CSV ----

    fun exportCsv(context: Context, sessionDir: File): ExportResult {
        val csv = File(sessionDir, "summary.csv")
        if (!csv.exists()) return ExportResult.NoData
        return try {
            val out = File(exportsDir(context), "${sessionDir.name}.csv")
            csv.copyTo(out, overwrite = true)
            ExportResult.Success(out, "text/csv")
        } catch (e: Exception) {
            Log.e("Export", "exportCsv failed", e)
            ExportResult.Error(e.message ?: "CSV export failed")
        }
    }

    // ---- GeoPackage ----

    fun exportGpkg(context: Context, sessionDir: File): ExportResult {
        return try {
            val gpkg = File(sessionDir, "data.gpkg")
            if (!gpkg.exists()) {
                // Sessioni vecchie o incomplete: rigenera il GeoPackage dal CSV.
                val points = readPoints(sessionDir)
                if (points.isEmpty()) return ExportResult.NoData
                if (!DataManager.rebuildGeoPackageFromPoints(context, sessionDir, points)) {
                    return ExportResult.Error("GeoPackage rebuild failed")
                }
            }
            if (!gpkg.exists()) return ExportResult.Error("GeoPackage not found")
            val out = File(exportsDir(context), "${sessionDir.name}.gpkg")
            gpkg.copyTo(out, overwrite = true)
            ExportResult.Success(out, "application/geopackage+sqlite3")
        } catch (e: Exception) {
            Log.e("Export", "exportGpkg failed", e)
            ExportResult.Error(e.message ?: "GeoPackage export failed")
        }
    }

    // ---- ZIP ----

    fun exportZip(context: Context, sessionDir: File): ExportResult {
        val files = sessionDir.listFiles()
        if (files.isNullOrEmpty()) return ExportResult.NoData
        val zipFile = File(exportsDir(context), "${sessionDir.name}.zip")
        return try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                addFolderToZip(sessionDir, sessionDir.name, zos)
            }
            ExportResult.Success(zipFile, "application/zip")
        } catch (e: Exception) {
            Log.e("Export", "exportZip failed", e)
            zipFile.delete()
            ExportResult.Error(e.message ?: "ZIP export failed")
        }
    }

    private fun addFolderToZip(folder: File, parentPath: String, zos: ZipOutputStream) {
        folder.listFiles()?.forEach { file ->
            // File temporanei di lavoro: mai inclusi nell'archivio.
            if (file.name == "data.tmp.gpkg" || file.name == "summary.tmp.csv") return@forEach
            if (file.isDirectory) {
                addFolderToZip(file, "$parentPath/${file.name}", zos)
            } else {
                FileInputStream(file).use { fis ->
                    val entry = ZipEntry("$parentPath/${file.name}")
                    zos.putNextEntry(entry)
                    fis.copyTo(zos)
                    zos.closeEntry()
                }
            }
        }
    }

    // ---- Share ----

    fun share(context: Context, file: File, mime: String = "*/*"): Boolean {
        if (!file.exists()) return false
        return try {
            val uri: Uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
            true
        } catch (e: Exception) {
            Log.e("Export", "share failed", e)
            false
        }
    }

    // ---- Helpers ----

    private fun exportsDir(context: Context): File =
        File(context.cacheDir, "exports").apply { mkdirs() }

    private fun writeExport(
        context: Context,
        text: String,
        filename: String,
        mime: String
    ): ExportResult {
        return try {
            val out = File(exportsDir(context), filename)
            out.writeText(text)
            ExportResult.Success(out, mime)
        } catch (e: Exception) {
            Log.e("Export", "write $filename failed", e)
            ExportResult.Error(e.message ?: "Write failed")
        }
    }

    private fun xmlEscape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
