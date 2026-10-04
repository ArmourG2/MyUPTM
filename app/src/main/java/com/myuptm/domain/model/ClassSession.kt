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
    // Sprint 8: section ("Subject_XX" grouping → the digit run after the underscore
    // selects the roster). A class belongs to exactly one lecturer section.
    val section: String = "",
    // Sprint 9: merged classes serve MULTIPLE programme levels (e.g. SWC3403/3713
    // = Diploma code + Degree code merged into one section), so a class carries a
    // SET of levels and Class Management level filters match any of them.
    val levels: List<ClassLevel> = listOf(ClassLevel.DIPLOMA),
    // Sprint 7B: ownership + change tracking for global (Firestore) classes.
    // ownerEmail == null marks the auto-seeded departmental classes shown to
    // everyone EXCEPT the owning lecturer (lecturers see ONLY their own).
    val ownerEmail: String? = null,
    val updatedAt: Long? = null
) {
    // Primary level = the merged set's first entry (display fallback / legacy reads).
    val level: ClassLevel get() = levels.firstOrNull() ?: ClassLevel.DIPLOMA

    // Display code mirrors the university convention "Subject_XX" (MAT2113_01);
    // a blank section shows the bare subject name.
    val displayCode: String
        get() = if (section.isBlank()) subjectName else "${subjectName}_$section"
}

enum class TeachingMedium {
    ONLINE, OFFLINE
}
