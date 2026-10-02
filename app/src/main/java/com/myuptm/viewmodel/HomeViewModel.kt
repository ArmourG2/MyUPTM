package com.myuptm.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myuptm.domain.model.ClassSession
import com.myuptm.domain.model.Post
import com.myuptm.domain.model.SupportLink
import com.myuptm.domain.model.SupportLinks
import com.myuptm.domain.repository.ClassRepository
import com.myuptm.domain.repository.PostsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

data class HomeUiState(
    val nextClass: ClassSession? = null,
    val countdownLabel: String = "No upcoming classes",
    val announcements: List<Post> = emptyList(),
    val supportLinks: List<SupportLink> = SupportLinks.ITEMS
)

class HomeViewModel(
    private val classRepository: ClassRepository,
    private val postsRepository: PostsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Sprint 7B: classes are global (Firestore) — stay live so the Next Class
        // card reflects lecturer edits immediately. Posts feed is live too (Sprint 8).
        viewModelScope.launch {
            combine(
                classRepository.observeClasses(),
                postsRepository.observePosts()
            ) { classes, posts -> classes to posts }.collect { (classes, posts) ->
                val now = LocalDateTime.now()
                val next = findNextClass(classes, now)
                _uiState.value = HomeUiState(
                    nextClass = next?.first,
                    countdownLabel = buildCountdownLabel(next, now),
                    announcements = posts.take(3),
                    supportLinks = SupportLinks.ITEMS
                )
            }
        }
    }

    // Returns (session, dayOffset). offset 0 = today, 1 = tomorrow, etc.
    private fun findNextClass(
        classes: List<ClassSession>,
        now: LocalDateTime
    ): Pair<ClassSession, Int>? {
        val todayIndex = now.dayOfWeek.value - 1 // Monday = 0, matches dayIndex
        for (offset in 0..6) {
            val dayIndex = (todayIndex + offset) % 7
            val candidates = classes
                .filter { it.dayIndex == dayIndex }
                .sortedBy { it.startTime }
            val session = if (offset == 0) {
                // Today: only sessions that have not ended yet
                candidates.firstOrNull { LocalTime.parse(it.endTime) > now.toLocalTime() }
            } else {
                candidates.firstOrNull()
            }
            if (session != null) return session to offset
        }
        return null
    }

    private fun buildCountdownLabel(
        next: Pair<ClassSession, Int>?,
        now: LocalDateTime
    ): String {
        if (next == null) return "No upcoming classes"
        val (session, offset) = next
        val start = LocalTime.parse(session.startTime)
        val end = LocalTime.parse(session.endTime)
        val today = now.toLocalTime()
        return when {
            offset == 0 && start > today ->
                "Next class in ${formatMinutes(ChronoUnit.MINUTES.between(today, start))}"
            offset == 0 ->
                "Ongoing – ends in ${formatMinutes(ChronoUnit.MINUTES.between(today, end))}"
            offset == 1 ->
                "Next: Tomorrow, ${session.startTime}"
            else ->
                "Next: ${dayName(session.dayIndex)}, ${session.startTime}"
        }
    }

    private fun formatMinutes(totalMinutes: Long): String {
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    private fun dayName(dayIndex: Int): String =
        DayOfWeek.of(dayIndex + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault())
}