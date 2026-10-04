package com.myuptm.data.repository

import com.myuptm.domain.model.ClassLevel
import com.myuptm.domain.model.ClassSession
import com.myuptm.domain.model.TeachingMedium
import com.myuptm.domain.repository.TimetableRepository

// Sprint 9: the seed table is now the REAL departmental timetable the owner provided
// (TimetableCC101). It is the STUDENT-facing board ("classes students take"); each
// subject has its own lecturer. The SWC3403/3713_01 slots additionally carry
// ownerEmail = the POC demo lecturer account, so that account's Timetable/Classes
// show exactly those two slots (its "taught" classes).
@Deprecated("Seed source for FirestoreClassRepository — not a mock anymore, real dept data")
class SeedTimetableRepository : TimetableRepository {

    override fun getWeeklyTimetable(): List<ClassSession> = realSeed

    companion object {
        // The one POC lecturer account that "teaches" SWC3403/3713 (owner-provided).
        const val DEMO_LECTURER_EMAIL = "zjosh9053@gmail.com"
        // Lecturer display name comes from their account profile (full name).
        private const val DEMO_LECTURER_NAME = "AKMAL IRFAN BIN ABDUL MUQSITH"

        private val realSeed = listOf(
            // Monday (0)
            ClassSession(
                id = "d1", dayIndex = 0, subjectName = "MAT2113", lecturerName = "Miss Nur",
                venue = "CR5.19", teachingMedium = TeachingMedium.OFFLINE,
                startTime = "08:00", endTime = "10:00",
                section = "01", levels = listOf(ClassLevel.DIPLOMA)
            ),
            ClassSession(
                id = "d2", dayIndex = 0, subjectName = "FYP3024", lecturerName = "NORFAZLINA BINTI JOHAR",
                venue = "LT3.7", teachingMedium = TeachingMedium.OFFLINE,
                startTime = "16:00", endTime = "18:00",
                section = "01", levels = listOf(ClassLevel.DIPLOMA)
            ),
            // Tuesday (1)
            ClassSession(
                id = "d3", dayIndex = 1, subjectName = "MPU2432", lecturerName = "KHADIJAH MAISARAH BINTI MOHD NAZRUDIN",
                venue = "LR4.12", teachingMedium = TeachingMedium.OFFLINE,
                startTime = "12:00", endTime = "14:00",
                section = "06", levels = listOf(ClassLevel.DIPLOMA)
            ),
            // SWC3403/3713 = diploma code + degree code merged; the POC lecturer owns it.
            ClassSession(
                id = "d4", dayIndex = 1, subjectName = "SWC3403/3713", lecturerName = DEMO_LECTURER_NAME,
                venue = "LR4.08", teachingMedium = TeachingMedium.OFFLINE,
                startTime = "14:00", endTime = "16:00",
                section = "01", levels = listOf(ClassLevel.DIPLOMA, ClassLevel.DEGREE),
                ownerEmail = DEMO_LECTURER_EMAIL
            ),
            ClassSession(
                id = "d5", dayIndex = 1, subjectName = "NWC24132", lecturerName = "NOR HAFIZA BINTI ABD SAMAD",
                venue = "LAB NETWORK", teachingMedium = TeachingMedium.OFFLINE,
                startTime = "18:00", endTime = "20:00",
                section = "04", levels = listOf(ClassLevel.DIPLOMA)
            ),
            // Wednesday (2)
            ClassSession(
                id = "d6", dayIndex = 2, subjectName = "MAT2113", lecturerName = "Miss Nur",
                venue = "CR5.19", teachingMedium = TeachingMedium.OFFLINE,
                startTime = "16:00", endTime = "18:00",
                section = "01", levels = listOf(ClassLevel.DIPLOMA)
            ),
            ClassSession(
                id = "d7", dayIndex = 2, subjectName = "MPU2432", lecturerName = "KHADIJAH MAISARAH BINTI MOHD NAZRUDIN",
                venue = "LR4.12", teachingMedium = TeachingMedium.OFFLINE,
                startTime = "12:00", endTime = "13:00",
                section = "06", levels = listOf(ClassLevel.DIPLOMA)
            ),
            // Thursday (3)
            ClassSession(
                id = "d8", dayIndex = 3, subjectName = "NWC24132", lecturerName = "NOR HAFIZA BINTI ABD SAMAD",
                venue = "LAB NETWORK", teachingMedium = TeachingMedium.OFFLINE,
                startTime = "12:00", endTime = "14:00",
                section = "04", levels = listOf(ClassLevel.DIPLOMA)
            ),
            // Friday (4)
            ClassSession(
                id = "d9", dayIndex = 4, subjectName = "SWC3403/3713", lecturerName = DEMO_LECTURER_NAME,
                venue = "LR4.08", teachingMedium = TeachingMedium.OFFLINE,
                startTime = "10:00", endTime = "12:00",
                section = "01", levels = listOf(ClassLevel.DIPLOMA, ClassLevel.DEGREE),
                ownerEmail = DEMO_LECTURER_EMAIL
            ),
            ClassSession(
                id = "d10", dayIndex = 4, subjectName = "FYP3024", lecturerName = "NORFAZLINA BINTI JOHAR",
                venue = "LT3.7", teachingMedium = TeachingMedium.OFFLINE,
                startTime = "17:00", endTime = "18:00",
                section = "01", levels = listOf(ClassLevel.DIPLOMA)
            )
        )
    }
}
