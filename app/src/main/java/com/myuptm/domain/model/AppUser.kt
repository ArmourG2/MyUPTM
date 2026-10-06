package com.myuptm.domain.model

// Represents the signed-in user's identity record stored in Firestore.
// Holds role + approval data used for authorization and role-based UI gating.
data class AppUser(
    val uid: String,
    val email: String,
    val role: UserRole,
    val approved: Boolean,
    val isAdminPrivilege: Boolean = false,
    // Sprint 13C: assigned via the admin approval dialog (students get
    // faculty/programme/semester + auto-derived studentId; lecturers faculty).
    val fullName: String? = null,
    val faculty: String? = null,
    val programme: String? = null,
    val semester: Int? = null,
    val studentId: String? = null
)