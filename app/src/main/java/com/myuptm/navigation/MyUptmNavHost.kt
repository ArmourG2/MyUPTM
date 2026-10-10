package com.myuptm.navigation

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.myuptm.navigation.MyUptmRoutes.HOME
import com.myuptm.navigation.MyUptmRoutes.SIGN_IN
import com.myuptm.domain.model.toPermissions
import com.myuptm.ui.screens.AddPostScreen
import com.myuptm.ui.screens.AdminDashboardScreen
import com.myuptm.ui.screens.AttendanceRecordScreen
import com.myuptm.ui.screens.AttendanceScreen
import com.myuptm.ui.screens.ClassManagementScreen
import com.myuptm.ui.screens.EnrolledSectionsScreen
import com.myuptm.ui.screens.HomeScreen
import com.myuptm.ui.screens.NotificationDetailScreen
import com.myuptm.ui.screens.NotificationsScreen
import com.myuptm.ui.screens.WarningLettersScreen
import com.myuptm.ui.screens.MissedClassesScreen
import com.myuptm.ui.screens.PostDetailScreen
import com.myuptm.ui.screens.PostsScreen
import com.myuptm.ui.screens.ProfileScreen
import com.myuptm.ui.screens.SettingsScreen
import com.myuptm.ui.screens.SignInScreen
import com.myuptm.ui.screens.TimetableScreen
import com.myuptm.ui.screens.HelpScreen
import com.myuptm.viewmodel.ClassManagementViewModel
import com.myuptm.data.repository.FirestoreAbsenceLetterRepository
import com.myuptm.data.repository.FirestoreProfileRepository
import com.myuptm.data.repository.RealCloudinaryRepository
import com.myuptm.viewmodel.AuthState
import com.myuptm.viewmodel.AuthViewModel
import com.myuptm.viewmodel.PostsViewModel
import com.myuptm.viewmodel.ProfileViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import com.myuptm.ui.components.TourSession


/**
 * Single source of truth for all destinations.
 * Sprint 7B: role/profile gating now flows from AuthViewModel.userRecord (AppUser),
 * permission decisions live in UserPermissions. The duplicate POSTS registration
 * from 7A is removed.
 */
@Composable
fun MyUptmNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val errorMessage by authViewModel.errorMessage.collectAsStateWithLifecycle()
    val userRecord by authViewModel.userRecord.collectAsStateWithLifecycle()

    // Sprint 9 (late): Profile + MissedClasses share ONE activity-scoped ProfileViewModel
    // (same default VM key + same store owner), so letter states and live stats stay
    // consistent across the navigation.
    val profileFactory = viewModelFactory {
        initializer {
            val app = this[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY]
                as android.content.Context
            ProfileViewModel(
                profileRepository = FirestoreProfileRepository(),
                cloudinaryRepository = RealCloudinaryRepository(app),
                lettersRepository = FirestoreAbsenceLetterRepository()
            )
        }
    }
    val sharedProfileViewModel: @Composable () -> ProfileViewModel = {
        viewModel(factory = profileFactory)
    }

    // Sprint 13C fix: the start destination is FROZEN at first composition —
    // flipping isLoggedIn mid-session (pending accounts now keep auth ALIVE)
    // used to re-evaluate this and retroactively jump to HOME on top of the
    // waiting screen (the friend's "fallback dashboard" bug). Landing is
    // decided by the ROLE at gate time; runtime navigation is driven by the
    // SUCCESS effect below.
    val startDestination = remember {
        if (authViewModel.userRole.value != null) HOME else SIGN_IN
    }

    LaunchedEffect(authState) {
        if (authState == AuthState.SUCCESS) {
            // Sprint 11: admins land on their Dashboard (they have no Home screen).
            val target = if (authViewModel.userRole.value == com.myuptm.domain.model.UserRole.ADMIN) {
                MyUptmRoutes.ADMIN_DASHBOARD
            } else HOME
            navController.navigate(target) {
                popUpTo(SIGN_IN) { inclusive = true }
            }
            authViewModel.resetState()
        }
    }

    // Sprint 11: cold-start redirect — an already signed-in admin briefly lands on
    // HOME (the start destination) until the role fetch resolves; swap it out so
    // Dashboard becomes the back-stack root (back = exit app, not Home).
    LaunchedEffect(userRecord) {
        val record = userRecord ?: return@LaunchedEffect
        if (record.role == com.myuptm.domain.model.UserRole.ADMIN &&
            navController.currentDestination?.route == HOME
        ) {
            navController.navigate(MyUptmRoutes.ADMIN_DASHBOARD) {
                popUpTo(HOME) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(SIGN_IN) {
            SignInScreen(
                onSignInClick = {
                    // ONLY trigger the login process here. Do NOT navigate yet.
                    val activity = navController.context as? Activity
                    if (activity != null) {
                        authViewModel.signIn(activity)
                    }
                },
                errorMessage = errorMessage, // Pass this to your SignInScreen to show errors
                isLoading = authState == AuthState.LOADING, // Pass this to show a spinner
                // Sprint 13: pending-account approval park
                isWaitingApproval = authState == AuthState.WAITING_APPROVAL,
                waitingEmail = authViewModel.pendingEmail.collectAsState().value,
                onCancelWait = { authViewModel.signOut() },
                // Sprint 14: help entry point on the login screen.
                onHelpClick = { navController.navigate(MyUptmRoutes.HELP) }
            )
        }
        composable(HOME) { HomeScreen(navController = navController, user = userRecord) }
        composable(MyUptmRoutes.TIMETABLE) {
            val record = userRecord
            if (record != null) {
                val unEnrolledStudent = record.role == com.myuptm.domain.model.UserRole.STUDENT &&
                    !com.myuptm.data.repository.MockAttendanceRoster
                        .isEnrolledDemoStudent(record.studentId, record.email)
                if (unEnrolledStudent) {
                    // Sprint 13C: fresh registrations have an EMPTY timetable —
                    // no demo board, no plans, nothing until the lecturer
                    // assigns them (same message as the Home dashboard card).
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        Text(
                            "Pending assignment by Lecturer",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "You are not assigned to any class yet. Your timetable appears once your lecturer assigns you.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                } else {
                    TimetableScreen(
                        user = record,
                        onOpenClassManagement = { navController.navigate(MyUptmRoutes.CLASS_MANAGEMENT) }
                    )
                }
            } else {
                LoadingBox()
            }
        }
        composable(MyUptmRoutes.ATTENDANCE) { AttendanceScreen() }
        composable(MyUptmRoutes.POSTS) {
            val record = userRecord
            if (record != null) {
                PostsScreen(
                    navController = navController,
                    user = record
                )
            } else {
                // Show this while the role is being fetched from Firestore
                LoadingBox()
            }
        }
        composable(MyUptmRoutes.PROFILE) {
            ProfileScreen(navController = navController, profileViewModel = sharedProfileViewModel())
        }

        composable(MyUptmRoutes.HELP) {
            com.myuptm.ui.screens.HelpScreen(
                user = userRecord,
                navController = navController,
                tourStarter = { tourRole: com.myuptm.domain.model.UserRole, startRoute: String ->
                    // Give the target screen a frame to compose (its tourTarget
                    // tags register on first layout), then run the tour over it.
                    com.myuptm.ui.components.TourSession.start(tourRole)
                    if (startRoute != MyUptmRoutes.HELP) {
                        navController.navigate(startRoute) {
                            popUpTo(MyUptmRoutes.HELP) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(MyUptmRoutes.MISSED_CLASSES) {
            MissedClassesScreen(
                viewModel = sharedProfileViewModel(),
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(MyUptmRoutes.ADMIN_DASHBOARD) {
            // The activity-scoped inbox (created in MyUptmApp) powers the bell.
            val inboxViewModel: com.myuptm.viewmodel.InboxViewModel = viewModel(
                viewModelStoreOwner = LocalContext.current as ViewModelStoreOwner
            )
            AdminDashboardScreen(
                navController = navController,
                inboxViewModel = inboxViewModel
            )
        }
        composable(MyUptmRoutes.CLASS_MANAGEMENT) {
            val record = userRecord
            when {
                record == null -> LoadingBox()
                record.toPermissions().canManageClassGlobal ->
                    ClassManagementScreen(
                        ownerEmail = record.email,
                        onOpenAttendanceRecord = { classId, matric ->
                            navController.navigate(
                                MyUptmRoutes.ATTENDANCE_RECORD
                                    .replace("{classId}", java.net.URLEncoder.encode(classId, "utf-8"))
                                    .replace("{matric}", java.net.URLEncoder.encode(matric, "utf-8"))
                            )
                        }
                    )
                else -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("You don't manage classes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        composable(MyUptmRoutes.NOTIFICATIONS) {
            NotificationsScreen(
                user = userRecord,
                onBackClick = { navController.popBackStack() },
                onOpenDetail = { id -> navController.navigate(MyUptmRoutes.notifDetail(id)) },
                onOpenPost = { id -> navController.navigate(MyUptmRoutes.postDetail(id)) }
            )
        }

        composable(
            route = MyUptmRoutes.NOTIF_DETAIL,
            arguments = listOf(navArgument("notifId") { type = NavType.StringType })
        ) { backStackEntry ->
            val notifId = backStackEntry.arguments?.getString("notifId").orEmpty()
            NotificationDetailScreen(
                notifId = notifId,
                canReview = userRecord?.toPermissions()?.canManageClassGlobal == true,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(MyUptmRoutes.ENROLLED_SECTIONS) {
            EnrolledSectionsScreen(onBackClick = { navController.popBackStack() })
        }

        composable(MyUptmRoutes.WARNING_LETTERS) {
            WarningLettersScreen(
                onOpenDetail = { id -> navController.navigate(MyUptmRoutes.notifDetail(id)) },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = MyUptmRoutes.ATTENDANCE_RECORD,
            arguments = listOf(
                navArgument("classId") { type = NavType.StringType },
                navArgument("matric") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            AttendanceRecordScreen(
                classId = backStackEntry.arguments?.getString("classId").orEmpty(),
                matric = backStackEntry.arguments?.getString("matric").orEmpty(),
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(MyUptmRoutes.SETTINGS) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onHelpClick = { navController.navigate(MyUptmRoutes.HELP) },
                onSignOut = {
                    authViewModel.signOut()
                    navController.navigate(SIGN_IN) {
                        // Sprint 11: pop the ENTIRE stack — admins have no HOME on it.
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(MyUptmRoutes.ADD_POST) {
            // Sprint 8 Task 4: authoring entry gated to Admin + Lecturer-with-Admin.
            val record = userRecord
            when {
                record == null -> LoadingBox()
                record.toPermissions().canAddPost -> {
                    val postsViewModel: PostsViewModel = viewModel(
                        viewModelStoreOwner = LocalContext.current as ViewModelStoreOwner
                    )
                    AddPostScreen(
                        authorEmail = record.email,
                        onBackClick = { navController.popBackStack() },
                        viewModel = postsViewModel
                    )
                }
                else -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("You can't add posts", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        composable(
            route = MyUptmRoutes.POST_DETAIL,
            arguments = listOf(navArgument("postId") { type = NavType.StringType })
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId").orEmpty()
            PostDetailScreen(
                postId = postId,
                canRemovePost = userRecord?.toPermissions()?.canRemovePost == true,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Loading profile...")
    }
}