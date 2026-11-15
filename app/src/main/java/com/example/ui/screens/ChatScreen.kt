package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.ChatMessageEntity
import com.example.data.repository.AiFeatureMode
import com.example.ui.MainViewModel
import com.example.ui.components.HudPanelCard
import com.example.ui.components.VoiceWaveformVisualizer
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalJarvisHudColors
import com.example.util.VoiceState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    onRequestMicPermissionThenListen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val hud = LocalJarvisHudColors.current

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val messages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val selectedMode by viewModel.selectedAiMode.collectAsStateWithLifecycle()
    val pendingAttachment by viewModel.pendingAttachment.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()
    val searchQuery by viewModel.chatSearchQuery.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val waveformLevels by viewModel.waveformLevels.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var showHistorySheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.attachImage(it) }
    }

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.attachDocument(it) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top Action Bar
        Surface(
            color = hud.panelGlass,
            border = BorderStroke(1.dp, hud.panelBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showHistorySheet = true },
                        modifier = Modifier.testTag("chat_history_button")
                    ) {
                        Icon(Icons.Default.History, contentDescription = "Conversation History", tint = hud.arcCyan)
                    }
                    Column {
                        Text(
                            text = "NEURAL INTERFACE",
                            style = MaterialTheme.typography.labelLarge,
                            color = hud.arcCyan
                        )
                        Text(
                            text = "${settings.selectedModel} • ${settings.language.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row {
                    if (voiceState == VoiceState.SPEAKING) {
                        IconButton(onClick = { viewModel.stopSpeaking() }) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop Speaking", tint = hud.errorRed)
                        }
                    }
                    IconButton(onClick = { viewModel.repeatLastSpoken() }) {
                        Icon(Icons.Default.Replay, contentDescription = "Repeat Response")
                    }
                    IconButton(
                        onClick = {
                            val exportBody = viewModel.buildExportText()
                            if (exportBody.isBlank()) {
                                viewModel.emitSnackbar("No messages in this conversation to export.")
                            } else {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "JARVIS AI Conversation")
                                    putExtra(Intent.EXTRA_TEXT, exportBody)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Export Conversation"))
                            }
                        },
                        modifier = Modifier.testTag("export_chat_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Export Conversation")
                    }
                    IconButton(
                        onClick = { viewModel.createNewChat() },
                        modifier = Modifier.testTag("new_chat_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Chat", tint = hud.arcCyan)
                    }
                }
            }
        }

        // AI Capability Mode Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(AiFeatureMode.entries) { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = { viewModel.setAiMode(mode) },
                    label = { Text(mode.label) }
                )
            }
        }

        // Voice Live Status Banner
        if (voiceState != VoiceState.IDLE || isGenerating) {
            Surface(
                color = hud.panelGlass,
                border = BorderStroke(1.dp, hud.arcCyan.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    VoiceWaveformVisualizer(
                        levels = waveformLevels,
                        voiceState = voiceState,
                        isThinking = isGenerating
                    )
                    val label = when {
                        isGenerating -> "JARVIS is synthesizing response..."
                        partialTranscript.isNotBlank() -> "\"$partialTranscript\""
                        voiceState == VoiceState.LISTENING -> "Listening (${settings.language.label})..."
                        voiceState == VoiceState.WAKE_LISTENING -> "Wake-word standby active (\"Jarvis\")..."
                        voiceState == VoiceState.SPEAKING -> "Speaking response..."
                        else -> ""
                    }
                    if (label.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall, color = hud.arcCyan)
                    }
                }
            }
        }

        // API / Network Error Banner
        if (!chatError.isNullOrBlank()) {
            Surface(
                color = hud.errorRed.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, hud.errorRed.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = chatError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (!settings.hasApiKey) {
                        TextButton(onClick = onOpenSettings) {
                            Text("API Settings")
                        }
                    } else {
                        IconButton(onClick = { viewModel.clearChatError() }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss error")
                        }
                    }
                }
            }
        }

        // Messages Area
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                HudPanelCard(modifier = Modifier.widthIn(max = 520.dp)) {
                    Text(
                        text = "J.A.R.V.I.S. READY // ${selectedMode.label.uppercase()}",
                        style = MaterialTheme.typography.titleLarge,
                        color = hud.arcCyan
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (settings.hasApiKey) {
                            "Ask anything, attach an image for vision analysis, upload a text/code document, or tap the microphone for English or Bengali voice commands."
                        } else {
                            "Gemini API key is not configured yet. Tap below to open API Settings and paste your key."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    if (!settings.hasApiKey) {
                        Button(onClick = onOpenSettings) {
                            Text("Configure Gemini API Key")
                        }
                    } else {
                        OutlinedButton(onClick = { inputText = selectedMode.starterPrompt }) {
                            Text("Insert ${selectedMode.label} Starter Prompt")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(
                        message = msg,
                        onCopy = { text ->
                            clipboard.setText(AnnotatedString(text))
                            viewModel.emitSnackbar("Copied to clipboard.")
                        },
                        onSpeak = { text -> viewModel.speakText(text) }
                    )
                }
                if (isGenerating) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Computing neural response...",
                                style = MaterialTheme.typography.labelMedium,
                                color = hud.arcCyan
                            )
                        }
                    }
                }
            }
        }

        // Pending Attachment Preview
        pendingAttachment?.let { att ->
            Surface(
                color = hud.panelGlass,
                border = BorderStroke(1.dp, hud.arcCyan.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        if (att.type == "image") {
                            AsyncImage(
                                model = att.uriString,
                                contentDescription = "Selected image preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Document",
                                tint = hud.arcCyan,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(att.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = if (att.type == "image") "Image ready for Gemini Vision" else "Document text loaded",
                                style = MaterialTheme.typography.labelSmall,
                                color = hud.arcCyan
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.clearPendingAttachment() }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove attachment")
                    }
                }
            }
        }

        // Composer Input Bar
        Surface(
            color = hud.panelGlass,
            border = BorderStroke(1.dp, hud.panelBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.testTag("attach_image_button")
                ) {
                    Icon(Icons.Default.Image, contentDescription = "Attach Image", tint = hud.arcCyan)
                }
                IconButton(
                    onClick = {
                        docPicker.launch(arrayOf("text/*", "application/json", "application/xml"))
                    },
                    modifier = Modifier.testTag("attach_doc_button")
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Attach Text Document", tint = hud.arcCyan)
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Command J.A.R.V.I.S...") },
                    maxLines = 4,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field")
                )
                IconButton(
                    onClick = onRequestMicPermissionThenListen,
                    modifier = Modifier.testTag("chat_mic_button")
                ) {
                    Icon(
                        imageVector = if (voiceState == VoiceState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        tint = if (voiceState == VoiceState.LISTENING) hud.onlineGreen else hud.arcCyan
                    )
                }
                IconButton(
                    onClick = {
                        val toSend = inputText
                        inputText = ""
                        viewModel.sendPrompt(toSend)
                    },
                    enabled = !isGenerating && (inputText.isNotBlank() || pendingAttachment != null),
                    modifier = Modifier.testTag("send_message_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Message",
                        tint = hud.arcCyan
                    )
                }
            }
        }
    }

    if (showHistorySheet) {
        ModalBottomSheet(onDismissRequest = { showHistorySheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("CONVERSATION ARCHIVE", style = MaterialTheme.typography.titleLarge, color = hud.arcCyan)
                    TextButton(onClick = {
                        viewModel.createNewChat()
                        showHistorySheet = false
                    }) {
                        Text("+ New Chat")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setChatSearchQuery(it) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Search previous chats...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (sessions.isEmpty()) {
                    Text(
                        "No matching conversations found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(340.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sessions, key = { it.id }) { session ->
                            val isSelected = session.id == activeSessionId
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) hud.arcCyan.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (isSelected) BorderStroke(1.dp, hud.arcCyan) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectSession(session.id)
                                        showHistorySheet = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = session.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = session.modeTag,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteSession(session.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete conversation")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessageEntity,
    onCopy: (String) -> Unit,
    onSpeak: (String) -> Unit
) {
    val hud = LocalJarvisHudColors.current
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else hud.panelGlass,
            border = BorderStroke(
                1.dp,
                if (isUser) hud.arcCyan.copy(alpha = 0.5f) else hud.panelBorder
            ),
            modifier = Modifier.widthIn(max = 580.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "YOU" else "J.A.R.V.I.S.",
                        style = MaterialTheme.typography.labelSmall,
                        color = hud.arcCyan
                    )
                    if (!isUser) {
                        Row {
                            IconButton(
                                onClick = { onSpeak(message.content) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Read Aloud",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onCopy(message.content) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy Response",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                if (message.attachmentType == "image" && !message.attachmentUri.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    AsyncImage(
                        model = message.attachmentUri,
                        contentDescription = message.attachmentName ?: "Attached image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else if (!message.attachmentName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Attachment: ${message.attachmentName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = hud.arcCyan
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                FormattedMessageContent(
                    content = message.content,
                    onCopyCode = onCopy
                )
            }
        }
    }
}

@Composable
private fun FormattedMessageContent(
    content: String,
    onCopyCode: (String) -> Unit
) {
    val hud = LocalJarvisHudColors.current
    val segments = remember(content) { splitCodeBlocks(content) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { (isCode, text) ->
            if (isCode) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = hud.codeBackground,
                    border = BorderStroke(1.dp, hud.panelBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("CODE", style = MaterialTheme.typography.labelSmall, color = hud.arcCyan)
                            IconButton(
                                onClick = { onCopyCode(text) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy Code",
                                    modifier = Modifier.size(14.dp),
                                    tint = hud.arcCyan
                                )
                            }
                        }
                        SelectionContainer {
                            Text(
                                text = text,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            } else {
                SelectionContainer {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun splitCodeBlocks(raw: String): List<Pair<Boolean, String>> {
    val result = mutableListOf<Pair<Boolean, String>>()
    val regex = Regex("```[a-zA-Z0-9_+-]*\\n?([\\s\\S]*?)```")
    var lastIndex = 0
    regex.findAll(raw).forEach { match ->
        if (match.range.first > lastIndex) {
            val plain = raw.substring(lastIndex, match.range.first).trim()
            if (plain.isNotEmpty()) result.add(false to plain)
        }
        val code = match.groupValues[1].trimEnd()
        if (code.isNotEmpty()) result.add(true to code)
        lastIndex = match.range.last + 1
    }
    if (lastIndex < raw.length) {
        val tail = raw.substring(lastIndex).trim()
        if (tail.isNotEmpty()) result.add(false to tail)
    }
    if (result.isEmpty()) result.add(false to raw)
    return result
}
