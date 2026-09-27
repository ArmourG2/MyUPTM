package com.myuptm.domain.model

data class ClassSession(
    val id: String,
    val dayIndex: Int,              // 0 = Monday, 4 = Friday
    val subjectName: String,
    val lecturerName: String,
    val venue: String,
    val teachingMedium: TeachingMedium,
    val startTime: String,          // "09:00"
    val endTime: String             // "11:00"
)

enum class TeachingMedium {
    ONLINE, OFFLINE
}