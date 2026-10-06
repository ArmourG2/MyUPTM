package com.myuptm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Application
import com.myuptm.data.repository.FirestoreAbsenceLetterRepository
import com.myuptm.data.repository.FirestoreClassRepository
import com.myuptm.data.repository.MatricMatcher
import com.myuptm.data.repository.MockAttendanceRoster
import com.myuptm.data.repository.matchesMatric
import com.myuptm.domain.model.ClassSession
import java.time.LocalDate
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.format.TextStyle
import java.util.Locale

private val RecordGreen = Color(0xFF43A047)
private val RecordRed = Color(0xFFE53935)
private val RecordBlue = Color(0xFF42A5F5)

// Sprint 9 (owner wireframe): lecturer drill-in "Attendance Record" for one student —
// date chip + subject/venue rows with a status chip (green Attend / red Missed).
// Rows derive from the class's weekly slot + the student's LIVE missed count
// (letters flip rows green). Most-recent first.
class AttendanceRecordViewModel(application: Application) : AndroidViewModel(application) {

    private val classRepository = FirestoreClassRepository()
    private val lettersRepository = FirestoreAbsenceLetterRepository()

    data class RecordRow(
        val date: LocalDate,
        val subject: String,
        val venue: String,
        val status: MockAttendanceRoster.SessionStatus
    )

    data class UiState(
        val loading: Boolean = true,
        val session: ClassSession? = null,
        val studentName: String? = null,
        val studentMatric: String? = null,
        val rows: List<RecordRow> = emptyList()
    )

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(UiState())
    val state: kotlinx.coroutines.flow.StateFlow<UiState> = _state.asStateFlow()

    fun bind(classId: String, matric: String) {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                classRepository.observeClasses(),
                lettersRepository.observeLetters()
            ) { classes, letters ->
                val session = classes.find { it.id == classId }
                if (session == null) {
                    UiState(loading = false)
                } else {
                    // Sprint 9: approved letters excuse the most recent slots;
                    // pending letters mark the NEXT ones (submitted, awaiting
                    // review); everything still absent stays Missed.
                    val own = letters.filter {
                        it.studentMatric.matchesMatric(MockAttendanceRoster.DEMO_MATRIC) &&
                            it.status != com.myuptm.domain.model.LetterStatus.DECLINED &&
                            (it.subjectName == null || it.subjectName == session.subjectName)
                    }
                    val approved = own.count { it.status == com.myuptm.domain.model.LetterStatus.APPROVED }
                    val pending = own.count { it.status == com.myuptm.domain.model.LetterStatus.PENDING }
                    val roster = MockAttendanceRoster.studentsFor(
                        session.section, session.subjectName, approved
                    )
                    val student = roster.find { MatricMatcher.matches(it.matric, matric) }
                        ?: roster.find { MatricMatcher.matches(it.matric, MockAttendanceRoster.DEMO_MATRIC) }
                    val base = (student?.missed ?: 0) + approved
                    val statuses = MockAttendanceRoster.sessionStatuses(base, approved, pending)
                    val slots = MockAttendanceRoster.recordSlots(session)
                    UiState(
                        loading = false,
                        session = session,
                        studentName = student?.name,
                        studentMatric = student?.matric,
                        rows = slots.mapIndexed { index, date ->
                            RecordRow(
                                date = date,
                                subject = session.displayCode,
                                venue = session.venue,
                                status = statuses.getOrElse(index) { MockAttendanceRoster.SessionStatus.ATTEND }
                            )
                        }
                    )
                }
            }.collect { _state.value = it }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceRecordScreen(
    classId: String,
    matric: String,
    onBackClick: () -> Unit,
    viewModel: AttendanceRecordViewModel = viewModel()
) {
    androidx.compose.runtime.LaunchedEffect(classId, matric) { viewModel.bind(classId, matric) }
    val state = viewModel.state.collectAsState().value

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("Attendance Record") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val session = state.session
            if (session != null) {
                item(key = "header") {
                    Column {
                        Text(
                            session.displayCode,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${state.studentName ?: "Student"} · ${state.studentMatric ?: ""} · " +
                                "${state.rows.count { it.status == MockAttendanceRoster.SessionStatus.MISSED }} missed" +
                                " · ${state.rows.count { it.status == MockAttendanceRoster.SessionStatus.PENDING }} pending",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (!state.loading && state.rows.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "No attendance records.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(state.rows.size) { index ->
                val row = state.rows[index]
                RecordRowCard(row)
            }
        }
    }
}

@Composable
private fun RecordRowCard(row: AttendanceRecordViewModel.RecordRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
    ) {
        // Date chip (wireframe left).
        Column(
            modifier = Modifier
                .width(64.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), RoundedCornerShape(12.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                row.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                row.date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f).padding(vertical = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                row.subject,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                row.venue,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Status chip (wireframe right) — three-way: red Missed / amber Pending /
        // green Attend (pending = letter submitted, waiting for lecturer review).
        val (chipColor, chipLabel) = when (row.status) {
            MockAttendanceRoster.SessionStatus.MISSED -> RecordRed to "Missed"
            MockAttendanceRoster.SessionStatus.PENDING -> RecordBlue to "Pending"
            MockAttendanceRoster.SessionStatus.ATTEND -> RecordGreen to "Attend"
        }
        Box(
            modifier = Modifier
                .width(72.dp)
                .fillMaxHeight()
                .background(chipColor, RoundedCornerShape(topStart = 0.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                chipLabel,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
