package com.myuptm.data.repository

// Sprint 9: static metadata for the student's enrolled sections (Academic Overview),
// structured like the wireframe: "CODE — title, Section X, programme line, hours".
// POC: single demo student (Section 1 of every subject except the merged ones).
object EnrolledSectionsData {

    data class EnrolledSection(
        val subjectCode: String,      // "SWC3403" (first code) / display handling for SWC pair
        val subjectTitle: String,
        val sectionLabel: String,     // "Section 1" / "Section 6 - KIAR DIPLOMA"
        val programme: String,        // "CC101 - DIPLOMA IN COMPUTER SCIENCE - Homegrown 0826"
        val hours: Int
    )

    // Matches the seeded Firestore classes: MAT2113_01, FYP3024_01, MPU2432_06,
    // NWC24132_04, SWC3403/3713_01 (also serves SWC3713, the Degree code).
    val ITEMS = listOf(
        EnrolledSection("FYP3024", "Computing Project", "Section 1", "CC101 - DIPLOMA IN COMPUTER SCIENCE - Homegrown 0826", 56),
        EnrolledSection("MAT2113", "Calculus", "Section 1", "CC101 - DIPLOMA IN COMPUTER SCIENCE - Homegrown 0826", 42),
        EnrolledSection("MPU2432", "Kursus Integriti Anti-Rasuah", "Section 6 - KIAR DIPLOMA", "CC101 - DIPLOMA IN COMPUTER SCIENCE - Homegrown 0826", 28),
        EnrolledSection("NWC24132", "Introduction to Networks", "Section 4", "CC101 - DIPLOMA IN COMPUTER SCIENCE - Homegrown 0826", 42),
        EnrolledSection("SWC3403/3713", "Introduction to Mobile Application Development", "Section 1", "CC101 - DIPLOMA IN COMPUTER SCIENCE - Homegrown 0826", 42)
    )

    const val MENTOR_NAME = "AKMAL IRFAN BIN ABDUL MUQSITH"
}
