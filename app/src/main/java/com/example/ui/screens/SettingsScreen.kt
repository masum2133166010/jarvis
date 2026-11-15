package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preferences.AssistantLanguage
import com.example.data.preferences.AssistantPersonality
import com.example.data.repository.ApiConnectionStatus
import com.example.ui.MainViewModel
import com.example.ui.components.HudPanelCard
import com.example.ui.components.StatusIndicatorPill
import com.example.ui.theme.LocalJarvisHudColors
import com.example.ui.theme.ThemeMode
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onRequestMicPermissionThenWake: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hud = LocalJarvisHudColors.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val apiStatus by viewModel.apiStatus.collectAsStateWithLifecycle()

    var apiKeyInput by remember { mutableStateOf("") }
    var showKeyPlain by remember { mutableStateOf(false) }
    var customModelInput by remember(settings.selectedModel) { mutableStateOf(settings.selectedModel) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showClearChatsDialog by remember { mutableStateOf(false) }

    val recommendedModels = listOf(
        "gemini-2.5-flash" to "Gemini 2.5 Flash (Fast + Vision)",
        "gemini-2.5-pro" to "Gemini 2.5 Pro (Deep Reasoning)",
        "gemini-2.0-flash" to "Gemini 2.0 Flash (Balanced)"
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Gemini API Configuration
        item {
            HudPanelCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = hud.arcCyan.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = hud.arcCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("1. GEMINI API CONFIGURATION", style = MaterialTheme.typography.labelLarge, color = hud.arcCyan)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                val (statusLabel, statusColor, statusDetail) = when (val s = apiStatus) {
                    is ApiConnectionStatus.Connected -> Triple("CONNECTED", hud.onlineGreen, s.message)
                    ApiConnectionStatus.Testing -> Triple("TESTING...", hud.warnAmber, "Verifying credentials with Gemini API...")
                    is ApiConnectionStatus.InvalidKey -> Triple("INVALID KEY", hud.errorRed, s.message)
                    is ApiConnectionStatus.RateLimited -> Triple("RATE LIMITED", hud.warnAmber, s.message)
                    is ApiConnectionStatus.NetworkError -> Triple("NETWORK ERROR", hud.errorRed, s.message)
                    ApiConnectionStatus.Unconfigured -> Triple("NOT CONFIGURED", hud.warnAmber, "Paste your Google AI Studio Gemini API key below.")
                }

                StatusIndicatorPill(label = statusLabel, dotColor = statusColor)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = statusDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (settings.hasApiKey) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = hud.onlineGreen)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Stored Encrypted Key: ${settings.maskedApiKey}",
                            style = MaterialTheme.typography.labelMedium,
                            color = hud.onlineGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text(if (settings.hasApiKey) "Paste new key to update..." else "Paste Gemini API Key (AIza...)") },
                    singleLine = true,
                    visualTransformation = if (showKeyPlain) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKeyPlain = !showKeyPlain }) {
                            Icon(
                                imageVector = if (showKeyPlain) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle key visibility"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_input")
                )
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.saveAndConnectApiKey(apiKeyInput)
                            apiKeyInput = ""
                        },
                        enabled = apiKeyInput.isNotBlank(),
                        modifier = Modifier.testTag("save_api_key_button")
                    ) {
                        Text(if (settings.hasApiKey) "Update" else "Save")
                    }
                    OutlinedButton(
                        onClick = {
                            val candidate = apiKeyInput.ifBlank { settings.decryptedApiKey }
                            viewModel.testApiKeyConnection(candidate)
                        },
                        modifier = Modifier.testTag("test_connection_button")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Connection")
                    }
                    if (settings.hasApiKey) {
                        OutlinedButton(
                            onClick = {
                                viewModel.deleteApiKey()
                                apiKeyInput = ""
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = hud.errorRed),
                            modifier = Modifier.testTag("delete_api_key_button")
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        }

        // 2. Gemini Model Selection
        item {
            HudPanelCard(modifier = Modifier.fillMaxWidth()) {
                Text("2. GEMINI MODEL SELECTION", style = MaterialTheme.typography.labelLarge, color = hud.arcCyan)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recommendedModels.forEach { (id, label) ->
                        FilterChip(
                            selected = settings.selectedModel == id,
                            onClick = {
                                viewModel.updateModel(id)
                                customModelInput = id
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customModelInput,
                        onValueChange = { customModelInput = it },
                        label = { Text("Custom Model ID") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        viewModel.updateModel(customModelInput)
                        viewModel.emitSnackbar("Model updated to ${customModelInput.trim()}.")
                    }) {
                        Text("Apply")
                    }
                }
            }
        }

        // 3. Voice Settings
        item {
            HudPanelCard(modifier = Modifier.fillMaxWidth()) {
                Text("3. VOICE SETTINGS", style = MaterialTheme.typography.labelLarge, color = hud.arcCyan)
                Spacer(modifier = Modifier.height(8.dp))
                SettingSwitchRow(
                    title = "Text-to-Speech Voice Output",
                    subtitle = "JARVIS speaks AI replies aloud automatically.",
                    checked = settings.ttsEnabled,
                    onCheckedChange = { viewModel.updateTtsEnabled(it) }
                )
                SettingSwitchRow(
                    title = "Voice Conversation Mode",
                    subtitle = "Automatically reactivates microphone after JARVIS finishes speaking.",
                    checked = settings.voiceConversationMode,
                    onCheckedChange = { viewModel.updateVoiceConversationMode(it) }
                )
                SettingSwitchRow(
                    title = "Active Wake-Word Listener (\"Jarvis\")",
                    subtitle = "Listens while app is open using Android SpeechRecognizer (requires Microphone permission).",
                    checked = settings.wakeWordListeningEnabled,
                    onCheckedChange = { onRequestMicPermissionThenWake() }
                )
            }
        }

        // 4. Language Settings
        item {
            HudPanelCard(modifier = Modifier.fillMaxWidth()) {
                Text("4. LANGUAGE SETTINGS", style = MaterialTheme.typography.labelLarge, color = hud.arcCyan)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AssistantLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = settings.language == lang,
                            onClick = { viewModel.updateLanguage(lang) },
                            label = { Text(lang.label) }
                        )
                    }
                }
            }
        }

        // 5. Theme Settings
        item {
            HudPanelCard(modifier = Modifier.fillMaxWidth()) {
                Text("5. THEME SETTINGS", style = MaterialTheme.typography.labelLarge, color = hud.arcCyan)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.updateThemeMode(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.DARK -> "JARVIS Dark"
                                        ThemeMode.LIGHT -> "Arctic Light"
                                        ThemeMode.SYSTEM -> "System"
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }

        // 6. Assistant Personality
        item {
            HudPanelCard(modifier = Modifier.fillMaxWidth()) {
                Text("6. ASSISTANT PERSONALITY", style = MaterialTheme.typography.labelLarge, color = hud.arcCyan)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistantPersonality.entries.forEach { p ->
                        FilterChip(
                            selected = settings.personality == p,
                            onClick = { viewModel.updatePersonality(p) },
                            label = { Text(p.title) }
                        )
                    }
                }
            }
        }

        // 7. Speech Speed
        item {
            HudPanelCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("7. SPEECH SPEED", style = MaterialTheme.typography.labelLarge, color = hud.arcCyan)
                    Text(
                        text = "${((settings.speechSpeed * 100).roundToInt() / 100.0)}x",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = settings.speechSpeed,
                    onValueChange = { viewModel.updateSpeechSpeed(it) },
                    valueRange = 0.7f..1.75f
                )
                OutlinedButton(onClick = {
                    val sample = if (settings.language == AssistantLanguage.BENGALI) {
                        "নমস্কার, আমি জার্ভিস। আপনার সহায়তায় প্রস্তুত।"
                    } else {
                        "Systems calibrated. J.A.R.V.I.S. voice synthesis online."
                    }
                    viewModel.speakText(sample)
                }) {
                    Text("Test Voice Output")
                }
            }
        }

        // 8. Sound Effects & 9. Conversation Memory
        item {
            HudPanelCard(modifier = Modifier.fillMaxWidth()) {
                Text("8. SOUND EFFECTS & 9. MEMORY", style = MaterialTheme.typography.labelLarge, color = hud.arcCyan)
                Spacer(modifier = Modifier.height(8.dp))
                SettingSwitchRow(
                    title = "Futuristic HUD Sound Effects",
                    subtitle = "Play synthesized holographic chimes on interactions.",
                    checked = settings.soundEffectsEnabled,
                    onCheckedChange = { viewModel.updateSoundEffects(it) }
                )
                SettingSwitchRow(
                    title = "Conversation Memory",
                    subtitle = "Include previous chat turns as context for Gemini.",
                    checked = settings.memoryEnabled,
                    onCheckedChange = { viewModel.updateMemoryEnabled(it) }
                )
            }
        }

        // 10. Privacy Settings & 11. Reset Application
        item {
            HudPanelCard(modifier = Modifier.fillMaxWidth()) {
                Text("10. PRIVACY & 11. RESET", style = MaterialTheme.typography.labelLarge, color = hud.arcCyan)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Security Architecture: Your Gemini API key is never hardcoded. It is encrypted locally using Android Keystore AES-256-GCM and sent via request header only to generativelanguage.googleapis.com. Chats, notes, tasks, and reminders remain strictly on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { showClearChatsDialog = true }) {
                        Text("Clear Chat History")
                    }
                    OutlinedButton(onClick = { viewModel.clearProductivityData() }) {
                        Text("Clear Notes & Tasks")
                    }
                    Button(
                        onClick = { showResetDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = hud.errorRed),
                        modifier = Modifier.testTag("reset_app_button")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset Application")
                    }
                }
            }
        }
    }

    if (showClearChatsDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatsDialog = false },
            title = { Text("Clear Conversation History?") },
            text = { Text("This permanently removes all saved neural chat sessions from this device.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.clearChatHistory()
                    showClearChatsDialog = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatsDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset JARVIS AI?") },
            text = { Text("This wipes your encrypted API key, preferences, chats, notes, tasks, and reminders.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetEntireApp()
                        apiKeyInput = ""
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = hud.errorRed)
                ) { Text("Confirm Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
