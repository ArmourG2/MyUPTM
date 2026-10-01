package com.myuptm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myuptm.domain.model.ClassSession
import com.myuptm.domain.model.PersonalPlan
import com.myuptm.ui.components.ClassTimelineItem
import com.myuptm.ui.components.GapTimelineItem
import com.myuptm.ui.components.PlanTimelineItem
import com.myuptm.ui.components.toMinutes

private const val GAP_THRESHOLD_MINUTES = 30

// Sprint 7B: a day's items can be official classes or the student's own plans.
private sealed interface TimelineEntry {
    data class ClassEntry(val session: ClassSession) : TimelineEntry
    data class PlanEntry(val plan: PersonalPlan) : TimelineEntry
}

@Composable
fun DayTimeline(
    dayIndex: Int,
    isToday: Boolean,
    classes: List<ClassSession>,
    plans: List<PersonalPlan> = emptyList(),
    onPlanClick: (PersonalPlan) -> Unit = {}
) {
    val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val dayName = dayNames[dayIndex]

    if (classes.isEmpty() && plans.isEmpty()) {
        DayOffView(dayName)
        return
    }

    val nowMinutes = if (isToday) {
        val now = java.time.LocalTime.now()
        now.hour * 60 + now.minute
    } else -1

    // Sprint 7B: merge official classes and personal plans, ordered by start time.
    val entries: List<TimelineEntry> = (
        classes.map { TimelineEntry.ClassEntry(it) } +
            plans.map { TimelineEntry.PlanEntry(it) }
        ).sortedBy { entry ->
        when (entry) {
            is TimelineEntry.ClassEntry -> entry.session.startTime
            is TimelineEntry.PlanEntry -> entry.plan.startTime
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
    ) {
        DayHeader(dayName, isToday)
        Spacer(Modifier.height(18.dp))

        entries.forEachIndexed { index, entry ->
            when (entry) {
                is TimelineEntry.ClassEntry -> ClassTimelineItem(entry.session)
                is TimelineEntry.PlanEntry -> PlanTimelineItem(entry.plan) { onPlanClick(entry.plan) }
            }

            val next = entries.getOrNull(index + 1)
            // Gaps are only drawn between two consecutive official class items.
            if (entry is TimelineEntry.ClassEntry && next is TimelineEntry.ClassEntry) {
                val gapStart = entry.session.endTime.toMinutes()
                val gapEnd = next.session.startTime.toMinutes()
                val gapMinutes = gapEnd - gapStart
                if (gapMinutes >= GAP_THRESHOLD_MINUTES) {
                    val isCurrentGap = isToday && nowMinutes in gapStart until gapEnd
                    GapTimelineItem(gapMinutes, isCurrentGap, gapEnd, nowMinutes)
                }
            }
        }

        Spacer(Modifier.height(2.dp))
        Footer(classes.size, plans.size)
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun DayHeader(dayName: String, isToday: Boolean) {
    Column(Modifier.width(IntrinsicSize.Max)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                dayName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isToday) {
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("TODAY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun DayOffView(dayName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(dayName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(16.dp))
        Text("Day Off!", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("No classes scheduled", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Footer(classCount: Int, planCount: Int) {
    val classText = "$classCount class${if (classCount != 1) "es" else ""}"
    val planText = if (planCount > 0) " · $planCount personal plan${if (planCount != 1) "s" else ""}" else ""
    Text(
        "No more items · $classText$planText today",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    )
}