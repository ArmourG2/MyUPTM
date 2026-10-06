package com.myuptm.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        icon = ImageVector.vectorResource(R.drawable.ic_people),
                        label = "Students",
                        value = "${state.studentCount}",
                        note = "POC roster figure",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        icon = ImageVector.vectorResource(R.drawable.ic_badge),
                        label = "Lecturers",
                        value = "${state.lecturerCount}",
                        note = "Live accounts",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        icon = ImageVector.vectorResource(R.drawable.ic_class),
                        label = "Classes",
                        value = "${state.classCount}",
                        note = "All programmes",
                        modifier = Modifier.weight(1f)
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

    // Sprint 13: role-assignment dialog — pick a role (+ optional admin
    // privilege) and write it; every client watching that doc resumes itself.
    pendingDialog?.let { dialogAccount ->
        var chosenRole by remember(dialogAccount.uid) {
            mutableStateOf(UserRole.STUDENT)
        }
        var chosenAdminPrivilege by remember(dialogAccount.uid) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { pendingDialog = null },
            title = {
                Text("Assign role", style = MaterialTheme.typography.titleLarge)
            },
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
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Switch(checked = chosenAdminPrivilege, onCheckedChange = { chosenAdminPrivilege = it })
                        Text(
                            "Admin privilege (notify / manual notify composer)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
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
                            adminPrivilege = chosenAdminPrivilege or (chosenRole == UserRole.ADMIN)
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

@Composable
private fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    note: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = modifier
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
