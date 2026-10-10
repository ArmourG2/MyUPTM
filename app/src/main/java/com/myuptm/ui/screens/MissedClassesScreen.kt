package com.myuptm.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myuptm.data.repository.MockAttendanceRoster
import com.myuptm.viewmodel.ProfileViewModel
import com.myuptm.viewmodel.SubjectAttendance
import java.time.format.TextStyle
import java.util.Locale

// Sprint 9 (late): the missed-classes overview as a DEDICATED PAGE (owner request —
// the popup looked wrong, and reopening it after the system file picker returned
// corrupt the Dialog's layout: a full screen has no nested-Dialog measurement
// quirks). Subject cards expand into per-session rows; each MISSED row submits its
// own per-class letter; a submitted letter flips its row to amber "Pending" until
// the lecturer approves or declines it.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MissedClassesScreen(
    viewModel: ProfileViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val classes by viewModel.classes.collectAsState()
    var expandedSubject by remember { mutableStateOf<String?>(null) }
    var pendingLetterSubject by remember { mutableStateOf<String?>(null) }
    val letterPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        val subject = pendingLetterSubject
        if (uri != null && subject != null) {
            viewModel.submitAbsenceLetter(uri, subject)
        }
        pendingLetterSubject = null
    }

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        // Campus-photo hero banner (design language from sign-in).
        com.myuptm.ui.components.CampusHeroBackdrop()
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                com.myuptm.ui.components.CampusHeroHeader(
                    title = "Missed classes",
                    subtitle = "Tap a subject to see which sessions need a letter",
                    navigationIcon = {
                        com.myuptm.ui.components.CampusBackButton(onBackClick)
                    }
                )
            }
        ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            val attendance = uiState.attendance
            if (attendance == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }
            if (attendance.subjects.isEmpty()) {
                Text(
                    "Nothing outstanding — perfect.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            attendance.subjects.forEach { info ->
                val expanded = expandedSubject == info.subject
                SubjectCard(
                    info = info,
                    expanded = expanded,
                    rows = if (expanded) viewModel.recordRowsFor(info.subject, classes) else emptyList(),
                    isUploading = uiState.letterUploading,
                    onToggle = {
                        expandedSubject = if (expanded) null else info.subject
                    },
                    onSubmit = {
                        viewModel.clearLetterStates()
                        pendingLetterSubject = info.subject
                        letterPickerLauncher.launch("application/pdf")
                    }
                )
                Spacer(Modifier.height(10.dp))
            }

            // Upload feedback banners (activity-level; survive the picker round-trip).
            if (uiState.letterUploading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Uploading letter...", style = MaterialTheme.typography.bodySmall)
                }
            }
            uiState.letterError?.let { err ->
                Text(
                    "Error: $err",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            if (uiState.letterSubmitted && !uiState.letterUploading) {
                Text(
                    "Letter submitted — pending lecturer review",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    }
}

// Sprint 9: subject card — collapsed shows the monogram + counts; expanded shows
// the per-session record rows (dates derived from the class weekday).
@Composable
private fun SubjectCard(
    info: SubjectAttendance,
    expanded: Boolean,
    rows: List<com.myuptm.viewmodel.ProfileViewModel.MissedRecordRow>,
    isUploading: Boolean,
    onToggle: () -> Unit,
    onSubmit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Subject monogram tile (M3 style).
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer,
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        info.subject.take(3),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        info.subject,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    // Small screens: pills must wrap onto extra lines instead of
                    // overflowing (the last chip used to squash vertically).
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TonalPill(
                            text = if (info.missed > 0) "${info.missed} missed" else "excused",
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer
                        )
                        if (info.pending > 0) {
                            TonalPill(
                                text = "${info.pending} pending",
                                container = MaterialTheme.colorScheme.tertiaryContainer,
                                content = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        if (info.approved > 0) {
                            TonalPill(
                                text = "${info.approved} excused",
                                container = MaterialTheme.colorScheme.primaryContainer,
                                content = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(if (expanded) 180f else 0f)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (rows.isEmpty()) {
                        Text(
                            "No schedule data for this subject.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    rows.forEach { row ->
                        SessionRecordRow(
                            row = row,
                            enabled = !isUploading &&
                                row.status == MockAttendanceRoster.SessionStatus.MISSED,
                            onSubmit = onSubmit
                        )
                    }
                }
            }
        }
    }
}

// Compact M3 session row: date chip + weekday/venue + tonal status pill;
// MISSED rows carry the per-session send action.
@Composable
private fun SessionRecordRow(
    row: com.myuptm.viewmodel.ProfileViewModel.MissedRecordRow,
    enabled: Boolean,
    onSubmit: () -> Unit
) {
    val (label, container, content) = when (row.status) {
        MockAttendanceRoster.SessionStatus.MISSED -> Triple(
            "Missed", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer
        )
        MockAttendanceRoster.SessionStatus.PENDING -> Triple(
            "Pending", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer
        )
        MockAttendanceRoster.SessionStatus.ATTEND -> Triple(
            "Attend", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .width(52.dp)
                .fillMaxHeight()
                .background(
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                row.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                row.date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                row.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                row.venue,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(container)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = content)
        }
        if (row.status == MockAttendanceRoster.SessionStatus.MISSED) {
            IconButton(onClick = onSubmit, enabled = enabled) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Submit absence letter",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}

// The pill never lets its text wrap — on narrow screens the whole pill moves
// to a FlowRow line instead of squashing "1 excused" letter-by-letter.
@Composable
private fun TonalPill(text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
    }
}
