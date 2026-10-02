package com.myuptm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myuptm.data.repository.RosterStudent
import com.myuptm.domain.model.AbsenceLetter
import com.myuptm.domain.model.ClassLevel
import com.myuptm.domain.model.ClassSession
import com.myuptm.domain.model.LetterStatus
import com.myuptm.domain.model.TeachingMedium
import com.myuptm.viewmodel.ClassManagementViewModel
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect

private val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val TIME_REGEX = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

// Wireframe badge palette (attendance severity): green full / yellow moderate / red high.
private val GreenBadge = Color(0xFF2E7D32)
private val YellowBadge = Color(0xFFF9A825)
private val RedBadge = Color(0xFFC62828)

// Sprint 8: Class Management rebuilt per the approved wireframes —
// search + level tabs (Diploma/Degree/Master), class cards with sections,
// per-class student roster (mock attendance), and per-student absence-letter
// review (Approve / Decline-with-reason → student notification).
@Composable
fun ClassManagementScreen(
    ownerEmail: String?,
    viewModel: ClassManagementViewModel = viewModel()
) {
    val weeklyClasses by viewModel.weeklyClasses.collectAsState()
    val letters by viewModel.letters.collectAsState()
    val status by viewModel.status.collectAsState()

    var searchText by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<ClassLevel?>(null) } // null = All
    var rosterClass by remember { mutableStateOf<ClassSession?>(null) }
    var detailStudent by remember { mutableStateOf<RosterStudent?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingSession by remember { mutableStateOf<ClassSession?>(null) }
    var deletingSession by remember { mutableStateOf<ClassSession?>(null) }
    var declineLetter by remember { mutableStateOf<AbsenceLetter?>(null) }

    LaunchedEffect(status) {
        if (status != null) {
            delay(2500)
            viewModel.clearStatus()
        }
    }

    // System back mirrors the in-screen back buttons: student detail → roster → class list,
    // THEN the real screen pop (before this, back skipped straight to Home).
    androidx.activity.compose.BackHandler(enabled = detailStudent != null) {
        detailStudent = null
    }
    androidx.activity.compose.BackHandler(enabled = detailStudent == null && rosterClass != null) {
        rosterClass = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            val isRosterView = rosterClass != null && detailStudent == null
            val isStudentView = detailStudent != null

            Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp)) {
                if (isStudentView) {
                    BackTitleRow(
                        title = "Letters",
                        onBack = { detailStudent = null }
                    )
                } else if (isRosterView) {
                    BackTitleRow(
                        title = "${rosterClass!!.subjectName} · ${rosterClass!!.section.ifEmpty { "No section" }}",
                        onBack = { rosterClass = null }
                    )
                } else {
                    Text(
                        "Class Management",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                status?.let { message ->
                    Spacer(Modifier.height(4.dp))
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            when {
                isStudentView -> StudentLetterReview(
                    student = detailStudent!!,
                    letters = letters.filter {
                        it.studentMatric.equals(detailStudent!!.matric, ignoreCase = true)
                    },
                    classSession = rosterClass!!,
                    onApprove = viewModel::approveLetter,
                    onOpenDecline = { declineLetter = it }
                )
                isRosterView -> StudentRosterView(
                    students = viewModel.rosterFor(rosterClass!!),
                    letters = letters,
                    onStudentClick = { detailStudent = it }
                )
                else -> ClassListView(
                    weeklyClasses = weeklyClasses,
                    searchText = searchText,
                    selectedLevel = selectedLevel,
                    ownerEmail = ownerEmail,
                    canModify = viewModel::canModify,
                    onSearchChange = { searchText = it },
                    onLevelChange = { selectedLevel = it },
                    onClassClick = { rosterClass = it },
                    onEdit = { editingSession = it },
                    onDelete = { deletingSession = it },
                    hasClasses = weeklyClasses.any { it.isNotEmpty() }
                )
            }
        }

        if (rosterClass == null) {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add class")
            }
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
            onSave = { subject, lecturer, venue, dayIndex, start, end, medium, section, level ->
                viewModel.addClass(subject, lecturer, venue, dayIndex, start, end, medium, section, level)
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
            onSave = { subject, lecturer, venue, dayIndex, start, end, medium, section, level ->
                viewModel.updateClass(session, subject, lecturer, venue, dayIndex, start, end, medium, section, level)
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

    declineLetter?.let { letter ->
        RejectionDetailDialog(
            letter = letter,
            onDismiss = { declineLetter = null },
            onSend = { reason ->
                viewModel.declineLetter(letter, reason)
                declineLetter = null
            }
        )
    }
}

@Composable
private fun BackTitleRow(title: String, onBack: () -> Unit) {    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ---------- Class list (root) ----------

@Composable
private fun ClassListView(
    weeklyClasses: List<List<ClassSession>>,
    searchText: String,
    selectedLevel: ClassLevel?,
    ownerEmail: String?,
    canModify: (ClassSession, String?) -> Boolean,
    onSearchChange: (String) -> Unit,
    onLevelChange: (ClassLevel?) -> Unit,
    onClassClick: (ClassSession) -> Unit,
    onEdit: (ClassSession) -> Unit,
    onDelete: (ClassSession) -> Unit,
    hasClasses: Boolean
) {
    val allClasses = weeklyClasses.flatten()
    val filtered = allClasses.filter { session ->
        (selectedLevel == null || session.level == selectedLevel) &&
            (searchText.isBlank() ||
                session.subjectName.contains(searchText, ignoreCase = true) ||
                session.section.contains(searchText, ignoreCase = true))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchChange,
            placeholder = { Text("Search class or section...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
        )
        Spacer(Modifier.height(10.dp))
        // Level tabs (wireframe: Diploma / Degree / Master) + All.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(null to "All", ClassLevel.DIPLOMA to "Diploma", ClassLevel.DEGREE to "Degree", ClassLevel.MASTER to "Master")
                .forEach { (level, label) ->
                    FilterChip(
                        selected = selectedLevel == level,
                        onClick = { onLevelChange(level) },
                        label = { Text(label) }
                    )
                }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (filtered.isEmpty()) {
                item {
                    Text(
                        if (hasClasses) "No classes match this filter." else "No classes yet — add the first one with the + button.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(filtered, key = { it.id }) { session ->
                ClassManagementCard(
                    session = session,
                    canModify = canModify(session, ownerEmail),
                    onClick = { onClassClick(session) },
                    onEdit = { onEdit(session) },
                    onDelete = { onDelete(session) }
                )
            }
        }
    }
}

@Composable
private fun ClassManagementCard(
    session: ClassSession,
    canModify: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val mediumLabel = if (session.teachingMedium == TeachingMedium.ONLINE) "Online" else "Offline"
    val ownerLabel = when {
        session.ownerEmail == null -> "Demo (editable)"
        else -> "Yours"
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
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
                buildString {
                    append(session.section.ifEmpty { "No section" })
                    append(" · ${session.level.name.lowercase().replaceFirstChar { it.uppercase() }}")
                    if (session.teachingMedium == TeachingMedium.ONLINE) append(" · Online")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${session.startTime}–${session.endTime} · ${session.venue} · Lecturer: ${session.lecturerName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        ownerLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

// ---------- Roster (class clicked) ----------

@Composable
private fun StudentRosterView(
    students: List<RosterStudent>,
    letters: List<AbsenceLetter>,
    onStudentClick: (RosterStudent) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(students, key = { it.matric }) { student ->
            val pendingLetters = letters.count {
                it.studentMatric.equals(student.matric, ignoreCase = true) && it.status == LetterStatus.PENDING
            }
            RosterCard(
                student = student,
                badgeText = if (student.missed == 0)
                    "Full\nAttendance" else "Missed\n${student.missed}",
                badgeColor = when (student.severity) {
                    0 -> GreenBadge
                    1 -> YellowBadge
                    else -> RedBadge
                },
                pending = pendingLetters,
                onClick = { onStudentClick(student) }
            )
        }
    }
}

@Composable
private fun RosterCard(
    student: RosterStudent,
    badgeText: String,
    badgeColor: Color,
    pending: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                Text(
                    student.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    student.matric,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (pending > 0) {
                    Text(
                        "$pending letter${if (pending != 1) "s" else ""} waiting${if (pending == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.30f)
                    .height(64.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    badgeText,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                        .background(badgeColor, RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

// ---------- Student letter review (student clicked) ----------

@Composable
private fun StudentLetterReview(
    student: RosterStudent,
    letters: List<AbsenceLetter>,
    classSession: ClassSession,
    onApprove: (AbsenceLetter) -> Unit,
    onOpenDecline: (AbsenceLetter) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp)
            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Identity row (wireframe: avatar circle + name + matric + section)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    student.name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString(""),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(student.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(student.matric, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    classSession.section.ifEmpty { "Section 1" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Verified / Missed counts (wireframe colour boxes)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CountBox(student.verified.toString(), "VERIFIED", GreenBadge, Modifier.weight(1f))
            CountBox(student.missed.toString(), "MISSED", RedBadge, Modifier.weight(1f))
        }

        Text(
            "Absent Letter",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp)
        )

        if (letters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("NONE", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
            }
        } else {
            letters.forEach { letter ->
                LetterCard(
                    letter = letter,
                    onApprove = { onApprove(letter) },
                    onDecline = { onOpenDecline(letter) }
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CountBox(count: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .height(84.dp)
            .background(color, RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(count, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

@Composable
private fun LetterCard(letter: AbsenceLetter, onApprove: () -> Unit, onDecline: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    )) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    letter.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                TextButton(onClick = {
                    // Open the stored letter in the browser (real Cloudinary raw URL).
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(letter.fileUrl))
                        )
                    }
                }) {
                    Text("Open")
                }
            }
            Text(
                letter.status.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = when (letter.status) {
                    LetterStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                    LetterStatus.APPROVED -> GreenBadge
                    LetterStatus.DECLINED -> RedBadge
                }
            )
            if (letter.status == LetterStatus.DECLINED && letter.declineReason != null) {
                Text(
                    "Reason: ${letter.declineReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (letter.status == LetterStatus.PENDING) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Button(
                        onClick = onApprove,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = GreenBadge),
                        modifier = Modifier.weight(1f)
                    ) { Text("Approve", color = Color.White) }
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.Button(
                        onClick = onDecline,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = RedBadge),
                        modifier = Modifier.weight(1f)
                    ) { Text("Decline", color = Color.White) }
                }
            }
        }
    }
}

// ---------- Rejection detail (wireframe) ----------

@Composable
private fun RejectionDetailDialog(
    letter: AbsenceLetter,
    onDismiss: () -> Unit,
    onSend: (reason: String) -> Unit
) {
    var title by remember { mutableStateOf("Rejection") }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rejection Detail") },
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
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (reason.isNotBlank()) onSend(reason.trim())
            }) { Text("Send") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
        medium: TeachingMedium,
        section: String,
        level: ClassLevel
    ) -> Unit
) {
    var subject by remember { mutableStateOf(initial?.subjectName ?: "") }
    var lecturer by remember { mutableStateOf(initial?.lecturerName ?: "") }
    var venue by remember { mutableStateOf(initial?.venue ?: "") }
    var start by remember { mutableStateOf(initial?.startTime ?: "") }
    var end by remember { mutableStateOf(initial?.endTime ?: "") }
    var dayIndex by remember { mutableStateOf(initial?.dayIndex ?: 0) }
    var medium by remember { mutableStateOf(initial?.teachingMedium ?: TeachingMedium.OFFLINE) }
    var section by remember { mutableStateOf(initial?.section ?: "") }
    var level by remember { mutableStateOf(initial?.level ?: ClassLevel.DIPLOMA) }
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
                    value = section,
                    onValueChange = { section = it },
                    label = { Text("Section (e.g. Section 1)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // Programme level chips (wireframe: Diploma / Degree / Master).
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = level == ClassLevel.DIPLOMA,
                        onClick = { level = ClassLevel.DIPLOMA },
                        label = { Text("Diploma") }
                    )
                    FilterChip(
                        selected = level == ClassLevel.DEGREE,
                        onClick = { level = ClassLevel.DEGREE },
                        label = { Text("Degree") }
                    )
                    FilterChip(
                        selected = level == ClassLevel.MASTER,
                        onClick = { level = ClassLevel.MASTER },
                        label = { Text("Master") }
                    )
                }
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
                onSave(subject, lecturer, venue, dayIndex, start, end, medium, section, level)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
