package com.myuptm.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myuptm.data.notifications.NotificationHelper
import com.myuptm.data.repository.DataStorePersonalPlanRepository
import com.myuptm.data.repository.FirestoreClassRepository
import com.myuptm.domain.model.AppNotification
import com.myuptm.domain.model.AppUser
import com.myuptm.domain.model.ClassSession
import com.myuptm.domain.model.NotificationType
import com.myuptm.domain.model.PersonalPlan
import com.myuptm.domain.model.toPermissions
import com.myuptm.domain.repository.ClassRepository
import com.myuptm.domain.repository.PersonalPlanRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

// Sprint 8 Task 1: Daily = one-day pager with date chips; Weekly = full-week board.
enum class TimetableViewMode { DAILY, WEEKLY }

class TimetableViewModel(application: Application) : AndroidViewModel(application) {

    private val classRepository: ClassRepository = FirestoreClassRepository()

    // Bound by TimetableScreen whenever it knows the signed-in user. The VM is
    // activity-scoped, so it must re-bind on account/role switches.
    private val _boundUser = MutableStateFlow<AppUser?>(null)
    private var planRepository: PersonalPlanRepository? = null

    // Global classes grouped by day (index 0 = Monday).
    private val _weeklyTimetable = MutableStateFlow<List<List<ClassSession>>>(emptyList())
    val weeklyTimetable: StateFlow<List<List<ClassSession>>> = _weeklyTimetable.asStateFlow()

    // Sprint 9: the signed-in LECTURER's email, used to scope their Timetable to the
    // classes THEY teach (ownerEmail binding). Students see the full department list.
    private var lecturerEmail: String? = null

    // Device-local personal plans, only populated for a bound student.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val plansFlow = _boundUser.flatMapLatest { user ->
        val repo = planRepository
        if (user == null || repo == null) flowOf(emptyList()) else repo.observePlans()
    }

    // Device-local personal plans (one-time events with concrete dates), students only.
    val plans: StateFlow<List<PersonalPlan>> = plansFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Persisted clash warnings for the banner.
    @OptIn(ExperimentalCoroutinesApi::class)
    val clashWarnings: StateFlow<List<AppNotification>> = _boundUser.flatMapLatest { user ->
        val repo = planRepository
        if (user == null || repo == null) flowOf(emptyList()) else repo.observeWarnings()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Set when the block rule rejects a plan save; cleared on the next attempt.
    private val _planError = MutableStateFlow<String?>(null)
    val planError: StateFlow<String?> = _planError.asStateFlow()

    // Incremented when the Timetable tab is re-selected. UI observes this to snap back to today.
    private val _resetTrigger = MutableStateFlow(0)
    val resetTrigger: StateFlow<Int> = _resetTrigger.asStateFlow()

    // ---- Sprint 8 Task 1: view mode + week navigation (Weekly view) ----

    private val _viewMode = MutableStateFlow(TimetableViewMode.DAILY)
    val viewMode: StateFlow<TimetableViewMode> = _viewMode.asStateFlow()

    // Week offset from the current week (Weekly view arrows only; Daily always shows today).
    private val _weekOffset = MutableStateFlow(0)
    val weekOffset: StateFlow<Int> = _weekOffset.asStateFlow()

    // Baseline for clash detection: the first non-empty snapshot never warns.
    private var clashBaseline: List<ClassSession>? = null

    init {
        viewModelScope.launch {
            classRepository.observeClasses().collect { classes ->
                // Sprint 9 (owner note 1): lecturer scope — only THEIR classes
                // (ownerEmail == lecturer email). Demo seeds (ownerEmail == null) and
                // other lecturers' classes are no longer shown to them.
                val scoped = lecturerEmail?.let { email ->
                    classes.filter { it.ownerEmail == email }
                } ?: classes
                _weeklyTimetable.value = List(7) { day -> scoped.filter { it.dayIndex == day } }
                detectClassChanges(clashBaseline, scoped)
                if (scoped.isNotEmpty() || clashBaseline == null) {
                    // Establish the baseline on the first meaningful snapshot.
                    if (scoped.isNotEmpty()) clashBaseline = scoped
                }
            }
        }
    }

    // Incremented when the Timetable tab is re-selected. UI observes this to snap back to today.
    fun requestReset() {
        _resetTrigger.value++
        // Re-entry resets Weekly to the current week as well.
        _weekOffset.value = 0
    }

    // ---- Sprint 8 Task 1: view-mode + week navigation API ----

    fun setViewMode(mode: TimetableViewMode) {
        _viewMode.value = mode
    }

    // Syncs the Weekly pager's implied week offset into VM state (two-way binding only
    // writes when the value actually differs, so it never echoes back into the pager).
    fun syncWeekOffset(offset: Int) {
        if (_weekOffset.value != offset) _weekOffset.value = offset
    }

    fun returnToCurrentWeek() {
        _weekOffset.value = 0
    }

    fun clearPlanError() {
        _planError.value = null
    }

    // Re-binds the VM to the current signed-in user (activity-scoped VM safety).
    fun bindUser(user: AppUser?) {
        lecturerEmail = user?.takeIf { it.toPermissions().canManageClassGlobal }?.email
        if (_boundUser.value?.email == user?.email) return
        planRepository = user?.let { DataStorePersonalPlanRepository(getApplication(), it.email) }
        _boundUser.value = user
    }

    // ---- Personal plan CRUD (students only) ----
    // Sprint 9 (owner note 3): the plan dialog's day circles are MULTI-SELECT.
    // One PersonalPlan is saved per chosen weekday; ALL days are clash-checked
    // first and any conflict blocks the whole save with the usual message.

    fun addPlan(
        title: String,
        dates: List<java.time.LocalDate>,
        startTime: String,
        endTime: String,
        venue: String?
    ) {
        val repo = planRepository ?: return
        _planError.value = null
        if (dates.isEmpty()) return
        if (startTime >= endTime) {
            _planError.value = "End time must be after start time"
            return
        }
        findConflictingPlanSlot(dates, startTime, endTime)?.let { (clash, _) ->
            _planError.value =
                "${clash.subjectName} is held ${dayName(clash.dayIndex)} " +
                    "${clash.startTime}–${clash.endTime}. Unable to add the custom plan."
            return
        }
        viewModelScope.launch {
            dates.forEach { date ->
                repo.addPlan(
                    PersonalPlan(
                        id = UUID.randomUUID().toString(),
                        title = title.trim(),
                        date = date,
                        startTime = startTime,
                        endTime = endTime,
                        venue = venue?.trim()?.ifEmpty { null }
                    )
                )
            }
        }
    }

    fun updatePlan(
        plan: PersonalPlan,
        title: String,
        dates: List<java.time.LocalDate>,
        startTime: String,
        endTime: String,
        venue: String?
    ) {
        val repo = planRepository ?: return
        _planError.value = null
        if (dates.isEmpty()) return
        if (startTime >= endTime) {
            _planError.value = "End time must be after start time"
            return
        }
        // The edited plan moves to the FIRST chosen day; the extra days are added as
        // new one-time plans (same multi-select semantics as Add).
        val others = dates.drop(1)
        findConflictingPlanSlot(listOf(dates.first()) + others, startTime, endTime)?.let { (clash, _) ->
            _planError.value =
                "${clash.subjectName} is held ${dayName(clash.dayIndex)} " +
                    "${clash.startTime}–${clash.endTime}. Unable to keep the plan in this slot."
            return
        }
        viewModelScope.launch {
            repo.updatePlan(
                plan.copy(
                    title = title.trim(),
                    date = dates.first(),
                    startTime = startTime,
                    endTime = endTime,
                    venue = venue?.trim()?.ifEmpty { null }
                )
            )
            others.forEach { date ->
                repo.addPlan(
                    PersonalPlan(
                        id = UUID.randomUUID().toString(),
                        title = title.trim(),
                        date = date,
                        startTime = startTime,
                        endTime = endTime,
                        venue = venue?.trim()?.ifEmpty { null }
                    )
                )
            }
        }
    }

    fun removePlan(planId: String) {
        val repo = planRepository ?: return
        viewModelScope.launch { repo.removePlan(planId) }
    }

    fun clearWarnings() {
        val repo = planRepository ?: return
        viewModelScope.launch { repo.clearWarnings() }
    }

    // Rule 1: a student cannot place a plan on a slot where a class is held (same weekday).
    // Sprint 9: scans ALL selected days at once; returns the first (class, chosen date).
    private fun findConflictingPlanSlot(
        dates: List<java.time.LocalDate>,
        startTime: String,
        endTime: String
    ): Pair<ClassSession, java.time.LocalDate>? =
        dates.firstNotNullOfOrNull { date ->
            _weeklyTimetable.value.flatten().firstOrNull { cls ->
                cls.dayIndex == date.dayOfWeek.value - 1 &&
                    overlaps(startTime, endTime, cls.startTime, cls.endTime)
            }?.let { cls -> cls to date }
        }

    // Rule 2: warn the student when a class is added/moved onto an existing plan.
    private fun detectClassChanges(previous: List<ClassSession>?, current: List<ClassSession>) {
        val repo = planRepository ?: return
        val user = _boundUser.value ?: return
        if (!user.toPermissions().canManagePersonalPlans) return
        if (previous == null) return // baseline snapshot — never warn

        val previousById = previous.associateBy { it.id }
        val newClashes = mutableListOf<Pair<ClassSession, PersonalPlan>>()
        current.forEach { cls ->
            val old = previousById[cls.id]
            val slotChanged = old == null ||
                old.dayIndex != cls.dayIndex ||
                old.startTime != cls.startTime ||
                old.endTime != cls.endTime
            if (!slotChanged) return@forEach
            plans.value.forEach { plan ->
                if (plan.dayIndex == cls.dayIndex &&
                    overlaps(plan.startTime, plan.endTime, cls.startTime, cls.endTime)
                ) {
                    newClashes += cls to plan
                }
            }
        }
        if (newClashes.isEmpty()) return

        viewModelScope.launch {
            newClashes.forEach { (cls, plan) ->
                val message =
                    "${cls.subjectName} (${dayName(cls.dayIndex)}, ${cls.startTime}–${cls.endTime}) " +
                        "now clashes with your plan '${plan.title}'."
                repo.addWarning(
                    AppNotification(
                        id = UUID.randomUUID().toString(),
                        type = NotificationType.CLASH_WARNING,
                        title = "Timetable clash",
                        message = message,
                        senderName = "MyUPTM",
                        createdAtEpochMs = System.currentTimeMillis()
                    )
                )
                NotificationHelper.show(getApplication(), "Timetable clash", message)
            }
        }
    }

    // HH:mm zero-padded strings compare lexicographically — overlap if ranges intersect.
    private fun overlaps(startA: String, endA: String, startB: String, endB: String): Boolean =
        startA < endB && startB < endA

    private fun dayName(dayIndex: Int): String = DAY_NAMES[dayIndex.coerceIn(0, 6)]

    private companion object {
        val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    }
}