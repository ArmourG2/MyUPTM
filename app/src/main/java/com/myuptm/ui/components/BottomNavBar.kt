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
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.myuptm.navigation.MyUptmRoutes

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

    NavigationBar {
        bottomNavItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        // Official bottom-nav pattern:
                        // avoid stacking copies of the same destination
                        popUpTo(MyUptmRoutes.HOME) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                label = { Text(text = item.label) }
            )
        }
    }
}