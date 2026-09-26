package com.agea.camerapro

import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor

enum class ResolutionOption(val label: String, val quality: Quality) {
    UHD_4K("4K", Quality.UHD),
    FHD_1080("1080p", Quality.FHD),
    HD_720("720p", Quality.HD),
}

class CameraController(private val context: Context) {

    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
    private val filterProcessor = CameraFilterProcessor()

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    var facing: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        private set
    var resolution: ResolutionOption = ResolutionOption.FHD_1080
        private set
    var stabilizationOn: Boolean = true
        private set
    var antiFlickerOn: Boolean = true
        private set
    var torchOn: Boolean = false
        private set

    fun setFilter(preset: FilterPreset) {
        filterProcessor.currentFilter = preset
    }

    fun start(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onError: (String) -> Unit
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
            bind(lifecycleOwner, previewView, onError)
        }, mainExecutor)
    }

    fun switchCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView, onError: (String) -> Unit) {
        facing = if (facing == CameraSelector.DEFAULT_BACK_CAMERA)
            CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        bind(lifecycleOwner, previewView, onError)
    }

    fun setResolution(option: ResolutionOption, lifecycleOwner: LifecycleOwner, previewView: PreviewView, onError: (String) -> Unit) {
        resolution = option
        bind(lifecycleOwner, previewView, onError)
    }

    private fun bind(lifecycleOwner: LifecycleOwner, previewView: PreviewView, onError: (String) -> Unit) {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(resolution.quality))
            .build()
        val newVideoCapture = VideoCapture.withOutput(recorder)

        // CameraEffect tem construtor protected: instanciar via subclasse FilterEffect.
        val effect = FilterEffect(filterProcessor, mainExecutor) { throwable ->
            Log.e("CameraController", "Effect error", throwable)
        }

        val useCaseGroup = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(newVideoCapture)
            .addEffect(effect)
            .build()

        try {
            camera = provider.bindToLifecycle(lifecycleOwner, facing, useCaseGroup)
            videoCapture = newVideoCapture
            applyStabilization()
            applyAntiFlicker()
        } catch (e: Exception) {
            onError(e.message ?: "Falha ao iniciar a câmara")
        }
    }

    // --- Real hardware controls via Camera2 interop (applied live, no rebind needed) ---

    fun toggleStabilization(): Boolean {
        stabilizationOn = !stabilizationOn
        applyStabilization()
        return stabilizationOn
    }

    fun toggleAntiFlicker(): Boolean {
        antiFlickerOn = !antiFlickerOn
        applyAntiFlicker()
        return antiFlickerOn
    }

    private fun applyStabilization() {
        val cam = camera ?: return
        val mode = if (stabilizationOn)
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
        else
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF
        val options = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, mode)
            .build()
        Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(options)
    }

    private fun applyAntiFlicker() {
        val cam = camera ?: return
        val mode = if (antiFlickerOn)
            CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO
        else
            CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_OFF
        val options = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, mode)
            .build()
        Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(options)
    }

    fun toggleTorch(): Boolean {
        torchOn = !torchOn
        camera?.cameraControl?.enableTorch(torchOn)
        return torchOn
    }

    fun hasTorch(): Boolean = camera?.cameraInfo?.hasFlashUnit() ?: false

    // --- Recording ---

    fun startRecording(onSaved: (android.net.Uri) -> Unit, onFailed: (String) -> Unit) {
        val capture = videoCapture ?: return
        val name = "camara-pro-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(java.util.Date())}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraPro")
            }
        }
        val outputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()

        val pending: PendingRecording = capture.output
            .prepareRecording(context, outputOptions)
            .withAudioEnabled()

        activeRecording = pending.start(mainExecutor) { event ->
            when (event) {
                is androidx.camera.video.VideoRecordEvent.Finalize -> {
                    if (!event.hasError()) {
                        onSaved(event.outputResults.outputUri)
                    } else {
                        onFailed("Erro ao gravar (código ${event.error})")
                    }
                }
                else -> Unit
            }
        }
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    fun isRecording(): Boolean = activeRecording != null

    fun release() {
        activeRecording?.stop()
        filterProcessor.release()
        cameraProvider?.unbindAll()
    }
}
