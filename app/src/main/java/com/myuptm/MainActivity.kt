package com.myuptm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.myuptm.domain.model.AppNotification
import com.myuptm.navigation.MyUptmNavHost
import com.myuptm.navigation.MyUptmRoutes
import com.myuptm.ui.components.BottomNavBar
import com.myuptm.ui.theme.MyUPTMTheme
import com.myuptm.viewmodel.AuthViewModel
import com.myuptm.viewmodel.InboxViewModel
import com.myuptm.viewmodel.SettingsViewModel

// Routes whose screens render the campus-photo hero header — they pad their
// own top inset, so the activity only reserves the bottom bar for them.
// (post_detail/* and notif_detail/* are matched by prefix at the call site.)
private val CampusHeroRoutes = setOf(
    MyUptmRoutes.HOME,
    MyUptmRoutes.TIMETABLE,
    MyUptmRoutes.ATTENDANCE,
    MyUptmRoutes.POSTS,
    MyUptmRoutes.PROFILE,
    MyUptmRoutes.HELP,
    MyUptmRoutes.SETTINGS,
    MyUptmRoutes.NOTIFICATIONS,
    MyUptmRoutes.MISSED_CLASSES,
    MyUptmRoutes.ENROLLED_SECTIONS,
    MyUptmRoutes.WARNING_LETTERS,
    MyUptmRoutes.ADD_POST
)

class MainActivity : ComponentActivity() {    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
            val settings by settingsViewModel.settings.collectAsState()

            MyUPTMTheme(
                themeMode = settings.themeMode,
                appFont = settings.appFont,
                themePalette = settings.themePalette
            ) {
                MyUptmApp()
            }
        }
    }
}

@Composable
fun MyUptmApp() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    val userRole by authViewModel.userRole.collectAsState()
    val userRecord by authViewModel.userRecord.collectAsState()
    // Sprint 13C: identity fetch gate (kills the bar-less Home flash on cold start).
    val identityReady by authViewModel.identityReady.collectAsState()
    // Sprint 13C: enrollment gate — roster-backed accounts see the demo board;
    // fresh registrations are empty by design.
    val enrolledStudent = userRecord?.let {
        com.myuptm.data.repository.MockAttendanceRoster.isEnrolledDemoStudent(it.studentId, it.email)
    } ?: false
    // Sprint 14 (owner ask): sticky student nav — the FIRST class visible for
    // their week upgrades the student to the 5-tab nav and it STAYS (a one-week
    // class expiring leaves Timetable/Attendance in place, timetable empty).
    val studentNavViewModel: com.myuptm.viewmodel.StudentNavViewModel = viewModel()
    val studentHasClasses by studentNavViewModel.hasClassThisWeek.collectAsState()
    val studentNavUpgraded by studentNavViewModel.navUpgraded.collectAsState()
    val studentFiveTabs = enrolledStudent || studentHasClasses || studentNavUpgraded
    androidx.compose.runtime.LaunchedEffect(userRecord) {
        studentNavViewModel.bindUser(userRecord)
    }
    // Sprint 9: one app-lifetime inbox listener (shade push + unread dot for all roles).
    val inboxViewModel: InboxViewModel = viewModel()
    val inboxState by inboxViewModel.inbox.collectAsState()
    androidx.compose.runtime.LaunchedEffect(userRecord) {
        inboxViewModel.bindUser(userRecord)
    }
    // Sprint 9: since there is no FCM push, opening the app with unread notifications
    // shows the wireframe popup card once (dismiss = mark seen).
    var popupDismissed by remember { mutableStateOf(false) }
    val showUnreadPopup = userRecord != null && !popupDismissed && inboxState.unread > 0
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // Sprint 8: keep the bar during transitions — route is briefly null while switching,
    // which previously made the bottom nav blink/disappear. SIGN_IN still hides it.
    val showBottomBar = userRole != null && (currentRoute == null || currentRoute != MyUptmRoutes.SIGN_IN)


    Scaffold(
        bottomBar = {
            if (showBottomBar && userRole != null) {
                BottomNavBar(
                    navController = navController,
                    userRole = userRole!!,
                    enrolledStudent = studentFiveTabs
                )
            }
        }
    ) { innerPadding ->
        if (authViewModel.isLoggedIn && !identityReady) {
            // Sprint 13C: identity gate — nothing renders until the role fetch
            // settles, so there is never a "content without the nav bar" frame.
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Campus-hero routes draw their photo header under the status bar
            // (each screen pads its own top content); only the bottom-bar
            // padding is kept here. Sign-in is fully full-bleed. Admin /
            // lecturer-only screens keep the original safe-area padding.
            val heroRoute = currentRoute != null && (
                currentRoute in CampusHeroRoutes ||
                    currentRoute.startsWith("post_detail/") ||
                    currentRoute.startsWith("notif_detail/")
                )
            MyUptmNavHost(
                navController = navController,
                modifier = when {
                    currentRoute == MyUptmRoutes.SIGN_IN -> Modifier
                    heroRoute -> Modifier.padding(
                        PaddingValues(bottom = innerPadding.calculateBottomPadding())
                    )
                    else -> Modifier.padding(innerPadding)
                }
            )
        }
    }

    // Sprint 14: the tour host renders OVER everything (Help page "Tour" runs it
    // against the tags registered by the visible screen).
    // Sprint 14 fix: the tour now MOVES between screens — the host needs a way
    // to navigate, so bind the NavController here once.
    androidx.compose.runtime.SideEffect {
        com.myuptm.ui.components.TourSession.navigator = { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                launchSingleTop = true
                restoreState = false
            }
        }
    }
    com.myuptm.ui.components.TourOverlayHost()

    if (showUnreadPopup) {
        UnreadNotificationPopup(
            latest = inboxState.unreadPost ?: inboxState.list.maxByOrNull { it.createdAtEpochMs },
            unreadCount = inboxState.unread,
            isPost = inboxState.unreadPost != null,
            onView = {
                popupDismissed = true
                // Deep-link: post pointers open the post itself.
                val target = inboxState.unreadPost
                if (target?.postId != null) {
                    inboxViewModel.markRead(target.id)
                    navController.navigate(MyUptmRoutes.postDetail(target.postId ?: ""))
                } else {
                    navController.navigate(MyUptmRoutes.NOTIFICATIONS)
                }
            },
            onDismiss = {
                // Popup dismiss clears yellow+blue (red stays pinned — upload rules).
                popupDismissed = true
                inboxViewModel.clearUpdates()
            }
        )
    }
}

// Sprint 9 wireframe: floating notification card — circle + title + text,
// red bar on the right edge. Shown once per app open when unread exist.
@Composable
private fun UnreadNotificationPopup(
    latest: AppNotification?,
    unreadCount: Int,
    isPost: Boolean,
    onView: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(20.dp)
                ) {
                    Text(
                        "Notification · $unreadCount unread",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                (latest?.senderName).orEmpty().split(" ")
                                    .take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }
                                    .joinToString(""),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            latest?.title ?: "You have notifications",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        latest?.message?.takeIf { it.isNotBlank() }
                            ?: "$unreadCount new notification(s) in your inbox.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4
                    )
                    Spacer(Modifier.height(18.dp))
                    // Bottom-right CTA buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("Dismiss") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onView) {
                            Text(if (isPost) "Open post" else "Open inbox")
                        }
                    }
                }
                // Right-edge red bar (wireframe).
                Box(
                    Modifier
                        .width(10.dp)
                        .height(IntrinsicSize.Max)
                        .background(androidx.compose.ui.graphics.Color(0xFFC62828))
                )
            }
        }
    }
}
