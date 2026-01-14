package com.kevinluo.autoglm.ui

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.kevinluo.autoglm.ComponentManager
import com.kevinluo.autoglm.action.AgentAction
import com.kevinluo.autoglm.agent.PhoneAgentListener
import com.kevinluo.autoglm.util.Logger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Data class representing the UI state for the main screen.
 *
 * @property shizukuStatus Current Shizuku connection status
 * @property hasOverlayPermission Whether the app has overlay permission
 * @property taskStatus Current task execution status
 * @property stepNumber Current step number in task execution
 * @property thinking Current thinking text from the model
 * @property currentAction Current action being executed
 * @property isTaskRunning Whether a task is currently running
 * @property canStartTask Whether a new task can be started
 *
 */
data class MainUiState(
    val shizukuStatus: ShizukuStatus = ShizukuStatus.NOT_RUNNING,
    val hasOverlayPermission: Boolean = false,
    val taskStatus: TaskStatus = TaskStatus.IDLE,
    val stepNumber: Int = 0,
    val thinking: String = "",
    val currentAction: String = "",
    val isTaskRunning: Boolean = false,
    val canStartTask: Boolean = false
)

/**
 * Enum representing the Shizuku connection status.
 *
 */
enum class ShizukuStatus {
    /** Shizuku service is not running. */
    NOT_RUNNING,
    /** Shizuku is running but permission not granted. */
    NO_PERMISSION,
    /** Currently connecting to Shizuku. */
    CONNECTING,
    /** Successfully connected to Shizuku. */
    CONNECTED
}

/**
 * Sealed class representing one-time UI events.
 *
 * These events are consumed once and not persisted in the UI state.
 *
 */
sealed class MainUiEvent {
    /**
     * Event to show a toast message from a string resource.
     *
     * @property messageResId The string resource ID for the message
     */
    data class ShowToast(val messageResId: Int) : MainUiEvent()

    /**
     * Event to show a toast message with a text string.
     *
     * @property message The message text to display
     */
    data class ShowToastText(val message: String) : MainUiEvent()

    /**
     * Event indicating task completion.
     *
     * @property message The completion message
     */
    data class TaskCompleted(val message: String) : MainUiEvent()

    /**
     * Event indicating task failure.
     *
     * @property error The error message
     */
    data class TaskFailed(val error: String) : MainUiEvent()

    /** Event to minimize the app. */
    object MinimizeApp : MainUiEvent()
}

/**
 * ViewModel for MainActivity.
 *
 * Manages UI state using StateFlow for reactive updates and implements
 * [PhoneAgentListener] to receive callbacks from the agent during task execution.
 *
 */
class MainViewModel(application: Application) : PhoneAgentListener {

    private val applicationContext: Application = application
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val componentManager: ComponentManager by lazy {
        ComponentManager.getInstance(applicationContext)
    }

    private val _uiState = MutableStateFlow(MainUiState())

    /** Observable UI state for the main screen. */
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MainUiEvent>()

    /** Observable stream of one-time UI events. */
    val events = _events.asSharedFlow()

    private val _outputLog = MutableStateFlow("")

    /** Observable output log for displaying task execution details. */
    val outputLog: StateFlow<String> = _outputLog.asStateFlow()

    private val logBuilder = StringBuilder()

    /**
     * Updates the Shizuku connection status.
     *
     * @param status The new Shizuku status
     *
     */
    fun updateShizukuStatus(status: ShizukuStatus) {
        Logger.d(TAG, "updateShizukuStatus: $status")
        _uiState.value = _uiState.value.copy(
            shizukuStatus = status,
            canStartTask = calculateCanStartTask(status)
        )
    }

    /**
     * Updates the overlay permission status.
     *
     * @param hasPermission Whether the app has overlay permission
     *
     */
    fun updateOverlayPermission(hasPermission: Boolean) {
        Logger.d(TAG, "updateOverlayPermission: $hasPermission")
        _uiState.value = _uiState.value.copy(
            hasOverlayPermission = hasPermission,
            canStartTask = calculateCanStartTask(hasOverlayPermission = hasPermission)
        )
    }

    /**
     * Updates the task input availability based on whether text is entered.
     *
     * @param hasText Whether the task input field has text
     *
     */
    fun updateTaskInput(hasText: Boolean) {
        _uiState.value = _uiState.value.copy(
            canStartTask = calculateCanStartTask(hasTaskText = hasText)
        )
    }

    /**
     * Calculates whether a new task can be started based on current state.
     *
     * @param status Current Shizuku status
     * @param hasOverlayPermission Whether overlay permission is granted
     * @param hasTaskText Whether task input has text
     * @param isRunning Whether a task is currently running
     * @return true if a new task can be started, false otherwise
     */
    private fun calculateCanStartTask(
        status: ShizukuStatus = _uiState.value.shizukuStatus,
        hasOverlayPermission: Boolean = _uiState.value.hasOverlayPermission,
        hasTaskText: Boolean = true,
        isRunning: Boolean = _uiState.value.isTaskRunning
    ): Boolean {
        return status == ShizukuStatus.CONNECTED &&
               hasOverlayPermission &&
               hasTaskText &&
               !isRunning &&
               componentManager.phoneAgent != null
    }

    /**
     * Starts a new task with the given description.
     *
     * @param taskDescription The description of the task to execute
     *
     */
    fun startTask(taskDescription: String) {
        val agent = componentManager.phoneAgent ?: return

        if (agent.isRunning()) {
            appendLog("Error: A task is already running")
            Logger.w(TAG, "Attempted to start task while another is running")
            return
        }

        // Clear previous output
        logBuilder.clear()
        _outputLog.value = ""

        // Update UI state
        _uiState.value = _uiState.value.copy(
            taskStatus = TaskStatus.RUNNING,
            isTaskRunning = true,
            stepNumber = 0,
            thinking = "",
            currentAction = "",
            canStartTask = false
        )

        Logger.d(TAG, "Starting task: ${taskDescription.take(50)}...")
        appendLog("Starting task: $taskDescription")

        viewModelScope.launch {
            // Minimize app
            _events.emit(MainUiEvent.MinimizeApp)

            try {
                val result = agent.run(taskDescription)

                withContext(Dispatchers.Main) {
                    if (result.success) {
                        Logger.i(TAG, "Task completed successfully: ${result.message}")
                        _uiState.value = _uiState.value.copy(
                            taskStatus = TaskStatus.COMPLETED,
                            isTaskRunning = false
                        )
                        appendLog("Task completed: ${result.message}")
                        appendLog("Total steps: ${result.stepCount}")
                        _events.emit(MainUiEvent.TaskCompleted(result.message))
                    } else {
                        Logger.w(TAG, "Task failed: ${result.message}")
                        _uiState.value = _uiState.value.copy(
                            taskStatus = TaskStatus.FAILED,
                            isTaskRunning = false
                        )
                        appendLog("Task failed: ${result.message}")
                        _events.emit(MainUiEvent.TaskFailed(result.message))
                    }
                    updateCanStartTask()
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Task error", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        taskStatus = TaskStatus.FAILED,
                        isTaskRunning = false
                    )
                    appendLog("Task error: ${e.message}")
                    _events.emit(MainUiEvent.TaskFailed(e.message ?: "Unknown error"))
                    updateCanStartTask()
                }
            }
        }
    }

    /**
     * Cancels the currently running task.
     *
     */
    fun cancelTask() {
        Logger.d(TAG, "Cancelling task")
        componentManager.phoneAgent?.cancel()
        _uiState.value = _uiState.value.copy(
            taskStatus = TaskStatus.FAILED,
            isTaskRunning = false
        )
        appendLog("Task cancelled by user")
        updateCanStartTask()
    }

    /**
     * Updates the canStartTask flag based on current state.
     */
    private fun updateCanStartTask() {
        _uiState.value = _uiState.value.copy(
            canStartTask = calculateCanStartTask()
        )
    }

    /**
     * Appends a timestamped message to the output log.
     *
     * @param message The message to append
     */
    private fun appendLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        logBuilder.append("[$timestamp] $message\n")
        _outputLog.value = logBuilder.toString()
    }

    // region PhoneAgentListener Implementation

    /**
     * Called when a new step starts in the task execution.
     *
     * @param stepNumber The step number that is starting
     *
     */
    override fun onStepStarted(stepNumber: Int) {
        viewModelScope.launch(Dispatchers.Main) {
            Logger.d(TAG, "Step $stepNumber started")
            _uiState.value = _uiState.value.copy(stepNumber = stepNumber)
            appendLog("Step $stepNumber started")
        }
    }

    /**
     * Called when the model's thinking text is updated.
     *
     * @param thinking The current thinking text from the model
     *
     */
    override fun onThinkingUpdate(thinking: String) {
        viewModelScope.launch(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(thinking = thinking)
            if (thinking.isNotBlank()) {
                appendLog("Thinking: ${thinking.take(100)}${if (thinking.length > 100) "..." else ""}")
            }
        }
    }

    /**
     * Called when an action is executed.
     *
     * @param action The action that was executed
     *
     */
    override fun onActionExecuted(action: AgentAction) {
        viewModelScope.launch(Dispatchers.Main) {
            Logger.d(TAG, "Action executed: ${action.formatForDisplay()}")
            _uiState.value = _uiState.value.copy(currentAction = action.formatForDisplay())
            appendLog("Action: ${action.formatForDisplay()}")
        }
    }

    /**
     * Called when the task completes successfully.
     *
     * @param message The completion message
     *
     */
    override fun onTaskCompleted(message: String) {
        viewModelScope.launch(Dispatchers.Main) {
            Logger.i(TAG, "Task completed: $message")
            _uiState.value = _uiState.value.copy(
                taskStatus = TaskStatus.COMPLETED,
                isTaskRunning = false
            )
            appendLog("Task completed: $message")
            updateCanStartTask()
        }
    }

    /**
     * Called when the task fails.
     *
     * @param error The error message
     *
     */
    override fun onTaskFailed(error: String) {
        viewModelScope.launch(Dispatchers.Main) {
            Logger.e(TAG, "Task failed: $error")
            _uiState.value = _uiState.value.copy(
                taskStatus = TaskStatus.FAILED,
                isTaskRunning = false
            )
            appendLog("Task failed: $error")
            updateCanStartTask()
        }
    }

    /**
     * Called when screenshot capture starts.
     *
     * Handled by FloatingWindowService.
     *
     */
    override fun onScreenshotStarted() {
        // Handled by FloatingWindowService
    }

    /**
     * Called when screenshot capture completes.
     *
     * Handled by FloatingWindowService.
     *
     */
    override fun onScreenshotCompleted() {
        // Handled by FloatingWindowService
    }

    /**
     * Called when the floating window needs to be refreshed.
     *
     */
    override fun onFloatingWindowRefreshNeeded() {
        Logger.d(TAG, "Floating window refresh needed")
        FloatingWindowService.getInstance()?.bringToFront()
    }

    // endregion

    companion object {
        private const val TAG = "MainViewModel"
    }
}
