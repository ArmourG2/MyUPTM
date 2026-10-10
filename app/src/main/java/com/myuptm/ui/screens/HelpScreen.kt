package com.myuptm.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.myuptm.domain.model.AppUser
import com.myuptm.domain.model.UserRole
import com.myuptm.domain.model.toPermissions
import com.myuptm.navigation.MyUptmRoutes
import com.myuptm.ui.components.TourSession
import com.myuptm.viewmodel.StudentNavViewModel

// Sprint 14: the Help page (rubric "Help menu / user manual"). Explains HOW to
// navigate the app for the current role, and the "Tour" button launches an
// automated spotlight sequence over the real screens (TourSession + overlay).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    user: AppUser?,
    navController: NavHostController,
    // Sprint 14: "Start tour" hands the role + the tour's starting route to the
    // NavHost (which starts the session and navigates so the targets compose).
    tourStarter: (UserRole, String) -> Unit
) {
    val role = user?.role
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        // Campus-photo hero banner (design language from sign-in).
        com.myuptm.ui.components.CampusHeroBackdrop()
        Column(modifier = Modifier.fillMaxSize()) {
            com.myuptm.ui.components.CampusHeroHeader(
                title = "Help",
                subtitle = "Guides & the campus tour",
                navigationIcon = {
                    com.myuptm.ui.components.CampusBackButton { navController.popBackStack() }
                }
            )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "How to navigate MyUPTM",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )

            when (role) {
                UserRole.STUDENT -> {
                    GuideRow(
                        title = "1 · Home",
                        body = "Your dashboard: next class (or your pending-assignment notice), announcements and quick student-support links."
                    )
                    GuideRow(
                        title = "2 · Timetable",
                        body = "Your class schedule. Classes appear as the lecturer publishes them; personal plans are added from the + button."
                    )
                    GuideRow(
                        title = "3 · Attendance",
                        body = "Verify with the camera (it opens automatically) or type the 6-digit PIN from your lecturer's session. The session is live for 5 minutes."
                    )
                    GuideRow(
                        title = "4 · Posts",
                        body = "Announcements and faculty news. Red = important/pinned, yellow = attention, blue = normal."
                    )
                    GuideRow(
                        title = "5 · Profile",
                        body = "Your identity, attendance overview, absence letters and Settings — the theme colours live there too."
                    )
                }
                UserRole.LECTURER -> {
                    GuideRow(
                        title = "1 · Timetable",
                        body = "Your teaching schedule is managed HERE: tap one of your classes to edit it, tap an empty area to add one — repeat rules let it run this week only, a few weeks, or every week."
                    )
                    GuideRow(
                        title = "2 · Classes",
                        body = "Class Management: open a class for the roster, start/stop attendance sessions and review absence letters and warnings."
                    )
                    GuideRow(
                        title = "3 · Sessions & notices",
                        body = "Start attendance → students scan or use the PIN. Ending the session notifies absentees automatically. Removing a class greys it out for this week and notifies students (with your reason, if given)."
                    )
                    GuideRow(
                        title = "4 · Posts / Profile",
                        body = "Publish attention-marked posts; Profile holds your identity and settings."
                    )
                }
                UserRole.ADMIN -> {
                    GuideRow(
                        title = "1 · Dashboard",
                        body = "Tiles for students, lecturers, classes and pending accounts — tap a tile to open the full list."
                    )
                    GuideRow(
                        title = "2 · Approving accounts",
                        body = "New logins appear in the pending list: tap Assign → choose the role, faculty and student details. The member's app updates automatically."
                    )
                    GuideRow(
                        title = "3 · Announcements / Posts",
                        body = "Publish Important (red) posts everyone sees first."
                    )
                    GuideRow(
                        title = "4 · Profile",
                        body = "Your identity and Settings."
                    )
                }
                else -> {}
            }

            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        "Guided tour",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "A step-by-step overlay will point at the real controls on your screens and explain each one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(onClick = {
                            val start = when (role) {
                                UserRole.LECTURER -> MyUptmRoutes.TIMETABLE
                                UserRole.ADMIN -> MyUptmRoutes.ADMIN_DASHBOARD
                                else -> MyUptmRoutes.HOME
                            }
                            tourStarter(role ?: UserRole.STUDENT, start)
                        }) {
                            Text("Start tour")
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        }
    }
}

@Composable
private fun GuideRow(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
