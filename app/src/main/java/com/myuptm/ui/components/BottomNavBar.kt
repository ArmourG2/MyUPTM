package com.myuptm.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.myuptm.navigation.MyUptmRoutes
import com.myuptm.viewmodel.TimetableViewModel

/**
 * Definition of one bottom navigation tab.
 */
data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(MyUptmRoutes.HOME, "Home", Icons.Filled.Home),
    BottomNavItem(MyUptmRoutes.TIMETABLE, "Timetable", Icons.Filled.DateRange),
    BottomNavItem(MyUptmRoutes.ATTENDANCE, "Attendance", Icons.Filled.CheckCircle),
    BottomNavItem(MyUptmRoutes.POSTS, "Posts", Icons.Filled.Email),
    BottomNavItem(MyUptmRoutes.PROFILE, "Profile", Icons.Filled.Person)
)

@Composable
fun BottomNavBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 1. Get the Activity-scoped TimetableViewModel (shared with TimetableScreen)
    val timetableViewModel: TimetableViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as ViewModelStoreOwner
    )

    NavigationBar {
        bottomNavItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    // 2. Use `item.route` because we are inside the forEach loop
                    if (currentRoute == item.route) {
                        // Already on this tab -> re-selection
                        if (item.route == MyUptmRoutes.TIMETABLE) {
                            timetableViewModel.requestReset()
                        }
                    } else {
                        if (item.route == MyUptmRoutes.TIMETABLE) {
                            timetableViewModel.requestReset()
                        }
                        navController.navigate(item.route) {
                            popUpTo(MyUptmRoutes.HOME) { saveState = false }
                            launchSingleTop = true
                            restoreState = false
                        }
                    }
                },
                icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                label = { Text(text = item.label) }
            )
        }
    }
}