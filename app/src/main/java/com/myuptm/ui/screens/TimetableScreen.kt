package com.myuptm.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myuptm.domain.model.AppNotification
import com.myuptm.domain.model.AppUser
import com.myuptm.domain.model.ClassSession
import com.myuptm.domain.model.PersonalPlan
import com.myuptm.domain.model.TeachingMedium
import com.myuptm.domain.model.toPermissions
import com.myuptm.ui.components.DayChip
import com.myuptm.viewmodel.TimetableViewMode
import com.myuptm.viewmodel.TimetableViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val DAY_SHORTS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
// Wireframe day circles run Sunday-first: S M T W T F S → mapped to our Monday-first indexes.
private val DAY_LETTERS = listOf("S", "M", "T", "W", "T", "F", "S")
private val CIRCLE_TO_DAY = listOf(6, 0, 1, 2, 3, 4, 5)

// Weekly pager: 61 pages centred on page 30 so swiping feels endless (~±30 weeks).
private const val WEEK_PAGES = 61
private const val WEEK_BASE = 30

// Prefilled values when opening the add-plan dialog from a grid slot tap (or the FAB).
// The date is recorded because plans are ONE-TIME events (Sprint 8).
private data class PlanPrefill(val date: LocalDate, val startTime: String, val endTime: String)

// Sprint 8 Task 1: Daily (chips + single-day grid w/ free bars) and Weekly (swipable week
// pages). Plans are one-time events; tapping a plan opens an Edit/Delete options popup.
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
    val allPlans by viewModel.plans.collectAsState()
    val clashWarnings by viewModel.clashWarnings.collectAsState()
    val planError by viewModel.planError.collectAsState()
    val resetTrigger by viewModel.resetTrigger.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val weekOffset by viewModel.weekOffset.collectAsState()

    val todayIndex = getCurrentDayIndex()
    val today = LocalDate.now()

    // Daily: plain selected-day state (chips tap; no pager).
    var dailyDayIndex by remember { mutableStateOf(todayIndex) }
    // Weekly: pager over weeks; VM offset mirrors the settled page.
    val weekPagerState = rememberPagerState(initialPage = WEEK_BASE, pageCount = { WEEK_PAGES })
    val gridScroll = rememberScrollState()

    // Pager → VM (one-way; VM never re-pushes into the pager, avoiding echo loops).
    LaunchedEffect(weekPagerState) {
        snapshotFlow { weekPagerState.currentPage }
            .collect { page -> viewModel.syncWeekOffset(page - WEEK_BASE) }
    }

    // Re-entry → snap back to today (Daily chip) and this week (Weekly pager).
    LaunchedEffect(resetTrigger) {
        dailyDayIndex = todayIndex
        weekPagerState.animateScrollToPage(WEEK_BASE)
    }

    // Back returns to today in Daily (Sprint 7B behaviour) and to this week in Weekly.
    BackHandler(enabled = viewMode == TimetableViewMode.DAILY && dailyDayIndex != todayIndex) {
        viewModel.requestReset()
    }
    BackHandler(enabled = viewMode == TimetableViewMode.WEEKLY && weekOffset != 0) {
        viewModel.requestReset()
    }

    var viewMenuOpen by remember { mutableStateOf(false) }
    var addDialogPrefill by remember { mutableStateOf<PlanPrefill?>(null) }
    var editingPlan by remember { mutableStateOf<PersonalPlan?>(null) }
    var optionsPlan by remember { mutableStateOf<PersonalPlan?>(null) }
    var detailClass by remember { mutableStateOf<ClassSession?>(null) }

    // Screen-level dialog prefill for Add: FAB → today now-ish slot; grid taps → tapped day.
    val openAddDialog: (date: LocalDate, startTime: String, endTime: String) -> Unit =
        { date, start, end -> addDialogPrefill = PlanPrefill(date, start, end) }

    // Default times for the FAB path: next full hour → +1h.
    val fabDefaults = {
        val nextHour = (java.time.LocalTime.now().hour + 1).coerceAtMost(21)
        PlanPrefill(today, "${"$nextHour".padStart(2, '0')}:00", "${"$nextHour".padStart(2, '0')}:00")
    }

    // Subtitle follows the visible date (Daily) or week range (Weekly).
    val subtitle = if (viewMode == TimetableViewMode.WEEKLY) {
        val weekStart = today.plusDays(weekOffset * 7L - todayIndex)
        val weekEnd = weekStart.plusDays(6)
        val dateFmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        "${weekStart.format(dateFmt)} – ${weekEnd.format(dateFmt)} ${weekEnd.year}"
    } else {
        // Chips cover the current week, so the date is this week's Monday + dayIndex - todayIndex.
        val chipDate = today.plusDays((dailyDayIndex - todayIndex).toLong())
        chipDate.format(DateTimeFormatter.ofPattern("EEEE · d MMMM yyyy", Locale.getDefault()))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- Immersive header: title + subtitle left, three-dot view menu right ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Timetable",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { viewMenuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "View options")
                    }
                    DropdownMenu(expanded = viewMenuOpen, onDismissRequest = { viewMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Daily view") },
                            trailingIcon = {
                                RadioButton(
                                    selected = viewMode == TimetableViewMode.DAILY,
                                    onClick = null
                                )
                            },
                            onClick = {
                                viewModel.setViewMode(TimetableViewMode.DAILY)
                                viewMenuOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Weekly view") },
                            trailingIcon = {
                                RadioButton(
                                    selected = viewMode == TimetableViewMode.WEEKLY,
                                    onClick = null
                                )
                            },
                            onClick = {
                                viewModel.setViewMode(TimetableViewMode.WEEKLY)
                                viewMenuOpen = false
                            }
                        )
                    }
                }
            }

            if (viewMode == TimetableViewMode.DAILY) {
                // --- Sprint 8 Task 1: top date selector (chips for the current week) ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    (0..6).forEach { dayIndex ->
                        val date = today.plusDays((dayIndex - todayIndex).toLong())
                        DayChip(
                            label = DAY_SHORTS[dayIndex],
                            dateText = date.dayOfMonth.toString(),
                            selected = dailyDayIndex == dayIndex,
                            isToday = dayIndex == todayIndex,
                            onClick = { dailyDayIndex = dayIndex }
                        )
                    }
                }
            }

            if (isStudent && clashWarnings.isNotEmpty()) {
                ClashWarningBanner(
                    warnings = clashWarnings,
                    onDismiss = { viewModel.clearWarnings() }
                )
            }

            val onClassClick = { session: ClassSession -> detailClass = session }
            val onPlanClick = { plan: PersonalPlan -> optionsPlan = plan }

            if (viewMode == TimetableViewMode.DAILY) {
                // --- Single-day grid with glance free-bars ---
                SingleDayGrid(
                    modifier = Modifier.fillMaxSize(),
                    scroll = gridScroll,
                    dayIndex = dailyDayIndex,
                    todayIndex = todayIndex,
                    isStudent = isStudent,
                    classes = weeklyTimetable.getOrNull(dailyDayIndex) ?: emptyList(),
                    plans = if (isStudent) allPlans.filter { it.dayIndex == dailyDayIndex && isThisWeek(it.date) } else emptyList(),
                    onClassClick = onClassClick,
                    onPlanClick = onPlanClick,
                    onAddSlot = openAddDialog
                )
            } else {
                // --- Weekly: horizontal pager over weeks (swipe left = previous week) ---
                HorizontalPager(
                    state = weekPagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    WeeklyGrid(
                        modifier = Modifier.fillMaxSize(),
                        scroll = gridScroll,
                        weekOffset = page - WEEK_BASE,
                        isCurrentPage = weekPagerState.currentPage == page,
                        todayIndex = todayIndex,
                        isStudent = isStudent,
                        classes = weeklyTimetable,
                        plans = if (isStudent) allPlans else emptyList(),
                        onClassClick = onClassClick,
                        onPlanClick = onPlanClick,
                        onAddSlot = openAddDialog
                    )
                }
            }
        }

        if (isStudent) {
            FloatingActionButton(
                onClick = { addDialogPrefill = fabDefaults() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add plan")
            }
        }
    }

    // --- Plan options popup (Edit / Delete) — user-created plans only ---
    optionsPlan?.let { plan ->
        PlanOptionsPopup(
            plan = plan,
            onEdit = {
                optionsPlan = null
                editingPlan = plan
            },
            onDelete = {
                optionsPlan = null
                viewModel.removePlan(plan.id)
            },
            onDismiss = { optionsPlan = null }
        )
    }

    addDialogPrefill?.let { prefill ->
        AddPlanDialog(
            title = "Add plan",
            initial = null,
            anchorDate = prefill.date,
            defaultStart = prefill.startTime,
            defaultEnd = prefill.endTime,
            errorMessage = planError,
            onDismiss = {
                addDialogPrefill = null
                viewModel.clearPlanError()
            },
            onSave = { name, date, start, end, venue ->
                viewModel.addPlan(name, date, start, end, venue)
                // Conflict/validation errors keep the dialog open and surface inline.
                if (viewModel.planError.value == null) addDialogPrefill = null
            }
        )
    }

    editingPlan?.let { plan ->
        AddPlanDialog(
            title = "Edit plan",
            initial = plan,
            anchorDate = plan.date,
            defaultStart = "",
            defaultEnd = "",
            errorMessage = planError,
            onDismiss = {
                editingPlan = null
                viewModel.clearPlanError()
            },
            onSave = { name, date, start, end, venue ->
                viewModel.updatePlan(plan, name, date, start, end, venue)
                if (viewModel.planError.value == null) editingPlan = null
            },
            onDelete = {
                viewModel.removePlan(plan.id)
                editingPlan = null
            }
        )
    }

    detailClass?.let { session ->
        ClassDetailPopup(session = session, onDismiss = { detailClass = null })
    }
}

// True when the plan date sits in the week currently anchored to "today" (Daily chips week).
private fun isThisWeek(planDate: LocalDate): Boolean {
    val today = LocalDate.now()
    val weekStart = today.minusDays(((today.dayOfWeek.value) - 1).toLong())
    return !planDate.isBefore(weekStart) && planDate.isBefore(weekStart.plusDays(7))
}

@Composable
private fun ClashWarningBanner(warnings: List<AppNotification>, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .padding(bottom = 8.dp)
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

// Plan options popup (requirement 1): user-created plans offer Edit or Delete on tap.
@Composable
private fun PlanOptionsPopup(
    plan: PersonalPlan,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    plan.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${formatAmPm(plan.startTime)} – ${formatAmPm(plan.endTime)} · one-time plan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onEdit) {
                        Text("Edit")
                    }
                }
            }
        }
    }
}

// Add/Edit plan dialog (approved wireframe): name field, S M T W T F S day circles,
// From/To buttons opening the "Choose Time" picker, venue field, Cancel/Save.
@Composable
private fun AddPlanDialog(
    title: String,
    initial: PersonalPlan?,
    anchorDate: LocalDate,
    defaultStart: String,
    defaultEnd: String,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, date: LocalDate, startTime: String, endTime: String, venue: String?) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initial?.title ?: "") }
    var venue by remember { mutableStateOf(initial?.venue ?: "") }
    var start by remember(initial, defaultStart) { mutableStateOf(initial?.startTime ?: defaultStart) }
    var end by remember(initial, defaultEnd) { mutableStateOf(initial?.endTime ?: defaultEnd) }
    // Sunday-first circles (wireframe); preselect the entry-point's weekday.
    var circleIndex by remember(initial, anchorDate) {
        mutableStateOf(CIRCLE_TO_DAY.indexOf(initial?.dayIndex ?: (anchorDate.dayOfWeek.value - 1)))
    }
    var chooseTimeFor by remember { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }

    // The week the anchor lives in — the chosen circle picks a date inside this week.
    val weekStart = anchorDate.minusDays(((anchorDate.dayOfWeek.value) - 1).toLong())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Plan name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // S M T W T F S circles (wireframe) — single-select.
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    DAY_LETTERS.forEachIndexed { index, letter ->
                        val selected = circleIndex == index
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { circleIndex = index },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                letter,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                // From / To rows, each opening the Choose Time picker.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("From", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(12.dp))
                    OutlinedButtonLocal(label = formatAmPm(start)) { chooseTimeFor = "start" }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("To", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(12.dp))
                    OutlinedButtonLocal(label = formatAmPm(end)) { chooseTimeFor = "end" }
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
                if (start >= end) {
                    localError = "End time must be after start time"
                    return@TextButton
                }
                val date = weekStart.plusDays(CIRCLE_TO_DAY[circleIndex.coerceIn(0, 6)].toLong())
                onSave(name, date, start, end, venue)
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

    // --- "Choose Time" picker dialog (wireframe) with Material 3 TimePicker ---
    chooseTimeFor?.let { which ->
        ChooseTimeDialog(
            initialTime = if (which == "start") start else end,
            onConfirm = { newTime ->
                if (which == "start") start = newTime else end = newTime
                chooseTimeFor = null
            },
            onDismiss = { chooseTimeFor = null }
        )
    }
}

// Local thin wrapper so the button reads like the wireframe (time-value chip).
@Composable
private fun OutlinedButtonLocal(label: String, onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text(label)
    }
}

// "Choose Time" dialog: Material 3 TimePicker (AM/PM wheel per wireframe).
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ChooseTimeDialog(
    initialTime: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialHour = initialTime.substringBefore(':').toIntOrNull() ?: 9
    val initialMinute = initialTime.substringAfter(':').toIntOrNull() ?: 0
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Time") },
        text = {
            Box(modifier = Modifier.fillMaxWidth()) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm("${state.hour.toString().padStart(2, '0')}:${state.minute.toString().padStart(2, '0')}")
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// Class detail popup (approved sketch): time range + medium on top, big subject centre,
// venue bottom-left, lecturer bottom-right, theme-colour bar on the right edge.
// Colour follows colorScheme.primary so the Backlog palette picker (B-013) re-tints it.
@Composable
private fun ClassDetailPopup(session: ClassSession, onDismiss: () -> Unit) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val mediumLabel = if (session.teachingMedium == TeachingMedium.ONLINE) "Online" else "Offline"

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${formatAmPm(session.startTime)} – ${formatAmPm(session.endTime)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            mediumLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(
                        session.subjectName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(28.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            session.venue,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = onSurface
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            session.lecturerName,
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurfaceVariant,
                            textAlign = TextAlign.End,
                            maxLines = 2,
                            modifier = Modifier.width(140.dp)
                        )
                    }
                }
                // Right-edge theme colour bar (B-013 default: brand blue).
                Box(
                    Modifier
                        .width(10.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

// "HH:mm" → "10:00am" style label (sketch style, 12h clock).
private fun formatAmPm(hhmm: String): String {
    val h = hhmm.substringBefore(':').toIntOrNull() ?: 0
    val m = hhmm.substringAfter(':').toIntOrNull() ?: 0
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12:${m.toString().padStart(2, '0')}${if (h < 12) "am" else "pm"}"
}

private fun getCurrentDayIndex(): Int {
    return java.time.LocalDate.now().dayOfWeek.value - 1
}
