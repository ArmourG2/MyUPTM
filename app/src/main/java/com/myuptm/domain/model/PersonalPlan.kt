package com.myuptm.domain.model

import java.time.LocalDate

// Sprint 8: a student's own timetable entry — a ONE-TIME event on a concrete calendar date.
// A plan created in one week does NOT carry over to other weeks (Sprint 8 decision).
// Stored ONLY on-device (DataStore), never in Firestore — visible exclusively to its owner.
data class PersonalPlan(
    val id: String,
    val title: String,
    val date: LocalDate,    // concrete date; plans never repeat across weeks
    val startTime: String,  // HH:mm, zero-padded (string compare is safe)
    val endTime: String,    // HH:mm
    val venue: String? = null
) {
    // 0 = Monday … 6 = Sunday (matches ClassSession.dayIndex).
    val dayIndex: Int get() = date.dayOfWeek.value - 1
}
