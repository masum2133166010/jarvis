package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.NoteEntity
import com.example.ui.MainViewModel
import com.example.ui.components.HudPanelCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalJarvisHudColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "Tasks" to Icons.Default.TaskAlt,
        "Calendar" to Icons.Default.CalendarMonth,
        "Notes" to Icons.Default.NoteAlt,
        "Calc" to Icons.Default.Calculate
    )

    Column(modifier = modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { idx, (title, icon) ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = {
                        viewModel.playTapSound()
                        selectedTab = idx
                    },
                    text = { Text(title) },
                    icon = { Icon(icon, contentDescription = title, modifier = Modifier.size(18.dp)) }
                )
            }
        }

        when (selectedTab) {
            0 -> TasksTab(viewModel)
            1 -> CalendarAndRemindersTab(viewModel)
            2 -> NotesTab(viewModel)
            3 -> CalculatorTab(viewModel)
        }
    }
}

@Composable
private fun TasksTab(viewModel: MainViewModel) {
    val hud = LocalJarvisHudColors.current
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    var newTaskTitle by remember { mutableStateOf("") }
    var newPriority by remember { mutableIntStateOf(1) }

    val todayEpochDay = remember { currentEpochDay() }
    val filtered = remember(tasks, search) {
        if (search.isBlank()) tasks
        else tasks.filter { it.title.contains(search, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            HudPanelCard(modifier = Modifier.fillMaxWidth()) {
                Text("TASK COMMANDS", style = MaterialTheme.typography.labelLarge, color = hud.arcCyan)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newTaskTitle,
                    onValueChange = { newTaskTitle = it },
                    label = { Text("Add a new task or mission...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_input_field")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "Low", 1 to "Med", 2 to "High").forEach { (p, label) ->
                            FilterChip(
                                selected = newPriority == p,
                                onClick = { newPriority = p },
                                label = { Text(label) }
                            )
                        }
                    }
                    Button(
                        onClick = {
                            if (newTaskTitle.isNotBlank()) {
                                viewModel.addTask(newTaskTitle, todayEpochDay, newPriority)
                                newTaskTitle = ""
                            }
                        },
                        modifier = Modifier.testTag("add_task_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Quick search tasks...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (filtered.isEmpty()) {
            item {
                HudPanelCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No active tasks. Add an objective above to track your day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filtered, key = { it.id }) { task ->
                val priColor = when (task.priority) {
                    2 -> hud.errorRed
                    1 -> hud.warnAmber
                    else -> hud.arcCyan
                }
                HudPanelCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.toggleTask(task) }
                        ) {
                            Icon(
                                imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = "Toggle Task",
                                tint = if (task.isCompleted) hud.onlineGreen else priColor
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                    ),
                                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Priority: ${when (task.priority) { 2 -> "HIGH"; 1 -> "MEDIUM"; else -> "LOW" }} • ${formatEpochDay(task.dueDateEpochDay)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = priColor
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.deleteTask(task.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Task")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarAndRemindersTab(viewModel: MainViewModel) {
    val context = LocalContext.current
    val hud = LocalJarvisHudColors.current
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()

    var monthOffset by remember { mutableIntStateOf(0) }
    var selectedEpochDay by remember { mutableLongStateOf(currentEpochDay()) }
    var reminderTitle by remember { mutableStateOf("") }
    var delayMinutes by remember { mutableIntStateOf(15) }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (reminderTitle.isNotBlank()) {
                viewModel.addReminder(reminderTitle, System.currentTimeMillis() + delayMinutes * 60_000L)
                reminderTitle = ""
            }
        } else {
            viewModel.emitSnackbar("Notification permission denied; reminder saved without push alert.")
            if (reminderTitle.isNotBlank()) {
                viewModel.addReminder(reminderTitle, System.currentTimeMillis() + delayMinutes * 60_000L)
                reminderTitle = ""
            }
        }
    }

    val calendarData = remember(monthOffset) { buildMonthGrid(monthOffset) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Monthly Calendar Card
        item {
            HudPanelCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { monthOffset-- }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month")
                    }
                    Text(
                        text = calendarData.monthTitle.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = hud.arcCyan,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { monthOffset++ }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                        Text(
                            text = d,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                val today = currentEpochDay()
                calendarData.weeks.forEach { week ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        week.forEach { dayCell ->
                            if (dayCell == null) {
                                Spacer(modifier = Modifier.weight(1f))
                            } else {
                                val isSelected = dayCell.epochDay == selectedEpochDay
                                val isToday = dayCell.epochDay == today
                                val hasTasks = tasks.any { it.dueDateEpochDay == dayCell.epochDay && !it.isCompleted }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                isSelected -> hud.arcCyan.copy(alpha = 0.25f)
                                                isToday -> hud.glowBlue.copy(alpha = 0.16f)
                                                else -> MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                                            }
                                        )
                                        .clickable { selectedEpochDay = dayCell.epochDay }
                                        .padding(vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = dayCell.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) hud.arcCyan else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (hasTasks) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(hud.onlineGreen)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                val dayTasks = tasks.filter { it.dueDateEpochDay == selectedEpochDay }
                Text(
                    text = "Tasks on ${formatEpochDay(selectedEpochDay)}: ${dayTasks.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = hud.arcCyan
                )
            }
        }

        // Reminder Scheduler Card
        item {
            HudPanelCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = hud.arcCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("REMINDER DISPATCHER", style = MaterialTheme.typography.labelLarge, color = hud.arcCyan)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = reminderTitle,
                    onValueChange = { reminderTitle = it },
                    label = { Text("Reminder message...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(5 to "5m", 15 to "15m", 30 to "30m", 60 to "1h", 180 to "3h").forEach { (mins, label) ->
                        FilterChip(
                            selected = delayMinutes == mins,
                            onClick = { delayMinutes = mins },
                            label = { Text("In $label") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (reminderTitle.isBlank()) return@Button
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!granted) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                return@Button
                            }
                        }
                        viewModel.addReminder(reminderTitle, System.currentTimeMillis() + delayMinutes * 60_000L)
                        reminderTitle = ""
                    },
                    modifier = Modifier.testTag("schedule_reminder_button")
                ) {
                    Text("Schedule Notification Reminder")
                }
            }
        }

        items(reminders, key = { it.id }) { rem ->
            val timeStr = remember(rem.triggerAtMillis) {
                SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(rem.triggerAtMillis))
            }
            HudPanelCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(rem.title, style = MaterialTheme.typography.bodyLarge)
                        Text("Scheduled: $timeStr", style = MaterialTheme.typography.labelSmall, color = hud.arcCyan)
                    }
                    IconButton(onClick = { viewModel.deleteReminder(rem.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Reminder")
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesTab(viewModel: MainViewModel) {
    val hud = LocalJarvisHudColors.current
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    var editingNote by remember { mutableStateOf<NoteEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    val filtered = remember(notes, search) {
        if (search.isBlank()) notes
        else notes.filter {
            it.title.contains(search, ignoreCase = true) || it.content.contains(search, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Search notes...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        editingNote = null
                        showEditor = true
                    },
                    modifier = Modifier.testTag("new_note_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Note")
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                HudPanelCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No notes stored in your vault yet. Tap + Note to create one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filtered, key = { it.id }) { note ->
                HudPanelCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(note.category.uppercase(), style = MaterialTheme.typography.labelSmall, color = hud.arcCyan)
                            Text(note.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = note.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Column {
                            IconButton(onClick = {
                                editingNote = note
                                showEditor = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Note")
                            }
                            IconButton(onClick = { viewModel.deleteNote(note.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Note")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(onClick = { viewModel.summarizeNoteWithJarvis(note) }) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Summarize with JARVIS")
                    }
                }
            }
        }
    }

    if (showEditor) {
        var title by remember(editingNote) { mutableStateOf(editingNote?.title.orEmpty()) }
        var content by remember(editingNote) { mutableStateOf(editingNote?.content.orEmpty()) }
        var category by remember(editingNote) { mutableStateOf(editingNote?.category ?: "General") }

        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (editingNote == null) "New Neural Note" else "Edit Note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Content") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveNote(
                        id = editingNote?.id ?: 0L,
                        title = title,
                        content = content,
                        category = category
                    )
                    showEditor = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CalculatorTab(viewModel: MainViewModel) {
    val hud = LocalJarvisHudColors.current
    val expression by viewModel.calcExpression.collectAsStateWithLifecycle()
    val preview by viewModel.calcPreview.collectAsStateWithLifecycle()
    val history by viewModel.calcHistory.collectAsStateWithLifecycle()

    val rows = listOf(
        listOf("sin(", "cos(", "tan(", "sqrt("),
        listOf("log(", "ln(", "^", "pi"),
        listOf("C", "(", ")", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+"),
        listOf("0", ".", "DEL", "=")
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            HudPanelCard(modifier = Modifier.fillMaxWidth()) {
                Text("QUANTUM CALCULATOR", style = MaterialTheme.typography.labelLarge, color = hud.arcCyan)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = expression.ifBlank { "0" },
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "= $preview",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.displayLarge,
                    color = hud.arcCyan,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { key ->
                            val isEquals = key == "="
                            val isClear = key == "C"
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = when {
                                    isEquals -> hud.arcCyan
                                    isClear -> hud.errorRed.copy(alpha = 0.2f)
                                    else -> hud.panelGlass
                                },
                                border = BorderStroke(1.dp, hud.panelBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        when (key) {
                                            "C" -> viewModel.clearCalc()
                                            "DEL" -> viewModel.backspaceCalc()
                                            "=" -> viewModel.evaluateCalc()
                                            else -> viewModel.appendCalcToken(key)
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (key == "DEL") {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Backspace,
                                            contentDescription = "Delete",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else {
                                        Text(
                                            text = key,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isEquals) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            item {
                HudPanelCard(modifier = Modifier.fillMaxWidth()) {
                    Text("CALCULATION LOG", style = MaterialTheme.typography.labelLarge, color = hud.arcCyan)
                    Spacer(modifier = Modifier.height(6.dp))
                    history.forEach { (expr, res) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.clearCalc()
                                    viewModel.appendCalcToken(res)
                                }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(expr, fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.bodySmall)
                            Text("= $res", fontFamily = JetBrainsMonoFontFamily, style = MaterialTheme.typography.bodySmall, color = hud.arcCyan)
                        }
                    }
                }
            }
        }
    }
}

private fun currentEpochDay(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis / 86_400_000L
}

private fun formatEpochDay(epochDay: Long): String {
    val millis = epochDay * 86_400_000L + 43_200_000L
    return SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(millis))
}

private data class DayCell(val dayOfMonth: Int, val epochDay: Long)
private data class MonthGrid(val monthTitle: String, val weeks: List<List<DayCell?>>)

private fun buildMonthGrid(monthOffset: Int): MonthGrid {
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.add(Calendar.MONTH, monthOffset)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)

    val title = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0..6
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val cells = mutableListOf<DayCell?>()
    repeat(firstDayOfWeek) { cells.add(null) }
    for (day in 1..daysInMonth) {
        cal.set(Calendar.DAY_OF_MONTH, day)
        val epochDay = cal.timeInMillis / 86_400_000L
        cells.add(DayCell(day, epochDay))
    }
    while (cells.size % 7 != 0) {
        cells.add(null)
    }
    return MonthGrid(title, cells.chunked(7))
}
