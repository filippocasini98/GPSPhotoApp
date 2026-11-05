package com.example.gpsphotapp

import android.app.Application
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

enum class CaptureMode(val label: String) {
    TIME("Time"),
    DISTANCE("Distance")
}

enum class PhotoQuality(val label: String) {
    SPEED("Speed"),
    BALANCED("Balanced"),
    HIGH("High")
}

@Serializable
data class SessionState(
    val sessionPath: String,
    val isAuto: Boolean,
    val interval: Double,
    val photoCount: Int,
    val captureMode: String,
    val captureDistance: Double
)

data class SessionInfo(
    val dir: File,
    val name: String,
    val date: Long,
    val photoCount: Int,
    val isAuto: Boolean
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    var isAutoRecording by mutableStateOf(false)
        private set
    var autoPhotoCount by mutableStateOf(0)
        private set
    var isManualSession by mutableStateOf(false)
        private set
    var manualPhotoCount by mutableStateOf(0)
        private set
    var currentAutoSessionDir by mutableStateOf<File?>(null)
        private set
    var currentManualSessionDir by mutableStateOf<File?>(null)
        private set

    var recordingInterval by mutableStateOf(5.0)
    var saveLocation by mutableStateOf(true)
    var focusDistance by mutableStateOf(0f)
    var isManualFocus by mutableStateOf(false)
    var captureMode by mutableStateOf(CaptureMode.TIME)
    var captureDistance by mutableStateOf(5.0)
    var burstCount by mutableStateOf(1)
    var photoQuality by mutableStateOf(PhotoQuality.BALANCED)
    var rawPlusJpeg by mutableStateOf(false)

    var hapticEnabled by mutableStateOf(true)
    var soundEnabled by mutableStateOf(true)
    var showGrid by mutableStateOf(false)
    var showLevel by mutableStateOf(false)
    var autoStopOnLowBattery by mutableStateOf(true)
    var autoStopOnLowStorage by mutableStateOf(true)

    var batteryWarning by mutableStateOf<String?>(null)
        private set
    var storageWarning by mutableStateOf<String?>(null)
        private set
    var captureError by mutableStateOf<String?>(null)
        private set
    var resumePromptVisible by mutableStateOf(false)
        private set
    var resumableState by mutableStateOf<SessionState?>(null)
        private set

    var labels by mutableStateOf<List<String>>(emptyList())
        private set

    private val appContext = application.applicationContext
    private val prefs = appContext.getSharedPreferences("gpsphotapp_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val stateFile = File(DataManager.getAppStorageDir(appContext), ".session_state.json")
    private val defaultLabels = listOf("Generic", "Pole", "Road", "Building")
    private var autoJob: Job? = null
    private var healthJob: Job? = null
    private var lastCaptureLocation: Location? = null

    // Contatori di sequenza per i nomi file, incrementati in modo eager alla
    // richiesta di scatto: due catture sovrapposte non possono ottenere lo
    // stesso numero. Il photoCount pubblicato avanza solo a scatto riuscito.
    private var autoCaptureSeq = 0
    private var manualCaptureSeq = 0

    // Le scritture su CSV/GeoPackage passano tutte da qui: un solo thread
    // garantisce l'ordine (init prima delle write, close dopo l'ultima write)
    // e tiene l'I/O fuori dal main thread.
    private val saveDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val saveScope = CoroutineScope(SupervisorJob() + saveDispatcher)

    init {
        loadLabels()
        loadSettings()
        loadResumableState()
        RecordingBridge.setStopHandler { stopAutoRecording() }
    }

    override fun onCleared() {
        autoJob?.cancel()
        healthJob?.cancel()
        RecordingBridge.setStopHandler(null)
        val openDirs = listOfNotNull(currentAutoSessionDir, currentManualSessionDir)
        saveScope.launch {
            openDirs.forEach { DataManager.closeSessionGeoPackage(appContext, it) }
        }
        // shutdown() dell'executor: i task già in coda (close incluso) vengono
        // completati prima che il thread termini.
        saveDispatcher.close()
        super.onCleared()
    }

    fun loadSettings() {
        hapticEnabled = prefs.getBoolean("hapticEnabled", true)
        soundEnabled = prefs.getBoolean("soundEnabled", true)
        showGrid = prefs.getBoolean("showGrid", false)
        showLevel = prefs.getBoolean("showLevel", false)
        autoStopOnLowBattery = prefs.getBoolean("autoStopOnLowBattery", true)
        autoStopOnLowStorage = prefs.getBoolean("autoStopOnLowStorage", true)
        saveLocation = prefs.getBoolean("saveLocation", true)
        recordingInterval = prefs.getFloat("recordingInterval", 5f).toDouble()
        captureDistance = prefs.getFloat("captureDistance", 5f).toDouble()
        burstCount = prefs.getInt("burstCount", 1)
        isManualFocus = prefs.getBoolean("isManualFocus", false)
        focusDistance = prefs.getFloat("focusDistance", 0f)
        rawPlusJpeg = prefs.getBoolean("rawPlusJpeg", false)
        captureMode = CaptureMode.valueOf(prefs.getString("captureMode", CaptureMode.TIME.name)!!)
        photoQuality = PhotoQuality.valueOf(
            prefs.getString("photoQuality", PhotoQuality.BALANCED.name)!!
        )
    }

    fun persistSettings() {
        prefs.edit()
            .putBoolean("hapticEnabled", hapticEnabled)
            .putBoolean("soundEnabled", soundEnabled)
            .putBoolean("showGrid", showGrid)
            .putBoolean("showLevel", showLevel)
            .putBoolean("autoStopOnLowBattery", autoStopOnLowBattery)
            .putBoolean("autoStopOnLowStorage", autoStopOnLowStorage)
            .putBoolean("saveLocation", saveLocation)
            .putFloat("recordingInterval", recordingInterval.toFloat())
            .putFloat("captureDistance", captureDistance.toFloat())
            .putInt("burstCount", burstCount)
            .putBoolean("isManualFocus", isManualFocus)
            .putFloat("focusDistance", focusDistance)
            .putBoolean("rawPlusJpeg", rawPlusJpeg)
            .putString("captureMode", captureMode.name)
            .putString("photoQuality", photoQuality.name)
            .apply()
    }

    fun loadLabels() {
        labels = prefs.getStringSet("labels", defaultLabels.toSet())!!.toList().sorted()
    }

    fun addLabel(label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty() || labels.contains(trimmed)) return
        labels = (labels + trimmed).sorted()
        saveLabels()
    }

    fun removeLabel(label: String) {
        labels = labels.filterNot { it == label }
        saveLabels()
    }

    private fun saveLabels() {
        prefs.edit().putStringSet("labels", labels.toSet()).apply()
    }

    fun startAutoRecording(cameraManager: CameraManager, locationHelper: LocationHelper) {
        if (isAutoRecording) return
        val sessionDir = makeSessionFolder("Auto")
        saveScope.launch {
            DataManager.initCsv(File(sessionDir, "summary.csv"))
            DataManager.openSessionGeoPackage(appContext, sessionDir)
        }
        currentAutoSessionDir = sessionDir
        autoPhotoCount = 0
        autoCaptureSeq = 0
        lastCaptureLocation = null
        captureError = null
        isAutoRecording = true
        startHealthMonitor()
        writeSessionState()
        startForegroundNotification(0)
        startAutoTimer(cameraManager, locationHelper)
    }

    fun stopAutoRecording() {
        autoJob?.cancel()
        autoJob = null
        isAutoRecording = false
        val sessionDir = currentAutoSessionDir
        currentAutoSessionDir = null
        sessionDir?.let { dir ->
            saveScope.launch { DataManager.closeSessionGeoPackage(appContext, dir) }
        }
        stopForegroundNotification()
        deleteSessionState()
        if (!isManualSession) {
            stopHealthMonitor()
        }
    }

    fun captureAuto(cameraManager: CameraManager, locationHelper: LocationHelper) {
        val sessionDir = currentAutoSessionDir ?: return
        val seq = ++autoCaptureSeq
        val location = if (saveLocation) locationHelper.location else null
        performCapture(
            sessionDir = sessionDir,
            count = seq,
            label = "auto",
            extra = "",
            location = location,
            cameraManager = cameraManager
        ) {
            // Uno scatto ancora in volo al momento dello stop non deve
            // ricreare lo stato di sessione né la notifica.
            if (!isAutoRecording) return@performCapture
            autoPhotoCount += 1
            lastCaptureLocation = location
            writeSessionState()
            startForegroundNotification(autoPhotoCount)
        }
    }

    fun checkDistanceCapture(cameraManager: CameraManager, locationHelper: LocationHelper) {
        val current = locationHelper.location ?: return
        if (!isAutoRecording || captureMode != CaptureMode.DISTANCE) return
        val shouldCapture = lastCaptureLocation?.distanceTo(current)?.let { it >= captureDistance } ?: true
        if (shouldCapture) {
            lastCaptureLocation = current
            captureAuto(cameraManager, locationHelper)
        }
    }

    fun startManualSession() {
        if (isManualSession) return
        val sessionDir = makeSessionFolder("ManualSession")
        saveScope.launch {
            DataManager.initCsv(File(sessionDir, "summary.csv"))
            DataManager.openSessionGeoPackage(appContext, sessionDir)
        }
        currentManualSessionDir = sessionDir
        manualPhotoCount = 0
        manualCaptureSeq = 0
        captureError = null
        isManualSession = true
        startHealthMonitor()
    }

    fun stopManualSession() {
        isManualSession = false
        val sessionDir = currentManualSessionDir
        currentManualSessionDir = null
        sessionDir?.let { dir ->
            saveScope.launch { DataManager.closeSessionGeoPackage(appContext, dir) }
        }
        if (!isAutoRecording) {
            stopHealthMonitor()
        }
    }

    fun captureManual(
        label: String,
        numericValue: String,
        cameraManager: CameraManager,
        locationHelper: LocationHelper
    ) {
        val sessionDir = currentManualSessionDir ?: return
        val seq = ++manualCaptureSeq
        val location = if (saveLocation) locationHelper.location else null
        performCapture(
            sessionDir = sessionDir,
            count = seq,
            label = label,
            extra = numericValue,
            location = location,
            cameraManager = cameraManager
        ) {
            manualPhotoCount += 1
        }
    }

    private fun performCapture(
        sessionDir: File,
        count: Int,
        label: String,
        extra: String,
        location: Location?,
        cameraManager: CameraManager,
        onSuccess: () -> Unit
    ) {
        val burst = burstCount.coerceIn(1, 5)
        captureBurst(
            sessionDir = sessionDir,
            count = count,
            label = label,
            extra = extra,
            location = location,
            cameraManager = cameraManager,
            burstIndex = 1,
            totalBurst = burst,
            onSuccess = onSuccess
        )
    }

    private fun captureBurst(
        sessionDir: File,
        count: Int,
        label: String,
        extra: String,
        location: Location?,
        cameraManager: CameraManager,
        burstIndex: Int,
        totalBurst: Int,
        onSuccess: () -> Unit
    ) {
        val file = File(sessionDir, photoFilename(count, if (totalBurst > 1) burstIndex else null))
        cameraManager.capturePhoto(
            file = file,
            location = location,
            quality = photoQuality,
            rawPlusJpeg = rawPlusJpeg,
            onError = { e ->
                captureError = "Capture failed: ${e.message ?: e.javaClass.simpleName}"
            }
        ) { savedFile ->
            captureError = null
            saveScope.launch {
                val csvFile = File(sessionDir, "summary.csv")
                DataManager.writeToCsv(csvFile, savedFile.name, location, label, extra)
                DataManager.writeToGeoPackage(appContext, sessionDir, savedFile.name, location, label, extra)
            }
            if (burstIndex == 1) {
                onSuccess()
            }
            if (burstIndex < totalBurst) {
                viewModelScope.launch {
                    delay(200)
                    captureBurst(
                        sessionDir,
                        count,
                        label,
                        extra,
                        location,
                        cameraManager,
                        burstIndex + 1,
                        totalBurst,
                        onSuccess
                    )
                }
            }
        }
    }

    private fun makeSessionFolder(prefix: String): File {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val name = "${prefix}_${formatter.format(Date())}"
        return File(DataManager.getAppStorageDir(appContext), name).apply { mkdirs() }
    }

    private fun photoFilename(count: Int, burstIndex: Int?): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US)
        val stamp = formatter.format(Date())
        return if (burstIndex != null) {
            "IMG_${stamp}_${count}_$burstIndex.jpg"
        } else {
            "IMG_${stamp}_$count.jpg"
        }
    }

    fun loadSessions(): List<SessionInfo> {
        val root = DataManager.getAppStorageDir(appContext)
        return root.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.map { dir ->
                SessionInfo(
                    dir = dir,
                    name = dir.name,
                    date = dir.lastModified(),
                    photoCount = dir.listFiles()?.count {
                        val ext = it.extension.lowercase()
                        ext == "jpg" || ext == "jpeg"
                    } ?: 0,
                    isAuto = dir.name.startsWith("Auto")
                )
            }
            ?.sortedByDescending { it.date }
            ?: emptyList()
    }

    fun renameSession(session: SessionInfo, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed.contains('/') || trimmed.startsWith(".")) return false
        if (isSessionActive(session.dir)) return false
        val target = File(session.dir.parentFile, trimmed)
        if (target.exists()) return false
        return session.dir.renameTo(target)
    }

    fun deleteSession(session: SessionInfo): Boolean {
        // La sessione in corso ha CSV/GeoPackage aperti: non si tocca.
        if (isSessionActive(session.dir)) return false
        return session.dir.deleteRecursively()
    }

    private fun isSessionActive(dir: File): Boolean =
        dir == currentAutoSessionDir || dir == currentManualSessionDir

    // ---- Modifica di sessioni esistenti ----
    // Le operazioni girano sul dispatcher di salvataggio single-thread, in
    // coda alle eventuali scritture di sessione; onDone torna sul main thread.

    fun updatePoint(
        sessionDir: File,
        filename: String,
        label: String,
        extra: String,
        onDone: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val ok = withContext(saveDispatcher) {
                DataManager.updatePoint(appContext, sessionDir, filename, label, extra)
            }
            onDone(ok)
        }
    }

    fun deletePhoto(
        sessionDir: File,
        photo: File,
        onDone: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val ok = withContext(saveDispatcher) {
                val fileOk = !photo.exists() || photo.delete()
                val pointOk = DataManager.deletePoint(appContext, sessionDir, photo.name)
                fileOk && pointOk
            }
            onDone(ok)
        }
    }

    private fun writeSessionState() {
        val dir = currentAutoSessionDir ?: return
        val state = SessionState(
            sessionPath = dir.absolutePath,
            isAuto = true,
            interval = recordingInterval,
            photoCount = autoPhotoCount,
            captureMode = captureMode.name,
            captureDistance = captureDistance
        )
        stateFile.writeText(json.encodeToString(state))
    }

    private fun deleteSessionState() {
        if (stateFile.exists() && !stateFile.delete()) {
            android.util.Log.w("AppViewModel", "Failed to delete session state file")
        }
    }

    private fun loadResumableState() {
        if (!stateFile.exists()) return
        runCatching {
            json.decodeFromString<SessionState>(stateFile.readText())
        }.getOrNull()?.takeIf { File(it.sessionPath).exists() }?.let { state ->
            resumableState = state
            resumePromptVisible = true
        } ?: stateFile.delete()
    }

    fun resumeSession(cameraManager: CameraManager, locationHelper: LocationHelper) {
        val state = resumableState ?: return
        val sessionDir = File(state.sessionPath)
        currentAutoSessionDir = sessionDir
        saveScope.launch { DataManager.openSessionGeoPackage(appContext, sessionDir) }
        autoPhotoCount = state.photoCount
        autoCaptureSeq = maxOf(state.photoCount, maxPhotoSequence(sessionDir))
        recordingInterval = state.interval
        captureMode = CaptureMode.valueOf(state.captureMode)
        captureDistance = state.captureDistance
        resumePromptVisible = false
        resumableState = null
        isAutoRecording = true
        startHealthMonitor()
        startForegroundNotification(autoPhotoCount)
        startAutoTimer(cameraManager, locationHelper)
    }

    /**
     * Riapre una sessione chiusa (auto o manuale) dalla lista sessioni,
     * ripristinando contatori e GeoPackage, e continua a scattare nella
     * stessa cartella. Ritorna false se una sessione dello stesso tipo
     * è già attiva o la cartella non esiste più.
     */
    fun resumeClosedSession(
        session: SessionInfo,
        cameraManager: CameraManager,
        locationHelper: LocationHelper
    ): Boolean {
        val dir = session.dir
        if (!dir.exists() || isSessionActive(dir)) return false
        if (session.isAuto) {
            if (isAutoRecording) return false
            saveScope.launch {
                DataManager.initCsv(File(dir, "summary.csv"))
                DataManager.openSessionGeoPackage(appContext, dir)
            }
            currentAutoSessionDir = dir
            autoPhotoCount = countPhotos(dir)
            autoCaptureSeq = maxPhotoSequence(dir)
            lastCaptureLocation = null
            captureError = null
            isAutoRecording = true
            startHealthMonitor()
            writeSessionState()
            startForegroundNotification(autoPhotoCount)
            startAutoTimer(cameraManager, locationHelper)
        } else {
            if (isManualSession) return false
            saveScope.launch {
                DataManager.initCsv(File(dir, "summary.csv"))
                DataManager.openSessionGeoPackage(appContext, dir)
            }
            currentManualSessionDir = dir
            manualPhotoCount = countPhotos(dir)
            manualCaptureSeq = maxPhotoSequence(dir)
            captureError = null
            isManualSession = true
            startHealthMonitor()
        }
        return true
    }

    private fun countPhotos(dir: File): Int =
        dir.listFiles()?.count {
            val ext = it.extension.lowercase()
            ext == "jpg" || ext == "jpeg"
        } ?: 0

    // IMG_<yyyyMMdd>_<HHmmssSSS>_<seq>[_<burst>].jpg
    private val photoSeqRegex = Regex(
        """IMG_\d{8}_\d+_(\d+)(?:_\d+)?\.jpe?g""",
        RegexOption.IGNORE_CASE
    )

    private fun maxPhotoSequence(dir: File): Int =
        dir.listFiles()?.mapNotNull { f ->
            photoSeqRegex.matchEntire(f.name)?.groupValues?.get(1)?.toIntOrNull()
        }?.maxOrNull() ?: countPhotos(dir)

    fun discardResumableState() {
        deleteSessionState()
        resumableState = null
        resumePromptVisible = false
    }

    private fun startHealthMonitor() {
        if (healthJob != null) return
        healthJob = viewModelScope.launch {
            while (isActive && (isAutoRecording || isManualSession)) {
                checkHealth()
                delay(30_000)
            }
        }
    }

    private fun stopHealthMonitor() {
        healthJob?.cancel()
        healthJob = null
        batteryWarning = null
        storageWarning = null
    }

    private fun startAutoTimer(cameraManager: CameraManager, locationHelper: LocationHelper) {
        autoJob?.cancel()
        if (captureMode != CaptureMode.TIME) return
        autoJob = viewModelScope.launch {
            while (isActive && isAutoRecording) {
                delay((recordingInterval.coerceAtLeast(1.0) * 1000).toLong())
                if (isAutoRecording) {
                    captureAuto(cameraManager, locationHelper)
                }
            }
        }
    }

    private fun checkHealth() {
        val battery = batteryLevel()
        batteryWarning = if (battery in 0f..0.149f) {
            "Low battery (${(battery * 100).toInt()}%)"
        } else {
            null
        }
        if (autoStopOnLowBattery && batteryWarning != null && isAutoRecording) {
            stopAutoRecording()
        }

        val storageFree = availableStorageBytes()
        storageWarning = if (storageFree < 500_000_000L) {
            "Low storage (<500MB)"
        } else {
            null
        }
        if (autoStopOnLowStorage && storageWarning != null && isAutoRecording) {
            stopAutoRecording()
        }
    }

    fun batteryLevel(): Float {
        val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val value = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (value >= 0) value / 100f else -1f
    }

    fun availableStorageBytes(): Long {
        val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: appContext.filesDir
        val stat = StatFs(dir.absolutePath)
        return stat.availableBytes
    }

    private fun startForegroundNotification(photoCount: Int) {
        val intent = Intent(appContext, RecordingService::class.java).apply {
            putExtra(RecordingService.EXTRA_PHOTO_COUNT, photoCount)
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    private fun stopForegroundNotification() {
        appContext.stopService(Intent(appContext, RecordingService::class.java))
    }
}
