package com.myuptm.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import com.myuptm.data.repository.FirestoreClassRepository
import com.myuptm.domain.model.AppUser
import com.myuptm.domain.model.SupportLinks
import com.myuptm.domain.model.UserRole
import com.myuptm.navigation.MyUptmRoutes
import com.myuptm.ui.components.AnnouncementRow
import com.myuptm.ui.components.NextClassCard
import com.myuptm.ui.components.SupportLinkTile
import com.myuptm.ui.components.openUrl
import com.myuptm.viewmodel.InboxViewModel
import com.myuptm.viewmodel.HomeViewModel
import com.myuptm.viewmodel.PostsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, user: AppUser? = null) {
    val context = LocalContext.current
    // Sprint 9: lecturers' Next Class counts only the classes THEY teach.
    val ownClassesEmail = user?.takeIf { it.role == UserRole.LECTURER }?.email
    val homeViewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                // Sprint 7B: classes come from Firestore (global); posts remain mock.
                HomeViewModel(
                    FirestoreClassRepository(),
                    com.myuptm.data.repository.FirestorePostsRepository(),
                    ownClassesOnlyEmail = ownClassesEmail
                )
            }
        }
    )
    val uiState by homeViewModel.uiState.collectAsState()

    // Sprint 9: red unread dot on the Home bell (activity-scoped inbox).
    val inboxViewModel: com.myuptm.viewmodel.InboxViewModel = viewModel(
        viewModelStoreOwner = context as androidx.lifecycle.ViewModelStoreOwner
    )
    val inbox by inboxViewModel.inbox.collectAsState()

    // Sprint 9: posts feed — resolves LEGACY post notifications (no postId) by
    // title match, so the bell can deep-link them to the post detail too.
    val postsViewModel: PostsViewModel = viewModel()
    val posts by postsViewModel.posts.collectAsState()

    // Sprint 7B: local notifications require POST_NOTIFICATIONS on Android 13+.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Denied -> in-app inbox still works */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Home") },
            actions = {
                IconButton(
                    // Sprint 9 bell routing:
                    //  1) an IMPORTANT post notification is ALWAYS opened (read or
                    //     unread — it's a pinned announcement, never consumed),
                    //  2) otherwise the newest UNREAD post pointer opens directly,
                    //  3) otherwise the inbox. Legacy docs (no postId) resolve by
                    //     title match against the posts feed.
                    onClick = {
                        val target = inbox.importantPost ?: inbox.unreadPost
                        val resolvedPostId = target?.postId
                            ?: target?.let { t -> posts.find { it.title == t.title }?.id }
                        if (target != null && resolvedPostId != null) {
                            navController.navigate(MyUptmRoutes.postDetail(resolvedPostId))
                            if (target != inbox.importantPost) {
                                inboxViewModel.markRead(target.id)
                            }
                        } else {
                            navController.navigate(MyUptmRoutes.NOTIFICATIONS)
                        }
                    }
                ) {
                    // Sprint 9: red unread dot — a targeted application-level alert
                    // on the bell that clears when the inbox is opened.
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
            // Sprint 13C: an un-enrolled STUDENT has no classes — instead of an
            // empty hole, Home shows a "Pending assignment by Lecturer" card.
            // (The bug this replaces: `user?.role != STUDENT` was true for a
            // null user, so the demo next-class card leaked to pending logins.)
            val demoEnrolled = when (user?.role) {
                UserRole.STUDENT -> com.myuptm.data.repository.MockAttendanceRoster.isEnrolledDemoStudent(
                    user.studentId, user.email
                )
                else -> true // lecturers/admins keep the existing behaviour
            }
            if (demoEnrolled) {
                item {
                    NextClassCard(session = uiState.nextClass, label = uiState.countdownLabel)
                }
            } else {
                item { PendingAssignmentCard() }
            }
            item { SectionHeader("Announcements") }
            items(uiState.announcements) { post ->
                AnnouncementRow(
                    post = post,
                    // Use the same helper call your PostsScreen already uses if named differently
                    onClick = { navController.navigate(MyUptmRoutes.postDetail(post.id)) }
                )
            }
            item { SectionHeader("Student Support") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(uiState.supportLinks) { link ->
                        SupportLinkTile(link = link, onClick = { context.openUrl(link.url) })
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { context.openUrl(SupportLinks.PORTAL_URL) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Student Portal")
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

// Sprint 13C: fresh registrations wait for the LECTURER/faculty head to assign
// them classes (out-of-scope step) — this card tells the student exactly that,
// so the Home slot never looks broken/empty.
@Composable
private fun PendingAssignmentCard() {
    androidx.compose.material3.Card(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(18.dp)) {
            Text(
                "Pending assignment by Lecturer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "You are not assigned to any class yet. Once your lecturer assigns you to a class, " +
                    "your schedule and attendance will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}