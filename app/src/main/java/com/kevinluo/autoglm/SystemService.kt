package com.kevinluo.autoglm

import android.app.ActivityManager
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import com.kevinluo.autoglm.util.Logger
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * System service for executing operations with system privileges.
 * This replaces UserService when running in system image.
 *
 * Uses Android System APIs instead of shell commands:
 * - Instrumentation API for touch/key events
 * - SurfaceControl/ImageReader for screenshots
 * - ActivityManager for app launching
 * - WindowManager for current app detection
 */
class SystemService(private val context: Context) : IUserService.Stub() {

    private val instrumentation = Instrumentation()
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val packageManager = context.packageManager

    // Handler thread for async operations
    private val handlerThread = HandlerThread("SystemService").apply { start() }
    private val handler = Handler(handlerThread.looper)

    companion object {
        private const val TAG = "SystemService"
    }

    /**
     * Destroys the service.
     */
    override fun destroy() {
        Logger.i(TAG, "destroy")
        handlerThread.quitSafely()
    }

    /**
     * Executes a command using System APIs.
     *
     * Parses common shell commands and executes them using appropriate System APIs.
     * Falls back to Runtime.exec for unsupported commands.
     *
     * @param command The shell command to execute
     * @return The command output
     */
    override fun executeCommand(command: String): String {
        return try {
            when {
                command.startsWith("input tap ") -> executeTap(command)
                command.startsWith("input swipe ") -> executeSwipe(command)
                command.startsWith("input keyevent ") -> executeKeyEvent(command)
                command.startsWith("screencap ") -> executeScreencap(command)
                command.startsWith("am start ") -> executeAmStart(command)
                command.startsWith("dumpsys window") -> executeDumpsysWindow()
                command.startsWith("ime set ") -> executeImeSet(command)
                command.startsWith("am broadcast ") -> executeAmBroadcast(command)
                command.startsWith("pm resolve-activity") -> executePmResolveActivity(command)
                else -> executeShellCommand(command)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error executing command: $command", e)
            "Error: ${e.message}\n${e.stackTraceToString()}"
        }
    }

    /**
     * Executes input tap command using Instrumentation.
     */
    private fun executeTap(command: String): String {
        val parts = command.split(" ")
        if (parts.size < 3) {
            return "Error: invalid tap command"
        }

        val x = parts[2].toIntOrNull() ?: return "Error: invalid x coordinate"
        val y = parts[3].toIntOrNull() ?: return "Error: invalid y coordinate"

        return try {
            val downTime = SystemClock.uptimeMillis()
            val eventTime = SystemClock.uptimeMillis()

            val downEvent = MotionEvent.obtain(
                downTime, eventTime, MotionEvent.ACTION_DOWN,
                x.toFloat(), y.toFloat(), 0
            )
            downEvent.source = InputDevice.SOURCE_TOUCHSCREEN

            val upEvent = MotionEvent.obtain(
                downTime, eventTime + 10, MotionEvent.ACTION_UP,
                x.toFloat(), y.toFloat(), 0
            )
            upEvent.source = InputDevice.SOURCE_TOUCHSCREEN

            instrumentation.sendPointerSync(downEvent)
            instrumentation.sendPointerSync(upEvent)

            downEvent.recycle()
            upEvent.recycle()

            "[exit code: 0]"
        } catch (e: Exception) {
            "Error: ${e.message}\n[exit code: 1]"
        }
    }

    /**
     * Executes input swipe command using Instrumentation.
     */
    private fun executeSwipe(command: String): String {
        val parts = command.split(" ")
        if (parts.size < 6) {
            return "Error: invalid swipe command"
        }

        val x1 = parts[2].toIntOrNull() ?: return "Error: invalid x1 coordinate"
        val y1 = parts[3].toIntOrNull() ?: return "Error: invalid y1 coordinate"
        val x2 = parts[4].toIntOrNull() ?: return "Error: invalid x2 coordinate"
        val y2 = parts[5].toIntOrNull() ?: return "Error: invalid y2 coordinate"
        val duration = if (parts.size > 6) parts[6].toLongOrNull() ?: 300L else 300L

        return try {
            val downTime = SystemClock.uptimeMillis()
            val steps = maxOf(10, (duration / 10).toInt())
            val stepDelay = duration / steps

            // Touch down
            val downEvent = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN,
                x1.toFloat(), y1.toFloat(), 0
            )
            downEvent.source = InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.sendPointerSync(downEvent)
            downEvent.recycle()

            // Move
            for (i in 1 until steps) {
                val progress = i.toFloat() / steps
                val x = x1 + (x2 - x1) * progress
                val y = y1 + (y2 - y1) * progress
                val eventTime = downTime + (stepDelay * i)

                val moveEvent = MotionEvent.obtain(
                    downTime, eventTime, MotionEvent.ACTION_MOVE,
                    x, y, 0
                )
                moveEvent.source = InputDevice.SOURCE_TOUCHSCREEN
                instrumentation.sendPointerSync(moveEvent)
                moveEvent.recycle()

                SystemClock.sleep(stepDelay)
            }

            // Touch up
            val upTime = downTime + duration
            val upEvent = MotionEvent.obtain(
                downTime, upTime, MotionEvent.ACTION_UP,
                x2.toFloat(), y2.toFloat(), 0
            )
            upEvent.source = InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.sendPointerSync(upEvent)
            upEvent.recycle()

            "[exit code: 0]"
        } catch (e: Exception) {
            "Error: ${e.message}\n[exit code: 1]"
        }
    }

    /**
     * Executes input keyevent command using Instrumentation.
     */
    private fun executeKeyEvent(command: String): String {
        val parts = command.split(" ")
        if (parts.size < 3) {
            return "Error: invalid keyevent command"
        }

        val keyCode = parts[2].toIntOrNull() ?: return "Error: invalid keycode"

        return try {
            val downTime = SystemClock.uptimeMillis()
            val eventTime = SystemClock.uptimeMillis()

            val downEvent = KeyEvent(downTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
            val upEvent = KeyEvent(downTime, eventTime + 10, KeyEvent.ACTION_UP, keyCode, 0)

            instrumentation.sendKeySync(downEvent)
            instrumentation.sendKeySync(upEvent)

            "[exit code: 0]"
        } catch (e: Exception) {
            "Error: ${e.message}\n[exit code: 1]"
        }
    }

    /**
     * Executes screencap command using System API.
     *
     * For system builds, we can use shell command with system permissions.
     * The actual screenshot capture is handled by ScreenshotService which
     * processes the output.
     */
    private fun executeScreencap(command: String): String {
        // In system build, we can execute screencap directly with system permissions
        // The command output will be processed by ScreenshotService
        return executeShellCommand(command)
    }

    /**
     * Executes am start command using ActivityManager.
     */
    private fun executeAmStart(command: String): String {
        return try {
            val parts = command.split(" ")
            var packageName: String? = null
            var componentName: String? = null

            for (i in parts.indices) {
                when (parts[i]) {
                    "-p" -> if (i + 1 < parts.size) packageName = parts[i + 1]
                    "-n" -> if (i + 1 < parts.size) componentName = parts[i + 1]
                }
            }

            val intent = if (componentName != null) {
                Intent().setComponent(
                    android.content.ComponentName.unflattenFromString(componentName)
                )
            } else if (packageName != null) {
                packageManager.getLaunchIntentForPackage(packageName)
                    ?: return "Error: no launch intent for package $packageName\n[exit code: 1]"
            } else {
                return "Error: no package or component specified\n[exit code: 1]"
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            "[exit code: 0]"
        } catch (e: Exception) {
            "Error: ${e.message}\n[exit code: 1]"
        }
    }

    /**
     * Executes dumpsys window to get current app.
     */
    private fun executeDumpsysWindow(): String {
        return try {
            val tasks = activityManager.appTasks
            if (tasks.isNotEmpty()) {
                val taskInfo = tasks[0].taskInfo
                val packageName = taskInfo.topActivity?.packageName ?: ""
                "mCurrentFocus=Window{...$packageName/...}\n[exit code: 0]"
            } else {
                "[exit code: 0]"
            }
        } catch (e: Exception) {
            "Error: ${e.message}\n[exit code: 1]"
        }
    }

    /**
     * Executes ime set command.
     */
    private fun executeImeSet(command: String): String {
        // IME switching requires system permissions
        // Fallback to shell command
        return executeShellCommand(command)
    }

    /**
     * Executes am broadcast command.
     */
    private fun executeAmBroadcast(command: String): String {
        return try {
            // Parse broadcast intent
            val intentStr = command.substringAfter("am broadcast ").trim()
            val intent = Intent.parseUri(intentStr, Intent.URI_INTENT_SCHEME)
            context.sendBroadcast(intent)
            "[exit code: 0]"
        } catch (e: Exception) {
            "Error: ${e.message}\n[exit code: 1]"
        }
    }

    /**
     * Executes pm resolve-activity command.
     */
    private fun executePmResolveActivity(command: String): String {
        return try {
            val parts = command.split(" ")
            var packageName: String? = null

            for (i in parts.indices) {
                if (parts[i] == "-p" && i + 1 < parts.size) {
                    packageName = parts[i + 1]
                    break
                }
            }

            if (packageName != null) {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    val component = intent.component
                    if (component != null) {
                        return "${component.packageName}/${component.className}\n[exit code: 0]"
                    }
                }
            }

            "[exit code: 1]"
        } catch (e: Exception) {
            "Error: ${e.message}\n[exit code: 1]"
        }
    }

    /**
     * Fallback: executes shell command using Runtime.exec.
     */
    private fun executeShellCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))

            val output = StringBuilder()
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            val errorOutput = StringBuilder()
            while (errorReader.readLine().also { line = it } != null) {
                errorOutput.append(line).append("\n")
            }

            val exitCode = process.waitFor()
            reader.close()
            errorReader.close()

            if (errorOutput.isNotEmpty()) {
                output.append("\n[stderr]\n").append(errorOutput)
            }
            output.append("\n[exit code: $exitCode]")

            output.toString()
        } catch (e: Exception) {
            "Error: ${e.message}\n${e.stackTraceToString()}"
        }
    }
}
