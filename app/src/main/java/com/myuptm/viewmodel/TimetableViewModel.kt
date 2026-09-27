package com.myuptm.viewmodel

import androidx.lifecycle.ViewModel
import com.myuptm.data.repository.MockTimetableRepository
import com.myuptm.domain.model.ClassSession
import com.myuptm.domain.repository.TimetableRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TimetableViewModel : ViewModel() {

    private val repository: TimetableRepository = MockTimetableRepository()

    private val _weeklyTimetable = MutableStateFlow<List<List<ClassSession>>>(emptyList())
    val weeklyTimetable: StateFlow<List<List<ClassSession>>> = _weeklyTimetable.asStateFlow()

    // Incremented when the Timetable tab is re-selected. UI observes this to snap back to today.
    private val _resetTrigger = MutableStateFlow(0)
    val resetTrigger: StateFlow<Int> = _resetTrigger.asStateFlow()

    init {
        loadTimetable()
    }

    fun requestReset() {
        _resetTrigger.value++
    }

    private fun loadTimetable() {
        val all = repository.getWeeklyTimetable()
        _weeklyTimetable.value = List(7) { day ->
            all.filter { it.dayIndex == day }
        }
    }
}