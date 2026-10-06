package com.myuptm.domain.repository

import com.myuptm.domain.model.AppUser
import kotlinx.coroutines.flow.Flow

// Contract for reading the signed-in user's record from the database.
// Returns null when the document does not exist.
interface UserRepository {
    suspend fun fetchUser(uid: String): Result<AppUser?>

    // Sprint 13 (pending-account approval): creates or refreshes the PENDING
    // skeleton document for a Google account that has no users/{uid} doc yet —
    // the Admin Dashboard picks it up for role assignment. Never writes
    // approved/role (approval is the admin's write).
    suspend fun registerPendingAccount(uid: String, email: String, fullName: String?): Result<Unit>

    // Sprint 13: live watch of one account's approval state — the waiting
    // screen auto-enters the app the moment the admin flips approved = true.
    fun observeAccountApproval(uid: String): Flow<AccountApproval>
}

// Lightweight snapshot of a user document's approval fields (pending docs may
// lack a role, so the full AppUser mapping can't be used here).
data class AccountApproval(
    val exists: Boolean,
    val approved: Boolean,
    val roleRaw: String? = null
)