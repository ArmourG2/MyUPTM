package com.myuptm.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myuptm.R
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.myuptm.domain.model.Post
import com.myuptm.domain.model.UserRole
import com.myuptm.navigation.MyUptmRoutes
import com.myuptm.ui.components.AnnouncementRow
import com.myuptm.viewmodel.AdminDashboardViewModel
import com.myuptm.viewmodel.InboxViewModel

// Sprint 11: the admin's FIRST screen (Home was removed for this role — its
// anatomy is timetable-tied and admins don't teach). A deliberately simple POC
// dashboard: overview tiles, the pending-UPTM-email follow-up list, and the
// live announcements feed (read-only; post taps deep-link to the detail page).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    navController: NavController,
    inboxViewModel: InboxViewModel
) {
    val dashboardViewModel: AdminDashboardViewModel = viewModel()
    val state by dashboardViewModel.state.collectAsState()
    val posts by dashboardViewModel.posts.collectAsState()
    val approvalBusy by dashboardViewModel.approvalBusy.collectAsState()
    val status by dashboardViewModel.status.collectAsState()

    // Sprint 13: role-assignment dialog state (the account being reassigned).
    var pendingDialog by remember { mutableStateOf<AdminDashboardViewModel.PendingAccount?>(null) }

    // Sprint 13C: tile directory dialogs.
    var showStudentsDialog by remember { mutableStateOf(false) }
    var showLecturersDialog by remember { mutableStateOf(false) }
    var showClassesDialog by remember { mutableStateOf(false) }
    val studentsDirectory by dashboardViewModel.students.collectAsState()
    val lecturersDirectory by dashboardViewModel.lecturers.collectAsState()
    val classDirectory by dashboardViewModel.classRows.collectAsState()

    // Same bell behaviour the student/lecturer Home screen has — the inbox is
    // activity-scoped, so the unread dot + deep-link routing stay consistent.
    val inbox by inboxViewModel.inbox.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Admin Dashboard") },
            actions = {
                IconButton(onClick = { navController.navigate(MyUptmRoutes.NOTIFICATIONS) }) {
                    BadgedBox(
                        badge = {
                            if (inbox.unread > 0) {
                                Badge(containerColor = androidx.compose.ui.graphics.Color(0xFFC62828))
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Notifications, contentDescription = "Notifications")
                    }
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- Overview tiles ----
            // Sprint 12: vector drawables instead of material-icons-extended
            // (the extended library costs ~35 MB of dex for 6 icons).
            // Sprint 13C: Students/Lecturers/Classes tiles open their directory.
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        icon = ImageVector.vectorResource(R.drawable.ic_people),
                        label = "Students",
                        value = "${if (studentsDirectory.isNotEmpty()) studentsDirectory.size else state.studentCount}",
                        note = "Tap to view the roster list",
                        modifier = Modifier.weight(1f),
                        onClick = { showStudentsDialog = true }
                    )
                    StatTile(
                        icon = ImageVector.vectorResource(R.drawable.ic_badge),
                        label = "Lecturers",
                        value = "${state.lecturerCount}",
                        note = "Tap to view lecturer accounts",
                        modifier = Modifier.weight(1f),
                        onClick = { showLecturersDialog = true }
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        icon = ImageVector.vectorResource(R.drawable.ic_class),
                        label = "Classes",
                        value = "${state.classCount}",
                        note = "Tap to view the class list",
                        modifier = Modifier.weight(1f),
                        onClick = { showClassesDialog = true }
                    )
                    StatTile(
                        icon = ImageVector.vectorResource(R.drawable.ic_mail),
                        label = "Pending",
                        value = "${state.pendingAccounts.size}",
                        note = "Unassigned UPTM emails",
                        modifier = Modifier.weight(1f),
                        highlight = state.pendingAccounts.isNotEmpty()
                    )
                }
            }

            // ---- Pending UPTM emails (not yet assigned lecturer/student/admin) ----
            item {
                Text(
                    "Pending UPTM email assignment",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (state.pendingAccounts.isEmpty()) {
                item {
                    Text(
                        "No pending accounts — every UPTM email has a role.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(state.pendingAccounts, key = { it.uid }) { account ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    account.email,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    account.role?.replaceFirstChar { it.uppercase() }
                                        ?: "Role not yet assigned",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            OutlinedButton(
                                onClick = { pendingDialog = account },
                                enabled = !approvalBusy
                            ) {
                                Text(if (account.role == null) "Assign role" else "Reassign")
                            }
                        }
                    }
                }
            }

            // ---- Announcements (live feed, read-only) ----
            item {
                Text(
                    "Announcements",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (posts.isEmpty()) {
                item {
                    Text(
                        "No announcements yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(posts.take(5), key = { it.id }) { post ->
                    AnnouncementRow(
                        post = post,
                        onClick = { navController.navigate(MyUptmRoutes.postDetail(post.id)) }
                    )
                }
            }
        }
    }

    // Sprint 13C: role-assignment dialog — pick a role; students also get
    // faculty/programme/semester + auto-derived student ID.
    pendingDialog?.let { dialogAccount ->
        var chosenRole by remember(dialogAccount.uid) { mutableStateOf(UserRole.STUDENT) }
        var chosenAdminPrivilege by remember(dialogAccount.uid) { mutableStateOf(false) }
        // Student fields — faculty dropdown, programme, semester (default 6),
        // student ID auto-derived from the UPTM email (kl+digits → AM+digits).
        var chosenFaculty by remember(dialogAccount.uid) {
            mutableStateOf(AdminDashboardViewModel.FACULTY_OPTIONS[0])
        }
        var programme by remember(dialogAccount.uid) { mutableStateOf("") }
        var chosenSemester by remember(dialogAccount.uid) {
            mutableStateOf(AdminDashboardViewModel.DEFAULT_SEMESTER)
        }
        var studentId by remember(dialogAccount.uid) {
            mutableStateOf(dashboardViewModel.deriveStudentId(dialogAccount.email))
        }
        AlertDialog(
            onDismissRequest = { pendingDialog = null },
            title = { Text("Assign role", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Text(
                        dialogAccount.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    RoleRadioRow("Student", UserRole.STUDENT, chosenRole) { chosenRole = it }
                    RoleRadioRow("Lecturer", UserRole.LECTURER, chosenRole) { chosenRole = it }
                    RoleRadioRow("Admin", UserRole.ADMIN, chosenRole) { chosenRole = it }
                    Spacer(Modifier.height(12.dp))
                    if (chosenRole != UserRole.ADMIN) {
                        LabeledDropdown(
                            label = "Faculty",
                            options = AdminDashboardViewModel.FACULTY_OPTIONS,
                            selected = chosenFaculty,
                            onSelect = { chosenFaculty = it }
                        )
                    }
                    if (chosenRole == UserRole.STUDENT) {
                        OutlinedTextField(
                            value = programme,
                            onValueChange = { programme = it },
                            label = { Text("Programme") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        LabeledDropdown(
                            label = "Semester (defaults to 6)",
                            options = AdminDashboardViewModel.SEMESTER_OPTIONS.map { "Semester $it" },
                            selectedIndex = AdminDashboardViewModel.SEMESTER_OPTIONS.indexOf(chosenSemester),
                            onSelectIndex = { chosenSemester = AdminDashboardViewModel.SEMESTER_OPTIONS[it] }
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = studentId,
                            onValueChange = { studentId = it },
                            label = { Text("Student ID (auto-filled from the email)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Switch(checked = chosenAdminPrivilege, onCheckedChange = { chosenAdminPrivilege = it })
                        Text(
                            "Admin privilege (notify composer)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    status?.let { line ->
                        Text(line, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !approvalBusy,
                    onClick = {
                        dashboardViewModel.approveAccount(
                            uid = dialogAccount.uid,
                            role = chosenRole,
                            // Admins always carry the privilege.
                            adminPrivilege = chosenAdminPrivilege || chosenRole == UserRole.ADMIN,
                            faculty = if (chosenRole == UserRole.ADMIN) null else chosenFaculty,
                            programme = if (chosenRole == UserRole.STUDENT) programme else null,
                            semester = if (chosenRole == UserRole.STUDENT) chosenSemester.toInt() else null,
                            studentId = if (chosenRole == UserRole.STUDENT) studentId else null
                        )
                        pendingDialog = null
                    }
                ) { Text(if (approvalBusy) "Approving..." else "Approve") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDialog = null }) { Text("Cancel") }
            }
        )
    }

    // Sprint 13C: the three tile directories.
    if (showStudentsDialog) {
        SimpleDirectoryDialog(
            title = "Students (${studentsDirectory.size})",
            headline = { "${it.name};" },
            subline = { "${it.studentId} · ${it.faculty}" },
            rows = studentsDirectory,
            onDismiss = { showStudentsDialog = false }
        )
    }
    if (showLecturersDialog) {
        SimpleDirectoryDialog(
            title = "Lecturers (${lecturersDirectory.size})",
            headline = { "${it.name};" },
            subline = { "${it.email} · ${it.classCount} class(es)" },
            rows = lecturersDirectory,
            onDismiss = { showLecturersDialog = false }
        )
    }
    if (showClassesDialog) {
        SimpleDirectoryDialog(
            title = "Classes (${classDirectory.size})",
            headline = { "${it.code};" },
            subline = { it.name },
            rows = classDirectory,
            onDismiss = { showClassesDialog = false }
        )
    }
}

@Composable
private fun RoleRadioRow(
    label: String,
    role: UserRole,
    chosenRole: UserRole,
    onChosen: (UserRole) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(
            selected = chosenRole == role,
            onClick = { onChosen(role) }
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

// Sprint 13C: compact ExposedDropdown for the approval dialog forms.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

// String-option overload bridging the semester number dropdown.
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LabeledDropdown(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = if (selectedIndex >= 0) options[selectedIndex] else "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelectIndex(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

// Sprint 13C: scrollable directory for the tile lists (Students/Lecturers/Classes).
@Composable
private fun <T> SimpleDirectoryDialog(
    title: String,
    rows: List<T>,
    headline: (T) -> String,
    subline: (T) -> String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (rows.isEmpty()) {
                    item {
                        Text(
                            "Nothing here yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(rows.size) { index ->
                    val row = rows[index]
                    Column {
                        Text(headline(row), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            subline(row),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    note: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    // Sprint 13C: fires the tile's directory dialog.
    onClick: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = modifier.clickable(enabled = onClick != null) { onClick?.invoke() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(22.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
