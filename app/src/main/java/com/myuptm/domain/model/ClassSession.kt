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
    val updatedAt: Long? = null,
    // Sprint 13D (owner rules): repeat rule —
    //  - date = null, weeks = null → recurring (faculty preset OR custom
    //    PERMANENT "every week" class),
    //  - date != null + weeks = 1  → ONCE: visible only in its own week,
    //  - date != null + weeks = N  → RANGE: visible for N weeks from that week.
    val date: Long? = null,
    val weeks: Int? = null
) {
    // Sprint 13D: a CUSTOM class = lecturer-created (ownerEmail != null), i.e.
    // NOT a faculty preset slot — edit/delete rights live in Class Management.
    val isCustom: Boolean get() = ownerEmail != null
    val isOneOff: Boolean get() = date != null

    // True when this class should appear in the week starting [weekMonday].
    // Recurring/permanent classes appear every week; dated classes follow their
    // repeat rule (own week for ONCE, weeks span for RANGE).
    fun visibleInWeek(weekMonday: java.time.LocalDate): Boolean {
        if (date == null) return true
        val startD = java.time.LocalDate.ofEpochDay(date)
        val startMonday = startD.minusDays(((startD.dayOfWeek.value) - 1).toLong())
        val shown = weekMonday.toEpochDay()
        val lo = startMonday.toEpochDay()
        return shown >= lo && shown <= lo + (((weeks ?: 1) - 1).toLong() * 7)
    }

    // Human label for the repeat rule (roster/directory tags).
    val repeatRuleLabel: String
        get() = when {
            date == null -> "Every week"
            else -> when (weeks) {
                null, 1 -> "This week only"
                else -> "$weeks weeks"
            }
        }

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
