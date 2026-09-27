package com.myuptm.data.repository

import com.myuptm.domain.model.ClassSession
import com.myuptm.domain.model.TeachingMedium
import com.myuptm.domain.repository.TimetableRepository

class MockTimetableRepository : TimetableRepository {

    override fun getWeeklyTimetable(): List<ClassSession> {
        return listOf(
            // Monday (0)
            ClassSession("1", 0, "Mobile Development", "Dr. Ali", "Lab 2", TeachingMedium.OFFLINE, "09:00", "11:00"),
            ClassSession("2", 0, "Database Systems", "Dr. Abu", "Zoom", TeachingMedium.ONLINE, "13:00", "15:00"),
            // Tuesday (1)
            ClassSession("3", 1, "Web Programming", "Dr. Kumar", "Lab 4", TeachingMedium.OFFLINE, "10:00", "12:00"),
            ClassSession("4", 1, "Mathematics II", "Dr. Omar", "Hall A", TeachingMedium.OFFLINE, "14:00", "16:00"),
            // Wednesday (2)
            ClassSession("5", 2, "Mobile Development", "Dr. Ali", "Lab 2", TeachingMedium.OFFLINE, "09:00", "10:00"),
            ClassSession("6", 2, "Cloud Computing", "Dr. Lim", "G.Meet", TeachingMedium.ONLINE, "16:00", "18:00"),
            // Thursday (3)
            ClassSession("7", 3, "Data Structures", "Dr. Noor", "Lab 3", TeachingMedium.OFFLINE, "09:00", "11:00"),
            ClassSession("8", 3, "Ethics in Computing", "Dr. Zed", "Hall B", TeachingMedium.OFFLINE, "15:00", "17:00"),
            // Friday (4)
            ClassSession("9", 4, "Database Systems", "Dr. Abu", "Lab 1", TeachingMedium.OFFLINE, "10:00", "12:00"),
            ClassSession("10", 4, "Soft Skills", "Dr. Ivy", "Hall C", TeachingMedium.OFFLINE, "13:00", "14:00")
        )
    }
}