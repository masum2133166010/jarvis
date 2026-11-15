package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.repository.AiFeatureMode
import com.example.data.repository.ApiConnectionStatus
import com.example.ui.MainViewModel
import com.example.ui.WeatherUiState
import com.example.ui.components.HudPanelCard
import com.example.ui.components.JarvisArcCore
import com.example.ui.components.StatusIndicatorPill
import com.example.ui.components.VoiceWaveformVisualizer
import com.example.ui.theme.LocalJarvisHudColors
import com.example.util.VoiceState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestMicPermissionThenListen: () -> Unit,
    onRequestMicPermissionThenWake: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hud = LocalJarvisHudColors.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val apiStatus by viewModel.apiStatus.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val waveformLevels by viewModel.waveformLevels.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val weatherState by viewModel.weatherState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()

    var currentTimeMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val clockText = remember(currentTimeMillis) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(currentTimeMillis))
    }
    val dateText = remember(currentTimeMillis) {
        SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault()).format(Date(currentTimeMillis))
    }

    var showCityDialog by remember { mutableStateOf(false) }
    var cityInput by remember(settings.weatherCity) { mutableStateOf(settings.weatherCity) }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            fetchLastCoarseLocation(context)?.let { (lat, lon) ->
                viewModel.refreshWeatherByCoords(lat, lon)
            } ?: viewModel.emitSnackbar("No cached coarse GPS fix yet; using city weather.")
        } else {
            viewModel.emitSnackbar("Location permission declined. You can search any city manually.")
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 780.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Holographic Hero Banner + Digital Clock
            item {
                HudPanelCard(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_jarvis_hud_banner_1794778219577),
                            contentDescription = "JARVIS Neural Command Banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .alpha(if (hud.isDark) 0.42f else 0.16f)
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "STARK NEURAL HUD // v4.2",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = hud.arcCyan
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = clockText,
                                        style = MaterialTheme.typography.displayLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = dateText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = onOpenSettings,
                                    modifier = Modifier.testTag("dashboard_settings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Open API & System Settings",
                                        tint = hud.arcCyan
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val (apiLabel, apiColor) = when (apiStatus) {
                                    is ApiConnectionStatus.Connected -> "GEMINI ONLINE" to hud.onlineGreen
                                    ApiConnectionStatus.Testing -> "CONNECTING..." to hud.warnAmber
                                    is ApiConnectionStatus.RateLimited -> "RATE LIMITED" to hud.warnAmber
                                    is ApiConnectionStatus.InvalidKey -> "KEY INVALID" to hud.errorRed
                                    is ApiConnectionStatus.NetworkError -> "NET ERROR" to hud.errorRed
                                    ApiConnectionStatus.Unconfigured -> "API NOT SET" to hud.warnAmber
                                }
                                StatusIndicatorPill(
                                    label = apiLabel,
                                    dotColor = apiColor,
                                    modifier = Modifier.clickable { onOpenSettings() }
                                )

                                val micColor = when (voiceState) {
                                    VoiceState.LISTENING -> hud.onlineGreen
                                    VoiceState.WAKE_LISTENING -> hud.warnAmber
                                    VoiceState.SPEAKING -> hud.arcCyan
                                    VoiceState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                val micLabel = when (voiceState) {
                                    VoiceState.LISTENING -> "MIC ACTIVE"
                                    VoiceState.WAKE_LISTENING -> "WAKE ARMED"
                                    VoiceState.SPEAKING -> "VOICE OUTPUT"
                                    VoiceState.IDLE -> "MIC STANDBY"
                                }
                                StatusIndicatorPill(label = micLabel, dotColor = micColor)
                                StatusIndicatorPill(
                                    label = settings.language.label.uppercase(),
                                    dotColor = hud.arcCyan
                                )
                            }
                        }
                    }
                }
            }

            // Central AI Core + Voice Console (+ Weather side-by-side on wide screens)
            item {
                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        CoreAndVoicePanel(
                            voiceState = voiceState,
                            isThinking = isGenerating,
                            isApiConnected = apiStatus is ApiConnectionStatus.Connected,
                            waveformLevels = waveformLevels,
                            partialTranscript = partialTranscript,
                            wakeArmed = settings.wakeWordListeningEnabled,
                            voiceConvMode = settings.voiceConversationMode,
                            onCoreClick = onRequestMicPermissionThenListen,
                            onToggleWake = onRequestMicPermissionThenWake,
                            onToggleVoiceConv = { viewModel.updateVoiceConversationMode(!settings.voiceConversationMode) },
                            onStopSpeaking = { viewModel.stopSpeaking() },
                            onRepeat = { viewModel.repeatLastSpoken() },
                            modifier = Modifier.weight(1.25f)
                        )
                        WeatherAndTelemetryColumn(
                            weatherState = weatherState,
                            telemetry = telemetry,
                            modelName = settings.selectedModel,
                            onRefreshWeather = { viewModel.refreshWeather() },
                            onEditCity = { showCityDialog = true },
                            onUseLocation = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    fetchLastCoarseLocation(context)?.let { (lat, lon) ->
                                        viewModel.refreshWeatherByCoords(lat, lon)
                                    } ?: viewModel.emitSnackbar("No GPS fix cached; enter city manually.")
                                } else {
                                    locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CoreAndVoicePanel(
                            voiceState = voiceState,
                            isThinking = isGenerating,
                            isApiConnected = apiStatus is ApiConnectionStatus.Connected,
                            waveformLevels = waveformLevels,
                            partialTranscript = partialTranscript,
                            wakeArmed = settings.wakeWordListeningEnabled,
                            voiceConvMode = settings.voiceConversationMode,
                            onCoreClick = onRequestMicPermissionThenListen,
                            onToggleWake = onRequestMicPermissionThenWake,
                            onToggleVoiceConv = { viewModel.updateVoiceConversationMode(!settings.voiceConversationMode) },
                            onStopSpeaking = { viewModel.stopSpeaking() },
                            onRepeat = { viewModel.repeatLastSpoken() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        WeatherAndTelemetryColumn(
                            weatherState = weatherState,
                            telemetry = telemetry,
                            modelName = settings.selectedModel,
                            onRefreshWeather = { viewModel.refreshWeather() },
                            onEditCity = { showCityDialog = true },
                            onUseLocation = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    fetchLastCoarseLocation(context)?.let { (lat, lon) ->
                                        viewModel.refreshWeatherByCoords(lat, lon)
                                    } ?: viewModel.emitSnackbar("No GPS fix cached; enter city manually.")
                                } else {
                                    locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Quick Action Capabilities
            item {
                HudPanelCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "NEURAL COMMAND MODULES",
                        style = MaterialTheme.typography.labelLarge,
                        color = hud.arcCyan
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val quickItems = listOf(
                            Triple(AiFeatureMode.GENERAL, Icons.Default.Forum, "General AI"),
                            Triple(AiFeatureMode.CODE_GEN, Icons.Default.Code, "Code Gen"),
                            Triple(AiFeatureMode.DEBUG_CODE, Icons.Default.Bolt, "Debug Code"),
                            Triple(AiFeatureMode.TRANSLATE, Icons.Default.Translate, "EN / BN Translate"),
                            Triple(AiFeatureMode.SUMMARIZE, Icons.Default.Description, "Summarize"),
                            Triple(AiFeatureMode.STUDY, Icons.Default.School, "Study Tutor"),
                            Triple(AiFeatureMode.EXPLAIN, Icons.Default.Search, "Explain Topic")
                        )
                        quickItems.forEach { (mode, icon, label) ->
                            AssistChip(
                                onClick = {
                                    viewModel.setAiMode(mode)
                                    onOpenChat()
                                },
                                label = { Text(label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = hud.arcCyan
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Recent Conversations
            item {
                HudPanelCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RECENT NEURAL SESSIONS",
                            style = MaterialTheme.typography.labelLarge,
                            color = hud.arcCyan
                        )
                        TextButton(onClick = {
                            viewModel.createNewChat()
                            onOpenChat()
                        }) {
                            Text("New Chat")
                        }
                    }
                    if (sessions.isEmpty()) {
                        Text(
                            text = "No sessions recorded yet. Tap the Arc Core or start a Neural Chat.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        sessions.take(4).forEach { session ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        viewModel.selectSession(session.id)
                                        onOpenChat()
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Forum,
                                        contentDescription = null,
                                        tint = hud.arcCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = session.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = session.modeTag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCityDialog) {
        AlertDialog(
            onDismissRequest = { showCityDialog = false },
            title = { Text("Atmospheric Telemetry City") },
            text = {
                OutlinedTextField(
                    value = cityInput,
                    onValueChange = { cityInput = it },
                    label = { Text("City name (e.g., Dhaka, London, Tokyo)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.refreshWeather(cityInput)
                    showCityDialog = false
                }) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCityDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoreAndVoicePanel(
    voiceState: VoiceState,
    isThinking: Boolean,
    isApiConnected: Boolean,
    waveformLevels: List<Float>,
    partialTranscript: String,
    wakeArmed: Boolean,
    voiceConvMode: Boolean,
    onCoreClick: () -> Unit,
    onToggleWake: () -> Unit,
    onToggleVoiceConv: () -> Unit,
    onStopSpeaking: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hud = LocalJarvisHudColors.current
    HudPanelCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "JARVIS ARC CORE",
                style = MaterialTheme.typography.labelLarge,
                color = hud.arcCyan
            )
            Spacer(modifier = Modifier.height(12.dp))
            JarvisArcCore(
                voiceState = voiceState,
                isThinking = isThinking,
                isApiConnected = isApiConnected,
                size = 188.dp,
                onCoreClick = onCoreClick,
                modifier = Modifier.testTag("jarvis_arc_core_button")
            )
            Spacer(modifier = Modifier.height(12.dp))
            VoiceWaveformVisualizer(
                levels = waveformLevels,
                voiceState = voiceState,
                isThinking = isThinking
            )
            if (partialTranscript.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "\"$partialTranscript\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = hud.arcCyan
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onCoreClick,
                    modifier = Modifier.testTag("dashboard_mic_button")
                ) {
                    Icon(
                        imageVector = if (voiceState == VoiceState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (voiceState == VoiceState.LISTENING) "Stop Mic" else "Speak")
                }
                OutlinedButton(onClick = onStopSpeaking) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop Speaking", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Mute")
                }
                OutlinedButton(onClick = onRepeat) {
                    Icon(Icons.Default.Replay, contentDescription = "Repeat Last Response", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Repeat")
                }
                OutlinedButton(onClick = onToggleWake) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = "Wake Word",
                        tint = if (wakeArmed) hud.warnAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (wakeArmed) "Wake: ON" else "Wake: OFF")
                }
                OutlinedButton(onClick = onToggleVoiceConv) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Voice Conversation Loop",
                        tint = if (voiceConvMode) hud.onlineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (voiceConvMode) "Loop: ON" else "Loop: OFF")
                }
            }
        }
    }
}

@Composable
private fun WeatherAndTelemetryColumn(
    weatherState: WeatherUiState,
    telemetry: com.example.util.SystemTelemetry,
    modelName: String,
    onRefreshWeather: () -> Unit,
    onEditCity: () -> Unit,
    onUseLocation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hud = LocalJarvisHudColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Weather Card
        HudPanelCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WbSunny, contentDescription = null, tint = hud.arcCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "WEATHER TELEMETRY",
                        style = MaterialTheme.typography.labelLarge,
                        color = hud.arcCyan
                    )
                }
                Row {
                    IconButton(onClick = onUseLocation) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Use Current Location")
                    }
                    IconButton(onClick = onEditCity) {
                        Icon(Icons.Default.LocationOn, contentDescription = "Change City")
                    }
                    IconButton(onClick = onRefreshWeather) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Weather")
                    }
                }
            }
            when (weatherState) {
                WeatherUiState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Acquiring atmospheric telemetry...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                is WeatherUiState.Success -> {
                    val w = weatherState.snapshot
                    Text(
                        text = w.locationLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "${w.temperatureC}°C",
                            style = MaterialTheme.typography.displayLarge,
                            color = hud.arcCyan
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(w.conditionLabel, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Feels ${w.feelsLikeC}°C • Humidity ${w.humidityPercent}% • Wind ${w.windKmh} km/h",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is WeatherUiState.Error -> {
                    Text(
                        text = weatherState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = hud.errorRed
                    )
                }
            }
        }

        // System Telemetry Card
        HudPanelCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "SYSTEM DIAGNOSTICS",
                style = MaterialTheme.typography.labelLarge,
                color = hud.arcCyan
            )
            Spacer(modifier = Modifier.height(10.dp))
            TelemetryRow(
                icon = if (telemetry.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                label = "Power Cell",
                value = "${telemetry.batteryPercent}%${if (telemetry.isCharging) " (Charging)" else ""}"
            )
            TelemetryRow(
                icon = Icons.Default.Memory,
                label = "Memory Load",
                value = "${telemetry.usedRamMb} MB / ${telemetry.totalRamMb} MB"
            )
            TelemetryRow(
                icon = Icons.Default.Wifi,
                label = "Uplink",
                value = telemetry.networkStatus
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${telemetry.deviceLabel} • ${telemetry.androidVersion} • Engine: $modelName",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TelemetryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    val hud = LocalJarvisHudColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = hud.arcCyan, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@SuppressLint("MissingPermission")
private fun fetchLastCoarseLocation(context: Context): Pair<Double, Double>? {
    return runCatching {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        loc?.let { it.latitude to it.longitude }
    }.getOrNull()
}
