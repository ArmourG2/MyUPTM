package com.myuptm.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myuptm.domain.model.AppNotification
import com.myuptm.domain.model.AppUser
import com.myuptm.domain.model.PersonalPlan
import com.myuptm.domain.model.toPermissions
import com.myuptm.viewmodel.TimetableViewModel

private val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val TIME_REGEX = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

// Sprint 7B: the Timetable screen doubles as the student's personal plan manager
// (feature-first pass; visual redesign deferred to Sprint 8).
@Composable
fun TimetableScreen(
    user: AppUser?,
    viewModel: TimetableViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as ViewModelStoreOwner
    )
) {
    // Re-bind on entry: the VM is activity-scoped and may outlive account switches.
    LaunchedEffect(user) { viewModel.bindUser(user) }

    val isStudent = user?.toPermissions()?.canManagePersonalPlans == true

    val weeklyTimetable by viewModel.weeklyTimetable.collectAsState()
    val weeklyPlans by viewModel.weeklyPlans.collectAsState()
    val clashWarnings by viewModel.clashWarnings.collectAsState()
    val planError by viewModel.planError.collectAsState()
    val resetTrigger by viewModel.resetTrigger.collectAsState()

    val todayPage = 7 + getCurrentDayIndex()

    val pagerState = rememberPagerState(
        initialPage = todayPage,
        pageCount = { 21 }
    )

    LaunchedEffect(resetTrigger) {
        pagerState.animateScrollToPage(
            page = todayPage,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
        )
    }
    BackHandler(enabled = pagerState.currentPage != todayPage) {
        viewModel.requestReset()
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingPlan by remember { mutableStateOf<PersonalPlan?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp)) {
                Text(
                    "Timetable",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Week 4 · September 2026",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isStudent && clashWarnings.isNotEmpty()) {
                ClashWarningBanner(
                    warnings = clashWarnings,
                    onDismiss = { viewModel.clearWarnings() }
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                val dayIndex = pageIndex % 7
                val isToday = pageIndex == todayPage
                DayTimeline(
                    dayIndex = dayIndex,
                    isToday = isToday,
                    classes = weeklyTimetable.getOrNull(dayIndex) ?: emptyList(),
                    plans = if (isStudent) (weeklyPlans.getOrNull(dayIndex) ?: emptyList()) else emptyList(),
                    onPlanClick = { editingPlan = it }
                )
            }
        }

        if (isStudent) {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add plan")
            }
        }
    }

    if (showAddDialog) {
        PlanEditorDialog(
            title = "Add plan",
            initial = null,
            defaultDay = getCurrentDayIndex(),
            errorMessage = planError,
            onDismiss = {
                showAddDialog = false
                viewModel.clearPlanError()
            },
            onSave = { name, dayIndex, start, end, venue ->
                viewModel.addPlan(name, dayIndex, start, end, venue)
                // Conflict/validation errors keep the dialog open and surface inline.
                if (viewModel.planError.value == null) showAddDialog = false
            }
        )
    }

    editingPlan?.let { plan ->
        PlanEditorDialog(
            title = "Edit plan",
            initial = plan,
            defaultDay = plan.dayIndex,
            errorMessage = planError,
            onDismiss = {
                editingPlan = null
                viewModel.clearPlanError()
            },
            onSave = { name, dayIndex, start, end, venue ->
                viewModel.updatePlan(plan, name, dayIndex, start, end, venue)
                if (viewModel.planError.value == null) editingPlan = null
            },
            onDelete = {
                viewModel.removePlan(plan.id)
                editingPlan = null
            }
        )
    }
}

@Composable
private fun ClashWarningBanner(warnings: List<AppNotification>, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    warnings.first().title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    warnings.first().message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 2
                )
                if (warnings.size > 1) {
                    Text(
                        "${warnings.size} clash warnings in total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                    )
                }
            }
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun PlanEditorDialog(
    title: String,
    initial: PersonalPlan?,
    defaultDay: Int,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, dayIndex: Int, startTime: String, endTime: String, venue: String?) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initial?.title ?: "") }
    var venue by remember { mutableStateOf(initial?.venue ?: "") }
    var start by remember { mutableStateOf(initial?.startTime ?: "") }
    var end by remember { mutableStateOf(initial?.endTime ?: "") }
    var dayIndex by remember { mutableStateOf(initial?.dayIndex ?: defaultDay) }
    var dayMenuOpen by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Plan name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedButton(
                        onClick = { dayMenuOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(DAY_NAMES[dayIndex.coerceIn(0, 6)])
                    }
                    DropdownMenu(
                        expanded = dayMenuOpen,
                        onDismissRequest = { dayMenuOpen = false }
                    ) {
                        DAY_NAMES.forEachIndexed { index, day ->
                            DropdownMenuItem(
                                text = { Text(day) },
                                onClick = {
                                    dayIndex = index
                                    dayMenuOpen = false
                                }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = start,
                        onValueChange = { start = it },
                        label = { Text("Start (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = end,
                        onValueChange = { end = it },
                        label = { Text("End (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = venue,
                    onValueChange = { venue = it },
                    label = { Text("Venue (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                (localError ?: errorMessage)?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                localError = null
                if (name.isBlank()) {
                    localError = "Plan name is required"
                    return@TextButton
                }
                if (!TIME_REGEX.matches(start) || !TIME_REGEX.matches(end)) {
                    localError = "Times must be HH:mm (e.g. 09:30)"
                    return@TextButton
                }
                onSave(name, dayIndex, start, end, venue)
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

private fun getCurrentDayIndex(): Int {
    return java.time.LocalDate.now().dayOfWeek.value - 1
}