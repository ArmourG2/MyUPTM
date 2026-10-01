package com.myuptm.domain.model

data class ClassSession(
    val id: String,
    val dayIndex: Int,
    val subjectName: String,
    val lecturerName: String,
    val venue: String,
    val teachingMedium: TeachingMedium,
    val startTime: String,
    val endTime: String,
    // Sprint 7B: ownership + change tracking for global (Firestore) classes.
    // ownerEmail == null marks the auto-seeded demo classes (editable by any lecturer).
    val ownerEmail: String? = null,
    val updatedAt: Long? = null
)

enum class TeachingMedium {
    ONLINE, OFFLINE
}