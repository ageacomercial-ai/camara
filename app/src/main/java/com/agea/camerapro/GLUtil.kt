package com.agea.camerapro

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

private const val VERTEX_SHADER = """
    attribute vec4 aPosition;
    attribute vec4 aTexCoord;
    uniform mat4 uTexTransform;
    varying vec2 vTexCoord;
    void main() {
        gl_Position = aPosition;
        vTexCoord = (uTexTransform * aTexCoord).xy;
    }
"""

// Fragment shader: sequential filter pipeline mirroring CSS filter() order
// (hue-rotate -> saturate -> sepia -> grayscale -> contrast -> brightness).
private const val FRAGMENT_SHADER = """
    #extension GL_OES_EGL_image_external : require
    precision mediump float;
    varying vec2 vTexCoord;
    uniform samplerExternalOES uTexture;
    uniform float uGrayscale;
    uniform float uSepia;
    uniform float uSaturate;
    uniform float uContrast;
    uniform float uBrightness;
    uniform float uHueRotate; // radians

    vec3 applyHueRotate(vec3 c, float a) {
        float cosA = cos(a);
        float sinA = sin(a);
        mat3 m = mat3(
            0.213 + cosA*0.787 - sinA*0.213, 0.213 - cosA*0.213 + sinA*0.143, 0.213 - cosA*0.213 - sinA*0.787,
            0.715 - cosA*0.715 - sinA*0.715, 0.715 + cosA*0.285 + sinA*0.140, 0.715 - cosA*0.715 + sinA*0.715,
            0.072 - cosA*0.072 + sinA*0.928, 0.072 - cosA*0.072 - sinA*0.283, 0.072 + cosA*0.928 + sinA*0.072
        );
        return clamp(m * c, 0.0, 1.0);
    }

    void main() {
        vec4 tex = texture2D(uTexture, vTexCoord);
        vec3 c = tex.rgb;

        c = applyHueRotate(c, uHueRotate);

        float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
        c = mix(vec3(luma), c, uSaturate);

        vec3 sepiaColor = vec3(
            dot(c, vec3(0.393, 0.769, 0.189)),
            dot(c, vec3(0.349, 0.686, 0.168)),
            dot(c, vec3(0.272, 0.534, 0.131))
        );
        c = mix(c, clamp(sepiaColor, 0.0, 1.0), uSepia);

        float gray = dot(c, vec3(0.2126, 0.7152, 0.0722));
        c = mix(c, vec3(gray), uGrayscale);

        c = (c - 0.5) * uContrast + 0.5;
        c = c * uBrightness;

        gl_FragColor = vec4(clamp(c, 0.0, 1.0), tex.a);
    }
"""

private val QUAD_VERTICES = floatArrayOf(
    -1f, -1f, 0f, 1f,
    1f, -1f, 0f, 1f,
    -1f, 1f, 0f, 1f,
    1f, 1f, 0f, 1f
)

private val QUAD_TEXCOORDS = floatArrayOf(
    0f, 0f, 0f, 1f,
    1f, 0f, 0f, 1f,
    0f, 1f, 0f, 1f,
    1f, 1f, 0f, 1f
)

private fun buffer(data: FloatArray): FloatBuffer =
    ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(data); position(0)
    }

/** Minimal EGL + GLES20 renderer that draws a camera OES texture through the filter shader. */
class FilterGLRenderer {
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    private var program = 0
    private var texId = 0
    private val posBuf = buffer(QUAD_VERTICES)
    private val texBuf = buffer(QUAD_TEXCOORDS)

    fun init() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

        val attribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, numConfigs, 0)
        eglConfig = configs[0]

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)

        val pbufferAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
        val dummy = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, pbufferAttribs, 0)
        EGL14.eglMakeCurrent(eglDisplay, dummy, dummy, eglContext)

        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)

        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        texId = texIds[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    fun inputTextureId(): Int = texId

    fun createWindowSurface(surface: Surface): EGLSurface {
        val attribs = intArrayOf(EGL14.EGL_NONE)
        return EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, attribs, 0)
    }

    fun makeCurrent(eglSurface: EGLSurface) {
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
    }

    fun swapBuffers(eglSurface: EGLSurface) {
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    fun setPresentationTime(eglSurface: EGLSurface, nanos: Long) {
        android.opengl.EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nanos)
    }

    fun destroySurface(eglSurface: EGLSurface) {
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
    }

    fun drawFrame(texTransform: FloatArray, filter: FilterPreset, viewportW: Int, viewportH: Int) {
        GLES20.glViewport(0, 0, viewportW, viewportH)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        val posLoc = GLES20.glGetAttribLocation(program, "aPosition")
        val texLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        GLES20.glEnableVertexAttribArray(posLoc)
        posBuf.position(0)
        GLES20.glVertexAttribPointer(posLoc, 4, GLES20.GL_FLOAT, false, 0, posBuf)
        GLES20.glEnableVertexAttribArray(texLoc)
        texBuf.position(0)
        GLES20.glVertexAttribPointer(texLoc, 4, GLES20.GL_FLOAT, false, 0, texBuf)

        val transformLoc = GLES20.glGetUniformLocation(program, "uTexTransform")
        GLES20.glUniformMatrix4fv(transformLoc, 1, false, texTransform, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)

        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uGrayscale"), filter.grayscale)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uSepia"), filter.sepia)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uSaturate"), filter.saturate)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uContrast"), filter.contrast)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uBrightness"), filter.brightness)
        GLES20.glUniform1f(
            GLES20.glGetUniformLocation(program, "uHueRotate"),
            Math.toRadians(filter.hueRotateDeg.toDouble()).toFloat()
        )

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(texLoc)
    }

    private fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs)
        GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        return p
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        return shader
    }
}
