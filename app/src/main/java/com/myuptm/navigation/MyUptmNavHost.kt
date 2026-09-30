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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.myuptm.navigation.MyUptmRoutes.HOME
import com.myuptm.navigation.MyUptmRoutes.SIGN_IN
import com.myuptm.ui.screens.AttendanceScreen
import com.myuptm.ui.screens.HomeScreen
import com.myuptm.ui.screens.PostDetailScreen
import com.myuptm.ui.screens.PostsScreen
import com.myuptm.ui.screens.ProfileScreen
import com.myuptm.ui.screens.SettingsScreen
import com.myuptm.ui.screens.SignInScreen
import com.myuptm.ui.screens.TimetableScreen
import com.myuptm.viewmodel.AuthState
import com.myuptm.viewmodel.AuthViewModel


/**
 * Single source of truth for all destinations.
 * Sprint 1: Sign-In shell -> 5 placeholder tabs.
 */
@Composable
fun MyUptmNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val errorMessage by authViewModel.errorMessage.collectAsStateWithLifecycle()

    val startDestination = if (authViewModel.isLoggedIn) HOME else SIGN_IN

    LaunchedEffect(authState) {
        if (authState == AuthState.SUCCESS) {
            navController.navigate(HOME) {
                popUpTo(SIGN_IN) { inclusive = true }
            }
            authViewModel.resetState()
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
                isLoading = authState == AuthState.LOADING // Pass this to show a spinner
            )
        }
        composable(HOME) { HomeScreen(navController = navController) }
        composable(MyUptmRoutes.TIMETABLE) { TimetableScreen() }
        composable(MyUptmRoutes.ATTENDANCE) { AttendanceScreen() }
        composable(MyUptmRoutes.POSTS) {
            val authViewModel: AuthViewModel = viewModel()
            val userRole by authViewModel.userRole.collectAsState()

            if (userRole != null) {
                PostsScreen(
                    navController = navController,
                    userRole = userRole!!
                )
            }
        }
        composable(MyUptmRoutes.PROFILE) { ProfileScreen(navController = navController) }

        composable(MyUptmRoutes.ADMIN_DASHBOARD) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("Admin Dashboard", style = MaterialTheme.typography.headlineMedium)
                Text("Welcome, Admin. Placeholder for dashboard stats.", style = MaterialTheme.typography.bodyLarge)
            }
        }
        composable(MyUptmRoutes.CLASS_MANAGEMENT) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("Class Management", style = MaterialTheme.typography.headlineMedium)
                Text("Placeholder for managing classes.", style = MaterialTheme.typography.bodyLarge)
            }
        }

        composable(MyUptmRoutes.SETTINGS) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onSignOut = {
                    authViewModel.signOut()
                    navController.navigate(SIGN_IN) {
                        popUpTo(HOME) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = MyUptmRoutes.POST_DETAIL,
            arguments = listOf(navArgument("postId") { type = NavType.StringType })
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId").orEmpty()
            PostDetailScreen(
                postId = postId,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(MyUptmRoutes.POSTS) {
            val authViewModel: AuthViewModel = viewModel()
            val userRole by authViewModel.userRole.collectAsState()

            if (userRole != null) {
                PostsScreen(
                    navController = navController,
                    userRole = userRole!!
                )
            } else {
                // Show this while the role is being fetched from Firestore
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading profile...")
                }
            }
        }
    }
}