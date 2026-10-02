package com.myuptm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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

internal fun String.toMinutes(): Int {
    val parts = split(":")
    return parts[0].toInt() * 60 + parts[1].toInt()
}

// Sprint 8 Task 1: shared merged-entry model for the timetable grids (classes + student
// plans; ordering/snap logic lives with the grid and timeline consumers).
sealed interface TimelineEntry {
    data class ClassEntry(val session: ClassSession) : TimelineEntry
    data class PlanEntry(val plan: PersonalPlan) : TimelineEntry
}

// A day chip in the top date selector: short day + date number, highlighted when selected,
// ringed when it is today (sketch: row of rounded squares, selected day filled).
@Composable
fun DayChip(
    label: String,
    dateText: String,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val background = when {
        selected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val foreground = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .then(
                if (isToday && !selected) Modifier.border(
                    1.5.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    RoundedCornerShape(14.dp)
                ) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = foreground)
        Text(
            dateText,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = foreground
        )
    }
}
