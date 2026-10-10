package com.myuptm.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
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
import com.myuptm.ui.components.tourTarget
import com.myuptm.viewmodel.ClassManagementViewModel
import com.myuptm.viewmodel.TimetableViewMode
import com.myuptm.viewmodel.TimetableViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

// Wireframe day circles run Sunday-first: S M T W T F S → mapped to our Monday-first indexes.
private val DAY_LETTERS = listOf("S", "M", "T", "W", "T", "F", "S")
private val CIRCLE_TO_DAY = listOf(6, 0, 1, 2, 3, 4, 5)

// Pagers: pages centred on a base page so swiping feels endless (~±30 days / weeks).
private const val DAY_PAGES = 61
private const val DAY_BASE = 30
private const val WEEK_PAGES = 61
private const val WEEK_BASE = 30
// The chip rail covers roughly one month around today (±15 days).
private const val CHIP_HALF_SPAN = 15

// Prefilled values when opening the add-plan dialog from a grid slot tap (or the FAB).
// The date is recorded because plans are ONE-TIME events (Sprint 8).
private data class PlanPrefill(val date: LocalDate, val startTime: String, val endTime: String)

// Sprint 8 Task 1: Daily (swipable day pages + month chip rail) and Weekly (swipable week
// pages) — like the Weekly pager. View switch = labelled pill in the top-right corner.
@Composable
fun TimetableScreen(
    user: AppUser?,
    onOpenClassManagement: (() -> Unit)? = null,
    viewModel: TimetableViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as ViewModelStoreOwner
    )
) {
    // Re-bind on entry: the VM is activity-scoped and may outlive account switches.
    LaunchedEffect(user) { viewModel.bindUser(user) }

    val isStudent = user?.toPermissions()?.canManagePersonalPlans == true

    val canManageClasses = user?.toPermissions()?.canManageClassGlobal == true

    val weeklyTimetable by viewModel.weeklyTimetable.collectAsState()
    val allPlans by viewModel.plans.collectAsState()
    val clashWarnings by viewModel.clashWarnings.collectAsState()
    val planError by viewModel.planError.collectAsState()
    val resetTrigger by viewModel.resetTrigger.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val weekOffset by viewModel.weekOffset.collectAsState()

    // Sprint 9: scoping moved INTO the VM (lecturers get only their own classes).
    // Kept as a defensive re-filter for lecturer screens in case of a timing gap.
    val visibleTimetable = if (user != null && canManageClasses) {
        weeklyTimetable.map { day -> day.filter { it.ownerEmail == user.email } }
    } else weeklyTimetable

    val today = LocalDate.now()
    val scope = rememberCoroutineScope()

    // Observable locale: derived from LocalConfiguration so date formats recompose when the
    // system locale changes while the screen is open (Locale.getDefault() is non-observable).
    val locale = LocalConfiguration.current.locales.get(0)

    // Daily: pager over day pages + mirrored selected page for the chip rail.
    val dayPagerState = rememberPagerState(initialPage = DAY_BASE, pageCount = { DAY_PAGES })
    var dailyPage by remember { mutableIntStateOf(DAY_BASE) }
    LaunchedEffect(dayPagerState) {
        snapshotFlow { dayPagerState.currentPage }.collect { dailyPage = it }
    }
    // Weekly: pager over weeks; VM offset mirrors the settled page.
    val weekPagerState = rememberPagerState(initialPage = WEEK_BASE, pageCount = { WEEK_PAGES })
    val gridScroll = rememberScrollState()

    // Weekly pager → VM (one-way; VM never re-pushes into the pager, avoiding echo loops).
    LaunchedEffect(weekPagerState) {
        snapshotFlow { weekPagerState.currentPage }
            .collect { page -> viewModel.syncWeekOffset(page - WEEK_BASE) }
    }

    // Re-entry → snap back to today (Daily page) and this week (Weekly page).
    LaunchedEffect(resetTrigger, viewMode) {
        when (viewMode) {
            TimetableViewMode.DAILY -> dayPagerState.animateScrollToPage(DAY_BASE)
            TimetableViewMode.WEEKLY -> weekPagerState.animateScrollToPage(WEEK_BASE)
        }
    }

    // Back returns to today in Daily and to this week in Weekly (Sprint 7B behaviour).
    BackHandler(enabled = viewMode == TimetableViewMode.DAILY && dailyPage != DAY_BASE) {
        viewModel.requestReset()
    }
    BackHandler(enabled = viewMode == TimetableViewMode.WEEKLY && weekOffset != 0) {
        viewModel.requestReset()
    }

    var viewMenuOpen by remember { mutableStateOf(false) }
    var addDialogPrefill by remember { mutableStateOf<PlanPrefill?>(null) }
    var editingPlan by remember { mutableStateOf<PersonalPlan?>(null) }
    var optionsPlan by remember { mutableStateOf<PersonalPlan?>(null) }
    var detailClass by remember { mutableStateOf<Pair<ClassSession, LocalDate>?>(null) }
    // Sprint 13D: lecturer manages classes DIRECTLY on the timetable — tap an
    // existing slot to edit it, an empty area to add a class prefilled with the
    // tapped day/time. The class VM is the SAME activity-scoped instance the
    // Class Management screen uses, so edits fire the usual notifications.
    val classManagementViewModel: ClassManagementViewModel = viewModel()
    // The class VM needs the signed-in lecturer's email for ownership scoping.
    LaunchedEffect(user?.email) { classManagementViewModel.bindOwner(user?.email) }
    var editingClass by remember { mutableStateOf<ClassSession?>(null) }
    var editingClassAnchor by remember { mutableStateOf<LocalDate?>(null) }
    var addClassPrefill by remember { mutableStateOf<PlanPrefill?>(null) }
    val classStatus by classManagementViewModel.status.collectAsState()
    // Sprint 13D: per-week cancellations — keys "classId|weekStartEpochDay",
    // reason as the value. Cancelled slots GREY OUT for everyone (owner removal
    // is global now, not device-local).
    val classCancellations by classManagementViewModel.cancellations.collectAsState()
    val canManageSlots = canManageClasses

    // Cancelling keys for ONE week (classId → reason) — the week containing date.
    fun cancellationReasonsFor(weekMonday: LocalDate): Map<String, String?> =
        classCancellations.mapNotNull { (key, reason) ->
            val parts = key.split("|")
            if (parts.size == 2 && parts[1].toLongOrNull() == weekMonday.toEpochDay()) {
                parts[0] to reason
            } else null
        }.toMap()

    // Screen-level dialog prefill for Add: FAB → today now-ish slot; grid taps → tapped day.
    val openAddDialog: (date: LocalDate, startTime: String, endTime: String) -> Unit =
        { date, start, end -> addDialogPrefill = PlanPrefill(date, start, end) }

    // Default times for the FAB path: next full hour → +1h.
    val fabDefaults: () -> PlanPrefill = {
        val nextHour = (java.time.LocalTime.now().hour + 1).coerceAtMost(21)
        PlanPrefill(today, nextHour.toString().padStart(2, '0') + ":00", nextHour.toString().padStart(2, '0') + ":00")
    }

    // The date the Daily pager currently shows (subtitle + content use it).
    val dailyDate = today.plusDays((dailyPage - DAY_BASE).toLong())

    // Subtitle follows the visible date (Daily) or week range (Weekly).
    val subtitle = if (viewMode == TimetableViewMode.WEEKLY) {
        val weekStart = today.plusDays(weekOffset * 7L - (today.dayOfWeek.value - 1))
        val weekEnd = weekStart.plusDays(6)
        val dateFmt = DateTimeFormatter.ofPattern("d MMM", locale)
        "${weekStart.format(dateFmt)} – ${weekEnd.format(dateFmt)} ${weekEnd.year}"
    } else {
        dailyDate.format(DateTimeFormatter.ofPattern("EEEE · d MMMM yyyy", locale))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Campus-photo hero banner (design language from sign-in).
        com.myuptm.ui.components.CampusHeroBackdrop()
        Column(modifier = Modifier.fillMaxSize().tourTarget("timetable_grid")) {
            // --- Hero header: title + subtitle left, self-explanatory view pill right ---
            com.myuptm.ui.components.CampusHeroHeader(
                title = "Timetable",
                subtitle = subtitle,
                actions = {
                    // Labelled view pill: shows the CURRENT view so users know what it switches.
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.16f))
                                .clickable { viewMenuOpen = true }
                                .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (viewMode == TimetableViewMode.DAILY) "Day view" else "Week view",
                                style = MaterialTheme.typography.labelLarge,
                                color = androidx.compose.ui.graphics.Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = "Switch timetable view",
                                tint = androidx.compose.ui.graphics.Color.White
                            )
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
            )

            if (isStudent && clashWarnings.isNotEmpty()) {
                ClashWarningBanner(
                    warnings = clashWarnings,
                    onDismiss = { viewModel.clearWarnings() }
                )
            }

            if (viewMode == TimetableViewMode.DAILY) {
                // --- Chip rail: ~1 month of days around today (±15 days), tappable ---
                val chipListState = rememberLazyListState(initialFirstVisibleItemIndex = CHIP_HALF_SPAN)
                LazyRow(
                    state = chipListState,
                    contentPadding = PaddingValues(horizontal = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    items((-CHIP_HALF_SPAN..CHIP_HALF_SPAN).toList()) { offset ->
                        val pageIndex = DAY_BASE + offset
                        val date = today.plusDays(offset.toLong())
                        DayChip(
                            label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                            dateText = date.dayOfMonth.toString(),
                            selected = dailyPage == pageIndex,
                            isToday = offset == 0,
                            onClick = {
                                scope.launch { dayPagerState.animateScrollToPage(pageIndex) }
                            }
                        )
                    }
                }
            }

            val onClassClick = { session: ClassSession, anchoredDate: LocalDate ->
                if (canManageClasses && session.ownerEmail != null) {
                    // Lecturer + own slot → the EDITOR opens directly.
                    editingClass = session
                    editingClassAnchor = anchoredDate
                } else {
                    detailClass = session to anchoredDate
                }
            }
            val onPlanClick = { plan: PersonalPlan -> optionsPlan = plan }
            // Sprint 13D dispatch: students add plans; lecturers add classes —
            // both via the grid's empty-area taps.
            val onAddSlot: (LocalDate, String, String) -> Unit = { date, start, end ->
                if (isStudent) openAddDialog(date, start, end) else addClassPrefill = PlanPrefill(date, start, end)
            }

            if (viewMode == TimetableViewMode.DAILY) {
                // --- Daily: full pager over days, same feel as Weekly ---
                HorizontalPager(
                    state = dayPagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val pageDate = today.plusDays((page - DAY_BASE).toLong())
                    val cancelledForPage = cancellationReasonsFor(weekMondayOf(pageDate))
                    SingleDayGrid(
                        modifier = Modifier.fillMaxSize(),
                        scroll = gridScroll,
                        date = pageDate,
                        isStudent = isStudent,
                        canManageSlots = canManageSlots,
                        classes = visibleTimetable.getOrNull(pageDate.dayOfWeek.value - 1) ?: emptyList(),
                        cancelledReasons = cancelledForPage,
                        plans = if (isStudent) allPlans.filter { it.date == pageDate } else emptyList(),
                        onClassClick = onClassClick,
                        onPlanClick = onPlanClick,
                        onAddSlot = onAddSlot
                    )
                }
            } else {
                // --- Weekly: horizontal pager over weeks (swipe left = previous week) ---
                HorizontalPager(
                    state = weekPagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val pageWeekStart = weekMondayOf(today.plusDays(((page - WEEK_BASE) * 7L)))
                    val cancelledForPage = cancellationReasonsFor(pageWeekStart)
                    WeeklyGrid(
                        modifier = Modifier.fillMaxSize(),
                        scroll = gridScroll,
                        weekOffset = page - WEEK_BASE,
                        isCurrentPage = weekPagerState.currentPage == page,
                        todayIndex = today.dayOfWeek.value - 1,
                        isStudent = isStudent,
                        canManageSlots = canManageSlots,
                        classes = visibleTimetable,
                        cancelledReasons = cancelledForPage,
                        plans = if (isStudent) allPlans else emptyList(),
                        onClassClick = onClassClick,
                        onPlanClick = onPlanClick,
                        onAddSlot = onAddSlot
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
        // Sprint 14 (owner ask): the lecturer timetable FAB is GONE — classes
        // are built on the board itself (tap slot = edit, empty area = add) and
        // Class Management is reachable from the "Classes" bottom tab.
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

    detailClass?.let { (session, anchoredDate) ->
        ClassDetailPopup(session = session, onDismiss = { detailClass = null })
    }

    // Sprint 13D: lecturer class editor, ADD prefilled from the tapped slot.
    addClassPrefill?.let { prefill ->
        ClassEditorDialog(
            title = "Add class",
            initial = null,
            defaultDayIndex = prefill.date.dayOfWeek.value - 1,
            defaultStart = prefill.startTime,
            defaultEnd = prefill.endTime,
            errorMessage = classStatus,
            onDismiss = {
                addClassPrefill = null
                classManagementViewModel.clearStatus()
            },
            onSave = { subject, venue, dayIndex, start, end, medium, section, levels, attention, reason, image, slotDate, weeks ->
                classManagementViewModel.addClass(subject, venue, dayIndex, start, end, medium, section, levels, attention, reason, image, slotDate, weeks)
                if (classManagementViewModel.status.value == null) addClassPrefill = null
            }
        )
    }

    // Sprint 13D: lecturer class editor, EDIT tapped straight from a slot —
    // with "Remove for this week" (global grey-out + automatic notification)
    // and "Reinstate" when that week is already cancelled.
    editingClass?.let { session ->
        val anchor = editingClassAnchor ?: LocalDate.now()
        val cancelledNow = classCancellations.containsKey(
            "${session.id}|${weekMondayOf(anchor).toEpochDay()}"
        )
        ClassEditorDialog(
            title = "Edit class",
            initial = session,
            errorMessage = classStatus,
            isCancelledForWeek = cancelledNow,
            onRemoveForWeek = { reason ->
                classManagementViewModel.cancelClassForWeek(session, anchor, reason)
                editingClass = null
            },
            onReinstateForWeek = {
                classManagementViewModel.reinstateClassForWeek(session, anchor)
                editingClass = null
            },
            onDismiss = {
                editingClass = null
                classManagementViewModel.clearStatus()
            },
            onSave = { subject, venue, dayIndex, start, end, medium, section, levels, attention, reason, image, slotDate, weeks ->
                classManagementViewModel.updateClass(session, subject, venue, dayIndex, start, end, medium, section, levels, attention, reason, image, slotDate, weeks)
                if (classManagementViewModel.status.value == null) editingClass = null
            }
        )
    }
}

// The Monday of the week containing [date] (per-week suppression anchor).
private fun weekMondayOf(date: LocalDate): LocalDate =
    date.minusDays(((date.dayOfWeek.value) - 1).toLong())

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
// Sprint 9 (owner note 3): day circles are MULTI-SELECT — one plan per chosen day.
@Composable
private fun AddPlanDialog(
    title: String,
    initial: PersonalPlan?,
    anchorDate: LocalDate,
    defaultStart: String,
    defaultEnd: String,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, dates: List<LocalDate>, startTime: String, endTime: String, venue: String?) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initial?.title ?: "") }
    var venue by remember { mutableStateOf(initial?.venue ?: "") }
    var start by remember(initial, defaultStart) { mutableStateOf(initial?.startTime ?: defaultStart) }
    var end by remember(initial, defaultEnd) { mutableStateOf(initial?.endTime ?: defaultEnd) }
    // Sunday-first circles (wireframe); preselect the entry-point's weekday.
    var selectedCircles by remember(initial, anchorDate) {
        val initialCircle = CIRCLE_TO_DAY.indexOf(initial?.dayIndex ?: (anchorDate.dayOfWeek.value - 1))
        mutableStateOf(setOf(initialCircle))
    }
    var chooseTimeFor by remember { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }

    // The week the anchor lives in — the chosen circles pick dates inside this week.
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
                // S M T W T F S circles (wireframe) — SPRINT 9: multi-select (toggle).
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    DAY_LETTERS.forEachIndexed { index, letter ->
                        val selected = index in selectedCircles
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    // Toggle: tap a selected circle again to unselect
                                    // (at least one day must remain for a valid save).
                                    selectedCircles =
                                        if (selected && selectedCircles.size > 1) selectedCircles - index
                                        else selectedCircles + index
                                },
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
                    Button(onClick = { chooseTimeFor = "start" }) {
                        Text(formatAmPm(start))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("To", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = { chooseTimeFor = "end" }) {
                        Text(formatAmPm(end))
                    }
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
                val dates = selectedCircles.sorted().map { weekStart.plusDays(CIRCLE_TO_DAY[it].toLong()) }
                onSave(name, dates, start, end, venue)
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

// Shared (Sprint 9): also used by the lecturer's Add/Edit class dialog.
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun ChooseTimeDialog(
    initialTime: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialHour = initialTime.substringBefore(':').toIntOrNull() ?: 9
    val initialMinute = initialTime.substringAfter(':').toIntOrNull() ?: 0
    val state = rememberTimePickerState(
        initialHour = initialHour.coerceIn(0, 23),
        initialMinute = initialMinute.coerceIn(0, 59),
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
                        session.displayCode,
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

// Shared (Sprint 9): also used by the lecturer's Add/Edit class dialog.
internal fun formatAmPm(hhmm: String): String {
    val h = hhmm.substringBefore(':').toIntOrNull() ?: 0
    val m = hhmm.substringAfter(':').toIntOrNull() ?: 0
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12:${m.toString().padStart(2, '0')}${if (h < 12) "am" else "pm"}"
}
