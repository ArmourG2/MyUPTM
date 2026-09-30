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
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.myuptm.R
import com.myuptm.domain.model.UserRole
import com.myuptm.navigation.MyUptmRoutes
import com.myuptm.viewmodel.TimetableViewModel


data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)
// Returns the correct list of tabs based on the user's role.
@Composable
fun getBottomNavItems(userRole: UserRole): List<BottomNavItem> {
    val baseItems = listOf(
        BottomNavItem(MyUptmRoutes.HOME, "Home", Icons.Filled.Home),
        BottomNavItem(MyUptmRoutes.POSTS, "Posts", Icons.Filled.Email),
        BottomNavItem(MyUptmRoutes.PROFILE, "Profile", Icons.Filled.Person)
    )

    // Insert role-specific tabs in the correct order (Home, [RoleTab1], [RoleTab2], Posts, Profile)
    return when (userRole) {
        UserRole.STUDENT -> {
            // Student gets Timetable and Attendance
            val studentTabs = listOf(
                BottomNavItem(MyUptmRoutes.TIMETABLE, "Timetable", Icons.Filled.DateRange),
                BottomNavItem(MyUptmRoutes.ATTENDANCE, "Attendance", Icons.Filled.CheckCircle)
            )
            listOf(baseItems[0]) + studentTabs + baseItems.subList(1, 3)
        }
        UserRole.LECTURER -> {
            // Lecturer gets Timetable and Class Management (replaces Attendance)
            val lecturerTabs = listOf(
                BottomNavItem(MyUptmRoutes.TIMETABLE, "Timetable", Icons.Filled.DateRange),
                BottomNavItem(MyUptmRoutes.CLASS_MANAGEMENT, "Classes", Icons.Filled.CheckCircle) // Replaces Attendance
            )
            listOf(baseItems[0]) + lecturerTabs + baseItems.subList(1, 3)
        }
        UserRole.ADMIN -> {
            // Admin loses Timetable, gets Admin Dashboard in the middle
            val adminTabs = listOf(
                BottomNavItem(
                    route = MyUptmRoutes.ADMIN_DASHBOARD,
                    label = "Dashboard",
                    icon = ImageVector.vectorResource(id = R.drawable.dashboard_24dp)
                )
            )
            listOf(baseItems[0]) + adminTabs + baseItems.subList(1, 3)
        }
    }
}

@Composable
fun BottomNavBar(
    navController: NavHostController,
        userRole: UserRole // <-- Added role parameter
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Dynamically get items based on role
    val navItems = getBottomNavItems(userRole)

    val timetableViewModel: TimetableViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as ViewModelStoreOwner
    )



    NavigationBar {
        navItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    if (currentRoute == item.route) {
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