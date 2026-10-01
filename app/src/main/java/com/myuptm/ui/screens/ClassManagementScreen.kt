package com.myuptm.ui.screens

import androidx.compose.foundation.background

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myuptm.domain.model.ClassSession
import com.myuptm.domain.model.TeachingMedium
import com.myuptm.viewmodel.ClassManagementViewModel
import kotlinx.coroutines.delay

private val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val TIME_REGEX = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

// Sprint 7B: global class management for lecturers. Changes propagate to every
// student (Firestore) and fire a CLASS_UPDATE notification.
@Composable
fun ClassManagementScreen(
    ownerEmail: String?,
    viewModel: ClassManagementViewModel = viewModel()
) {
    val weeklyClasses by viewModel.weeklyClasses.collectAsState()
    val status by viewModel.status.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingSession by remember { mutableStateOf<ClassSession?>(null) }
    var deletingSession by remember { mutableStateOf<ClassSession?>(null) }

    LaunchedEffect(status) {
        if (status != null) {
            delay(2500)
            viewModel.clearStatus()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp)) {
                Text(
                    "Class Management",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Your global classes — students see changes instantly",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                status?.let { message ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            val hasClasses = weeklyClasses.any { it.isNotEmpty() }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                weeklyClasses.forEachIndexed { dayIndex, sessions ->
                    if (sessions.isNotEmpty()) {
                        item(key = "header_$dayIndex") {
                            Text(
                                DAY_NAMES[dayIndex],
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(sessions, key = { it.id }) { session ->
                            ClassManagementCard(
                                session = session,
                                canModify = viewModel.canModify(session, ownerEmail),
                                onEdit = { editingSession = session },
                                onDelete = { deletingSession = session }
                            )
                        }
                    }
                }
                if (!hasClasses) {
                    item {
                        Text(
                            "No classes yet — add the first one with the + button.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add class")
        }
    }

    if (showAddDialog) {
        ClassEditorDialog(
            title = "Add class",
            initial = null,
            errorMessage = status,
            onDismiss = {
                showAddDialog = false
                viewModel.clearStatus()
            },
            onSave = { subject, lecturer, venue, dayIndex, start, end, medium ->
                viewModel.addClass(subject, lecturer, venue, dayIndex, start, end, medium)
                // Validation errors stay in the dialog via status; success closes it.
                if (viewModel.status.value == null) showAddDialog = false
            }
        )
    }

    editingSession?.let { session ->
        ClassEditorDialog(
            title = "Edit class",
            initial = session,
            errorMessage = status,
            onDismiss = {
                editingSession = null
                viewModel.clearStatus()
            },
            onSave = { subject, lecturer, venue, dayIndex, start, end, medium ->
                viewModel.updateClass(session, subject, lecturer, venue, dayIndex, start, end, medium)
                if (viewModel.status.value == null) editingSession = null
            }
        )
    }

    deletingSession?.let { session ->
        AlertDialog(
            onDismissRequest = { deletingSession = null },
            title = { Text("Remove class?") },
            text = {
                Text("${session.subjectName} (${DAY_NAMES[session.dayIndex.coerceIn(0, 6)]} ${session.startTime}–${session.endTime}) will be removed for every student.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeClass(session)
                    deletingSession = null
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSession = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ClassManagementCard(
    session: ClassSession,
    canModify: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val mediumLabel = if (session.teachingMedium == TeachingMedium.ONLINE) "Online" else "Offline"
    val ownerLabel = when {
        session.ownerEmail == null -> "Demo (editable)"
        else -> "Yours"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                session.subjectName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${session.startTime}–${session.endTime} · ${session.venue}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Lecturer: ${session.lecturerName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TagChip(mediumLabel, isAccent = session.teachingMedium == TeachingMedium.ONLINE)
                Spacer(Modifier.width(6.dp))
                TagChip(ownerLabel, isAccent = false)
                Spacer(Modifier.weight(1f))
                if (canModify) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit class")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove class")
                    }
                }
            }
        }
    }
}

@Composable
private fun TagChip(text: String, isAccent: Boolean) {
    val background = if (isAccent) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = if (isAccent) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = foreground)
    }
}

@Composable
private fun ClassEditorDialog(
    title: String,
    initial: ClassSession?,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (
        subject: String,
        lecturer: String,
        venue: String,
        dayIndex: Int,
        startTime: String,
        endTime: String,
        medium: TeachingMedium
    ) -> Unit
) {
    var subject by remember { mutableStateOf(initial?.subjectName ?: "") }
    var lecturer by remember { mutableStateOf(initial?.lecturerName ?: "") }
    var venue by remember { mutableStateOf(initial?.venue ?: "") }
    var start by remember { mutableStateOf(initial?.startTime ?: "") }
    var end by remember { mutableStateOf(initial?.endTime ?: "") }
    var dayIndex by remember { mutableStateOf(initial?.dayIndex ?: 0) }
    var medium by remember { mutableStateOf(initial?.teachingMedium ?: TeachingMedium.OFFLINE) }
    var dayMenuOpen by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lecturer,
                    onValueChange = { lecturer = it },
                    label = { Text("Lecturer name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = venue,
                    onValueChange = { venue = it },
                    label = { Text("Venue") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedButton(
                        onClick = { dayMenuOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(DAY_NAMES[dayIndex.coerceIn(0, 6)])
                    }
                    DropdownMenu(
                        expanded = dayMenuOpen,
                        onDismissRequest = { dayMenuOpen = false }
                    ) {
                        DAY_NAMES.forEachIndexed { index, day ->
                            DropdownMenuItem(
                                text = { Text(day) },
                                onClick = {
                                    dayIndex = index
                                    dayMenuOpen = false
                                }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = start,
                        onValueChange = { start = it },
                        label = { Text("Start (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = end,
                        onValueChange = { end = it },
                        label = { Text("End (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = medium == TeachingMedium.ONLINE,
                        onClick = { medium = TeachingMedium.ONLINE },
                        label = { Text("Online") }
                    )
                    FilterChip(
                        selected = medium == TeachingMedium.OFFLINE,
                        onClick = { medium = TeachingMedium.OFFLINE },
                        label = { Text("Offline") }
                    )
                }
                (localError ?: errorMessage)?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                localError = null
                if (subject.isBlank()) {
                    localError = "Subject name is required"
                    return@TextButton
                }
                if (!TIME_REGEX.matches(start) || !TIME_REGEX.matches(end)) {
                    localError = "Times must be HH:mm (e.g. 09:30)"
                    return@TextButton
                }
                if (start >= end) {
                    localError = "End time must be after start time"
                    return@TextButton
                }
                onSave(subject, lecturer, venue, dayIndex, start, end, medium)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}