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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
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
fun getBottomNavItems(userRole: UserRole, enrolledStudent: Boolean): List<BottomNavItem> {
    val baseItems = listOf(
        BottomNavItem(MyUptmRoutes.HOME, "Home", Icons.Filled.Home),
        BottomNavItem(MyUptmRoutes.POSTS, "Posts", Icons.Filled.Email),
        BottomNavItem(MyUptmRoutes.PROFILE, "Profile", Icons.Filled.Person)
    )

    return when (userRole) {
        UserRole.STUDENT -> {
            // Sprint 13C: a fresh (un-enrolled) registration has no classes,
            if (!enrolledStudent) baseItems
            else {
                val studentTabs = listOf(
                    BottomNavItem(MyUptmRoutes.TIMETABLE, "Timetable", Icons.Filled.DateRange),
                    BottomNavItem(MyUptmRoutes.ATTENDANCE, "Attend", Icons.Filled.CheckCircle)
                )
                listOf(baseItems[0]) + studentTabs + baseItems.subList(1, 3)
            }
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
            // Sprint 11 (owner ruling): admins have NO Home — the Home anatomy is
            // timetable-tied and admins don't teach. The Dashboard IS the first screen.
            listOf(
                BottomNavItem(
                    route = MyUptmRoutes.ADMIN_DASHBOARD,
                    label = "Dashboard",
                    icon = ImageVector.vectorResource(id = R.drawable.dashboard_24dp)
                ),
                baseItems[1], // Posts
                baseItems[2]  // Profile
            )
        }
    }
}

@Composable
fun BottomNavBar(
    navController: NavHostController,
        userRole: UserRole,
    // Sprint 13C: false for fresh registrations — they get the lean 3-tab nav.
    enrolledStudent: Boolean = true
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Dynamically get items based on role
    val navItems = getBottomNavItems(userRole, enrolledStudent)

    val timetableViewModel: TimetableViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as ViewModelStoreOwner
    )



    NavigationBar {
        navItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                // Sprint 14: tour spotlight targets (route-keyed tags).
                modifier = Modifier.tourTarget(item.route),
                onClick = {
                    if (currentRoute == item.route) {
                        if (item.route == MyUptmRoutes.TIMETABLE) {
                            timetableViewModel.requestReset()
                        }
                    } else {
                        if (item.route == MyUptmRoutes.TIMETABLE) {
                            timetableViewModel.requestReset()
                        }
                        // Sprint 11: pop to the GRAPH start destination (admins have no
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                            launchSingleTop = true
                            restoreState = false
                        }
                    }
                },
                icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                label = {
                    // Sprint 13C: single-line labels — "Attendance" wrapped into
                    // "attendanc/e" on narrow 5-tab rows; never wrap now.
                    Text(text = item.label, maxLines = 1)
                },
                // Sprint 12: unselected tabs show icon only; the selected tab's icon
                // rises and its label slides in — saves space on 5-tab small screens.
                alwaysShowLabel = false
            )
        }
    }
}