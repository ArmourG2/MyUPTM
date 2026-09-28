package com.myuptm.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.myuptm.ui.screens.AttendanceScreen
import com.myuptm.ui.screens.HomeScreen
import com.myuptm.ui.screens.PostDetailScreen
import com.myuptm.ui.screens.PostsScreen
import com.myuptm.ui.screens.ProfileScreen
import com.myuptm.ui.screens.SettingsScreen
import com.myuptm.ui.screens.SignInScreen
import com.myuptm.ui.screens.TimetableScreen


/**
 * Single source of truth for all destinations.
 * Sprint 1: Sign-In shell -> 5 placeholder tabs.
 */
@Composable
fun MyUptmNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = MyUptmRoutes.SIGN_IN,
        modifier = modifier
    ) {
        composable(MyUptmRoutes.SIGN_IN) {
            SignInScreen(
                onSignInClick = {
                    // Sprint 4: replace with real auth check, then navigate.
                    navController.navigate(MyUptmRoutes.HOME) {
                        // Remove Sign-In from the back stack after "signing in"
                        popUpTo(MyUptmRoutes.SIGN_IN) { inclusive = true }
                    }
                }
            )
        }
        composable(MyUptmRoutes.HOME) { HomeScreen() }
        composable(MyUptmRoutes.TIMETABLE) { TimetableScreen() }
        composable(MyUptmRoutes.ATTENDANCE) { AttendanceScreen() }
        composable(MyUptmRoutes.POSTS) { PostsScreen(navController = navController) }
        composable(MyUptmRoutes.PROFILE) { ProfileScreen(navController = navController) }
        composable(MyUptmRoutes.SETTINGS) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onSignOut = {
                    navController.navigate(MyUptmRoutes.SIGN_IN) {
                        popUpTo(MyUptmRoutes.HOME) { inclusive = true }
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