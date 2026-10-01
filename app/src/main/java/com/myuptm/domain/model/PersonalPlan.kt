package com.myuptm.domain.model

// Sprint 7B: a student's own timetable entry. Stored ONLY on-device (DataStore),
// never in Firestore — visible exclusively to the student who created it.
data class PersonalPlan(
    val id: String,
    val title: String,
    val dayIndex: Int,      // 0 = Monday … 6 = Sunday (matches ClassSession.dayIndex)
    val startTime: String,  // HH:mm, zero-padded (string compare is safe)
    val endTime: String,    // HH:mm
    val venue: String? = null
)