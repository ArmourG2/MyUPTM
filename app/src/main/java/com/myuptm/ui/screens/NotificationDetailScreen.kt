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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myuptm.data.repository.EnrolledSectionsData
import com.myuptm.viewmodel.AttendanceViewModel
import com.myuptm.viewmodel.ClassManagementViewModel
import com.myuptm.viewmodel.InboxViewModel
import com.myuptm.viewmodel.notificationTier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Sprint 9: notification pages implemented from the wireframes —
//  1) tapping a notification opens the post-detail-style page (title + date,
//     attachment box, message context, "by: Lecturer Name"); a WARNING letter gets
//     the "Submit Letter" action (upload PDF → Cloudinary → Firestore, same flow
//     the Attendance screen used — that flow now lives here + Profile).
//  2) Warning Letters list: officially issued letters for the signed-in student.
//  3) Enrolled Sections: the student's current sections (Academic Overview).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationDetailScreen(
    notifId: String,
    canReview: Boolean = false,
    onBackClick: () -> Unit
) {
    // Same activity-scoped instance the bell/listen chain uses — no extra listener.
    val inboxViewModel: InboxViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as ViewModelStoreOwner
    )
    // Content comes from the RAW docs (the inbox list auto-hides opened ones).
    val rawDocs by inboxViewModel.rawList.collectAsState()
    val notification = rawDocs.find { it.id == notifId }
    val context = LocalContext.current

    // Sprint 9: staff quick-review — the "Absence letter submitted" notification
    // carries the letter id; Approve/Decline happen right here (no roster hunt).
    val classViewModel: ClassManagementViewModel = viewModel()
    val reviewLetters by classViewModel.letters.collectAsState()
    val reviewStatus by classViewModel.status.collectAsState()
    val isSubmission = notification?.title?.startsWith("Absence letter submitted") == true
    val reviewLetter = if (isSubmission) {
        notification?.postId?.let { id -> reviewLetters.find { it.id == id } }
    } else null
    var showDeclineDialog by remember { mutableStateOf(false) }
    var declineReason by remember { mutableStateOf("") }

    // Staff: opening a submission consumes it (it was their to-do).
    androidx.compose.runtime.LaunchedEffect(notifId, isSubmission) {
        if (isSubmission) inboxViewModel.markRead(notifId)
    }

    // Opened NORMAL notifications auto-disappear from the inbox (per-account).
    // RED (warning) stays pinned until upload; YELLOW stays until confirmed swipe.
    androidx.compose.runtime.LaunchedEffect(notifId, notification) {
        if (notification?.notificationTier() == com.myuptm.viewmodel.NotificationTier.NORMAL) {
            inboxViewModel.markRead(notifId)
        }
    }

    // The absence-letter upload flow (was Attendance) — reused verbatim.
    val attendanceViewModel: AttendanceViewModel = viewModel()
    val attendanceState by attendanceViewModel.uiState.collectAsState()
    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Sprint 9: bind the letter to the SUBJECT the warning letter is about
            // (parsed from the "Class: <displayCode>" line of the message).
            val subject = notification?.message
                ?.lineSequence()
                ?.firstOrNull { it.startsWith("Class:", ignoreCase = true) }
                ?.substringAfter("Class:")
                ?.trim()
                ?.substringBeforeLast("_")
            attendanceViewModel.submitAbsenceLetter(it, subjectName = subject)
        }
    }

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        // Campus-photo hero banner (design language from sign-in).
        com.myuptm.ui.components.CampusHeroBackdrop()
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                com.myuptm.ui.components.CampusHeroHeader(
                    title = "Notifications",
                    navigationIcon = {
                        com.myuptm.ui.components.CampusBackButton(onBackClick)
                    }
                )
            }
        ) { innerPadding ->
        if (notification == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Notification not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title row — date on the right (wireframe).
                // ---- Hero: image with scrim title, or a tonal band (Material You
                // article anatomy shared with the post detail page).
                if (notification.imageUrl != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(28.dp))
                    ) {
                        coil3.compose.AsyncImage(
                            model = coil3.request.ImageRequest.Builder(context)
                                .data(notification.imageUrl)
                                .build(),
                            contentDescription = "Notification image",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        1f to Color.Black.copy(alpha = 0.75f)
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            Text(
                                notification.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                formatDate(notification.createdAtEpochMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                notification.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                formatDate(notification.createdAtEpochMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                // Optional PDF attachment chip (warning letters etc.).
                if (notification.attachmentUrl != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(notification.attachmentUrl)
                                        )
                                    )
                                }
                            }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                notification.attachmentName ?: "Attached document",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                "Tap to open the attached PDF",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // ---- Meta strip: who sent it.
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "by: ${notification.senderName}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // ---- Context body inside an article card (label dropped — the
                // examiner called the bare layout wordy; the message speaks for
                // itself inside a proper surface).
                if (notification.message.isNotBlank()) {
                    androidx.compose.material3.Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            notification.message,
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }

                Text(
                    "by: ${notification.senderName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Warning letter → the extra-proof submission (same upload workflow).
                val isWarningLetter =
                    notification.title.startsWith("Warning letter:", ignoreCase = true)
                if (isWarningLetter) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { pdfPicker.launch("application/pdf") },
                        enabled = !attendanceState.isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (attendanceState.isSubmitting) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Uploading...")
                        } else {
                            Text("Submit Letter")
                        }
                    }
                    attendanceState.submitError?.let { error ->
                        Text(
                            "Error: $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (attendanceState.submitSuccess) {
                        Text(
                            "Letter submitted — pending lecturer review",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                // Sprint 9: staff review section for "Absence letter submitted" —
                // Approve/Decline right here; no trip to Class Management needed.
                if (isSubmission && canReview && reviewLetter != null) {
                    val letter = reviewLetter
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                letter.fileName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Submitted ${formatDate(letter.submittedAt)} · Status: " +
                                    letter.status.name.lowercase()
                                    .replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(letter.fileUrl)
                                            )
                                        )
                                    }
                                }
                            ) { Text("Open letter PDF") }
                            if (letter.status == com.myuptm.domain.model.LetterStatus.PENDING) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { classViewModel.approveLetter(letter) },
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = androidx.compose.ui.graphics.Color(0xFF2E7D32)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Approve", color = Color.White)
                                    }
                                    Button(
                                        onClick = { showDeclineDialog = true },
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = androidx.compose.ui.graphics.Color(0xFFC62828)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Decline", color = Color.White)
                                    }
                                }
                            } else if (letter.status == com.myuptm.domain.model.LetterStatus.DECLINED &&
                                letter.declineReason != null
                            ) {
                                Text(
                                    "Reason: ${letter.declineReason}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            reviewStatus?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    }

    // Decline-with-reason (mirrors the Class Management review dialog).
    if (showDeclineDialog && reviewLetter != null) {
        AlertDialog(
            onDismissRequest = { showDeclineDialog = false },
            title = { Text("Rejection Detail") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = declineReason,
                    onValueChange = { declineReason = it },
                    label = { Text("Reason") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (declineReason.isNotBlank()) {
                            classViewModel.declineLetter(reviewLetter, declineReason.trim())
                            declineReason = ""
                            showDeclineDialog = false
                        }
                    }
                ) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeclineDialog = false
                    declineReason = ""
                }) { Text("Cancel") }
            }
        )
    }
}

// ---- Warning Letters (student) ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarningLettersScreen(onOpenDetail: (String) -> Unit, onBackClick: () -> Unit) {
    val inboxViewModel: InboxViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as ViewModelStoreOwner
    )
    // Records view: uses RAW docs so opening one letter never hides it here.
    val rawDocs by inboxViewModel.rawList.collectAsState()
    val warnings = rawDocs.filter {
        it.title.startsWith("Warning letter:", ignoreCase = true) ||
            it.title.contains("warning", ignoreCase = true)
    }

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        // Campus-photo hero banner (design language from sign-in).
        com.myuptm.ui.components.CampusHeroBackdrop()
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                com.myuptm.ui.components.CampusHeroHeader(
                    title = "Warning Letters",
                    navigationIcon = {
                        com.myuptm.ui.components.CampusBackButton(onBackClick)
                    }
                )
            }
        ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 16.dp)
        ) {
            Text(
                "Warning letters officially issued and verified for your viewing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            if (warnings.isEmpty()) {
                Text(
                    "No warning letters.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            warnings.forEachIndexed { index, warning ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { onOpenDetail(warning.id) }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "WL ${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            warning.title.removePrefix("Warning letter: "),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            warning.message.lineSequence().firstOrNull().orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Issued ${formatDate(warning.createdAtEpochMs)} · ${warning.senderName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    }
}

// ---- Enrolled Sections (student, Academic Overview) ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnrolledSectionsScreen(onBackClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        // Campus-photo hero banner (design language from sign-in).
        com.myuptm.ui.components.CampusHeroBackdrop()
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                com.myuptm.ui.components.CampusHeroHeader(
                    title = "Academic Overview",
                    navigationIcon = {
                        com.myuptm.ui.components.CampusBackButton(onBackClick)
                    }
                )
            }
        ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 16.dp)
        ) {
            Text(
                "${EnrolledSectionsData.ITEMS.size} enrolled sections",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            EnrolledSectionsData.ITEMS.forEach { section ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                section.subjectCode.take(3),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    section.subjectCode,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.weight(1f))
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        "${section.hours} HOURS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                section.subjectTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                section.sectionLabel,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                section.programme,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(epochMs))
