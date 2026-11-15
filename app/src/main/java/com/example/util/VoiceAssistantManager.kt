package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class VoiceState {
    IDLE,
    WAKE_LISTENING,
    LISTENING,
    SPEAKING
}

class VoiceAssistantManager(
    context: Context,
    private val onTranscriptReady: (String) -> Unit,
    private val onWakeWordTriggered: () -> Unit,
    private val onVoiceError: (String) -> Unit,
    private val onSpeechCompleted: () -> Unit
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var currentLocaleTag: String = "en-US"
    private var currentSpeechRate: Float = 1.0f
    private var lastSpokenText: String = ""
    private var isWakeModeCycle: Boolean = false

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _waveformLevels = MutableStateFlow(List(24) { 0.12f })
    val waveformLevels: StateFlow<List<Float>> = _waveformLevels.asStateFlow()

    val isRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                applyTtsConfig()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post {
                            _voiceState.value = VoiceState.SPEAKING
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            if (_voiceState.value == VoiceState.SPEAKING) {
                                _voiceState.value = VoiceState.IDLE
                            }
                            _waveformLevels.value = List(24) { 0.12f }
                            onSpeechCompleted()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post {
                            if (_voiceState.value == VoiceState.SPEAKING) {
                                _voiceState.value = VoiceState.IDLE
                            }
                            _waveformLevels.value = List(24) { 0.12f }
                        }
                    }
                })
            }
        }
    }

    fun updateVoiceConfig(localeTag: String, speechRate: Float) {
        currentLocaleTag = localeTag
        currentSpeechRate = speechRate.coerceIn(0.6f, 2.0f)
        applyTtsConfig()
    }

    private fun applyTtsConfig() {
        val engine = tts ?: return
        if (!ttsReady) return
        val locale = Locale.forLanguageTag(currentLocaleTag)
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.language = Locale.US
        }
        engine.setSpeechRate(currentSpeechRate)
    }

    fun startListening(localeTag: String, wakeMode: Boolean = false) {
        currentLocaleTag = localeTag
        isWakeModeCycle = wakeMode
        mainHandler.post {
            if (!isRecognitionAvailable) {
                onVoiceError("Speech recognition service is not available on this device.")
                _voiceState.value = VoiceState.IDLE
                return@post
            }
            stopSpeaking()
            destroyRecognizer()

            val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _voiceState.value = if (isWakeModeCycle) VoiceState.WAKE_LISTENING else VoiceState.LISTENING
                    _partialTranscript.value = ""
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.08f, 1.0f)
                    val current = _waveformLevels.value.toMutableList()
                    if (current.isNotEmpty()) {
                        current.removeAt(0)
                        current.add(normalized)
                        _waveformLevels.value = current
                    }
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _waveformLevels.value = List(24) { 0.12f }
                }

                override fun onError(error: Int) {
                    _waveformLevels.value = List(24) { 0.12f }
                    val wasWake = isWakeModeCycle
                    if (wasWake && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                        // Gently restart foreground wake-word listener
                        mainHandler.postDelayed({
                            if (_voiceState.value == VoiceState.WAKE_LISTENING) {
                                startListening(currentLocaleTag, wakeMode = true)
                            }
                        }, 350)
                        return
                    }
                    _voiceState.value = VoiceState.IDLE
                    val message = when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Try speaking clearly."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Speech service network error."
                        else -> "Voice recognizer error ($error)."
                    }
                    if (!wasWake) onVoiceError(message)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spoken = matches?.firstOrNull()?.trim().orEmpty()
                    _partialTranscript.value = ""
                    _waveformLevels.value = List(24) { 0.12f }

                    if (isWakeModeCycle) {
                        val lower = spoken.lowercase(Locale.ROOT)
                        if (lower.contains("jarvis")) {
                            val stripped = spoken.replace(Regex("(?i)(hey\\s+)?jarvis[,\\s]*"), "").trim()
                            isWakeModeCycle = false
                            _voiceState.value = VoiceState.IDLE
                            if (stripped.isNotBlank()) {
                                onTranscriptReady(stripped)
                            } else {
                                onWakeWordTriggered()
                            }
                        } else {
                            // Keep listening in foreground wake-word mode
                            mainHandler.postDelayed({
                                if (_voiceState.value == VoiceState.WAKE_LISTENING) {
                                    startListening(currentLocaleTag, wakeMode = true)
                                }
                            }, 250)
                        }
                    } else {
                        _voiceState.value = VoiceState.IDLE
                        if (spoken.isNotBlank()) {
                            onTranscriptReady(spoken)
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!partial.isNullOrBlank()) {
                        _partialTranscript.value = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            recognizer.startListening(intent)
        }
    }

    fun stopListening() {
        mainHandler.post {
            isWakeModeCycle = false
            destroyRecognizer()
            if (_voiceState.value != VoiceState.SPEAKING) {
                _voiceState.value = VoiceState.IDLE
            }
            _partialTranscript.value = ""
            _waveformLevels.value = List(24) { 0.12f }
        }
    }

    fun speak(text: String) {
        val cleaned = sanitizeForSpeech(text)
        if (cleaned.isBlank()) return
        lastSpokenText = cleaned
        mainHandler.post {
            if (!ttsReady) return@post
            applyTtsConfig()
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "jarvis_utterance_${System.currentTimeMillis()}")
            }
            tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, params, "jarvis_utterance")
        }
    }

    fun repeatLast(): Boolean {
        if (lastSpokenText.isBlank()) return false
        speak(lastSpokenText)
        return true
    }

    fun hasLastSpoken(): Boolean = lastSpokenText.isNotBlank()

    fun stopSpeaking() {
        mainHandler.post {
            tts?.stop()
            if (_voiceState.value == VoiceState.SPEAKING) {
                _voiceState.value = VoiceState.IDLE
            }
        }
    }

    private fun destroyRecognizer() {
        runCatching {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
    }

    private fun sanitizeForSpeech(raw: String): String {
        return raw
            .replace(Regex("```[\\s\\S]*?```"), " Code block omitted. ")
            .replace(Regex("[*#_`~>]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(3500)
    }

    fun release() {
        mainHandler.post {
            destroyRecognizer()
            tts?.stop()
            tts?.shutdown()
            tts = null
        }
    }
}
