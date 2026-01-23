package com.kevinluo.autoglm.screenshot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import android.view.SurfaceControl
import com.kevinluo.autoglm.IUserService
import com.kevinluo.autoglm.util.ErrorHandler
import com.kevinluo.autoglm.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import com.kevinluo.autoglm.service.AutoGLMAccessibilityService

/**
 * Data class representing a captured screenshot.
 *
 * Contains the base64-encoded image data along with metadata about the capture.
 * The image is typically in WebP format for optimal compression.
 *
 * @property base64Data Base64-encoded image data (typically WebP format)
 * @property width Width of the screenshot in pixels (after scaling)
 * @property height Height of the screenshot in pixels (after scaling)
 * @property originalWidth Original screen width in pixels (before scaling)
 * @property originalHeight Original screen height in pixels (before scaling)
 * @property isSensitive Flag indicating if the screen was sensitive (e.g., password input)
 *
 */
data class Screenshot(
    val base64Data: String,
    val width: Int,
    val height: Int,
    val originalWidth: Int = width,
    val originalHeight: Int = height,
    val isSensitive: Boolean = false
)

/**
 * Interface for controlling floating window visibility during screenshot capture.
 *
 * The floating window can be hidden before capture or excluded from screenshots
 * using SurfaceControl.setSkipScreenshot() for better performance.
 * Implementations should handle the visibility state transitions safely.
 *
 */
interface FloatingWindowController {
    /**
     * Hides the floating window from the screen.
     */
    fun hide()

    /**
     * Shows the floating window on the screen.
     */
    fun show()

    /**
     * Shows the floating window and brings it to the front of other windows.
     */
    fun showAndBringToFront()

    /**
     * Checks if the floating window is currently visible.
     *
     * @return true if the window is visible, false otherwise
     */
    fun isVisible(): Boolean

    /**
     * Gets the SurfaceControl for the floating window.
     *
     * This is used to exclude the window from screenshots using
     * SurfaceControl.setSkipScreenshot() for better performance.
     *
     * @return SurfaceControl instance, or null if not available or API not supported
     */
    fun getSurfaceControl(): android.view.SurfaceControl?

    /**
     * Enables touch passthrough mode for the floating window.
     *
     * When enabled, touch events will pass through the window to the underlying
     * application, allowing actions to be executed without hiding the window.
     * The window remains visible and buttons remain clickable.
     *
     * This is used during action execution to prevent the floating window from
     * intercepting touch events while keeping the window visible.
     */
    fun enableTouchPassthrough()

    /**
     * Disables touch passthrough mode for the floating window.
     *
     * Restores normal touch behavior where the floating window can receive
     * touch events normally.
     */
    fun disableTouchPassthrough()
}

/**
 * Service for capturing device screenshots using Shizuku shell commands.
 *
 * Manages floating window visibility during capture to ensure the window
 * doesn't appear in captured screenshots. Screenshots are captured in PNG format,
 * then scaled and converted to WebP for optimal file size.
 *
 * @param userService Shizuku user service for executing shell commands
 * @param floatingWindowControllerProvider Provider function for the floating window controller
 *
 */
class ScreenshotService(
    private val userService: IUserService,
    private val cachePath: String? = null,
    private val floatingWindowControllerProvider: () -> FloatingWindowController? = { null },
    private val accessibilityServiceProvider: () -> AutoGLMAccessibilityService? = { null }
) {

    companion object {
        private const val TAG = "ScreenshotService"
        private const val HIDE_DELAY_MS = 200L
        private const val SHOW_DELAY_MS = 100L
        private const val FALLBACK_WIDTH = 1080
        private const val FALLBACK_HEIGHT = 1920

        // Minimum API level for SurfaceControl.setSkipScreenshot (Android 11 / API 30)
        private const val MIN_API_FOR_SKIP_SCREENSHOT = Build.VERSION_CODES.R

        // Screenshot compression settings - optimized for API upload
        private const val WEBP_QUALITY = 65  // Reduced from 70 for better compression

        // Debug screenshot save feature (default: disabled)
        private const val ENABLE_DEBUG_SCREENSHOT = false
        private const val PACKAGE_NAME = "com.kevinluo.autoglm"

        /**
         * Returns the appropriate WebP compress format based on API level.
         * WEBP_LOSSY is only available on API 30+, use deprecated WEBP for older versions.
         */
        @Suppress("DEPRECATION")
        val WEBP_FORMAT: Bitmap.CompressFormat
            get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.WEBP
            }

        // Screenshot scaling settings - use max dimensions instead of fixed scale factor
        // This ensures consistent output size regardless of device resolution
        private const val MAX_WIDTH = 720       // Max width after scaling
        private const val MAX_HEIGHT = 1280     // Max height after scaling

        // Base64 output chunk size for reading (safe for Binder)
        private const val BASE64_CHUNK_SIZE = 500000
    }

    /**
     * Captures the current screen content.
     *
     * Uses try-finally pattern to ensure floating window is always restored,
     * even if an exception occurs during capture. The screenshot is scaled
     * and compressed to WebP format for optimal size.
     *
     * Prefers using SurfaceControl.setSkipScreenshot() for better performance,
     * falls back to hide/show window if API is not available.
     *
     * @return Screenshot object containing the captured image data and metadata
     *
     */
    suspend fun capture(): Screenshot = withContext(Dispatchers.IO) {
        val floatingWindowController = floatingWindowControllerProvider()
        val hasFloatingWindow = floatingWindowController != null
        Logger.d(TAG, "Starting screenshot capture, window visible: ${floatingWindowController?.isVisible()}")

        // Try to use setSkipScreenshot API for better performance
        val surfaceControl = if (hasFloatingWindow && Build.VERSION.SDK_INT >= MIN_API_FOR_SKIP_SCREENSHOT) {
            withContext(Dispatchers.Main) {
                floatingWindowController?.getSurfaceControl()
            }
        } else {
            null
        }

        val useSkipScreenshot = surfaceControl != null

        if (useSkipScreenshot) {
            // Use setSkipScreenshot API - no need to hide/show window
            Logger.d(TAG, "Using setSkipScreenshot API to exclude floating window")
            val transaction = SurfaceControl.Transaction()
            try {
                // Use reflection to call setSkipScreenshot (hidden API)
                val setSkipScreenshotMethod = SurfaceControl.Transaction::class.java.getDeclaredMethod(
                    "setSkipScreenshot",
                    SurfaceControl::class.java,
                    Boolean::class.javaPrimitiveType
                )
                setSkipScreenshotMethod.isAccessible = true
                setSkipScreenshotMethod.invoke(transaction, surfaceControl, true)
                transaction.apply()
                Logger.d(TAG, "setSkipScreenshot(true) applied")

                // Small delay to ensure setSkipScreenshot takes effect before screenshot
                delay(100)
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to set skip screenshot, falling back to hide/show", e)
                // Fall back to hide/show method
                return@withContext captureWithHideShow(floatingWindowController)
            }

            val result = try {
                // Capture screenshot
                // Always prefer AccessibilityService, with shell as fallback
                val screenshot = captureScreen()
                Logger.logScreenshot(screenshot.width, screenshot.height, screenshot.isSensitive)
                screenshot
            } catch (e: Exception) {
                val handledError = ErrorHandler.handleScreenshotError(e.message ?: "Unknown error", false, e)
                Logger.e(TAG, ErrorHandler.formatErrorForLog(handledError), e)
                createFallbackScreenshot()
            } finally {
                // Always restore skip screenshot flag
                try {
                    val setSkipScreenshotMethod = SurfaceControl.Transaction::class.java.getDeclaredMethod(
                        "setSkipScreenshot",
                        SurfaceControl::class.java,
                        Boolean::class.javaPrimitiveType
                    )
                    setSkipScreenshotMethod.isAccessible = true
                    setSkipScreenshotMethod.invoke(transaction, surfaceControl, false)
                    transaction.apply()
                    Logger.d(TAG, "setSkipScreenshot(false) applied")
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to restore skip screenshot flag", e)
                }
            }
            result
        } else {
            // Fall back to hide/show method
            Logger.d(TAG, "Using hide/show method (setSkipScreenshot not available)")
            captureWithHideShow(floatingWindowController)
        }
    }

    /**
     * Captures screenshot using the traditional hide/show window method.
     *
     * This is used as a fallback when setSkipScreenshot API is not available.
     *
     * @param floatingWindowController The floating window controller, or null
     * @return Screenshot object containing the captured image data and metadata
     */
    private suspend fun captureWithHideShow(floatingWindowController: FloatingWindowController?): Screenshot {
        val hasFloatingWindow = floatingWindowController != null

        // Hide floating window before capture
        if (hasFloatingWindow) {
            Logger.d(TAG, "Hiding floating window")
            withContext(Dispatchers.Main) {
                floatingWindowController?.hide()
            }
            delay(HIDE_DELAY_MS)
        }

        val result = try {
            // Capture screenshot
            val screenshot = captureScreen()
            Logger.logScreenshot(screenshot.width, screenshot.height, screenshot.isSensitive)
            screenshot
        } catch (e: Exception) {
            val handledError = ErrorHandler.handleScreenshotError(e.message ?: "Unknown error", false, e)
            Logger.e(TAG, ErrorHandler.formatErrorForLog(handledError), e)
            createFallbackScreenshot()
        } finally {
            // Always restore floating window after capture (success or failure)
            if (hasFloatingWindow) {
                delay(SHOW_DELAY_MS)
                Logger.d(TAG, "Restoring floating window")
                withContext(Dispatchers.Main) {
                    floatingWindowController?.showAndBringToFront()
                }
            }
        }
        return result
    }

    /**
     * Captures the screen using AccessibilityService or shell command.
     *
     * Always tries AccessibilityService first (preferred method), falls back to shell command if needed.
     * Scales down the image if needed and converts to WebP format for smaller file size.
     *
     * @return Screenshot object with captured image data, or fallback screenshot on failure
     *
     */
    private suspend fun captureScreen(): Screenshot = withContext(Dispatchers.IO) {
        try {
            var bitmap: Bitmap? = null

            // Always try AccessibilityService first (preferred method)
            val accessibilityService = accessibilityServiceProvider()
            if (accessibilityService != null) {
                Logger.d(TAG, "Attempting capture via AccessibilityService")
                bitmap = accessibilityService.captureScreenshot()
                if (bitmap == null) {
                    Logger.d(TAG, "Accessibility capture returned null, falling back to shell")
                } else {
                    Logger.d(TAG, "Accessibility capture successful")
                }
            }

            // Fallback to Shell command if Accessibility failed or unavailable
            if (bitmap == null) {
                Logger.d(TAG, "Executing screencap command (shell fallback)")
                val pngData = executeScreencapToBytes()
                if (pngData != null && pngData.isNotEmpty()) {
                    bitmap = BitmapFactory.decodeByteArray(pngData, 0, pngData.size)
                    if (bitmap == null) {
                        Logger.w(TAG, "Failed to decode PNG")
                    } else {
                        Logger.d(TAG, "Shell capture successful: ${pngData.size} bytes")
                    }
                }
            }

            if (bitmap == null) {
                Logger.w(TAG, "Failed to capture screenshot (all methods failed), returning fallback")
                return@withContext createFallbackScreenshot()
            }

            Logger.d(TAG, "Bitmap captured: ${bitmap.width}x${bitmap.height}, config=${bitmap.config}")

            // Save bitmap for debugging (if enabled)
            // Save BEFORE scaling to preserve original size
            if (ENABLE_DEBUG_SCREENSHOT) {
                try {
                    val timestamp = System.currentTimeMillis()
                    val debugDir = java.io.File("/sdcard/Android/data/$PACKAGE_NAME")
                    val debugFile = java.io.File(debugDir, "screenshot_debug_${timestamp}.png")

                    // Ensure directory exists
                    if (!debugDir.exists()) {
                        debugDir.mkdirs()
                    }

                    // Save bitmap directly
                    java.io.FileOutputStream(debugFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }

                    // Verify file was created
                    if (debugFile.exists() && debugFile.length() > 0) {
                        Logger.d(TAG, "Debug screenshot saved: ${debugFile.absolutePath}, size: ${debugFile.length()} bytes")
                    }
                } catch (e: Exception) {
                    // Silently fail for debug feature
                }
            }

            val originalWidth = bitmap.width
            val originalHeight = bitmap.height

            // Calculate scaled dimensions based on max size constraints
            val (scaledWidth, scaledHeight) = calculateOptimalDimensions(originalWidth, originalHeight)

            // Scale bitmap if needed
            if (scaledWidth != originalWidth || scaledHeight != originalHeight) {
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                bitmap.recycle()
                bitmap = scaledBitmap
                Logger.d(TAG, "Scaled from ${originalWidth}x${originalHeight} to ${scaledWidth}x${scaledHeight}")
            }

            // Convert to WebP for better compression
            val webpStream = ByteArrayOutputStream()
            bitmap.compress(WEBP_FORMAT, WEBP_QUALITY, webpStream)
            bitmap.recycle()

            val webpData = webpStream.toByteArray()

            // val compressionRatio = if (pngData.isNotEmpty()) 100 * webpData.size / pngData.size else 0
            Logger.d(TAG, "Converted to WebP: ${webpData.size} bytes")

            val base64Data = encodeToBase64(webpData)
            Logger.d(TAG, "Screenshot captured: ${scaledWidth}x${scaledHeight}, base64 length: ${base64Data.length}")

            Screenshot(
                base64Data = base64Data,
                width = scaledWidth,
                height = scaledHeight,
                originalWidth = originalWidth,
                originalHeight = originalHeight,
                isSensitive = false
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Screenshot capture failed", e)
            createFallbackScreenshot()
        }
    }

    /**
     * Calculates optimal dimensions based on max size constraints.
     *
     * Maintains aspect ratio while ensuring the image fits within MAX_WIDTH x MAX_HEIGHT.
     * If the original image is already smaller, returns original dimensions.
     *
     * @param originalWidth Original image width in pixels
     * @param originalHeight Original image height in pixels
     * @return Pair of (scaledWidth, scaledHeight) maintaining aspect ratio
     *
     */
    private fun calculateOptimalDimensions(originalWidth: Int, originalHeight: Int): Pair<Int, Int> {
        // If already within limits, no scaling needed
        if (originalWidth <= MAX_WIDTH && originalHeight <= MAX_HEIGHT) {
            Logger.d(TAG, "Image already within limits: ${originalWidth}x${originalHeight}")
            return originalWidth to originalHeight
        }

        // Calculate scale ratios for both dimensions
        val widthRatio = MAX_WIDTH.toFloat() / originalWidth
        val heightRatio = MAX_HEIGHT.toFloat() / originalHeight

        // Use the smaller ratio to ensure both dimensions fit within limits
        val ratio = minOf(widthRatio, heightRatio)

        val scaledWidth = (originalWidth * ratio).toInt()
        val scaledHeight = (originalHeight * ratio).toInt()

        Logger.d(TAG, "Scaling with ratio $ratio: ${originalWidth}x${originalHeight} -> ${scaledWidth}x${scaledHeight}")
        return scaledWidth to scaledHeight
    }

    /**
     * Executes screencap command and returns raw bytes.
     *
     * Uses sequential chunk reading for large files to avoid Binder buffer overflow.
     * The screenshot is captured as PNG, converted to base64, then decoded back to bytes.
     *
     * @return Raw PNG bytes of the screenshot, or null if capture failed
     *
     */
    private suspend fun executeScreencapToBytes(): ByteArray? = coroutineScope {
        val timestamp = System.currentTimeMillis()
        // Use provided cache path or fallback to /data/local/tmp
        val baseDir = cachePath ?: "/data/local/tmp"
        val pngFile = "$baseDir/screenshot_$timestamp.png"

        try {
            val startTime = System.currentTimeMillis()

            // Ensure directory exists
            userService.executeCommand("mkdir -p $baseDir")

            // Capture screenshot directly to file
            // Note: We specify -d 0 to target the default display, reducing ambiguity on multi-display units.
            userService.executeCommand("screencap -d 0 -p $pngFile")

            // Check if file exists and is readable
            val file = java.io.File(pngFile)
            if (!file.exists() || file.length() == 0L) {
                return@coroutineScope null
            }

            // Read file directly
            return@coroutineScope file.readBytes()

        } catch (e: Exception) {
            // Silently fail - will fallback to AccessibilityService
            null
        } finally {
            // Clean up
            try {
                userService.executeCommand("rm -f $pngFile")
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Creates a fallback black screenshot in WebP format.
     *
     * Used when screenshot capture fails to provide a valid Screenshot object.
     * The fallback is marked as sensitive to indicate capture failure.
     *
     * @return Screenshot object with a black image and isSensitive=true
     *
     */
    private fun createFallbackScreenshot(): Screenshot {
        val bitmap = Bitmap.createBitmap(FALLBACK_WIDTH, FALLBACK_HEIGHT, Bitmap.Config.ARGB_8888)

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(WEBP_FORMAT, WEBP_QUALITY, outputStream)
        bitmap.recycle()

        val base64Data = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

        return Screenshot(
            base64Data = base64Data,
            width = FALLBACK_WIDTH,
            height = FALLBACK_HEIGHT,
            isSensitive = false
        )
    }

    /**
     * Encodes byte array to base64 string.
     *
     * @param data Byte array to encode
     * @return Base64-encoded string without line wrapping
     *
     */
    fun encodeToBase64(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    /**
     * Decodes base64 string to byte array.
     *
     * @param base64Data Base64-encoded string to decode
     * @return Decoded byte array
     *
     */
    fun decodeFromBase64(base64Data: String): ByteArray {
        return Base64.decode(base64Data, Base64.DEFAULT)
    }

    /**
     * Decodes base64 screenshot data to a Bitmap.
     *
     * @param base64Data Base64-encoded image data
     * @return Decoded Bitmap, or null if decoding fails
     *
     */
    fun decodeScreenshotToBitmap(base64Data: String): Bitmap? {
        return try {
            val bytes = decodeFromBase64(base64Data)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Encodes a Bitmap to base64 string.
     *
     * @param bitmap Bitmap to encode
     * @param format Compression format (default: WEBP_LOSSY)
     * @param quality Compression quality 0-100 (default: WEBP_QUALITY)
     * @return Base64-encoded string of the compressed image
     *
     */
    fun encodeBitmapToBase64(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat = WEBP_FORMAT,
        quality: Int = WEBP_QUALITY
    ): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(format, quality, outputStream)
        return encodeToBase64(outputStream.toByteArray())
    }

    /**
     * Creates a Screenshot object from a Bitmap.
     *
     * @param bitmap Source bitmap to convert
     * @param isSensitive Whether the content is sensitive (default: false)
     * @return Screenshot object with encoded image data and dimensions
     *
     */
    fun createScreenshotFromBitmap(bitmap: Bitmap, isSensitive: Boolean = false): Screenshot {
        return Screenshot(
            base64Data = encodeBitmapToBase64(bitmap),
            width = bitmap.width,
            height = bitmap.height,
            isSensitive = isSensitive
        )
    }
}
