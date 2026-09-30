package com.myuptm.domain.repository

import com.myuptm.domain.model.AppUser

// Contract for reading the signed-in user's record from the database.
// Returns null when the document does not exist.
interface UserRepository {
    suspend fun fetchUser(uid: String): Result<AppUser?>
}