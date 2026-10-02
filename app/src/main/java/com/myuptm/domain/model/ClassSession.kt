package com.myuptm.domain.model

// Sprint 8: programmes are grouped by level (Class Management tabs: Diploma/Degree/Master).
enum class ClassLevel {
    DIPLOMA, DEGREE, MASTER
}

data class ClassSession(
    val id: String,
    val dayIndex: Int,
    val subjectName: String,
    val lecturerName: String,
    val venue: String,
    val teachingMedium: TeachingMedium,
    val startTime: String,
    val endTime: String,
    // Sprint 8: section (e.g. "Section 1") + programme level. Every class belongs to
    // exactly one lecturer section of one level.
    val section: String = "",
    val level: ClassLevel = ClassLevel.DIPLOMA,
    // Sprint 7B: ownership + change tracking for global (Firestore) classes.
    // ownerEmail == null marks the auto-seeded demo classes (editable by any lecturer).
    val ownerEmail: String? = null,
    val updatedAt: Long? = null
)

enum class TeachingMedium {
    ONLINE, OFFLINE
}
