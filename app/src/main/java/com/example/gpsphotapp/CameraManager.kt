package com.example.gpsphotapp

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class CameraLens(
    val id: String,
    val label: String
)

class CameraManager(private val context: Context) {

    var permissionGranted by mutableStateOf(false)
        private set
    var availableCameras by mutableStateOf<List<CameraLens>>(emptyList())
        private set
    var currentCameraId by mutableStateOf("")
        private set
    var currentZoom by mutableStateOf(1f)
        private set
    var minZoom by mutableStateOf(1f)
        private set
    var maxZoom by mutableStateOf(1f)
        private set
    var minExposureBias by mutableStateOf(-3f)
        private set
    var maxExposureBias by mutableStateOf(3f)
        private set
    var currentExposureBias by mutableStateOf(0f)
        private set
    var rawSupported by mutableStateOf(false)
        private set
    var exposureAutoMode by mutableStateOf(true)
        private set

    private var previewView: PreviewView? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        permissionGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun attach(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        this.lifecycleOwner = lifecycleOwner
        this.previewView = previewView
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
            bindUseCases()
        }, ContextCompat.getMainExecutor(context))
    }

    fun onPermissionsChanged() {
        permissionGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (permissionGranted) {
            bindUseCases()
        }
    }

    fun unbind() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }

    private fun bindUseCases() {
        val owner = lifecycleOwner ?: return
        val surface = previewView ?: return
        val provider = cameraProvider ?: return
        if (!permissionGranted) return

        provider.unbindAll()

        preview = Preview.Builder().build().apply {
            setSurfaceProvider(surface.surfaceProvider)
        }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()

        val selector = if (currentCameraId.isBlank()) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.Builder()
                .addCameraFilter { infos ->
                    infos.filter { info -> Camera2CameraInfo.from(info).cameraId == currentCameraId }
                }
                .build()
        }

        camera = provider.bindToLifecycle(owner, selector, preview, imageCapture)
        discoverCameras(provider)
        publishCameraState()
    }

    private data class LensSpec(val id: String, val equiv35mm: Float)

    private fun discoverCameras(provider: ProcessCameraProvider) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        val specs = provider.availableCameraInfos.mapNotNull { info ->
            val id = Camera2CameraInfo.from(info).cameraId
            val chars = manager.getCameraCharacteristics(id)
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            if (facing != CameraCharacteristics.LENS_FACING_BACK) return@mapNotNull null
            val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull()
            val sensor = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            val equiv = if (focal != null && sensor != null && sensor.width > 0f) {
                focal * 36f / sensor.width
            } else focal ?: 0f
            LensSpec(id, equiv)
        }

        if (specs.isEmpty()) {
            availableCameras = emptyList()
            return
        }

        val sorted = specs.sortedBy { it.equiv35mm }
        val mainEquiv = sorted.minByOrNull { kotlin.math.abs(it.equiv35mm - 26f) }?.equiv35mm
            ?: sorted.first().equiv35mm
        val lenses = sorted.map { spec ->
            val ratio = if (mainEquiv > 0f) spec.equiv35mm / mainEquiv else 1f
            CameraLens(spec.id, formatZoomLabel(ratio))
        }

        availableCameras = lenses
        if (currentCameraId.isBlank()) {
            val mainId = sorted.minByOrNull { kotlin.math.abs(it.equiv35mm - 26f) }?.id
            currentCameraId = mainId ?: lenses.first().id
        }
    }

    private fun formatZoomLabel(ratio: Float): String {
        if (ratio in 0.85f..1.2f) return "1x"
        return if (ratio < 1f) {
            val r = kotlin.math.round(ratio * 10f) / 10f
            "${r}x"
        } else {
            "${kotlin.math.round(ratio).toInt()}x"
        }
    }

    private fun publishCameraState() {
        val activeCamera = camera ?: return
        val zoomState = activeCamera.cameraInfo.zoomState.value
        currentZoom = zoomState?.zoomRatio ?: 1f
        minZoom = zoomState?.minZoomRatio ?: 1f
        maxZoom = zoomState?.maxZoomRatio ?: 1f

        val exposureState = activeCamera.cameraInfo.exposureState
        val step = exposureState.exposureCompensationStep.toFloat()
        minExposureBias = exposureState.exposureCompensationRange.lower * step
        maxExposureBias = exposureState.exposureCompensationRange.upper * step
        currentExposureBias = exposureState.exposureCompensationIndex * step

        rawSupported = false
    }

    fun switchCamera(cameraLens: CameraLens) {
        currentCameraId = cameraLens.id
        bindUseCases()
    }

    fun tapToFocus(action: androidx.camera.core.FocusMeteringAction) {
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    fun setZoom(factor: Float, ramp: Boolean = false) {
        val activeCamera = camera ?: return
        val clamped = factor.coerceIn(minZoom, maxZoom)
        activeCamera.cameraControl.setZoomRatio(clamped)
        currentZoom = clamped
    }

    fun setExposureBias(ev: Float) {
        val activeCamera = camera ?: return
        val exposureState = activeCamera.cameraInfo.exposureState
        val step = exposureState.exposureCompensationStep.toFloat().takeIf { it != 0f } ?: 1f
        val clamped = ev.coerceIn(minExposureBias, maxExposureBias)
        val index = (clamped / step).toInt().coerceIn(
            exposureState.exposureCompensationRange.lower,
            exposureState.exposureCompensationRange.upper
        )
        activeCamera.cameraControl.setExposureCompensationIndex(index)
        currentExposureBias = index * step
    }

    fun setExposureAuto(auto: Boolean) {
        val activeCamera = camera ?: return
        exposureAutoMode = auto
        val camera2Control = Camera2CameraControl.from(activeCamera.cameraControl)
        val builder = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, !auto)
        camera2Control.captureRequestOptions = builder.build()
    }

    fun setFocus(distance: Float, manual: Boolean) {
        val activeCamera = camera ?: return
        val camera2Control = Camera2CameraControl.from(activeCamera.cameraControl)
        val builder = CaptureRequestOptions.Builder()
        if (manual) {
            val maxFocus = cameraMinFocusDistance() ?: return
            val ratio = (distance / 10f).coerceIn(0f, 1f)
            val focusDistance = maxFocus * ratio
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance)
        } else {
            builder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
        }
        camera2Control.captureRequestOptions = builder.build()
    }

    private fun cameraMinFocusDistance(): Float? {
        val activeId = currentCameraId.ifBlank { return null }
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        return manager.getCameraCharacteristics(activeId)
            .get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
    }

    @SuppressLint("MissingPermission")
    fun capturePhoto(
        file: File,
        location: Location?,
        quality: PhotoQuality,
        rawPlusJpeg: Boolean,
        onError: (Exception) -> Unit = {},
        onSuccess: (File) -> Unit
    ) {
        // Alias: dentro l'object il nome onError risolverebbe al metodo del
        // callback, non alla lambda.
        val reportError = onError
        val capture = imageCapture
        if (capture == null) {
            mainHandler.post { reportError(IllegalStateException("Camera not ready")) }
            return
        }
        val output = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(
            output,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    addExif(file, location)
                    mainHandler.post { onSuccess(file) }
                }

                override fun onError(exception: ImageCaptureException) {
                    android.util.Log.e("CameraManager", "Capture failed", exception)
                    mainHandler.post { reportError(exception) }
                }
            }
        )
    }

    private fun addExif(file: File, location: Location?) {
        runCatching {
            val exif = ExifInterface(file.absolutePath)
            val stamp = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date())
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, stamp)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, stamp)
            if (location != null) {
                exif.setLatLong(location.latitude, location.longitude)
                exif.setAltitude(location.altitude)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    exif.setGpsInfo(location)
                }
            }
            exif.saveAttributes()
        }
    }
}
