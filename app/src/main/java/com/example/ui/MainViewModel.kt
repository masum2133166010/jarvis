package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.local.JarvisDatabase
import com.example.data.local.NoteEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.TaskEntity
import com.example.data.preferences.AssistantLanguage
import com.example.data.preferences.AssistantPersonality
import com.example.data.preferences.JarvisSettings
import com.example.data.preferences.SettingsDataStore
import com.example.data.remote.GeminiApiService
import com.example.data.remote.WeatherApiService
import com.example.data.repository.AiFeatureMode
import com.example.data.repository.ApiConnectionStatus
import com.example.data.repository.JarvisRepository
import com.example.data.repository.PendingAttachment
import com.example.data.repository.WeatherSnapshot
import com.example.ui.theme.ThemeMode
import com.example.util.FileAttachmentHelper
import com.example.util.HudSoundType
import com.example.util.MathEvaluator
import com.example.util.ReminderScheduler
import com.example.util.SoundEffectsPlayer
import com.example.util.SystemTelemetry
import com.example.util.TelemetryProvider
import com.example.util.VoiceAssistantManager
import com.example.util.VoiceState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class WeatherUiState {
    data object Loading : WeatherUiState()
    data class Success(val snapshot: WeatherSnapshot) : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = JarvisRepository(
        dao = JarvisDatabase.getInstance(application).jarvisDao(),
        settingsStore = SettingsDataStore(application),
        geminiApi = GeminiApiService.create(),
        weatherApi = WeatherApiService.create()
    )

    val soundPlayer = SoundEffectsPlayer()

    val settings: StateFlow<JarvisSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, JarvisSettings())

    private val _apiStatus = MutableStateFlow<ApiConnectionStatus>(ApiConnectionStatus.Unconfigured)
    val apiStatus: StateFlow<ApiConnectionStatus> = _apiStatus.asStateFlow()

    private val _chatSearchQuery = MutableStateFlow("")
    val chatSearchQuery: StateFlow<String> = _chatSearchQuery.asStateFlow()

    val sessions: StateFlow<List<ChatSessionEntity>> = _chatSearchQuery
        .flatMapLatest { q -> repository.searchSessions(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    val activeMessages: StateFlow<List<ChatMessageEntity>> = _activeSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.observeMessages(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedAiMode = MutableStateFlow(AiFeatureMode.GENERAL)
    val selectedAiMode: StateFlow<AiFeatureMode> = _selectedAiMode.asStateFlow()

    private val _pendingAttachment = MutableStateFlow<PendingAttachment?>(null)
    val pendingAttachment: StateFlow<PendingAttachment?> = _pendingAttachment.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    private val _navigateToChatSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
    val navigateToChatSignal = _navigateToChatSignal.asSharedFlow()

    private val _weatherState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val weatherState: StateFlow<WeatherUiState> = _weatherState.asStateFlow()

    private val _telemetry = MutableStateFlow(TelemetryProvider.read(application))
    val telemetry: StateFlow<SystemTelemetry> = _telemetry.asStateFlow()

    // Tools data
    val notes: StateFlow<List<NoteEntity>> = repository.notesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tasks: StateFlow<List<TaskEntity>> = repository.tasksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminders: StateFlow<List<ReminderEntity>> = repository.remindersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Calculator state
    private val _calcExpression = MutableStateFlow("")
    val calcExpression: StateFlow<String> = _calcExpression.asStateFlow()

    private val _calcPreview = MutableStateFlow("0")
    val calcPreview: StateFlow<String> = _calcPreview.asStateFlow()

    private val _calcHistory = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val calcHistory: StateFlow<List<Pair<String, String>>> = _calcHistory.asStateFlow()

    private var initialConnectionChecked = false

    val voiceManager: VoiceAssistantManager = VoiceAssistantManager(
        context = application,
        onTranscriptReady = { spokenText ->
            handleVoiceTranscript(spokenText)
        },
        onWakeWordTriggered = {
            soundPlayer.play(HudSoundType.MIC_START)
            val lang = settings.value.language.localeTag
            voiceManager.startListening(lang, wakeMode = false)
        },
        onVoiceError = { err ->
            soundPlayer.play(HudSoundType.ALERT)
            _snackbarMessage.tryEmit(err)
        },
        onSpeechCompleted = {
            val current = settings.value
            if (current.voiceConversationMode && !_isGenerating.value) {
                voiceManager.startListening(current.language.localeTag, wakeMode = false)
            } else if (current.wakeWordListeningEnabled && !_isGenerating.value) {
                voiceManager.startListening(current.language.localeTag, wakeMode = true)
            }
        }
    )

    val voiceState: StateFlow<VoiceState> = voiceManager.voiceState
    val partialTranscript: StateFlow<String> = voiceManager.partialTranscript
    val waveformLevels: StateFlow<List<Float>> = voiceManager.waveformLevels

    init {
        viewModelScope.launch {
            settings.collect { s ->
                soundPlayer.enabled = s.soundEffectsEnabled
                voiceManager.updateVoiceConfig(s.language.localeTag, s.speechSpeed)

                if (!initialConnectionChecked) {
                    initialConnectionChecked = true
                    if (s.hasApiKey) {
                        testApiKeyConnection(s.decryptedApiKey, silent = true)
                    } else {
                        _apiStatus.value = ApiConnectionStatus.Unconfigured
                    }
                    refreshWeather(s.weatherCity)
                }
            }
        }

        // Select newest session automatically when sessions load
        viewModelScope.launch {
            combine(sessions, _activeSessionId) { list, active -> list to active }
                .distinctUntilChanged()
                .collect { (list, active) ->
                    if (active == null && list.isNotEmpty()) {
                        _activeSessionId.value = list.first().id
                    }
                }
        }

        // Periodic device telemetry refresh every 15s
        viewModelScope.launch {
            while (true) {
                _telemetry.value = TelemetryProvider.read(getApplication())
                delay(15_000L)
            }
        }
    }

    fun playTapSound() = soundPlayer.play(HudSoundType.TAP)

    // API Key Management
    fun saveAndConnectApiKey(rawKey: String) {
        val cleaned = rawKey.trim()
        if (cleaned.isEmpty()) {
            _snackbarMessage.tryEmit("Please paste a valid Gemini API key first.")
            return
        }
        viewModelScope.launch {
            _apiStatus.value = ApiConnectionStatus.Testing
            repository.saveApiKey(cleaned)
            val result = repository.testGeminiConnection(cleaned)
            _apiStatus.value = result
            when (result) {
                is ApiConnectionStatus.Connected -> {
                    soundPlayer.play(HudSoundType.RESPONSE_READY)
                    _snackbarMessage.tryEmit("API key saved securely & connected to Gemini.")
                }
                is ApiConnectionStatus.InvalidKey -> {
                    soundPlayer.play(HudSoundType.ALERT)
                    _snackbarMessage.tryEmit("Key saved, but Gemini rejected it: ${result.message}")
                }
                is ApiConnectionStatus.RateLimited -> {
                    soundPlayer.play(HudSoundType.ALERT)
                    _snackbarMessage.tryEmit(result.message)
                }
                is ApiConnectionStatus.NetworkError -> {
                    soundPlayer.play(HudSoundType.ALERT)
                    _snackbarMessage.tryEmit("Key saved. ${result.message}")
                }
                ApiConnectionStatus.Unconfigured, ApiConnectionStatus.Testing -> Unit
            }
        }
    }

    fun testApiKeyConnection(candidateKey: String? = null, silent: Boolean = false) {
        val keyToTest = candidateKey?.trim()?.takeIf { it.isNotEmpty() }
            ?: settings.value.decryptedApiKey
        if (keyToTest.isEmpty()) {
            _apiStatus.value = ApiConnectionStatus.Unconfigured
            if (!silent) _snackbarMessage.tryEmit("No API key entered or saved.")
            return
        }
        viewModelScope.launch {
            _apiStatus.value = ApiConnectionStatus.Testing
            val status = repository.testGeminiConnection(keyToTest)
            _apiStatus.value = status
            if (!silent) {
                when (status) {
                    is ApiConnectionStatus.Connected -> {
                        soundPlayer.play(HudSoundType.RESPONSE_READY)
                        _snackbarMessage.tryEmit(status.message)
                    }
                    is ApiConnectionStatus.InvalidKey -> {
                        soundPlayer.play(HudSoundType.ALERT)
                        _snackbarMessage.tryEmit(status.message)
                    }
                    is ApiConnectionStatus.RateLimited -> {
                        soundPlayer.play(HudSoundType.ALERT)
                        _snackbarMessage.tryEmit(status.message)
                    }
                    is ApiConnectionStatus.NetworkError -> {
                        soundPlayer.play(HudSoundType.ALERT)
                        _snackbarMessage.tryEmit(status.message)
                    }
                    else -> Unit
                }
            }
        }
    }

    fun deleteApiKey() {
        viewModelScope.launch {
            repository.deleteApiKey()
            _apiStatus.value = ApiConnectionStatus.Unconfigured
            soundPlayer.play(HudSoundType.TAP)
            _snackbarMessage.tryEmit("Gemini API key deleted from encrypted storage.")
        }
    }

    // Chat & AI Features
    fun setAiMode(mode: AiFeatureMode) {
        soundPlayer.play(HudSoundType.TAP)
        _selectedAiMode.value = mode
    }

    fun setChatSearchQuery(query: String) {
        _chatSearchQuery.value = query
    }

    fun selectSession(sessionId: Long) {
        soundPlayer.play(HudSoundType.TAP)
        _activeSessionId.value = sessionId
        _chatError.value = null
    }

    fun createNewChat(mode: AiFeatureMode = _selectedAiMode.value) {
        soundPlayer.play(HudSoundType.TAP)
        _selectedAiMode.value = mode
        viewModelScope.launch {
            val id = repository.createSession(title = "New Neural Session", modeTag = mode.name)
            _activeSessionId.value = id
            _chatError.value = null
        }
    }

    fun deleteSession(sessionId: Long) {
        soundPlayer.play(HudSoundType.TAP)
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_activeSessionId.value == sessionId) {
                _activeSessionId.value = null
            }
            _snackbarMessage.tryEmit("Conversation deleted.")
        }
    }

    fun attachImage(uri: Uri) {
        viewModelScope.launch {
            FileAttachmentHelper.loadImageAttachment(getApplication(), uri)
                .onSuccess {
                    soundPlayer.play(HudSoundType.TAP)
                    _pendingAttachment.value = it
                }
                .onFailure {
                    soundPlayer.play(HudSoundType.ALERT)
                    _snackbarMessage.tryEmit(it.message ?: "Failed to load image.")
                }
        }
    }

    fun attachDocument(uri: Uri) {
        viewModelScope.launch {
            FileAttachmentHelper.loadTextAttachment(getApplication(), uri)
                .onSuccess {
                    soundPlayer.play(HudSoundType.TAP)
                    _pendingAttachment.value = it
                }
                .onFailure {
                    soundPlayer.play(HudSoundType.ALERT)
                    _snackbarMessage.tryEmit(it.message ?: "Failed to read document.")
                }
        }
    }

    fun clearPendingAttachment() {
        _pendingAttachment.value = null
    }

    fun clearChatError() {
        _chatError.value = null
    }

    fun sendPrompt(text: String) {
        val currentSettings = settings.value
        val attachment = _pendingAttachment.value
        if (text.isBlank() && attachment == null) return
        if (_isGenerating.value) return

        if (!currentSettings.hasApiKey) {
            _chatError.value = "Gemini API key is not configured. Open Settings -> Gemini API Configuration to add your key."
            soundPlayer.play(HudSoundType.ALERT)
            return
        }

        soundPlayer.play(HudSoundType.TAP)
        _chatError.value = null
        _isGenerating.value = true
        _pendingAttachment.value = null

        viewModelScope.launch {
            val sessionId = _activeSessionId.value ?: repository.createSession(
                title = text.take(40).ifBlank { attachment?.name ?: "Neural Session" },
                modeTag = _selectedAiMode.value.name
            ).also { _activeSessionId.value = it }

            val result = repository.sendMessage(
                sessionId = sessionId,
                userText = text,
                attachment = attachment,
                mode = _selectedAiMode.value,
                settings = currentSettings
            )
            _isGenerating.value = false

            result.onSuccess { reply ->
                soundPlayer.play(HudSoundType.RESPONSE_READY)
                _apiStatus.value = ApiConnectionStatus.Connected(message = "Gemini Online")
                if (currentSettings.ttsEnabled) {
                    voiceManager.speak(reply)
                }
            }.onFailure { err ->
                soundPlayer.play(HudSoundType.ALERT)
                val msg = err.message ?: "Request failed."
                _chatError.value = msg
                if (msg.contains("key", ignoreCase = true) || msg.contains("401") || msg.contains("403")) {
                    _apiStatus.value = ApiConnectionStatus.InvalidKey(msg)
                } else if (msg.contains("rate", ignoreCase = true) || msg.contains("429")) {
                    _apiStatus.value = ApiConnectionStatus.RateLimited(msg)
                }
            }
        }
    }

    fun summarizeNoteWithJarvis(note: NoteEntity) {
        viewModelScope.launch {
            val id = repository.createSession(title = "Summary: ${note.title}", modeTag = AiFeatureMode.SUMMARIZE.name)
            _activeSessionId.value = id
            _selectedAiMode.value = AiFeatureMode.SUMMARIZE
            _navigateToChatSignal.emit(Unit)
            sendPrompt("Please summarize and extract key action items from this note:\n\nTitle: ${note.title}\n\n${note.content}")
        }
    }

    fun buildExportText(): String {
        val msgs = activeMessages.value
        if (msgs.isEmpty()) return ""
        val title = sessions.value.firstOrNull { it.id == _activeSessionId.value }?.title ?: "JARVIS Session"
        return buildString {
            appendLine("# J.A.R.V.I.S. AI Transcript: $title")
            appendLine()
            msgs.forEach { m ->
                val speaker = if (m.role == "user") "USER" else "JARVIS"
                appendLine("[$speaker]")
                if (!m.attachmentName.isNullOrBlank()) {
                    appendLine("(Attachment: ${m.attachmentName})")
                }
                appendLine(m.content)
                appendLine()
            }
        }
    }

    // Voice Controls
    fun toggleVoiceListening() {
        val current = voiceState.value
        if (current == VoiceState.LISTENING || current == VoiceState.WAKE_LISTENING) {
            voiceManager.stopListening()
            soundPlayer.play(HudSoundType.TAP)
        } else {
            soundPlayer.play(HudSoundType.MIC_START)
            voiceManager.startListening(settings.value.language.localeTag, wakeMode = false)
        }
    }

    fun toggleWakeListening() {
        val next = !settings.value.wakeWordListeningEnabled
        viewModelScope.launch {
            repository.setWakeWordListening(next)
        }
        if (next) {
            soundPlayer.play(HudSoundType.MIC_START)
            voiceManager.startListening(settings.value.language.localeTag, wakeMode = true)
            _snackbarMessage.tryEmit("Active wake-word listener armed (say \"Jarvis\").")
        } else {
            voiceManager.stopListening()
        }
    }

    fun speakText(text: String) {
        soundPlayer.play(HudSoundType.TAP)
        voiceManager.speak(text)
    }

    fun stopSpeaking() {
        soundPlayer.play(HudSoundType.TAP)
        voiceManager.stopSpeaking()
    }

    fun repeatLastSpoken() {
        soundPlayer.play(HudSoundType.TAP)
        if (!voiceManager.repeatLast()) {
            val lastModelMsg = activeMessages.value.lastOrNull { it.role == "model" }?.content
            if (!lastModelMsg.isNullOrBlank()) {
                voiceManager.speak(lastModelMsg)
            } else {
                _snackbarMessage.tryEmit("No recent JARVIS response to repeat.")
            }
        }
    }

    private fun handleVoiceTranscript(spokenText: String) {
        if (spokenText.isBlank()) return
        soundPlayer.play(HudSoundType.TAP)
        viewModelScope.launch {
            _navigateToChatSignal.emit(Unit)
            sendPrompt(spokenText)
        }
    }

    // Weather
    fun refreshWeather(city: String = settings.value.weatherCity) {
        viewModelScope.launch {
            _weatherState.value = WeatherUiState.Loading
            repository.setWeatherCity(city)
            repository.fetchWeatherByCity(city)
                .onSuccess { _weatherState.value = WeatherUiState.Success(it) }
                .onFailure { _weatherState.value = WeatherUiState.Error(it.message ?: "Weather unavailable") }
        }
    }

    fun refreshWeatherByCoords(lat: Double, lon: Double) {
        viewModelScope.launch {
            _weatherState.value = WeatherUiState.Loading
            repository.fetchWeatherByCoordinates(lat, lon, "Current Sector")
                .onSuccess { _weatherState.value = WeatherUiState.Success(it) }
                .onFailure { _weatherState.value = WeatherUiState.Error(it.message ?: "Weather unavailable") }
        }
    }

    // Productivity Tools CRUD
    fun saveNote(id: Long = 0, title: String, content: String, category: String = "General") {
        if (title.isBlank() && content.isBlank()) return
        viewModelScope.launch {
            repository.saveNote(
                NoteEntity(
                    id = id,
                    title = title.trim().ifBlank { "Untitled Note" },
                    content = content.trim(),
                    category = category.trim().ifBlank { "General" },
                    updatedAt = System.currentTimeMillis()
                )
            )
            soundPlayer.play(HudSoundType.TAP)
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            repository.deleteNote(id)
            soundPlayer.play(HudSoundType.TAP)
        }
    }

    fun addTask(title: String, dueEpochDay: Long, priority: Int) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.saveTask(
                TaskEntity(
                    title = title.trim(),
                    dueDateEpochDay = dueEpochDay,
                    priority = priority
                )
            )
            soundPlayer.play(HudSoundType.TAP)
        }
    }

    fun toggleTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.toggleTask(task)
            soundPlayer.play(HudSoundType.TAP)
        }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch {
            repository.deleteTask(id)
            soundPlayer.play(HudSoundType.TAP)
        }
    }

    fun addReminder(title: String, triggerAtMillis: Long) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val id = repository.saveReminder(
                ReminderEntity(
                    title = title.trim(),
                    triggerAtMillis = triggerAtMillis,
                    isActive = true
                )
            )
            ReminderScheduler.schedule(getApplication(), id, title.trim(), triggerAtMillis)
            soundPlayer.play(HudSoundType.RESPONSE_READY)
            _snackbarMessage.tryEmit("Reminder scheduled.")
        }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch {
            ReminderScheduler.cancel(getApplication(), id)
            repository.deleteReminder(id)
            soundPlayer.play(HudSoundType.TAP)
        }
    }

    // Calculator actions
    fun appendCalcToken(token: String) {
        soundPlayer.play(HudSoundType.TAP)
        val next = _calcExpression.value + token
        _calcExpression.value = next
        updateCalcPreview(next)
    }

    fun backspaceCalc() {
        soundPlayer.play(HudSoundType.TAP)
        if (_calcExpression.value.isNotEmpty()) {
            val next = _calcExpression.value.dropLast(1)
            _calcExpression.value = next
            updateCalcPreview(next)
        }
    }

    fun clearCalc() {
        soundPlayer.play(HudSoundType.TAP)
        _calcExpression.value = ""
        _calcPreview.value = "0"
    }

    fun evaluateCalc() {
        val expr = _calcExpression.value
        if (expr.isBlank()) return
        MathEvaluator.evaluate(expr)
            .onSuccess { value ->
                val formatted = MathEvaluator.formatResult(value)
                soundPlayer.play(HudSoundType.RESPONSE_READY)
                _calcHistory.value = (listOf(expr to formatted) + _calcHistory.value).take(12)
                _calcExpression.value = formatted
                _calcPreview.value = formatted
            }
            .onFailure {
                soundPlayer.play(HudSoundType.ALERT)
                _calcPreview.value = "Syntax Error"
            }
    }

    private fun updateCalcPreview(expr: String) {
        if (expr.isBlank()) {
            _calcPreview.value = "0"
            return
        }
        MathEvaluator.evaluate(expr)
            .onSuccess { _calcPreview.value = MathEvaluator.formatResult(it) }
            .onFailure { _calcPreview.value = "…" }
    }

    // Settings updates
    fun updateModel(modelId: String) = viewModelScope.launch { repository.setModel(modelId) }
    fun updateLanguage(language: AssistantLanguage) = viewModelScope.launch { repository.setLanguage(language) }
    fun updateThemeMode(mode: ThemeMode) = viewModelScope.launch { repository.setThemeMode(mode) }
    fun updatePersonality(p: AssistantPersonality) = viewModelScope.launch { repository.setPersonality(p) }
    fun updateTtsEnabled(enabled: Boolean) = viewModelScope.launch {
        repository.setTtsEnabled(enabled)
        if (!enabled) voiceManager.stopSpeaking()
    }
    fun updateVoiceConversationMode(enabled: Boolean) = viewModelScope.launch {
        repository.setVoiceConversationMode(enabled)
    }
    fun updateSpeechSpeed(speed: Float) = viewModelScope.launch { repository.setSpeechSpeed(speed) }
    fun updateSoundEffects(enabled: Boolean) = viewModelScope.launch { repository.setSoundEffectsEnabled(enabled) }
    fun updateMemoryEnabled(enabled: Boolean) = viewModelScope.launch { repository.setMemoryEnabled(enabled) }

    fun clearChatHistory() {
        viewModelScope.launch {
            repository.clearAllChats()
            _activeSessionId.value = null
            soundPlayer.play(HudSoundType.TAP)
            _snackbarMessage.tryEmit("All conversation history cleared.")
        }
    }

    fun clearProductivityData() {
        viewModelScope.launch {
            repository.clearAllProductivityData()
            soundPlayer.play(HudSoundType.TAP)
            _snackbarMessage.tryEmit("Notes, tasks, and reminders cleared.")
        }
    }

    fun resetEntireApp() {
        viewModelScope.launch {
            voiceManager.stopListening()
            voiceManager.stopSpeaking()
            repository.resetApplication()
            _activeSessionId.value = null
            _apiStatus.value = ApiConnectionStatus.Unconfigured
            _calcExpression.value = ""
            _calcPreview.value = "0"
            _calcHistory.value = emptyList()
            soundPlayer.play(HudSoundType.TAP)
            _snackbarMessage.tryEmit("JARVIS AI restored to factory defaults.")
        }
    }

    fun emitSnackbar(msg: String) {
        _snackbarMessage.tryEmit(msg)
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.release()
    }
}
