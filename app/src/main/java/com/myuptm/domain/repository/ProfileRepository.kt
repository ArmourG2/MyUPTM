package com.myuptm.domain.repository

import com.myuptm.domain.model.UserProfile

interface ProfileRepository {
    suspend fun getUserProfile(): Result<UserProfile>

    // Sprint 8 Task 3: persists the uploaded avatar URL into the user's identity document.
    suspend fun updateAvatarUrl(url: String): Result<Unit>
}
