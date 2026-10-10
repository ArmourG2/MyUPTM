package com.myuptm.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myuptm.domain.model.ClassSession
import com.myuptm.domain.model.PersonalPlan
import com.myuptm.ui.components.TimelineEntry
import com.myuptm.ui.components.toMinutes
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private const val GRID_START_HOUR = 8     // earliest class begins 8:00 (agreed)
private const val GRID_END_HOUR = 22      // last class ends 22:00 (agreed)
private const val HOUR_HEIGHT_DP = 56f
private const val GRID_START_MIN = GRID_START_HOUR * 60
private const val GRID_TOTAL_MINUTES = (GRID_END_HOUR - GRID_START_HOUR) * 60
private const val GRID_HEIGHT_DP = (GRID_END_HOUR - GRID_START_HOUR) * HOUR_HEIGHT_DP
private const val FREE_BAR_THRESHOLD_MINUTES = 30
private val RAIL_WIDTH = 48.dp

// Sprint 8 Task 1: single-day TIME GRID used by the Daily tab (rail list retired).
// date is the concrete day the page shows — one-time plans filter on it.
@Composable
fun SingleDayGrid(
    modifier: Modifier = Modifier,
    scroll: ScrollState,
    date: LocalDate,
    isStudent: Boolean,
    // Sprint 13D: lecturers tap empty timetable areas to add classes.
    canManageSlots: Boolean,
    // Sprint 13D: classId → cancellation reason for the shown date. Cancelled
    // classes GREY OUT ("Cancelled (this week)") instead of disappearing.
    cancelledReasons: Map<String, String?>,
    classes: List<ClassSession>,
    plans: List<PersonalPlan>,
    onClassClick: (ClassSession, LocalDate) -> Unit,
    onPlanClick: (PersonalPlan) -> Unit,
    onAddSlot: (date: LocalDate, startTime: String, endTime: String) -> Unit
) {
    val isToday = date == LocalDate.now()
    val density = LocalDensity.current
    var autoScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(date, classes, plans) {
        if (!isToday || autoScrolled) return@LaunchedEffect
        val target = firstUpcomingMinute(classes, plans)
        if (target != null) scrollToDateMinute(scroll, density, target)
        autoScrolled = true
    }
    Row(modifier = modifier) {
        HourRail(scroll)
        DayColumn(
            modifier = Modifier.weight(1f),
            scroll = scroll,
            isToday = isToday,
            dayIndex = date.dayOfWeek.value - 1,
            columnDate = date,
            showFreeBars = true,
            isStudent = isStudent,
            canManageSlots = canManageSlots,
            cancelledReasons = cancelledReasons,
            classes = classes,
            plans = plans,
            onClassClick = onClassClick,
            onPlanClick = onPlanClick,
            onAddSlot = onAddSlot
        )
    }
}

// Sprint 8 Task 1: full-week TIME GRID (08:00–22:00) in the approved Google Calendar styling.
// Paging lives in TimetableScreen; this renders one week: day header + hour rail + 7 columns.
@Composable
fun WeeklyGrid(
    modifier: Modifier = Modifier,
    scroll: ScrollState,
    weekOffset: Int,
    isCurrentPage: Boolean,
    todayIndex: Int,
    isStudent: Boolean,
    // Sprint 13D: lecturers tap empty timetable areas to add classes.
    canManageSlots: Boolean,
    // Sprint 13D: classId → cancellation reason for the displayed week
    // (cancelled blocks grey out instead of disappearing).
    cancelledReasons: Map<String, String?>,
    classes: List<List<ClassSession>>,
    plans: List<PersonalPlan>,
    onClassClick: (ClassSession, LocalDate) -> Unit,
    onPlanClick: (PersonalPlan) -> Unit,
    onAddSlot: (date: LocalDate, startTime: String, endTime: String) -> Unit
) {
    val today = LocalDate.now()
    // Monday of the displayed week (todayIndex = 0-based weekday index from Monday).
    val weekStart = today.plusDays(weekOffset * 7L - todayIndex)
    val dayHeaderFmt = java.time.format.DateTimeFormatter.ofPattern("d", Locale.getDefault())
    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primary

    // Auto-scroll this page to today's current/first item, exactly once, when visible.
    var autoScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(weekOffset, isCurrentPage, classes, plans) {
        if (!isCurrentPage || weekOffset != 0 || autoScrolled) return@LaunchedEffect
        val target = firstUpcomingMinute(
            classes.getOrNull(todayIndex) ?: emptyList(),
            if (isStudent) plans.filter { it.date == today } else emptyList()
        )
        if (target != null) scrollToDateMinute(scroll, density, target)
        autoScrolled = true
    }

    Column(modifier = modifier) {
        // --- Day header row (short name + date number, aligned with the columns) ---
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Spacer(Modifier.width(RAIL_WIDTH))
            (0..6).forEach { dayIndex ->
                val isToday = weekOffset == 0 && dayIndex == todayIndex
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        weekStart.plusDays(dayIndex.toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isToday) primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        weekStart.plusDays(dayIndex.toLong()).format(dayHeaderFmt),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isToday) primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        // --- Grid body: hour rail + 7 day columns sharing the same vertical scroll ---
        // Plans are ONE-TIME events: each column only shows the plans dated that exact day.
        Row(modifier = Modifier.fillMaxSize()) {
            HourRail(scroll)
            Row(modifier = Modifier.weight(1f)) {
                (0..6).forEach { dayIndex ->
                    val columnDate = weekStart.plusDays(dayIndex.toLong())
                    DayColumn(
                        modifier = Modifier.weight(1f),
                        scroll = scroll,
                        isToday = weekOffset == 0 && dayIndex == todayIndex,
                        dayIndex = dayIndex,
                        columnDate = columnDate,
                        showFreeBars = false,
                        isStudent = isStudent,
                        canManageSlots = canManageSlots,
                        cancelledReasons = cancelledReasons,
                        classes = classes.getOrNull(dayIndex) ?: emptyList(),
                        plans = plans.filter { it.date == columnDate },
                        onClassClick = onClassClick,
                        onPlanClick = onPlanClick,
                        onAddSlot = onAddSlot
                    )
                }
            }
        }
    }
}

// Left rail of hour labels ("8 AM"…"9 PM"); 8dp head-room keeps the first label visible.
@Composable
internal fun HourRail(scroll: ScrollState) {
    Column(
        modifier = Modifier
            .width(RAIL_WIDTH)
            .verticalScroll(scroll)
    ) {
        Spacer(Modifier.height(8.dp))
        repeat(GRID_END_HOUR - GRID_START_HOUR) {
            Box(modifier = Modifier.fillMaxWidth().height(HOUR_HEIGHT_DP.dp)) {
                Text(
                    hourLabel(GRID_START_HOUR + it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Sits on the hour line; the 8dp head-room spacer stops top clipping.
                    modifier = Modifier
                        .offset(y = (-8).dp)
                        .padding(start = 2.dp)
                )
            }
        }
    }
}

// One weekday column: hour tiles as background, class/plan blocks on top,
// tap-empty-slot → prefilled add-plan dialog (students only, 30-min snap).
// showFreeBars (Daily): ONE compact bar per gap, labelled "XH FREE UNTIL NEXT CLASS".
@Composable
internal fun DayColumn(
    modifier: Modifier = Modifier,
    scroll: ScrollState,
    isToday: Boolean,
    dayIndex: Int,
    columnDate: LocalDate,
    showFreeBars: Boolean,
    isStudent: Boolean,
    // Sprint 13D: lecturers manage classes straight on the timetable — empty
    // area taps open the prefilled class editor.
    canManageSlots: Boolean,
    // Sprint 13D: classId → cancellation reason for THIS column's week.
    // Cancelled classes GREY OUT ("Cancelled (this week)") instead of being
    // removed; the record survives in Firestore and returns next week.
    cancelledReasons: Map<String, String?>,
    classes: List<ClassSession>,
    plans: List<PersonalPlan>,
    // Sprint 13D: class clicks carry the day's date (needed for the per-week
    // removal anchor + editor context).
    onClassClick: (ClassSession, LocalDate) -> Unit,
    onPlanClick: (PersonalPlan) -> Unit,
    onAddSlot: (date: LocalDate, startTime: String, endTime: String) -> Unit
) {
    // Today's column is tinted (Google Calendar keeps today highlighted).
    val cellBackground = if (isToday) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val entries = entriesFor(classes, plans)

    Column(modifier = modifier.verticalScroll(scroll)) {
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(GRID_HEIGHT_DP.dp)
                .pointerInput(dayIndex, columnDate, isStudent, canManageSlots) {
                    if (!isStudent && !canManageSlots) return@pointerInput
                    // Empty-area tap: students → prefilled Add-plan (30-min snapped
                    // 1h slot); lecturers → prefilled Add-class (dispatch paddup in the screen).
                    detectTapGestures { position ->
                        val pxPerMinute = size.height / GRID_TOTAL_MINUTES.toFloat()
                        val slot = ((position.y / pxPerMinute) / 30).toInt() * 30
                        val abs = (GRID_START_MIN + slot)
                            .coerceIn(GRID_START_MIN, GRID_START_MIN + GRID_TOTAL_MINUTES - 30)
                        onAddSlot(columnDate, minutesToHHmm(abs), minutesToHHmm(abs + 60))
                    }
                }
        ) {
            // Hour-tile background (1dp gutters create the Google-like seams).
            Column(Modifier.matchParentSize()) {
                repeat(GRID_END_HOUR - GRID_START_HOUR) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(HOUR_HEIGHT_DP.dp)
                            .padding(1.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(cellBackground)
                    )
                }
            }
            // Blocks overlay, positioned by time offset.
            entries.forEach { entry ->
                when (entry) {
                    is TimelineEntry.ClassEntry -> {
                        val s = entry.session
                        val durMinutes = s.endTime.toMinutes() - s.startTime.toMinutes()
                        // Sprint 13D: cancelled-for-this-week classes GREY OUT with
                        // a "Cancelled" overlay instead of animating away — students
                        // still see the slot, just marked as not happening.
                        val cancelled = cancelledReasons.containsKey(s.id)
                        val greyedColors = if (cancelled) {
                            MaterialTheme.colorScheme.surfaceContainerHighest to
                                MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primaryContainer to
                                MaterialTheme.colorScheme.onPrimaryContainer
                        }
                        GridBlock(
                            title = if (cancelled) "${s.subjectName} (cancelled)" else s.subjectName,
                            subtitle = when {
                                cancelled -> cancelledReasons[s.id]?.let { "Why: $it" }
                                    ?: "Not happening this week"
                                durMinutes >= 90 -> s.lecturerName
                                else -> null
                            },
                            startMinute = s.startTime.toMinutes(),
                            endMinute = s.endTime.toMinutes(),
                            container = greyedColors.first,
                            onContainer = greyedColors.second,
                            onClick = { onClassClick(s, columnDate) }
                        )
                    }
                    is TimelineEntry.PlanEntry -> {
                        val p = entry.plan
                        val durMinutes = p.endTime.toMinutes() - p.startTime.toMinutes()
                        GridBlock(
                            title = p.title,
                            subtitle = if (durMinutes >= 90) "MY PLAN" else null,
                            startMinute = p.startTime.toMinutes(),
                            endMinute = p.endTime.toMinutes(),
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                            onClick = { onPlanClick(p) }
                        )
                    }
                }
            }
            // Daily glance: one bar per free gap, from 8:00 and between consecutive blocks.
            if (showFreeBars) {
                var cursor = GRID_START_MIN
                entries.forEach { entry ->
                    val start = when (entry) {
                        is TimelineEntry.ClassEntry -> entry.session.startTime
                        is TimelineEntry.PlanEntry -> entry.plan.startTime
                    }.toMinutes()
                    val end = when (entry) {
                        is TimelineEntry.ClassEntry -> entry.session.endTime
                        is TimelineEntry.PlanEntry -> entry.plan.endTime
                    }.toMinutes()
                    val gap = start - cursor
                    if (gap >= FREE_BAR_THRESHOLD_MINUTES) {
                        FreeLabelBar(cursor, start, gap)
                    }
                    if (end > cursor) cursor = end
                }
            }
        }
    }
}

// "XH FREE UNTIL NEXT CLASS" bar: one compact overlay per gap (Daily glance mode).
@Composable
private fun FreeLabelBar(startMinute: Int, endMinute: Int, gapMinutes: Int) {
    val topDp = ((startMinute - GRID_START_MIN).coerceAtLeast(0) / 60f) * HOUR_HEIGHT_DP
    val heightDp = (((endMinute - startMinute) / 60f) * HOUR_HEIGHT_DP).coerceAtLeast(20f)
    Box(
        modifier = Modifier
            .offset(y = topDp.dp)
            .fillMaxWidth()
            .height(heightDp.dp)
            .padding(horizontal = 3.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "${formatDuration(gapMinutes).uppercase()} FREE UNTIL NEXT CLASS",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// A positioned event block: rounded tinted rectangle stretched to its time range.
@Composable
private fun GridBlock(
    title: String,
    subtitle: String?,
    startMinute: Int,
    endMinute: Int,
    container: Color,
    onContainer: Color,
    onClick: () -> Unit
) {
    val topDp = ((startMinute - GRID_START_MIN).coerceAtLeast(0) / 60f) * HOUR_HEIGHT_DP
    val heightDp = (((endMinute - startMinute) / 60f) * HOUR_HEIGHT_DP).coerceAtLeast(20f)
    Box(
        modifier = Modifier
            .offset(y = topDp.dp)
            .fillMaxWidth()
            .height(heightDp.dp)
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = onContainer,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Merged + time-sorted entries for one day (same rule everywhere: classes then plans).
private fun entriesFor(classes: List<ClassSession>, plans: List<PersonalPlan>): List<TimelineEntry> =
    (classes.map { TimelineEntry.ClassEntry(it) } + plans.map { TimelineEntry.PlanEntry(it) })
        .sortedBy { it.startMinute() }

private fun TimelineEntry.startMinute(): Int = when (this) {
    is TimelineEntry.ClassEntry -> session.startTime
    is TimelineEntry.PlanEntry -> plan.startTime
}.toMinutes()

// Auto-scroll target: the first item at/after "now", else the day's first item.
private fun firstUpcomingMinute(classes: List<ClassSession>, plans: List<PersonalPlan>): Int? {
    val now = LocalTime.now().let { it.hour * 60 + it.minute }
    val starts = (classes.map { it.startTime.toMinutes() } + plans.map { it.startTime.toMinutes() }).sorted()
    return starts.firstOrNull { it >= now } ?: starts.firstOrNull()
}

// Scrolls so the target minute sits ~48dp below the visible top edge.
private suspend fun scrollToDateMinute(scroll: ScrollState, density: androidx.compose.ui.unit.Density, minute: Int) {
    val yDp = (((minute - GRID_START_MIN).toFloat() / 60f) * HOUR_HEIGHT_DP - 48f)
    with(density) { scroll.animateScrollTo(yDp.dp.toPx().roundToInt().coerceAtLeast(0)) }
}

// "8 AM" / "1 PM" style label for a 24h hour value.
private fun hourLabel(hour: Int): String {
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    return "$h12 ${if (hour < 12) "AM" else "PM"}"
}

// Minutes-since-midnight → "HH:mm" (matches the plans data format).
private fun minutesToHHmm(minutes: Int): String =
    "${(minutes / 60).toString().padStart(2, '0')}:${(minutes % 60).toString().padStart(2, '0')}"

// "HH:mm" → "2h 30m" style duration (used by block labels and free bars).
private fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}
