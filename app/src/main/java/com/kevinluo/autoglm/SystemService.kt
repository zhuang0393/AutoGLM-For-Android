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
                command.startsWith("input tap ") -> executeShellCommand(command)
                command.startsWith("input swipe ") -> executeShellCommand(command)
                command.startsWith("input keyevent ") -> executeShellCommand(command)
                command.startsWith("screencap ") -> executeScreencap(command)
                command.startsWith("am start ") -> executeAmStart(command)
                command.startsWith("dumpsys window") -> executeDumpsysWindow()
                command.startsWith("ime list -s") -> executeImeList()
                command.startsWith("ime enable ") -> executeImeEnable(command)
                command.startsWith("ime set ") -> executeImeSet(command)
                command.startsWith("settings get secure default_input_method") -> executeGetDefaultIme()
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
     * Executes ime list -s command using InputMethodManager.
     */
    private fun executeImeList(): String {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            val enabledMethods = imm.enabledInputMethodList
            val ids = enabledMethods.joinToString("\n") { it.id }
            "$ids\n[exit code: 0]"
        } catch (e: Exception) {
            "Error: ${e.message}\n[exit code: 1]"
        }
    }

    /**
     * Executes ime enable command using Settings.Secure.
     */
    private fun executeImeEnable(command: String): String {
        return try {
            val id = command.removePrefix("ime enable ").trim()
            val resolver = context.contentResolver
            val enabledImes = android.provider.Settings.Secure.getString(
                resolver,
                android.provider.Settings.Secure.ENABLED_INPUT_METHODS
            ) ?: ""

            if (enabledImes.contains(id)) {
                return "Already enabled: $id\n[exit code: 0]"
            }

            val newEnabledImes = if (enabledImes.isEmpty()) id else "$enabledImes:$id"
            val success = android.provider.Settings.Secure.putString(
                resolver,
                android.provider.Settings.Secure.ENABLED_INPUT_METHODS,
                newEnabledImes
            )

            if (success) "Input method enabled: $id\n[exit code: 0]" else "Failed to enable input method\n[exit code: 1]"
        } catch (e: Exception) {
            "Error: ${e.message}\n[exit code: 1]"
        }
    }

    /**
     * Executes ime set command using Settings.Secure.
     */
    private fun executeImeSet(command: String): String {
        return try {
            val id = command.removePrefix("ime set ").trim()
            val success = android.provider.Settings.Secure.putString(
                context.contentResolver,
                android.provider.Settings.Secure.DEFAULT_INPUT_METHOD,
                id
            )
            if (success) "Input method set to $id\n[exit code: 0]" else "Failed to set input method\n[exit code: 1]"
        } catch (e: Exception) {
            "Error: ${e.message}\n[exit code: 1]"
        }
    }

    /**
     * Executes settings get secure default_input_method.
     */
    private fun executeGetDefaultIme(): String {
        return try {
            val id = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
            ) ?: ""
            "$id\n[exit code: 0]"
        } catch (e: Exception) {
            "Error: ${e.message}\n[exit code: 1]"
        }
    }

    /**
     * Executes am broadcast command.
     *
     * Supports format: am broadcast -a ACTION -p PACKAGE [--es key 'value'] [--receiver-include-background]
     *
     * Handles quoted values correctly, including values with spaces and escaped quotes.
     */
    private fun executeAmBroadcast(command: String): String {
        return try {
            val argsStr = command.substringAfter("am broadcast ").trim()
            val intent = Intent()
            var packageName: String? = null

            // Parse arguments manually to handle quoted strings correctly
            var i = 0
            val args = mutableListOf<String>()
            var currentArg = StringBuilder()
            var inQuotes = false
            var quoteChar: Char? = null

            while (i < argsStr.length) {
                val char = argsStr[i]
                when {
                    !inQuotes && (char == '\'' || char == '"') -> {
                        inQuotes = true
                        quoteChar = char
                        i++
                    }
                    inQuotes && char == quoteChar -> {
                        // Check for escaped quote
                        if (i + 1 < argsStr.length && argsStr[i + 1] == quoteChar) {
                            currentArg.append(char)
                            i += 2
                        } else {
                            inQuotes = false
                            quoteChar = null
                            i++
                        }
                    }
                    !inQuotes && char.isWhitespace() -> {
                        if (currentArg.isNotEmpty()) {
                            args.add(currentArg.toString())
                            currentArg.clear()
                        }
                        i++
                    }
                    else -> {
                        currentArg.append(char)
                        i++
                    }
                }
            }
            if (currentArg.isNotEmpty()) {
                args.add(currentArg.toString())
            }

            // Parse arguments
            i = 0
            while (i < args.size) {
                when (args[i]) {
                    "-a" -> {
                        if (i + 1 < args.size) {
                            intent.action = args[i + 1]
                            i += 2
                        } else {
                            i++
                        }
                    }
                    "-p" -> {
                        if (i + 1 < args.size) {
                            packageName = args[i + 1]
                            intent.setPackage(packageName)
                            i += 2
                        } else {
                            i++
                        }
                    }
                    "--es" -> {
                        // String extra: --es key value
                        if (i + 2 < args.size) {
                            val key = args[i + 1]
                            var value = args[i + 2]
                            // Remove surrounding quotes if still present
                            value = value.removeSurrounding("'", "'").removeSurrounding("\"", "\"")
                            // Handle escaped quotes
                            value = value.replace("'\\''", "'")
                            intent.putExtra(key, value)
                            Logger.d(TAG, "Added extra: $key = '${value.take(50)}${if (value.length > 50) "..." else ""}'")
                            i += 3
                        } else {
                            i++
                        }
                    }
                    "--receiver-include-background" -> {
                        // Add flag for background receivers (Android 8.0+)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            intent.addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND)
                        }
                        i++
                    }
                    else -> {
                        i++
                    }
                }
            }

            Logger.i(TAG, "Sending broadcast: action=${intent.action}, package=$packageName, hasExtras=${intent.hasExtra("msg")}")

            // Send broadcast with appropriate flags
            // FLAG_RECEIVER_INCLUDE_BACKGROUND is already set in intent flags if needed
            context.sendBroadcast(intent)

            "[exit code: 0]"
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to execute broadcast: $command", e)
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
