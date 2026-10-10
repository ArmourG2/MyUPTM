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
import androidx.compose.material.icons.filled.AccountBox
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.myuptm.data.repository.EnrolledSectionsData
import com.myuptm.data.repository.FirestoreAbsenceLetterRepository
import com.myuptm.data.repository.FirestoreProfileRepository
import com.myuptm.data.repository.RealCloudinaryRepository
import com.myuptm.domain.model.UserRole
import com.myuptm.navigation.MyUptmRoutes
import com.myuptm.viewmodel.ProfileViewModel

@Composable
fun ProfileScreen(navController: NavController, profileViewModel: ProfileViewModel) {
    val uiState by profileViewModel.uiState.collectAsState()
    val profile = uiState.userProfile
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { profileViewModel.uploadAvatar(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Campus-photo hero banner (design language from sign-in).
        com.myuptm.ui.components.CampusHeroBackdrop()
        Column(modifier = Modifier.fillMaxSize()) {
            com.myuptm.ui.components.CampusHeroHeader(
                title = "Profile",
                subtitle = "Account & academic overview",
                actions = {
                    IconButton(onClick = { navController.navigate(MyUptmRoutes.SETTINGS) }) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }
            )

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

                // Sprint 9 (late): the missed-classes overview is a DEDICATED PAGE
                // (MISSED_CLASSES) — the popup re-measured badly after the system
                // file picker closed over the Dialog window. See MissedClassesScreen.
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
                            onClick = if (attendance.subjects.isNotEmpty()) {
                                { navController.navigate(MyUptmRoutes.MISSED_CLASSES) }
                            } else null,
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
                Spacer(Modifier.height(32.dp))
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
