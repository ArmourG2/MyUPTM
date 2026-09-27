package com.myuptm.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myuptm.viewmodel.TimetableViewModel

@Composable
fun TimetableScreen(
    viewModel: TimetableViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as ViewModelStoreOwner
    )
) {
    val weeklyTimetable by viewModel.weeklyTimetable.collectAsState()
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

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val dayIndex = pageIndex % 7
            val isToday = pageIndex == todayPage
            DayTimeline(
                dayIndex = dayIndex,
                isToday = isToday,
                classes = weeklyTimetable.getOrNull(dayIndex) ?: emptyList()
            )
        }
    }
}

private fun getCurrentDayIndex(): Int {
    return java.time.LocalDate.now().dayOfWeek.value - 1
}