package com.myuptm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myuptm.domain.model.ClassSession
import com.myuptm.domain.model.TeachingMedium

internal fun String.toMinutes(): Int {
    val parts = split(":")
    return parts[0].toInt() * 60 + parts[1].toInt()
}

internal fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

@Composable
fun ClassTimelineItem(session: ClassSession) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val duration = session.endTime.toMinutes() - session.startTime.toMinutes()
    val mediumLabel = if (session.teachingMedium == TeachingMedium.ONLINE) "Online" else "Offline"

    TimelineItem(dotColor = primary, showBranch = true) {
        // Time with underline
        Column(Modifier.width(IntrinsicSize.Max)) {
            Text(
                "${session.startTime} · ${formatDuration(duration)}",
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariant
            )
            Spacer(Modifier.height(3.dp))
            Box(Modifier.fillMaxWidth().height(1.5.dp).background(primary.copy(alpha = 0.5f)))
        }
        Spacer(Modifier.height(12.dp))
        // Title with underline
        Column(Modifier.width(IntrinsicSize.Max)) {
            Text(
                session.subjectName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = onSurface
            )
            Spacer(Modifier.height(3.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).background(primary))
        }
        Spacer(Modifier.height(7.dp))
        Text(session.lecturerName, style = MaterialTheme.typography.bodyMedium, color = onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TagChip(session.venue, isAccent = false)
            TagChip(mediumLabel, isAccent = session.teachingMedium == TeachingMedium.ONLINE)
        }
    }
}

@Composable
fun GapTimelineItem(
    gapMinutes: Int,
    isCurrentGap: Boolean,
    gapEndMinutes: Int,
    nowMinutes: Int
) {
    val primary = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val label = if (isCurrentGap) {
        "Next class in ${formatDuration(gapEndMinutes - nowMinutes)}"
    } else {
        "${formatDuration(gapMinutes)} free"
    }

    TimelineItem(
        dotColor = if (isCurrentGap) primary else dim.copy(alpha = 0.5f),
        showBranch = false,
        bottomPadding = 18.dp
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isCurrentGap) primary else dim,
            letterSpacing = 2.sp
        )
    }
}

// The shared rail wrapper: draws this item's rail segment + dot + optional branch.
@Composable
private fun TimelineItem(
    dotColor: Color,
    showBranch: Boolean,
    dotCenterY: Dp = 10.dp,
    bottomPadding: Dp = 30.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val railColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val branchColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val railX = 10.dp
    val contentIndent = 36.dp
    val dotSize = 9.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val rx = railX.toPx()
                val lineW = 1.5.dp.toPx()
                // vertical rail segment (stacks into one continuous line)
                drawLine(railColor, Offset(rx, 0f), Offset(rx, size.height), lineW)
                // dot on the rail
                drawCircle(dotColor, dotSize.toPx() / 2f, Offset(rx, dotCenterY.toPx()))
                // branch from rail toward the content
                if (showBranch) {
                    drawLine(
                        branchColor,
                        Offset(rx, dotCenterY.toPx()),
                        Offset(contentIndent.toPx(), dotCenterY.toPx()),
                        1.5.dp.toPx()
                    )
                }
            }
    ) {
        Column(
            modifier = Modifier.padding(start = contentIndent, bottom = bottomPadding),
            content = content
        )
    }
}

@Composable
private fun TagChip(text: String, isAccent: Boolean) {
    val background = if (isAccent) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = if (isAccent) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = foreground)
    }
}