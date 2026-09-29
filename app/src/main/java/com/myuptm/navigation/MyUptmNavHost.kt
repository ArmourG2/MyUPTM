package com.myuptm.navigation

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
        startDestination = SIGN_IN,
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
        composable(MyUptmRoutes.POSTS) { PostsScreen(navController = navController) }
        composable(MyUptmRoutes.PROFILE) { ProfileScreen(navController = navController) }
        composable(MyUptmRoutes.SETTINGS) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onSignOut = {
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
    }
}