package com.agea.camerapro

import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

private const val TAG = "CameraFilterProcessor"

/**
 * Runs the camera feed through [FilterGLRenderer] on a dedicated GL thread, so the SAME
 * filtered frames go both to the on-screen preview and to the encoded recording — the
 * filter is baked into the actual video file, not just a screen overlay.
 *
 * Selected filter can be changed live via [currentFilter]; it is read on every frame.
 */
class CameraFilterProcessor : SurfaceProcessor {

    @Volatile
    var currentFilter: FilterPreset = FilterPreset.ALL.first()

    private val thread = HandlerThread("CameraFilterGL").apply { start() }
    private val handler = Handler(thread.looper)
    private val glExecutor = Executor { command -> handler.post(command) }

    private val renderer = FilterGLRenderer()
    private var inputSurfaceTexture: SurfaceTexture? = null
    private var inputSurface: Surface? = null
    private val texMatrix = FloatArray(16)

    private data class OutputTarget(
        val surfaceOutput: SurfaceOutput,
        val eglSurface: android.opengl.EGLSurface,
        val width: Int,
        val height: Int
    )

    private val outputs = CopyOnWriteArrayList<OutputTarget>()
    private var initialized = false

    override fun onInputSurface(request: SurfaceRequest) {
        handler.post {
            ensureGlInit()
            val resolution = request.resolution
            val texture = SurfaceTexture(renderer.inputTextureId())
            texture.setDefaultBufferSize(resolution.width, resolution.height)
            val surface = Surface(texture)
            inputSurfaceTexture = texture
            inputSurface = surface

            texture.setOnFrameAvailableListener({
                handler.post { drawToAllOutputs() }
            }, handler)

            request.provideSurface(surface, glExecutor) { result ->
                Log.d(TAG, "Input surface released: ${result.resultCode}")
                texture.release()
                surface.release()
            }
        }
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        handler.post {
            ensureGlInit()
            val surface = surfaceOutput.getSurface(glExecutor) { event ->
                // Output surface no longer needed (use case removed / effect stopped).
                handler.post {
                    outputs.removeAll { it.surfaceOutput == surfaceOutput }
                }
            }
            val size = surfaceOutput.size
            val eglSurface = renderer.createWindowSurface(surface)
            outputs.add(OutputTarget(surfaceOutput, eglSurface, size.width, size.height))
        }
    }

    private fun ensureGlInit() {
        if (!initialized) {
            renderer.init()
            initialized = true
        }
    }

    private fun drawToAllOutputs() {
        val texture = inputSurfaceTexture ?: return
        texture.updateTexImage()
        texture.getTransformMatrix(texMatrix)

        for (target in outputs) {
            val combined = FloatArray(16)
            // Let CameraX fold in rotation / mirroring for this specific output target.
            target.surfaceOutput.updateTransformMatrix(combined, texMatrix)
            renderer.makeCurrent(target.eglSurface)
            renderer.drawFrame(combined, currentFilter, target.width, target.height)
            renderer.setPresentationTime(target.eglSurface, texture.timestamp)
            renderer.swapBuffers(target.eglSurface)
        }
    }

    fun release() {
        handler.post {
            for (target in outputs) {
                target.surfaceOutput.close()
                renderer.destroySurface(target.eglSurface)
            }
            outputs.clear()
            inputSurfaceTexture?.release()
            inputSurface?.release()
            thread.quitSafely()
        }
    }
}
