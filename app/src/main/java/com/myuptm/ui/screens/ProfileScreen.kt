package com.myuptm.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import com.myuptm.data.repository.EnrolledSectionsData
import com.myuptm.data.repository.FirestoreAbsenceLetterRepository
import com.myuptm.data.repository.FirestoreProfileRepository
import com.myuptm.data.repository.MockAttendanceRoster
import com.myuptm.data.repository.RealCloudinaryRepository
import com.myuptm.domain.model.UserRole
import com.myuptm.navigation.MyUptmRoutes
import com.myuptm.viewmodel.ProfileViewModel
import com.myuptm.viewmodel.SubjectAttendance
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ProfileScreen(navController: NavController) {
    val profileViewModel: ProfileViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                // Sprint 8 Task 3: real Cloudinary upload replaces the mock seam.
                val app = this[ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY]
                ProfileViewModel(
                    profileRepository = FirestoreProfileRepository(),
                    cloudinaryRepository = RealCloudinaryRepository(app as android.content.Context),
                    // Sprint 9: student attendance statistics (letters reduce misses).
                    lettersRepository = FirestoreAbsenceLetterRepository()
                )
            }
        }
    )
    val uiState by profileViewModel.uiState.collectAsState()
    val profile = uiState.userProfile
    var showMissedSubjects by remember { mutableStateOf(false) }
    var pendingLetterSubject by remember { mutableStateOf<String?>(null) }
    val letterPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val subject = pendingLetterSubject
        if (uri != null && subject != null) {
            profileViewModel.submitAbsenceLetter(uri, subject)
        }
        pendingLetterSubject = null
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { profileViewModel.uploadAvatar(it) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Profile",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { navController.navigate(MyUptmRoutes.SETTINGS) }) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        }


        if (profile == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { imagePickerLauncher.launch("image/*") }, // Opens system image picker
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isUploading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                    } else if (!profile.avatarUrl.isNullOrBlank()) {
                        // Sprint 8 Task 3: real avatar image (Coil) replaces initials.
                        coil3.compose.AsyncImage(
                            model = coil3.request.ImageRequest.Builder(LocalContext.current)
                                .data(profile.avatarUrl)
                                .build(),
                            contentDescription = "Profile picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = profile.initials,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                uiState.uploadError?.let { err ->
                    Text("Error: $err", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = profile.fullName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                RoleChip(profile.role)
                if (profile.studentId != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "ID: ${profile.studentId}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(24.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        DetailRow(Icons.Filled.Email, "Email", profile.email)
                        DetailRow(Icons.Filled.Info, "Faculty", profile.faculty)
                        if (profile.programme != null) {
                            DetailRow(Icons.Filled.AccountBox, "Programme", profile.programme)
                        }
                        if (profile.semester != null) {
                            DetailRow(Icons.Filled.DateRange, "Semester", "Semester ${profile.semester}")
                        }
                    }
                }

                // Sprint 9: missed-subject popup — rows EXPAND to show which
                // occurrences were missed (like the lecturer's record); each missed
                // occurrence can submit its own letter. A submitted letter flips
                // its row to amber "Pending" until the lecturer approves.
                val attendanceForPopup = uiState.attendance
                val classesForPopup by profileViewModel.classes.collectAsState()
                if (showMissedSubjects && attendanceForPopup != null) {
                    MissedSubjectsPopup(
                        subjects = attendanceForPopup.subjects,
                        classes = classesForPopup,
                        recordRowsFor = profileViewModel::recordRowsFor,
                        isUploading = uiState.letterUploading,
                        error = uiState.letterError,
                        submitted = uiState.letterSubmitted,
                        onSubmit = { subject ->
                            profileViewModel.clearLetterStates()
                            pendingLetterSubject = subject
                            letterPickerLauncher.launch("application/pdf")
                        },
                        onDismiss = {
                            showMissedSubjects = false
                            profileViewModel.clearLetterStates()
                        }
                    )
                }

                Spacer(Modifier.height(24.dp))
                // Academic overview (wireframe): stats + enrolled sections + letters.
                val attendance = uiState.attendance
                if (attendance != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatTile(
                            count = attendance.attended.toString(),
                            label = "ATTENDED",
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            onClick = null,
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            count = attendance.missed.toString(),
                            label = "MISSED",
                            containerColor = MaterialTheme.colorScheme.error,
                            onClick = if (attendance.subjects.isNotEmpty()) { { showMissedSubjects = true } } else null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Academic overview",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Your current sections and official warning letters",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OverviewCard(
                        title = "Enrolled sections",
                        highlight = "${EnrolledSectionsData.ITEMS.size} sections",
                        subtitle = "Mentor: ${EnrolledSectionsData.MENTOR_NAME}",
                        onClick = { navController.navigate(MyUptmRoutes.ENROLLED_SECTIONS) }
                    )
                    Spacer(Modifier.height(10.dp))
                    OverviewCard(
                        title = "Warning letters",
                        subtitle = "Review warning letters officially issued and verified for you.",
                        onClick = { navController.navigate(MyUptmRoutes.WARNING_LETTERS) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleChip(role: UserRole) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = role.name.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// Academic overview row card (wireframe): icon tile, title, optional bold
// highlight/subtitle, trailing arrow, whole card clickable.
@Composable
private fun OverviewCard(
    title: String,
    subtitle: String,
    highlight: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                highlight?.let { hl ->
                    Text(
                        hl,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Sprint 9: attendance statistic tile (the big colored number boxes).
@Composable
private fun StatTile(
    count: String,
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val clickable = onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier
    Box(
        modifier = modifier
            .height(84.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .then(clickable),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                count,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.surface
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
            )
        }
    }
}

// Sprint 9: Material 3 missed-classes menu. Subject cards expand into their
// session records (date chip + weekday/venue + tonal status pill); MISSED rows can
// each submit a letter, and a submitted letter flips its row to amber "Pending"
// until the lecturer approves or declines it.
@Composable
private fun MissedSubjectsPopup(
    subjects: List<SubjectAttendance>,
    classes: List<com.myuptm.domain.model.ClassSession>,
    recordRowsFor: (String, List<com.myuptm.domain.model.ClassSession>) -> List<com.myuptm.viewmodel.ProfileViewModel.MissedRecordRow>,
    isUploading: Boolean,
    error: String?,
    submitted: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var expandedSubject by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Missed classes",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Tap a subject to see which sessions need a letter",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                if (subjects.isEmpty()) {
                    Text(
                        "Nothing outstanding — perfect.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                subjects.forEach { info ->
                    val expanded = expandedSubject == info.subject
                    SubjectCard(
                        info = info,
                        expanded = expanded,
                        rows = if (expanded) recordRowsFor(info.subject, classes) else emptyList(),
                        isUploading = isUploading,
                        onToggle = {
                            expandedSubject = if (expanded) null else info.subject
                        },
                        onSubmit = { onSubmit(info.subject) }
                    )
                    Spacer(Modifier.height(10.dp))
                }

                if (isUploading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Uploading letter...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                error?.let { err ->
                    Text(
                        "Error: $err",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (submitted && !isUploading) {
                    Text(
                        "Letter submitted — pending lecturer review",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Close") }
            }
        }
    }
}

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
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

            androidx.compose.animation.AnimatedVisibility(
                visible = expanded,
                enter = androidx.compose.animation.expandVertically() +
                    androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() +
                    androidx.compose.animation.fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
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
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
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

@Composable
private fun TonalPill(text: String, container: Color, content: Color) {
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
            fontWeight = FontWeight.SemiBold
        )
    }
}
