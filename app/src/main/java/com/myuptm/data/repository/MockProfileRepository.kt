package com.myuptm.data.repository

import com.myuptm.domain.model.UserProfile
import com.myuptm.domain.model.UserRole
import com.myuptm.domain.repository.ProfileRepository

class MockProfileRepository : ProfileRepository {
    override fun getCurrentUser(): UserProfile {
        // Placeholder data. Will be replaced by Firebase Auth data in Sprint 6.
        return UserProfile(
            id = "12345",
            fullName = "Akmal Irfan Bin Abdul Muqsith",
            email = "kl2412018161@student.uptm.edu.my",
            studentId = "AM2412018161",
            faculty = "Faculty of Computing & Multimedia",
            programme = "Diploma in Computer Science",
            semester = 6,
            role = UserRole.STUDENT
        )
    }
}