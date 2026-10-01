package com.myuptm.domain.model

// Sprint 7B: single source of truth for what the signed-in user may do.
// Derived ONLY from role + isAdminPrivilege so the UI stays dumb.
data class UserPermissions(
    val canAddPost: Boolean,          // Admin, or Lecturer with admin privilege
    val canRemovePost: Boolean,       // Admin, or Lecturer with admin privilege
    val canNotifyStudents: Boolean,   // Manual "Notify Students" (same gate as canAddPost)
    val canManageClassGlobal: Boolean // Lecturer edits their own classes (admins don't teach)
    ,
    val canManagePersonalPlans: Boolean, // Student adds device-local timetable plans
    val showTimetable: Boolean        // Admin has no Timetable tab (7A decision)
)

// Maps the Firestore identity record onto concrete UI permissions.
fun AppUser.toPermissions(): UserPermissions = when (role) {
    UserRole.STUDENT -> UserPermissions(
        canAddPost = false,
        canRemovePost = false,
        canNotifyStudents = false,
        canManageClassGlobal = false,
        canManagePersonalPlans = true,
        showTimetable = true
    )
    UserRole.LECTURER -> UserPermissions(
        canAddPost = isAdminPrivilege,
        canRemovePost = isAdminPrivilege,
        canNotifyStudents = isAdminPrivilege,
        canManageClassGlobal = true,
        canManagePersonalPlans = false,
        showTimetable = true
    )
    UserRole.ADMIN -> UserPermissions(
        canAddPost = true,
        canRemovePost = true,
        canNotifyStudents = true,
        canManageClassGlobal = false,
        canManagePersonalPlans = false,
        showTimetable = false
    )
}