package com.myuptm.domain.repository

import com.myuptm.domain.model.UserProfile

interface ProfileRepository {
    suspend fun getUserProfile(): Result<UserProfile>
}