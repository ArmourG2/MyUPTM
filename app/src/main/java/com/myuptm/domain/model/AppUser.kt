package com.myuptm.domain.model

// Represents the signed-in user's identity record stored in Firestore.
// Holds role + approval data used for authorization and role-based UI gating.
data class AppUser(
    val uid: String,
    val email: String,
    val role: UserRole,
    val approved: Boolean,
    val isAdminPrivilege: Boolean = false
)