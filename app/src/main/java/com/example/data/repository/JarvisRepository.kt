package com.example.data.repository

import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.local.JarvisDao
import com.example.data.local.NoteEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.TaskEntity
import com.example.data.preferences.AssistantLanguage
import com.example.data.preferences.AssistantPersonality
import com.example.data.preferences.JarvisSettings
import com.example.data.preferences.SettingsDataStore
import com.example.data.remote.GeminiApiService
import com.example.data.remote.GeminiContent
import com.example.data.remote.GeminiErrorEnvelope
import com.example.data.remote.GeminiGenerateRequest
import com.example.data.remote.GeminiGenerationConfig
import com.example.data.remote.GeminiInlineData
import com.example.data.remote.GeminiPart
import com.example.data.remote.WeatherApiService
import com.example.ui.theme.ThemeMode
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import java.io.IOException

sealed class ApiConnectionStatus {
    data object Unconfigured : ApiConnectionStatus()
    data object Testing : ApiConnectionStatus()
    data class Connected(val modelCount: Int = 0, val message: String = "Connected to Gemini Neural Engine") : ApiConnectionStatus()
    data class InvalidKey(val message: String) : ApiConnectionStatus()
    data class RateLimited(val message: String) : ApiConnectionStatus()
    data class NetworkError(val message: String) : ApiConnectionStatus()
}

enum class AiFeatureMode(val label: String, val systemAddon: String, val starterPrompt: String) {
    GENERAL("General", "", "How can I assist you today?"),
    EXPLAIN("Explain", "Explain complex concepts clearly using structured analogies and step-by-step clarity.", "Explain quantum entanglement in simple terms."),
    TRANSLATE("Translate", "Translate accurately while preserving tone, nuance, and cultural context.", "Translate this into Bengali and English: "),
    EMAIL_DOCS("Email / Docs", "Draft polished, professional emails or documents with clear structure and tone.", "Write a professional project update email about "),
    CODE_GEN("Code Gen", "Write clean, well-commented, production-ready code with concise explanation.", "Write a clean function in Kotlin/Python for "),
    DEBUG_CODE("Debug", "Analyze bugs methodically, identify root causes, and provide fixed code.", "Debug this code and explain the root cause:\n"),
    SUMMARIZE("Summarize", "Extract key points, action items, and a concise executive summary.", "Summarize the following text into key bullet points:\n"),
    MATH("Math", "Solve mathematical problems step-by-step and clearly highlight the final result.", "Solve step by step: "),
    CREATIVE("Creative", "Write vivid, original creative prose, stories, or scripts.", "Write a short sci-fi scene about "),
    STUDY("Study", "Act as an engaging tutor: break down concepts, give practice questions, and verify mastery.", "Help me study and quiz me on "),
    PRODUCTIVITY("Productivity", "Organize schedules, prioritize tasks, and break goals into actionable steps.", "Create an actionable daily plan for ")
}

data class PendingAttachment(
    val name: String,
    val type: String, // "image" or "text"
    val uriString: String,
    val mimeType: String,
    val imageBase64: String? = null,
    val textContent: String? = null
)

data class WeatherSnapshot(
    val locationLabel: String,
    val temperatureC: Double,
    val feelsLikeC: Double,
    val humidityPercent: Int,
    val windKmh: Double,
    val conditionLabel: String
)

class JarvisRepository(
    private val dao: JarvisDao,
    private val settingsStore: SettingsDataStore,
    private val geminiApi: GeminiApiService,
    private val weatherApi: WeatherApiService
) {
    private val errorMoshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val errorAdapter = errorMoshi.adapter(GeminiErrorEnvelope::class.java)

    val settingsFlow: Flow<JarvisSettings> = settingsStore.settingsFlow
    val sessionsFlow: Flow<List<ChatSessionEntity>> = dao.observeSessions()
    val notesFlow: Flow<List<NoteEntity>> = dao.observeNotes()
    val tasksFlow: Flow<List<TaskEntity>> = dao.observeTasks()
    val remindersFlow: Flow<List<ReminderEntity>> = dao.observeReminders()

    fun searchSessions(query: String): Flow<List<ChatSessionEntity>> =
        if (query.isBlank()) dao.observeSessions() else dao.searchSessions(query.trim())

    fun observeMessages(sessionId: Long): Flow<List<ChatMessageEntity>> =
        dao.observeMessages(sessionId)

    suspend fun createSession(title: String = "New Neural Session", modeTag: String = "GENERAL"): Long {
        val now = System.currentTimeMillis()
        return dao.insertSession(
            ChatSessionEntity(
                title = title,
                modeTag = modeTag,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun deleteSession(sessionId: Long) = dao.deleteSession(sessionId)

    suspend fun clearAllChats() = dao.deleteAllSessions()

    suspend fun clearAllProductivityData() {
        dao.deleteAllNotes()
        dao.deleteAllTasks()
        dao.deleteAllReminders()
    }

    suspend fun resetApplication() {
        clearAllChats()
        clearAllProductivityData()
        settingsStore.resetAll()
    }

    // Settings mutations
    suspend fun saveApiKey(rawKey: String) = settingsStore.saveApiKey(rawKey)
    suspend fun deleteApiKey() = settingsStore.deleteApiKey()
    suspend fun setModel(modelId: String) = settingsStore.setModel(modelId)
    suspend fun setLanguage(language: AssistantLanguage) = settingsStore.setLanguage(language)
    suspend fun setThemeMode(themeMode: ThemeMode) = settingsStore.setThemeMode(themeMode)
    suspend fun setPersonality(personality: AssistantPersonality) = settingsStore.setPersonality(personality)
    suspend fun setTtsEnabled(enabled: Boolean) = settingsStore.setTtsEnabled(enabled)
    suspend fun setVoiceConversationMode(enabled: Boolean) = settingsStore.setVoiceConversationMode(enabled)
    suspend fun setWakeWordListening(enabled: Boolean) = settingsStore.setWakeWordListening(enabled)
    suspend fun setSpeechSpeed(speed: Float) = settingsStore.setSpeechSpeed(speed)
    suspend fun setSoundEffectsEnabled(enabled: Boolean) = settingsStore.setSoundEffectsEnabled(enabled)
    suspend fun setMemoryEnabled(enabled: Boolean) = settingsStore.setMemoryEnabled(enabled)
    suspend fun setWeatherCity(city: String) = settingsStore.setWeatherCity(city)

    suspend fun testGeminiConnection(apiKey: String): ApiConnectionStatus {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            return ApiConnectionStatus.Unconfigured
        }
        return try {
            val response = geminiApi.listModels(trimmed)
            if (response.isSuccessful) {
                val count = response.body()?.models?.size ?: 0
                ApiConnectionStatus.Connected(
                    modelCount = count,
                    message = "Authenticated ($count models available)"
                )
            } else {
                parseGeminiHttpError(response.code(), response.errorBody()?.string())
            }
        } catch (e: IOException) {
            ApiConnectionStatus.NetworkError("Network error: ${e.localizedMessage ?: "Check internet connection"}")
        } catch (e: Exception) {
            ApiConnectionStatus.NetworkError("Connection failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    suspend fun sendMessage(
        sessionId: Long,
        userText: String,
        attachment: PendingAttachment?,
        mode: AiFeatureMode,
        settings: JarvisSettings
    ): Result<String> {
        if (!settings.hasApiKey || settings.decryptedApiKey.isBlank()) {
            return Result.failure(IllegalStateException("Gemini API key is not configured. Open Settings to add your key."))
        }

        val cleanPrompt = buildString {
            if (userText.isNotBlank()) append(userText.trim())
            if (attachment?.type == "text" && !attachment.textContent.isNullOrBlank()) {
                if (isNotEmpty()) append("\n\n")
                append("--- Attached Document: ${attachment.name} ---\n")
                append(attachment.textContent)
            }
        }

        if (cleanPrompt.isBlank() && attachment?.imageBase64 == null) {
            return Result.failure(IllegalArgumentException("Please enter a message or attach a file/image."))
        }

        val displayUserText = userText.trim().ifBlank {
            when (attachment?.type) {
                "image" -> "Analyze attached image: ${attachment.name}"
                "text" -> "Summarize & analyze attached file: ${attachment.name}"
                else -> ""
            }
        }

        val now = System.currentTimeMillis()
        dao.insertMessage(
            ChatMessageEntity(
                sessionId = sessionId,
                role = "user",
                content = displayUserText,
                attachmentName = attachment?.name,
                attachmentType = attachment?.type,
                attachmentUri = attachment?.uriString,
                timestamp = now
            )
        )

        val history = dao.getMessagesOnce(sessionId)
        val isFirstUserTurn = history.count { it.role == "user" } <= 1
        if (isFirstUserTurn) {
            val autoTitle = displayUserText.take(42).let { if (displayUserText.length > 42) "$it…" else it }
            dao.updateSession(
                ChatSessionEntity(
                    id = sessionId,
                    title = autoTitle.ifBlank { "Neural Session" },
                    modeTag = mode.name,
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            val existingTitle = history.firstOrNull { it.role == "user" }?.content?.take(42) ?: "Neural Session"
            dao.updateSession(
                ChatSessionEntity(
                    id = sessionId,
                    title = existingTitle,
                    modeTag = mode.name,
                    createdAt = history.firstOrNull()?.timestamp ?: now,
                    updatedAt = now
                )
            )
        }

        // Build Gemini request contents
        val systemPrompt = buildString {
            append(settings.personality.systemInstruction)
            append(" ")
            append(settings.language.promptHint)
            if (mode.systemAddon.isNotBlank()) {
                append(" ")
                append(mode.systemAddon)
            }
        }

        val priorTurns = if (settings.memoryEnabled) {
            // Exclude the just-inserted latest user turn so we can attach binary/full text cleanly
            history.dropLast(1).takeLast(16).map { msg ->
                GeminiContent(
                    role = if (msg.role == "model") "model" else "user",
                    parts = listOf(GeminiPart(text = msg.content))
                )
            }
        } else {
            emptyList()
        }

        val currentParts = buildList {
            if (cleanPrompt.isNotBlank()) {
                add(GeminiPart(text = cleanPrompt))
            } else if (attachment?.type == "image") {
                add(GeminiPart(text = "Analyze this image in detail."))
            }
            if (attachment?.type == "image" && !attachment.imageBase64.isNullOrBlank()) {
                add(
                    GeminiPart(
                        inline_data = GeminiInlineData(
                            mime_type = attachment.mimeType.ifBlank { "image/jpeg" },
                            data = attachment.imageBase64
                        )
                    )
                )
            }
        }

        val request = GeminiGenerateRequest(
            system_instruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
            contents = priorTurns + GeminiContent(role = "user", parts = currentParts),
            generationConfig = GeminiGenerationConfig(temperature = 0.7, maxOutputTokens = 4096)
        )

        val modelPath = settings.selectedModel.removePrefix("models/")
        return try {
            val response = geminiApi.generateContent(
                model = modelPath,
                apiKey = settings.decryptedApiKey,
                body = request
            )
            if (response.isSuccessful) {
                val replyText = response.body()
                    ?.candidates
                    ?.firstOrNull()
                    ?.content
                    ?.parts
                    ?.mapNotNull { it.text }
                    ?.joinToString("\n")
                    ?.trim()

                if (replyText.isNullOrBlank()) {
                    Result.failure(IllegalStateException("Gemini returned an empty response. Try rephrasing or switching models."))
                } else {
                    dao.insertMessage(
                        ChatMessageEntity(
                            sessionId = sessionId,
                            role = "model",
                            content = replyText,
                            modelUsed = modelPath,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    Result.success(replyText)
                }
            } else {
                val parsed = parseGeminiHttpError(response.code(), response.errorBody()?.string())
                val errorMsg = when (parsed) {
                    is ApiConnectionStatus.InvalidKey -> parsed.message
                    is ApiConnectionStatus.RateLimited -> parsed.message
                    is ApiConnectionStatus.NetworkError -> parsed.message
                    else -> "Gemini API error (${response.code()})"
                }
                Result.failure(IllegalStateException(errorMsg))
            }
        } catch (e: IOException) {
            Result.failure(IOException("Network error contacting Gemini: ${e.localizedMessage ?: "Check connectivity"}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseGeminiHttpError(code: Int, rawErrorBody: String?): ApiConnectionStatus {
        val serverMsg = try {
            rawErrorBody?.let { errorAdapter.fromJson(it)?.error?.message }
        } catch (_: Exception) {
            null
        }
        return when (code) {
            400, 401, 403 -> ApiConnectionStatus.InvalidKey(
                serverMsg ?: "Invalid or unauthorized Gemini API key (HTTP $code)."
            )
            429 -> ApiConnectionStatus.RateLimited(
                serverMsg ?: "Gemini API rate limit or quota exceeded (HTTP 429). Please retry shortly."
            )
            404 -> ApiConnectionStatus.NetworkError(
                serverMsg ?: "Selected Gemini model was not found (HTTP 404). Change model in Settings."
            )
            else -> ApiConnectionStatus.NetworkError(
                serverMsg ?: "Gemini server returned HTTP $code."
            )
        }
    }

    // Weather via Open-Meteo
    suspend fun fetchWeatherByCity(city: String): Result<WeatherSnapshot> {
        return try {
            val geo = weatherApi.searchCity(name = city.trim())
            val first = geo.results?.firstOrNull()
                ?: return Result.failure(IllegalArgumentException("City '$city' not found"))
            val forecast = weatherApi.getCurrentWeather(
                latitude = first.latitude,
                longitude = first.longitude
            )
            val current = forecast.current
                ?: return Result.failure(IllegalStateException("Weather telemetry unavailable"))
            val label = listOfNotNull(first.name, first.country).joinToString(", ")
            Result.success(
                WeatherSnapshot(
                    locationLabel = label,
                    temperatureC = current.temperature_2m ?: 0.0,
                    feelsLikeC = current.apparent_temperature ?: (current.temperature_2m ?: 0.0),
                    humidityPercent = current.relative_humidity_2m ?: 0,
                    windKmh = current.wind_speed_10m ?: 0.0,
                    conditionLabel = decodeWmoWeather(current.weather_code ?: 0)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchWeatherByCoordinates(lat: Double, lon: Double, label: String = "Current Location"): Result<WeatherSnapshot> {
        return try {
            val forecast = weatherApi.getCurrentWeather(latitude = lat, longitude = lon)
            val current = forecast.current
                ?: return Result.failure(IllegalStateException("Weather telemetry unavailable"))
            Result.success(
                WeatherSnapshot(
                    locationLabel = label,
                    temperatureC = current.temperature_2m ?: 0.0,
                    feelsLikeC = current.apparent_temperature ?: (current.temperature_2m ?: 0.0),
                    humidityPercent = current.relative_humidity_2m ?: 0,
                    windKmh = current.wind_speed_10m ?: 0.0,
                    conditionLabel = decodeWmoWeather(current.weather_code ?: 0)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun decodeWmoWeather(code: Int): String = when (code) {
        0 -> "Clear Sky"
        1, 2, 3 -> "Partly Cloudy"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Drizzle"
        61, 63, 65 -> "Rain"
        71, 73, 75 -> "Snow"
        80, 81, 82 -> "Rain Showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Atmospheric"
    }

    // Notes CRUD
    suspend fun saveNote(note: NoteEntity): Long = dao.insertNote(note)
    suspend fun deleteNote(id: Long) = dao.deleteNote(id)

    // Tasks CRUD
    suspend fun saveTask(task: TaskEntity): Long = dao.insertTask(task)
    suspend fun toggleTask(task: TaskEntity) = dao.updateTask(task.copy(isCompleted = !task.isCompleted))
    suspend fun deleteTask(id: Long) = dao.deleteTask(id)

    // Reminders CRUD
    suspend fun saveReminder(reminder: ReminderEntity): Long = dao.insertReminder(reminder)
    suspend fun updateReminder(reminder: ReminderEntity) = dao.updateReminder(reminder)
    suspend fun deleteReminder(id: Long) = dao.deleteReminder(id)
}
