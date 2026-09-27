package com.myuptm.domain.repository

import com.myuptm.domain.model.ClassSession

interface TimetableRepository {
    fun getWeeklyTimetable(): List<ClassSession>
}