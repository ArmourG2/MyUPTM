package com.myuptm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.Button
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myuptm.data.repository.MatricMatcher
import com.myuptm.data.repository.RosterStudent
import com.myuptm.data.repository.matchesMatric
import com.myuptm.domain.model.AbsenceLetter
import com.myuptm.domain.model.AttendanceSession
import com.myuptm.domain.model.ClassLevel
import com.myuptm.domain.model.ClassSession
import com.myuptm.domain.model.LetterStatus
import com.myuptm.domain.model.TeachingMedium
import com.myuptm.ui.components.QrCodeImage
import com.myuptm.viewmodel.AttendanceSessionViewModel
import com.myuptm.viewmodel.ClassManagementViewModel
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect

private val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val TIME_REGEX = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

// Multi-select helper for merged-level chips (Diploma/Degree/Master toggles).
private fun <T> MutableSet<T>.toggle(value: T): MutableSet<T> =
    if (contains(value)) {
        remove(value); this
    } else {
        add(value); this
    }

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
    onOpenAttendanceRecord: (classId: String, matric: String) -> Unit = { _, _ -> },
    viewModel: ClassManagementViewModel = viewModel()
) {
    val weeklyClasses by viewModel.weeklyClasses.collectAsState()
    val letters by viewModel.letters.collectAsState()
    val status by viewModel.status.collectAsState()
    val warningBusy by viewModel.warningBusy.collectAsState()

    // Sprint 11: QR/PIN attendance — one live session per lecturer; the roster
    // screen hosts the session card (code + QR + countdown + verified count).
    val sessionViewModel: AttendanceSessionViewModel = viewModel()
    LaunchedEffect(ownerEmail) { sessionViewModel.bindOwner(ownerEmail) }
    val activeSession by sessionViewModel.activeSession.collectAsState()
    val sessionCheckIns by sessionViewModel.sessionCheckIns.collectAsState()
    val isStartingSession by sessionViewModel.isStarting.collectAsState()
    val sessionStatus by sessionViewModel.status.collectAsState()

    // Sprint 9: bind scoping to the signed-in lecturer ("only MY classes").
    LaunchedEffect(ownerEmail) { viewModel.bindOwner(ownerEmail) }

    var warningTargets by remember { mutableStateOf<List<RosterStudent>?>(null) }
    // Sprint 9: end-of-session confirmation (the old "Close attendance" is now
    // self-explanatory: confirm → absentees get yellow miss notices).
    var showEndAttendanceConfirm by remember { mutableStateOf(false) }

    var searchText by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<ClassLevel?>(null) } // null = All
    // Sprint 9: drill-down state is SAVEABLE (ids only, objects re-derived) so the
    // system back / gesture back from the Attendance Record screen returns to the
    // exact Letters screen instead of the Class Management root.
    var rosterClassId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailStudentMatric by rememberSaveable { mutableStateOf<String?>(null) }
    val rosterClass: ClassSession? = rosterClassId?.let { id ->
        weeklyClasses.flatten().find { it.id == id }
    }
    val detailStudent: RosterStudent? = detailStudentMatric?.let { matric ->
        rosterClass?.let { session ->
            viewModel.rosterFor(session, letters).find { it.matric.matchesMatric(matric) }
        }
    }
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
        detailStudentMatric = null
    }
    androidx.activity.compose.BackHandler(enabled = detailStudent == null && rosterClass != null) {
        rosterClassId = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            val isRosterView = rosterClass != null && detailStudent == null
            val isStudentView = detailStudent != null

            Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp)) {
                if (isStudentView) {
                    BackTitleRow(
                        title = "Letters",
                        onBack = { detailStudentMatric = null }
                    )
                } else if (isRosterView) {
                    BackTitleRow(
                        title = rosterClass!!.displayCode,
                        onBack = { rosterClassId = null }
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
                sessionStatus?.let { message ->
                    Spacer(Modifier.height(4.dp))
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            when {
                isStudentView -> StudentLetterReview(
                    student = detailStudent!!,
                    letters = viewModel.lettersFor(detailStudent!!.matric, rosterClass!!, letters),
                    classSession = rosterClass!!,
                    onApprove = viewModel::approveLetter,
                    onOpenDecline = { declineLetter = it },
                    onWarnStudent = { warningTargets = listOf(detailStudent!!) },
                    onViewAttendance = {
                        onOpenAttendanceRecord(rosterClass!!.id, detailStudent!!.matric)
                    }
                )
                isRosterView -> StudentRosterView(
                    students = viewModel.rosterFor(rosterClass!!, letters),
                    letters = letters,
                    subjectName = rosterClass!!.subjectName,
                    activeSession = activeSession?.takeIf { it.classId == rosterClass!!.id },
                    otherSessionName = activeSession?.takeIf { it.classId != rosterClass!!.id }?.displayCode,
                    sessionCheckIns = sessionCheckIns,
                    isStartingSession = isStartingSession,
                    onStartAttendance = { sessionViewModel.startSession(rosterClass!!) },
                    onEndSession = { sessionViewModel.endSession() },
                    onStudentClick = { detailStudentMatric = it.matric },
                    onWarnRed = { warningTargets = it },
                    onEndAttendance = { showEndAttendanceConfirm = true }
                )
                else -> ClassListView(
                    weeklyClasses = weeklyClasses,
                    searchText = searchText,
                    selectedLevel = selectedLevel,
                    ownerEmail = ownerEmail,
                    canModify = viewModel::canModify,
                    onSearchChange = { searchText = it },
                    onLevelChange = { selectedLevel = it },
                    onClassClick = { rosterClassId = it.id },
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
            onSave = { subject, venue, dayIndex, start, end, medium, section, levels, attention, reason, image ->
                viewModel.addClass(subject, venue, dayIndex, start, end, medium, section, levels, attention, reason, image)
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
            onSave = { subject, venue, dayIndex, start, end, medium, section, levels, attention, reason, image ->
                viewModel.updateClass(session, subject, venue, dayIndex, start, end, medium, section, levels, attention, reason, image)
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

    // Sprint 9: end-of-session confirmation — states plainly what happens, so the
    // demo narrative is self-explanatory (full system runs it automatically after
    // QR/PIN closes; the POC triggers the check manually).
    if (showEndAttendanceConfirm) {
        AlertDialog(
            onDismissRequest = { showEndAttendanceConfirm = false },
            title = { Text("End attendance session?") },
            text = {
                Text(
                    "This marks the ${rosterClass?.displayCode ?: "class"} session as ended. " +
                        "Students who did not verify their attendance will receive a missed-class " +
                        "notification and can submit an absence letter for review."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showEndAttendanceConfirm = false
                    rosterClass?.let { viewModel.closeAttendance(it) }
                }) { Text("End & notify") }
            },
            dismissButton = {
                TextButton(onClick = { showEndAttendanceConfirm = false }) { Text("Cancel") }
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

    warningTargets?.let { targets ->
        WarningComposerDialog(
            targets = targets,
            classSession = rosterClass!!,
            isSending = warningBusy,
            onDismiss = { if (!warningBusy) warningTargets = null },
            onSend = { title, desc, attachment ->
                viewModel.sendWarning(targets, rosterClass!!, title, desc, attachment)
                if (viewModel.status.value != null) warningTargets = null
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
        // Sprint 9: merged classes (Diploma+Degree) match ANY of their levels.
        (selectedLevel == null || selectedLevel in session.levels) &&
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
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                session.displayCode,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    if (session.section.isNotBlank()) append("Section ${session.section} · ")
                    append(session.levels.joinToString(" · ") { l ->
                        l.name.lowercase().replaceFirstChar { it.uppercase() }
                    })
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
    subjectName: String,
    // Sprint 11: live QR/PIN attendance session for THIS class (null = none).
    activeSession: AttendanceSession?,
    otherSessionName: String?,
    sessionCheckIns: List<com.myuptm.domain.model.AttendanceCheckIn>,
    isStartingSession: Boolean,
    onStartAttendance: () -> Unit,
    onEndSession: () -> Unit,
    onStudentClick: (RosterStudent) -> Unit,
    onWarnRed: (List<RosterStudent>) -> Unit,
    onEndAttendance: () -> Unit
) {
    val redStudents = students.filter { it.missed >= 5 }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "attendance_session") {
            when {
                activeSession != null -> AttendanceSessionCard(
                    session = activeSession,
                    verifiedCount = sessionCheckIns.size,
                    onEndSession = onEndSession
                )
                otherSessionName != null -> Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Attendance is running for $otherSessionName — end that session first.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> OutlinedButton(
                    onClick = onStartAttendance,
                    enabled = !isStartingSession,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isStartingSession) "Starting..."
                        else "Start attendance (QR / PIN) — 5 min window"
                    )
                }
            }
        }
        item(key = "roster_actions") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                // Sprint 9 (Option A): simulates the END of the class session —
                // students who did not verify attendance receive a yellow
                // missed-class notice (the automatic check the full system runs
                // after a QR/PIN session closes; the POC triggers it manually).
                OutlinedButton(onClick = onEndAttendance) {
                    Text("End class & notify absentees")
                }
                // Sprint 8 Task 5: batch warning for ALL red students of this class.
                if (redStudents.isNotEmpty()) {
                    androidx.compose.material3.Button(
                        onClick = { onWarnRed(redStudents) },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = RedBadge)
                    ) {
                        Text(
                            "Warn all red students (${redStudents.size})",
                            color = Color.White
                        )
                    }
                }
            }
        }
        itemsIndexed(students, key = { _, student -> student.name + student.matric }) { _, student ->
            val pendingLetters = letters.count {
                it.studentMatric.matchesMatric(student.matric) &&
                    (it.subjectName == null || it.subjectName == subjectName) &&
                    it.status == LetterStatus.PENDING
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

// Sprint 11: the live session card — big shared 6-digit code (QR payload = PIN),
// scannable QR, live countdown, verified count. Auto-closes the session when the
// window expires (lazy close; verification also rejects expired sessions).
@Composable
private fun AttendanceSessionCard(
    session: AttendanceSession,
    verifiedCount: Int,
    onEndSession: () -> Unit
) {
    var remainingMs by remember { mutableStateOf(session.remainingMs()) }

    LaunchedEffect(session.id, session.expiresAt) {
        while (true) {
            remainingMs = session.remainingMs()
            if (remainingMs <= 0L) {
                onEndSession() // lazy close — flips active=false in Firestore
                break
            }
            delay(1_000)
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Attendance is OPEN — ${session.displayCode}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            QrCodeImage(content = session.code, size = 180.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                session.code,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp
            )
            Text(
                "PIN = QR — students scan or type this code",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            val minutes = remainingMs / 60_000
            val seconds = (remainingMs % 60_000) / 1_000
            Text(
                if (remainingMs > 0) "Expires in %d:%02d".format(minutes, seconds)
                else "Expired",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (remainingMs > 30_000) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
            Text(
                "$verifiedCount student(s) verified",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onEndSession) { Text("End session") }
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
            containerColor = MaterialTheme.colorScheme.surfaceContainer
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
    onOpenDecline: (AbsenceLetter) -> Unit,
    onWarnStudent: () -> Unit = {},
    onViewAttendance: () -> Unit = {}
) {
    val redOnly = student.missed >= 5
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

        // Sprint 9 wireframe: the blue View Attendance button opens the student's
        // attendance record (date chip + subject/venue + Attend/Missed chips).
        Button(
            onClick = onViewAttendance,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = androidx.compose.ui.graphics.Color(0xFF42A5F5)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("View Attendance", color = Color.White)
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

        // Sprint 8 Task 5: warning entry — RED students only (missed >= 5).
        if (redOnly) {
            TextButton(onClick = onWarnStudent, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Send warning letter",
                    color = RedBadge,
                    fontWeight = FontWeight.SemiBold
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
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
    title: String,    initial: ClassSession?,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (
        subject: String,
        venue: String,
        dayIndex: Int,
        startTime: String,
        endTime: String,
        medium: TeachingMedium,
        section: String,
        levels: List<ClassLevel>,
        attention: Boolean,
        reason: String?,
        imageUri: android.net.Uri?
    ) -> Unit
) {
    var subject by remember { mutableStateOf(initial?.subjectName ?: "") }
    var venue by remember { mutableStateOf(initial?.venue ?: "") }
    var start by remember {
        mutableStateOf(initial?.startTime ?: "09:00")
    }
    var end by remember {
        mutableStateOf(initial?.endTime ?: "10:00")
    }
    var dayIndex by remember { mutableStateOf(initial?.dayIndex ?: 0) }
    var medium by remember { mutableStateOf(initial?.teachingMedium ?: TeachingMedium.OFFLINE) }
    var section by remember { mutableStateOf(initial?.section ?: "") }
    var levels by remember {
        mutableStateOf(initial?.levels?.toSet() ?: setOf(ClassLevel.DIPLOMA))
    }
    // Sprint 9: lecturer toggle — promotes the change notification to attention/yellow.
    var attention by remember { mutableStateOf(false) }
    // Sprint 9: optional context/reason + optional image (like posts).
    var reason by remember { mutableStateOf("") }
    var selectedImage by remember { mutableStateOf<android.net.Uri?>(null) }
    val contextPicker = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? -> selectedImage = uri }
    var dayMenuOpen by remember { mutableStateOf(false) }
    var chooseTimeFor by remember { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }

    // Sprint 9 revamp (wireframe): Dialog + Surface, two-column field rows,
    // day + Online/Offline side by side, From/To open the shared M3 "Choose Time"
    // picker (same UX as the student's plan dialog), Cancel/Save pinned bottom-right.
    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                // Row 1: Subject Name | Section
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f)
                    )
                    OutlinedTextField(
                        value = section,
                        onValueChange = { section = it },
                        label = { Text("Section") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2: programme level chips (MULTI-select — merged classes like
                // SWC3403/3713 serve Diploma AND Degree in one section).
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = ClassLevel.DIPLOMA in levels,
                        onClick = { levels = levels.toMutableSet().toggle(ClassLevel.DIPLOMA) },
                        label = { Text("Diploma") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = ClassLevel.DEGREE in levels,
                        onClick = { levels = levels.toMutableSet().toggle(ClassLevel.DEGREE) },
                        label = { Text("Degree") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = ClassLevel.MASTER in levels,
                        onClick = { levels = levels.toMutableSet().toggle(ClassLevel.MASTER) },
                        label = { Text("Master") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 3: Venue (full width — the lecturer is the ACCOUNT, no name field).
                OutlinedTextField(
                    value = venue,
                    onValueChange = { venue = it },
                    label = { Text("Venue") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Row 4: day dropdown | Online/Offline segmented toggle.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        androidx.compose.material3.OutlinedButton(
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
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                        SegmentedButton(
                            selected = medium == TeachingMedium.ONLINE,
                            onClick = { medium = TeachingMedium.ONLINE },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text("Online") }
                        SegmentedButton(
                            selected = medium == TeachingMedium.OFFLINE,
                            onClick = { medium = TeachingMedium.OFFLINE },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text("Offline") }
                    }
                }

                // Row 5: From / To (M3 TimePicker via the shared ChooseTime dialog).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("From", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.Button(onClick = { chooseTimeFor = "start" }) {
                        Text(formatAmPm(start))
                    }
                    Spacer(Modifier.width(16.dp))
                    Text("To", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.Button(onClick = { chooseTimeFor = "end" }) {
                        Text(formatAmPm(end))
                    }
                }

                // Sprint 9: attention toggle — students receive this change as a
                // yellow reminder instead of a normal blue update.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Mark change as attention (yellow)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    androidx.compose.material3.Switch(
                        checked = attention,
                        onCheckedChange = { attention = it }
                    )
                }

                // Optional context/reason pushed into the notification detail.
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Context / reason (optional)") },
                    placeholder = { Text("e.g. Room swap for this week only") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                // Optional image shown on the notification detail (like posts).
                selectedImage?.let { uri ->
                    coil3.compose.AsyncImage(
                        model = coil3.request.ImageRequest.Builder(LocalContext.current).data(uri).build(),
                        contentDescription = "Notification image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    TextButton(onClick = { selectedImage = null }) { Text("Remove image") }
                } ?: run {
                    TextButton(
                        onClick = { contextPicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Attach image (optional)") }
                }

                (localError ?: errorMessage)?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Pinned actions (wireframe: Cancel / Save bottom-right).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        localError = null
                        if (subject.isBlank()) {
                            localError = "Subject name is required"
                            return@TextButton
                        }
                        if (levels.isEmpty()) {
                            localError = "Pick at least one programme level"
                            return@TextButton
                        }
                        if (start >= end) {
                            localError = "End time must be after start time"
                            return@TextButton
                        }
                        onSave(
                            subject, venue, dayIndex, start, end, medium, section, levels.toList(),
                            attention, reason.trim().ifBlank { null }, selectedImage
                        )
                    }) { Text("Save") }
                }
            }
        }
    }

    chooseTimeFor?.let { which ->
        ChooseTimeDialog(
            initialTime = if (which == "start") start else end,
            onConfirm = { newTime ->
                if (which == "start") start = newTime else end = newTime
                chooseTimeFor = null
            },
            onDismiss = { chooseTimeFor = null }
        )
    }
}

// Sprint 8 Task 5: warning composer — Title, Description, optional PDF attachment;
// target list (single student or all red students of the class) decided by the entry.
@Composable
private fun WarningComposerDialog(
    targets: List<RosterStudent>,
    classSession: ClassSession,
    isSending: Boolean,
    onDismiss: () -> Unit,
    onSend: (title: String, description: String, attachment: android.net.Uri?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var attachment by remember { mutableStateOf<android.net.Uri?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }

    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? -> attachment = uri }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(
            if (targets.size == 1) "Warning letter"
            else "Warning letter · ${targets.size} students"
        ) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (targets.size == 1)
                        "To: ${targets.first().name} (${targets.first().matric})"
                    else
                        "Batch warning to red students (missed 5+)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                if (attachment != null) {
                    Text(
                        "Attachment: ${attachment?.lastPathSegment}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    )
                }
                TextButton(onClick = { picker.launch("application/pdf") }) {
                    Text(if (attachment == null) "Attach PDF (optional)" else "Change attachment")
                }
                if (isSending) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Sending warning...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                localError?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSending && title.isNotBlank() && description.isNotBlank(),
                onClick = {
                    if (title.isBlank() || description.isBlank()) {
                        localError = "Title and description are required"
                        return@TextButton
                    }
                    onSend(title.trim(), description.trim(), attachment)
                }
            ) { Text("Send") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSending) { Text("Cancel") }
        }
    )
}
