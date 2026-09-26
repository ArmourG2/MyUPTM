package com.myuptm.domain.repository

import com.myuptm.domain.model.User

interface AuthRepository {

    val currentUserId: String?

    suspend fun signInWithGoogle(idToken: String): Result<User>

    suspend fun signOut()
}