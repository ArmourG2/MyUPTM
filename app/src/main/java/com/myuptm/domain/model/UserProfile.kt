package com.myuptm.domain.model

data class UserProfile(
    val id: String,
    val fullName: String,
    val email: String,
    val studentId: String?, // Null for Staff
    val faculty: String,
    val programme: String?, // Null for Staff
    val semester: Int?,     // Null for Staff
    val role: UserRole,
    val avatarUrl: String? = null
) {
    // Computes "JD" from "John Doe" for the avatar circle
    val initials: String
        get() = fullName.split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
}