package com.kevinluo.autoglm.model

import com.kevinluo.autoglm.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
// Note: kotlinx.serialization and okhttp3 may not be available in system build
// Replaced with org.json and HttpURLConnection
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Configuration for the model client.
 *
 * Contains all settings required to connect to and communicate with the AutoGLM API.
 *
 * @property baseUrl API base URL for the model service
 * @property apiKey API key for authentication
 * @property modelName Name of the model to use for requests
 * @property maxTokens Maximum number of tokens in the response
 * @property temperature Sampling temperature for response generation
 * @property topP Top-p (nucleus) sampling parameter
 * @property frequencyPenalty Frequency penalty for response generation
 * @property timeoutSeconds Request timeout in seconds
 *
 */
data class ModelConfig(
    val baseUrl: String = "https://open.bigmodel.cn/api/paas/v4",
    val apiKey: String = "",
    val modelName: String = "autoglm-phone",
    val maxTokens: Int = 3000,
    val temperature: Float = 0.0f,
    val topP: Float = 0.85f,
    val frequencyPenalty: Float = 0.2f,
    val timeoutSeconds: Long = 120
)

/**
 * Response from the model containing thinking and action.
 *
 * Represents the parsed response from the AutoGLM model, separating the thinking
 * process from the action to be executed.
 *
 * @property thinking The model's reasoning/thinking text
 * @property action The action command to execute (e.g., "do(...)" or "finish(...)")
 * @property rawContent The raw unparsed response content
 * @property timeToFirstToken Time in milliseconds until the first token was received
 * @property totalTime Total time in milliseconds for the complete response
 *
 */
data class ModelResponse(
    val thinking: String,
    val action: String,
    val rawContent: String,
    val timeToFirstToken: Long?,
    val totalTime: Long?
)

/**
 * Sealed class representing different types of chat messages.
 *
 * Provides a type-safe way to represent system, user, and assistant messages
 * in a conversation with the model.
 *
 */
sealed class ChatMessage {
    /**
     * System message providing context or instructions to the model.
     *
     * @property content The system message content
     */
    data class System(val content: String) : ChatMessage()

    /**
     * User message, optionally including an image.
     *
     * @property text The text content of the user message
     * @property imageBase64 Optional base64-encoded image data
     */
    data class User(val text: String, val imageBase64: String? = null) : ChatMessage()

    /**
     * Assistant message representing the model's response.
     *
     * @property content The assistant's response content
     */
    data class Assistant(val content: String) : ChatMessage()
}


/**
 * DTO for serializing messages to the API.
 *
 * Converts [ChatMessage] instances to the JSON format expected by the API.
 *
 * @property role The role of the message sender ("system", "user", or "assistant")
 * @property content The message content as a JSON element (string or array for multi-modal)
 *
 */
// Note: @Serializable removed, using manual JSON serialization
data class MessageDto(
    val role: String,
    val content: Any // JSONObject or JSONArray or String
) {
    companion object {
        /**
         * Creates a MessageDto from a ChatMessage.
         *
         * Handles conversion of different message types including multi-modal
         * user messages with images.
         *
         * @param message The ChatMessage to convert
         * @return A MessageDto suitable for API serialization
         */
        fun fromChatMessage(message: ChatMessage): MessageDto {
            return when (message) {
                is ChatMessage.System -> MessageDto(
                    role = "system",
                    content = message.content
                )
                is ChatMessage.Assistant -> MessageDto(
                    role = "assistant",
                    content = message.content
                )
                is ChatMessage.User -> {
                    if (message.imageBase64 != null) {
                        // Multi-modal content with text and image
                        // Detect image format from base64 header or default to PNG
                        val mimeType = detectImageMimeType(message.imageBase64)
                        val contentParts = JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", message.text)
                            })
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply {
                                    put("url", "data:$mimeType;base64,${message.imageBase64}")
                                })
                            })
                        }
                        MessageDto(role = "user", content = contentParts)
                    } else {
                        // Text-only content
                        MessageDto(role = "user", content = message.text)
                    }
                }
            }
        }

        /**
         * Detects the MIME type of an image from its base64-encoded data.
         *
         * Checks the first few bytes (magic numbers) to determine the format.
         *
         * @param base64Data The base64-encoded image data
         * @return The detected MIME type, defaults to "image/png" if unknown
         */
        private fun detectImageMimeType(base64Data: String): String {
            // Base64 magic number prefixes for common image formats
            // PNG: iVBORw0KGgo (starts with 0x89 0x50 0x4E 0x47)
            // JPEG: /9j/ (starts with 0xFF 0xD8 0xFF)
            // GIF: R0lGOD (starts with GIF87a or GIF89a)
            // WebP: UklGR (starts with RIFF....WEBP)
            return when {
                base64Data.startsWith("/9j/") -> "image/jpeg"
                base64Data.startsWith("iVBORw") -> "image/png"
                base64Data.startsWith("R0lGOD") -> "image/gif"
                base64Data.startsWith("UklGR") -> "image/webp"
                else -> "image/png" // Default to PNG
            }
        }
    }
}

/**
 * Content part for multi-modal messages.
 *
 * Represents a single part of a multi-modal message, which can be either
 * text or an image URL.
 *
 * @property type The type of content ("text" or "image_url")
 * @property text The text content, if type is "text"
 * @property imageUrl The image URL wrapper, if type is "image_url"
 *
 */
// Note: @Serializable removed, using manual JSON serialization
data class ContentPart(
    val type: String,
    val text: String? = null,
    val imageUrl: ImageUrl? = null
)

/**
 * Image URL wrapper for API.
 *
 * Wraps an image URL for inclusion in multi-modal messages.
 *
 * @property url The image URL (can be a data URL with base64 content)
 *
 */
// Note: @Serializable removed, using manual JSON serialization
data class ImageUrl(
    val url: String
)

/**
 * Chat completion request body.
 *
 * Represents the request payload sent to the chat completions API endpoint.
 *
 * @property model The model name to use for completion
 * @property messages List of messages in the conversation
 * @property maxTokens Maximum number of tokens to generate
 * @property temperature Sampling temperature (0.0 to 2.0)
 * @property topP Top-p (nucleus) sampling parameter
 * @property frequencyPenalty Frequency penalty (-2.0 to 2.0)
 * @property stream Whether to stream the response
 *
 */
// Note: @Serializable removed, using manual JSON serialization
data class ChatCompletionRequest(
    val model: String,
    val messages: List<MessageDto>,
    val maxTokens: Int,
    val temperature: Float,
    val topP: Float,
    val frequencyPenalty: Float,
    val stream: Boolean = true
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                messages.forEach { msg ->
                    put(JSONObject().apply {
                        put("role", msg.role)
                        when (val content = msg.content) {
                            is String -> put("content", content)
                            is JSONArray -> put("content", content)
                            is JSONObject -> put("content", content)
                            else -> put("content", content.toString())
                        }
                    })
                }
            })
            put("max_tokens", maxTokens)
            put("temperature", temperature)
            put("top_p", topP)
            put("frequency_penalty", frequencyPenalty)
            put("stream", stream)
        }
    }
}

/**
 * Streaming response chunk.
 *
 * Represents a single chunk in a streaming chat completion response.
 *
 * @property choices List of completion choices in this chunk
 *
 */
// Note: @Serializable removed, using manual JSON parsing
data class ChatCompletionChunk(
    val choices: List<ChunkChoice> = emptyList()
) {
    companion object {
        fun fromJson(json: JSONObject): ChatCompletionChunk {
            val choicesArray = json.optJSONArray("choices")
            val choices = if (choicesArray != null) {
                (0 until choicesArray.length()).map { i ->
                    ChunkChoice.fromJson(choicesArray.getJSONObject(i))
                }
            } else {
                emptyList()
            }
            return ChatCompletionChunk(choices = choices)
        }
    }
}

/**
 * Choice in a streaming chunk.
 *
 * Represents a single choice within a streaming response chunk.
 *
 * @property delta The delta content for this choice
 *
 */
// Note: @Serializable removed, using manual JSON parsing
data class ChunkChoice(
    val delta: Delta = Delta()
) {
    companion object {
        fun fromJson(json: JSONObject): ChunkChoice {
            val deltaObj = json.optJSONObject("delta")
            val delta = if (deltaObj != null) {
                Delta.fromJson(deltaObj)
            } else {
                Delta()
            }
            return ChunkChoice(delta = delta)
        }
    }
}

/**
 * Delta content in streaming response.
 *
 * Contains the incremental content added in a streaming chunk.
 *
 * @property content The content string added in this delta, or null if none
 *
 */
// Note: @Serializable removed, using manual JSON parsing
data class Delta(
    val content: String? = null
) {
    companion object {
        fun fromJson(json: JSONObject): Delta {
            val content = if (json.has("content") && !json.isNull("content")) {
                json.getString("content")
            } else {
                null
            }
            return Delta(content = content)
        }
    }
}


/**
 * Sealed class representing network errors.
 *
 * Provides type-safe error handling for various network failure scenarios.
 *
 */
sealed class NetworkError : Exception() {
    /**
     * Error indicating a connection failure.
     *
     * @property message Description of the connection failure
     */
    data class ConnectionFailed(override val message: String) : NetworkError()

    /**
     * Error indicating a request timeout.
     *
     * @property timeoutMs The timeout duration in milliseconds
     */
    data class Timeout(val timeoutMs: Long) : NetworkError() {
        override val message: String = "Request timed out after ${timeoutMs}ms"
    }

    /**
     * Error indicating a server-side error.
     *
     * @property statusCode The HTTP status code returned by the server
     * @property message Description of the server error
     */
    data class ServerError(val statusCode: Int, override val message: String) : NetworkError()

    /**
     * Error indicating a response parsing failure.
     *
     * @property rawResponse The raw response that failed to parse
     */
    data class ParseError(val rawResponse: String) : NetworkError() {
        override val message: String = "Failed to parse response: $rawResponse"
    }
}

/**
 * Result wrapper for model requests.
 *
 * Provides a type-safe way to represent success or failure of model requests.
 *
 */
sealed class ModelResult {
    /**
     * Successful model response.
     *
     * @property response The parsed model response
     */
    data class Success(val response: ModelResponse) : ModelResult()

    /**
     * Failed model request.
     *
     * @property error The network error that occurred
     */
    data class Error(val error: NetworkError) : ModelResult()
}

/**
 * Client for communicating with the AutoGLM API.
 *
 * Handles request/response serialization and streaming for chat completions.
 * Uses Server-Sent Events (SSE) for streaming responses.
 *
 * @property config Configuration for the model client
 *
 */
class ModelClient(private val config: ModelConfig) {

    // Note: okhttp3 and kotlinx.serialization replaced with HttpURLConnection and org.json
    // Track current connection for cancellation
    @Volatile
    private var currentConnection: HttpURLConnection? = null

    /**
     * Cancels the current ongoing request if any.
     *
     * Safe to call even if no request is in progress.
     */
    fun cancelCurrentRequest() {
        currentConnection?.let { conn ->
            Logger.d(TAG, "Cancelling current request")
            conn.disconnect()
            currentConnection = null
        }
    }

    /**
     * Sends a request to the model and returns the response.
     *
     * Uses Server-Sent Events (SSE) for streaming responses. The response
     * is accumulated and parsed to extract thinking and action components.
     *
     * @param messages List of chat messages to send to the model
     * @return ModelResult containing either the parsed response or an error
     */
    suspend fun request(messages: List<ChatMessage>): ModelResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var timeToFirstToken: Long? = null

        val url = "${config.baseUrl}/chat/completions"
        Logger.logNetworkRequest("POST", url)

        try {
            val messageDtos = messages.map { MessageDto.fromChatMessage(it) }
            Logger.d(TAG, "Preparing request with ${messageDtos.size} messages")

            val requestBody = ChatCompletionRequest(
                model = config.modelName,
                messages = messageDtos,
                maxTokens = config.maxTokens,
                temperature = config.temperature,
                topP = config.topP,
                frequencyPenalty = config.frequencyPenalty,
                stream = true
            )

            val requestJson = requestBody.toJsonObject().toString()

            val contentBuilder = StringBuilder()

            suspendCancellableCoroutine<ModelResult> { continuation ->
                try {
                    val urlObj = URL(url)
                    val connection = urlObj.openConnection() as HttpURLConnection
                    currentConnection = connection

                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Accept", "text/event-stream")
                    connection.connectTimeout = (config.timeoutSeconds * 1000).toInt()
                    connection.readTimeout = (config.timeoutSeconds * 1000).toInt()
                    connection.doOutput = true

                    // Write request body
                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(requestJson)
                        writer.flush()
                    }

                    val responseCode = connection.responseCode
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                        Logger.logNetworkError("Server error $responseCode: $errorBody")
                        continuation.resume(
                            ModelResult.Error(NetworkError.ServerError(responseCode, errorBody))
                        )
                        return@suspendCancellableCoroutine
                    }

                    // Read SSE stream
                    BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null && continuation.isActive) {
                            val data = line ?: continue

                            if (data.startsWith("data: ")) {
                                val jsonData = data.substring(6).trim()

                                if (jsonData == "[DONE]") {
                                    val totalTime = System.currentTimeMillis() - startTime
                                    val rawContent = contentBuilder.toString()
                                    val (thinking, action) = parseThinkingAndAction(rawContent)

                                    Logger.logNetworkResponse(HttpURLConnection.HTTP_OK, totalTime)
                                    Logger.d(TAG, "Response complete: ${rawContent.length} chars, TTFT=${timeToFirstToken}ms")

                                    val response = ModelResponse(
                                        thinking = thinking,
                                        action = action,
                                        rawContent = rawContent,
                                        timeToFirstToken = timeToFirstToken,
                                        totalTime = totalTime
                                    )

                                    continuation.resume(ModelResult.Success(response))
                                    break
                                }

                                try {
                                    val chunkJson = JSONObject(jsonData)
                                    val chunk = ChatCompletionChunk.fromJson(chunkJson)
                                    val content = chunk.choices.firstOrNull()?.delta?.content

                                    if (content != null) {
                                        if (timeToFirstToken == null) {
                                            timeToFirstToken = System.currentTimeMillis() - startTime
                                            Logger.d(TAG, "First token received after ${timeToFirstToken}ms")
                                        }
                                        contentBuilder.append(content)
                                    }
                                } catch (e: Exception) {
                                    // Ignore parse errors for individual chunks
                                    Logger.v(TAG, "Chunk parse error (ignored): ${e.message}")
                                }
                            }
                        }

                        // If we haven't resumed yet, do so with what we have
                        if (continuation.isActive && contentBuilder.isNotEmpty()) {
                            val totalTime = System.currentTimeMillis() - startTime
                            val rawContent = contentBuilder.toString()
                            val (thinking, action) = parseThinkingAndAction(rawContent)
                            val response = ModelResponse(
                                thinking = thinking,
                                action = action,
                                rawContent = rawContent,
                                timeToFirstToken = timeToFirstToken,
                                totalTime = totalTime
                            )
                            continuation.resume(ModelResult.Success(response))
                        } else if (continuation.isActive) {
                            Logger.logNetworkError("Empty response received")
                            continuation.resume(
                                ModelResult.Error(NetworkError.ParseError("Empty response"))
                            )
                        }
                    }
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        val error = when (e) {
                            is java.net.SocketTimeoutException -> {
                                Logger.logNetworkError("Request timeout", e)
                                NetworkError.Timeout(config.timeoutSeconds * 1000)
                            }
                            is IOException -> {
                                Logger.logNetworkError("Connection failed: ${e.message}", e)
                                NetworkError.ConnectionFailed(e.message ?: "Connection failed")
                            }
                            else -> {
                                Logger.logNetworkError("Unknown error: ${e.message}", e)
                                NetworkError.ConnectionFailed(e.message ?: "Unknown error")
                            }
                        }
                        continuation.resume(ModelResult.Error(error))
                    }
                } finally {
                    currentConnection?.disconnect()
                    currentConnection = null
                }

                continuation.invokeOnCancellation {
                    Logger.d(TAG, "Request cancelled via coroutine cancellation")
                    currentConnection?.disconnect()
                    currentConnection = null
                }
            }
        } catch (e: Exception) {
            currentConnection?.disconnect()
            currentConnection = null
            Logger.logNetworkError("Request failed: ${e.message}", e)
            when (e) {
                is java.net.SocketTimeoutException ->
                    ModelResult.Error(NetworkError.Timeout(config.timeoutSeconds * MILLIS_PER_SECOND))
                is IOException ->
                    ModelResult.Error(NetworkError.ConnectionFailed(e.message ?: "Connection failed"))
                else ->
                    ModelResult.Error(NetworkError.ConnectionFailed(e.message ?: "Unknown error"))
            }
        }
    }

    /**
     * Parses the thinking and action from the model response content.
     *
     * The response format typically contains:
     * - Thinking section (before the action)
     * - Action in format: do(action="...", ...) or finish(message="...")
     *
     * Note: The model may wrap content in <think> and <answer> tags, which we strip out.
     *
     * @param content The raw response content to parse
     * @return Pair of (thinking, action) strings
     */
    private fun parseThinkingAndAction(content: String): Pair<String, String> {
        // Clean up the content by removing all XML-style tags
        val cleanContent = content
            .replace(Regex("""<think>\s*"""), "")
            .replace(Regex("""\s*</think>"""), "")
            .replace(Regex("""<answer>\s*"""), "")
            .replace(Regex("""\s*</answer>"""), "")
            .trim()

        // Find action using bracket-aware matching to handle nested parentheses
        val doAction = findActionWithBalancedParens(cleanContent, "do")
        val finishAction = findActionWithBalancedParens(cleanContent, "finish")

        // Find the earliest action match
        val actionMatch = listOfNotNull(doAction, finishAction)
            .minByOrNull { it.first }

        return if (actionMatch != null) {
            val thinking = cleanContent.substring(0, actionMatch.first).trim()
            val action = actionMatch.second.trim()
            Pair(thinking, action)
        } else {
            // No action found, entire content is thinking
            Pair(cleanContent, "")
        }
    }

    /**
     * Finds an action pattern with balanced parentheses.
     *
     * This correctly handles nested parentheses in text content like:
     * do(action=Type, text="hello (world)")
     *
     * @param content The content to search in
     * @param actionName The action name to find ("do" or "finish")
     * @return Pair of (startIndex, matchedString) or null if not found
     */
    private fun findActionWithBalancedParens(content: String, actionName: String): Pair<Int, String>? {
        // Find the start of the action pattern (actionName followed by optional whitespace and '(')
        val startPattern = Regex("""$actionName\s*\(""")
        val startMatch = startPattern.find(content) ?: return null

        val startIndex = startMatch.range.first
        val openParenIndex = startMatch.range.last // Index of '('

        // Now find the matching closing parenthesis, accounting for nesting and quotes
        var depth = 1
        var i = openParenIndex + 1
        var inDoubleQuote = false
        var inSingleQuote = false
        var escaped = false

        while (i < content.length && depth > 0) {
            val char = content[i]

            if (escaped) {
                escaped = false
                i++
                continue
            }

            when (char) {
                '\\' -> escaped = true
                '"' -> if (!inSingleQuote) inDoubleQuote = !inDoubleQuote
                '\'' -> if (!inDoubleQuote) inSingleQuote = !inSingleQuote
                '(' -> if (!inDoubleQuote && !inSingleQuote) depth++
                ')' -> if (!inDoubleQuote && !inSingleQuote) depth--
            }
            i++
        }

        return if (depth == 0) {
            Pair(startIndex, content.substring(startIndex, i))
        } else {
            // Unbalanced parentheses, fall back to simple match
            null
        }
    }

    /**
     * Checks if the response indicates task completion.
     *
     * @param action The action string to check
     * @return True if the action is a finish action
     */
    fun isFinishAction(action: String): Boolean {
        return action.startsWith("finish(") || action.startsWith("finish (")
    }

    /**
     * Checks if the response indicates a do action.
     *
     * @param action The action string to check
     * @return True if the action is a do action
     */
    fun isDoAction(action: String): Boolean {
        return action.startsWith("do(") || action.startsWith("do (")
    }

    /**
     * Extracts the finish message from a finish action.
     *
     * Handles escaped quotes within the message.
     *
     * @param action The finish action string
     * @return The extracted message, or null if not a valid finish action
     */
    fun extractFinishMessage(action: String): String? {
        if (!isFinishAction(action)) return null

        // Find message= followed by a quote
        val messageStartPattern = Regex("""message\s*=\s*["']""")
        val startMatch = messageStartPattern.find(action) ?: return null

        val quoteChar = action[startMatch.range.last]
        val contentStart = startMatch.range.last + 1

        // Find the closing quote, handling escaped quotes
        val result = StringBuilder()
        var i = contentStart
        var escaped = false

        while (i < action.length) {
            val char = action[i]

            if (escaped) {
                result.append(char)
                escaped = false
                i++
                continue
            }

            when (char) {
                '\\' -> escaped = true
                quoteChar -> return result.toString() // Found closing quote
                else -> result.append(char)
            }
            i++
        }

        // No closing quote found, return what we have
        return result.toString().ifEmpty { null }
    }

    /**
     * Tests the connection to the model API.
     *
     * Sends a simple request to verify API connectivity and authentication.
     *
     * @return TestResult indicating success or failure with details
     */
    suspend fun testConnection(): TestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val url = "${config.baseUrl}/chat/completions"

        Logger.logNetworkRequest("POST", url)
        Logger.d(TAG, "Testing connection to: $url")

        try {
            // Create a minimal test request
            val testMessage = MessageDto(
                role = "user",
                content = "Hi"
            )

            val requestBody = ChatCompletionRequest(
                model = config.modelName,
                messages = listOf(testMessage),
                maxTokens = TEST_MAX_TOKENS,
                temperature = 0f,
                topP = 1f,
                frequencyPenalty = 0f,
                stream = false
            )

            val requestJson = requestBody.toJsonObject().toString()

            val urlObj = URL(url)
            val connection = urlObj.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = (config.timeoutSeconds * 1000).toInt()
            connection.readTimeout = (config.timeoutSeconds * 1000).toInt()
            connection.doOutput = true

            // Write request body
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestJson)
                writer.flush()
            }

            val responseCode = connection.responseCode
            val latency = System.currentTimeMillis() - startTime

            when {
                responseCode == HttpURLConnection.HTTP_OK -> {
                    Logger.logNetworkResponse(responseCode, latency)
                    Logger.d(TAG, "Connection test successful, latency: ${latency}ms")
                    TestResult.Success(latency)
                }
                responseCode == HttpURLConnection.HTTP_UNAUTHORIZED -> {
                    Logger.logNetworkError("Connection test failed: Invalid API key ($responseCode)")
                    TestResult.AuthError("API 密钥无效")
                }
                responseCode == HttpURLConnection.HTTP_NOT_FOUND -> {
                    Logger.logNetworkError("Connection test failed: Model not found ($responseCode)")
                    TestResult.ModelNotFound("模型 '${config.modelName}' 不存在")
                }
                else -> {
                    val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    Logger.logNetworkError("Connection test failed: $responseCode - $errorBody")
                    TestResult.ServerError(responseCode, errorBody.ifEmpty { "服务器错误" })
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            Logger.logNetworkError("Connection test timeout", e)
            TestResult.Timeout("连接超时")
        } catch (e: java.net.UnknownHostException) {
            Logger.logNetworkError("Connection test failed: Unknown host", e)
            TestResult.ConnectionError("无法解析服务器地址")
        } catch (e: java.net.ConnectException) {
            Logger.logNetworkError("Connection test failed: Connection refused", e)
            TestResult.ConnectionError("无法连接到服务器")
        } catch (e: IOException) {
            Logger.logNetworkError("Connection test failed: ${e.message}", e)
            TestResult.ConnectionError(e.message ?: "网络错误")
        } catch (e: Exception) {
            Logger.logNetworkError("Connection test failed: ${e.message}", e)
            TestResult.ConnectionError(e.message ?: "未知错误")
        }
    }

    /**
     * Result of a connection test.
     *
     * Sealed class representing the various outcomes of a connection test.
     *
     */
    sealed class TestResult {
        /**
         * Successful connection test.
         *
         * @property latencyMs Round-trip latency in milliseconds
         */
        data class Success(val latencyMs: Long) : TestResult()

        /**
         * Authentication error (invalid API key).
         *
         * @property message Error message describing the auth failure
         */
        data class AuthError(val message: String) : TestResult()

        /**
         * Model not found error.
         *
         * @property message Error message describing the missing model
         */
        data class ModelNotFound(val message: String) : TestResult()

        /**
         * Server-side error.
         *
         * @property code HTTP status code
         * @property message Error message from the server
         */
        data class ServerError(val code: Int, val message: String) : TestResult()

        /**
         * Connection error (network issues).
         *
         * @property message Error message describing the connection failure
         */
        data class ConnectionError(val message: String) : TestResult()

        /**
         * Request timeout.
         *
         * @property message Error message describing the timeout
         */
        data class Timeout(val message: String) : TestResult()
    }

    companion object {
        private const val TAG = "ModelClient"
        private const val HTTP_STATUS_OK = 200
        private const val HTTP_STATUS_UNAUTHORIZED = 401
        private const val HTTP_STATUS_NOT_FOUND = 404
        private const val MILLIS_PER_SECOND = 1000L
        private const val TEST_MAX_TOKENS = 10
    }
}
