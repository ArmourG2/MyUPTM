package com.myuptm.domain.model

data class ClassSession(
    val id: String,
    val dayIndex: Int,
    val subjectName: String,
    val lecturerName: String,
    val venue: String,
    val teachingMedium: TeachingMedium,
    val startTime: String,
    val endTime: String
)

enum class TeachingMedium {
    ONLINE, OFFLINE
}