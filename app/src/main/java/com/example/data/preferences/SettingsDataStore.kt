package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.security.ApiKeyCryptoManager
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_settings")

enum class AssistantLanguage(val label: String, val localeTag: String, val promptHint: String) {
    ENGLISH("English", "en-US", "Respond clearly and naturally in English."),
    BENGALI("বাংলা (Bengali)", "bn-BD", "Respond clearly and naturally in Bengali (বাংলা script).")
}

enum class AssistantPersonality(val title: String, val systemInstruction: String) {
    JARVIS_CLASSIC(
        "Classic JARVIS",
        "You are J.A.R.V.I.S., an advanced, poised, articulate, and subtly witty AI personal assistant. Address the user respectfully, be thorough yet structured, and proactively offer helpful next steps."
    ),
    CONCISE_TACTICAL(
        "Concise Tactical",
        "You are JARVIS in Tactical Mode. Deliver high-precision, concise, bullet-pointed, actionable answers with minimal filler."
    ),
    FRIENDLY_MENTOR(
        "Friendly Mentor",
        "You are JARVIS in Mentor Mode. Be warm, encouraging, patient, and explain ideas clearly with relatable analogies."
    ),
    ACADEMIC_TUTOR(
        "Academic Tutor",
        "You are JARVIS in Study & Academic Mode. Break down complex subjects step-by-step, verify understanding, and include clear examples."
    ),
    CODE_ARCHITECT(
        "Code Architect",
        "You are JARVIS in Engineering Mode. Provide clean, idiomatic, production-grade code, highlight edge cases, and explain architectural trade-offs."
    )
}

data class JarvisSettings(
    val hasApiKey: Boolean = false,
    val decryptedApiKey: String = "",
    val maskedApiKey: String = "",
    val selectedModel: String = "gemini-2.5-flash",
    val language: AssistantLanguage = AssistantLanguage.ENGLISH,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val personality: AssistantPersonality = AssistantPersonality.JARVIS_CLASSIC,
    val ttsEnabled: Boolean = true,
    val voiceConversationMode: Boolean = false,
    val wakeWordListeningEnabled: Boolean = false,
    val speechSpeed: Float = 1.0f,
    val soundEffectsEnabled: Boolean = true,
    val memoryEnabled: Boolean = true,
    val weatherCity: String = "Dhaka"
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val ENCRYPTED_API_KEY = stringPreferencesKey("encrypted_gemini_api_key")
        val MODEL_ID = stringPreferencesKey("gemini_model_id")
        val LANGUAGE = stringPreferencesKey("assistant_language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PERSONALITY = stringPreferencesKey("assistant_personality")
        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val VOICE_CONV_MODE = booleanPreferencesKey("voice_conversation_mode")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_listening")
        val SPEECH_SPEED = floatPreferencesKey("speech_speed")
        val SOUND_FX_ENABLED = booleanPreferencesKey("sound_effects_enabled")
        val MEMORY_ENABLED = booleanPreferencesKey("conversation_memory_enabled")
        val WEATHER_CITY = stringPreferencesKey("weather_city")
    }

    val settingsFlow: Flow<JarvisSettings> = context.jarvisDataStore.data.map { prefs ->
        val encrypted = prefs[Keys.ENCRYPTED_API_KEY].orEmpty()
        val decrypted = if (encrypted.isNotBlank()) ApiKeyCryptoManager.decrypt(encrypted) else ""
        val masked = maskKey(decrypted)

        JarvisSettings(
            hasApiKey = decrypted.isNotBlank(),
            decryptedApiKey = decrypted,
            maskedApiKey = masked,
            selectedModel = prefs[Keys.MODEL_ID] ?: "gemini-2.5-flash",
            language = prefs[Keys.LANGUAGE]?.let {
                runCatching { AssistantLanguage.valueOf(it) }.getOrNull()
            } ?: AssistantLanguage.ENGLISH,
            themeMode = prefs[Keys.THEME_MODE]?.let {
                runCatching { ThemeMode.valueOf(it) }.getOrNull()
            } ?: ThemeMode.DARK,
            personality = prefs[Keys.PERSONALITY]?.let {
                runCatching { AssistantPersonality.valueOf(it) }.getOrNull()
            } ?: AssistantPersonality.JARVIS_CLASSIC,
            ttsEnabled = prefs[Keys.TTS_ENABLED] ?: true,
            voiceConversationMode = prefs[Keys.VOICE_CONV_MODE] ?: false,
            wakeWordListeningEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: false,
            speechSpeed = (prefs[Keys.SPEECH_SPEED] ?: 1.0f).coerceIn(0.6f, 2.0f),
            soundEffectsEnabled = prefs[Keys.SOUND_FX_ENABLED] ?: true,
            memoryEnabled = prefs[Keys.MEMORY_ENABLED] ?: true,
            weatherCity = prefs[Keys.WEATHER_CITY] ?: "Dhaka"
        )
    }

    suspend fun saveApiKey(rawKey: String) {
        val trimmed = rawKey.trim()
        val encrypted = if (trimmed.isNotEmpty()) ApiKeyCryptoManager.encrypt(trimmed) else ""
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.ENCRYPTED_API_KEY] = encrypted
        }
    }

    suspend fun deleteApiKey() {
        context.jarvisDataStore.edit { prefs ->
            prefs.remove(Keys.ENCRYPTED_API_KEY)
        }
    }

    suspend fun setModel(modelId: String) {
        context.jarvisDataStore.edit { it[Keys.MODEL_ID] = modelId.trim().ifEmpty { "gemini-2.5-flash" } }
    }

    suspend fun setLanguage(language: AssistantLanguage) {
        context.jarvisDataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.jarvisDataStore.edit { it[Keys.THEME_MODE] = themeMode.name }
    }

    suspend fun setPersonality(personality: AssistantPersonality) {
        context.jarvisDataStore.edit { it[Keys.PERSONALITY] = personality.name }
    }

    suspend fun setTtsEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.TTS_ENABLED] = enabled }
    }

    suspend fun setVoiceConversationMode(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.VOICE_CONV_MODE] = enabled }
    }

    suspend fun setWakeWordListening(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.WAKE_WORD_ENABLED] = enabled }
    }

    suspend fun setSpeechSpeed(speed: Float) {
        context.jarvisDataStore.edit { it[Keys.SPEECH_SPEED] = speed.coerceIn(0.6f, 2.0f) }
    }

    suspend fun setSoundEffectsEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.SOUND_FX_ENABLED] = enabled }
    }

    suspend fun setMemoryEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.MEMORY_ENABLED] = enabled }
    }

    suspend fun setWeatherCity(city: String) {
        context.jarvisDataStore.edit { it[Keys.WEATHER_CITY] = city.trim().ifEmpty { "Dhaka" } }
    }

    suspend fun resetAll() {
        context.jarvisDataStore.edit { it.clear() }
    }

    private fun maskKey(key: String): String {
        if (key.length <= 8) return if (key.isEmpty()) "" else "••••••••"
        return "${key.take(4)}••••••••••••${key.takeLast(4)}"
    }
}
