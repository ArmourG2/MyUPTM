package com.myuptm.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.myuptm.domain.model.UserRole

// ---- Sprint 14: help tour framework ---------------------------------------
// Screens register spotlight targets with Modifier.tourTarget(tag); the Help
// page "Tour" button starts a step sequence; THIS host renders the dim + hole
// + tooltip card over everything (hosted once in MyUptmApp, above the NavHost).

data class TourStep(
    // Which registered target to spotlight (null = centre card, no hole).
    val tag: String?,
    val title: String,
    val body: String,
    // The screen this step is about: the overlay navigates here BEFORE
    // spotlighting, so each step plays on its own screen (Sprint 14 fix: the
    // tour used to only highlight whatever targets were already on screen).
    val route: String? = null
)

object TourTargets {
    // tag → bounds in root coordinates; updated by every tourTarget on layout.
    internal val bounds = mutableStateMapOf<String, Rect>()
}

fun Modifier.tourTarget(tag: String): Modifier = onGloballyPositioned { coordinates ->
    TourTargets.bounds[tag] = coordinates.boundsInRoot()
}
// ---- Tour session (process-wide; harmless if the app restarts mid-tour) ----
object TourSession {
    val steps = mutableStateListOf<TourStep>()
    val index = mutableIntStateOf(-1)
    // Route each step brings the user to; the host navigates there per step.
    // The navigation hook is bound by MainActivity (the host itself has no
    // NavController); null while unbound = navigate nowhere (tags on screen only).
    var navigator: ((String) -> Unit)? = null
    private var lastRoute: String? = null

    val active: Boolean get() = index.intValue >= 0

    fun start(role: UserRole) {
        steps.clear()
        steps.addAll(stepsForRole(role))
        lastRoute = null
        index.intValue = if (steps.isEmpty()) -1 else 0
    }

    fun stop() {
        index.intValue = -1
        steps.clear()
        lastRoute = null
    }

    // Called from the overlay host on every step change: brings the screen the
    // step describes to the front, then the target's tags register on layout.
    fun navigateFor(step: TourStep) {
        val route = step.route ?: return
        if (route == lastRoute) return
        lastRoute = route
        navigator?.invoke(route)
    }
}

private fun stepsForRole(role: UserRole): List<TourStep> = when (role) {
    UserRole.STUDENT -> listOf(
        TourStep("ATTENDANCE", "Attendance tab",
            "This is where you verify attendance: the camera opens automatically during a session, or type the 6-digit PIN.",
            route = com.myuptm.navigation.MyUptmRoutes.ATTENDANCE),
        TourStep("home_bell", "Notifications bell",
            "Your notifications: class updates, warnings and marks. A red dot means unread.",
            route = com.myuptm.navigation.MyUptmRoutes.HOME),
        TourStep("home_class_card", "Your class status",
            "Your next class (with a live countdown) appears here — or a notice while you wait for a lecturer's assignment.",
            route = com.myuptm.navigation.MyUptmRoutes.HOME),
        TourStep("home_announcements", "Announcements",
            "Official university and faculty news, posted by admins and lecturers.",
            route = com.myuptm.navigation.MyUptmRoutes.HOME),
        TourStep("home_support", "Student support",
            "Quick links to the LMS, CMS and calendar, plus the student portal.",
            route = com.myuptm.navigation.MyUptmRoutes.HOME),
        TourStep("PROFILE", "Profile",
            "Your identity, avatar, attendance overview and settings — the Help guide also lives here.",
            route = com.myuptm.navigation.MyUptmRoutes.PROFILE)
    )
    UserRole.LECTURER -> listOf(
        TourStep("CLASS_MANAGEMENT", "Classes tab",
            "Class Management: rosters, attendance sessions, absence letters and warnings.",
            route = com.myuptm.navigation.MyUptmRoutes.CLASS_MANAGEMENT),
        TourStep("timetable_grid", "Your timetable",
            "Tap one of your classes to edit it, or an empty area to add a class right there — a class can run once, a few weeks, or every week.",
            route = com.myuptm.navigation.MyUptmRoutes.TIMETABLE),
        TourStep(null, "Removals & notices",
            "Removing a class greys it out on student timetables for THAT week only, and students are notified automatically — with your reason, if you give one.",
            route = com.myuptm.navigation.MyUptmRoutes.TIMETABLE),
        TourStep("PROFILE", "Profile",
            "Settings, your identity and the Help guide.",
            route = com.myuptm.navigation.MyUptmRoutes.PROFILE)
    )
    UserRole.ADMIN -> listOf(
        TourStep("dash_tiles", "Dashboard tiles",
            "Live counts: the student roster, lecturer accounts, classes and pending accounts — tap a tile to open its list.",
            route = com.myuptm.navigation.MyUptmRoutes.ADMIN_DASHBOARD),
        TourStep("dash_pending", "Pending accounts",
            "New logins wait here for a role — tap Assign to set it (role, faculty and student details).",
            route = com.myuptm.navigation.MyUptmRoutes.ADMIN_DASHBOARD),
        TourStep("dash_announcements", "Announcements",
            "Important posts published by admins — visible to everyone in Posts.",
            route = com.myuptm.navigation.MyUptmRoutes.ADMIN_DASHBOARD),
        TourStep("PROFILE", "Profile",
            "Settings and the Help guide.",
            route = com.myuptm.navigation.MyUptmRoutes.PROFILE)
    )
}

// ---- Overlay host ----------------------------------------------------------

@Composable
fun TourOverlayHost() {
    if (!TourSession.active) return // hosts nothing until a tour runs
    val step = TourSession.steps.getOrNull(TourSession.index.intValue) ?: return

    // Sprint 14 fix: each step brings its OWN screen to the front before
    // spotlighting — the tour now walks the app with the user instead of only
    // highlighting targets that happen to be on the current screen.
    androidx.compose.runtime.LaunchedEffect(step) {
        TourSession.navigateFor(step)
    }
    val target = step.tag?.let { TourTargets.bounds[it] }

    Box(modifier = Modifier.fillMaxSize()) {
        // Dim + spotlight hole. Offscreen compositing so the Clear punch works.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawBehind {
                    // Sprint 14 fix: a 0.72 black scrim was almost opaque on the
                    // dark theme — the spotlight hole disappeared into it. Use a
                    // lighter scrim AND outline the hole so the highlighted
                    // control is clearly visible in both themes.
                    drawRect(Color.Black.copy(alpha = 0.45f))
                    target?.let { rect ->
                        val pad = 12.dp.toPx()
                        val holeTopLeft = Offset(rect.left - pad, rect.top - pad)
                        val holeSize = Size(rect.width + pad * 2, rect.height + pad * 2)
                        drawRoundRect(
                            color = Color.Transparent,
                            topLeft = holeTopLeft,
                            size = holeSize,
                            cornerRadius = CornerRadius(16.dp.toPx()),
                            blendMode = BlendMode.Clear
                        )
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.9f),
                            topLeft = holeTopLeft,
                            size = holeSize,
                            cornerRadius = CornerRadius(16.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                        )
                    }
                }
                .pointerInput(Unit) {
                    // Blocking layer: taps do nothing here; the card drives.
                }
        ) {}

        // The explanation card — centred so scripts never depend on desktop
        // layout, with Next/Skip to walk the sequence.
        MaterialSurfaceCard(step = step)
    }
}

@Composable
private fun MaterialSurfaceCard(step: TourStep) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { },
        title = {
            Column {
                Text(
                    "Step ${TourSession.index.intValue + 1} of ${TourSession.steps.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(step.title, style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Text(
                step.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(onClick = {
                if (TourSession.index.intValue >= TourSession.steps.lastIndex) {
                    TourSession.stop()
                } else TourSession.index.intValue++
            }) {
                Text(
                    if (TourSession.index.intValue >= TourSession.steps.lastIndex)
                        "Done"
                    else "Next"
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { TourSession.stop() }) { Text("Skip tour") }
        }
    )
}
