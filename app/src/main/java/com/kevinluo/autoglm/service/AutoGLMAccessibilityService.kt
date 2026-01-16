package com.kevinluo.autoglm.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.kevinluo.autoglm.ComponentManager
import com.kevinluo.autoglm.util.Logger
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume

class AutoGLMAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoGLMAccessibilityService"
        var instance: AutoGLMAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Logger.i(TAG, "AutoGLM Accessibility Service Connected")
        instance = this
        // Notify ComponentManager
        ComponentManager.getInstance(applicationContext).onAccessibilityServiceConnected(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We process events here if we want to track UI changes
    }

    override fun onInterrupt() {
        Logger.w(TAG, "AutoGLM Accessibility Service Interrupted")
        instance = null
        ComponentManager.getInstance(applicationContext).onAccessibilityServiceDisconnected()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Logger.i(TAG, "AutoGLM Accessibility Service Unbound")
        instance = null
        ComponentManager.getInstance(applicationContext).onAccessibilityServiceDisconnected()
        return super.onUnbind(intent)
    }

    /**
     * Captures a screenshot of the specified display.
     * 
     * @param displayId The ID of the display to capture (default: Display.DEFAULT_DISPLAY)
     * @return The captured Bitmap, or null if failed
     */
    suspend fun captureScreenshot(displayId: Int = Display.DEFAULT_DISPLAY): Bitmap? = suspendCancellableCoroutine { cont ->
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val executor = Executors.newSingleThreadExecutor()
            
            takeScreenshot(displayId, executor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    try {
                        val hardwareBuffer = screenshot.hardwareBuffer
                        val colorSpace = screenshot.colorSpace
                        
                        // Create bitmap from hardware buffer
                        val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                        
                        // Hardware bitmaps are immutable and difficult to process directly in some cases
                        // Copy to software bitmap for easier handling (compression to base64 etc)
                        val softwareBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        
                        // Close the hardware buffer to release resources
                        hardwareBuffer.close()
                        
                        if (softwareBitmap != null) {
                            cont.resume(softwareBitmap)
                        } else {
                            Logger.e(TAG, "Failed to copy hardware bitmap to software")
                            cont.resume(null)
                        }
                    } catch (e: Exception) {
                        Logger.e(TAG, "Error processing screenshot result", e)
                        cont.resume(null)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Logger.e(TAG, "Accessibility screenshot failed with error code: $errorCode")
                    cont.resume(null)
                }
            })
        } else {
            Logger.e(TAG, "AccessibilityService.takeScreenshot requires Android R (API 30)+")
            cont.resume(null)
        }
    }
}
