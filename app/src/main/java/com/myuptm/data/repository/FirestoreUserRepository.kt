package com.myuptm.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.myuptm.domain.model.AppUser
import com.myuptm.domain.model.UserRole
import com.myuptm.domain.repository.UserRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// Reads the user's identity document from the Firestore "users" collection.
class FirestoreUserRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : UserRepository {

    // Fetches a single user document by uid; returns null if it does not exist.
    override suspend fun fetchUser(uid: String): Result<AppUser?> {
        return suspendCancellableCoroutine { continuation ->
            db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        continuation.resume(Result.success(doc.toAppUser(uid)))
                    } else {
                        continuation.resume(Result.success(null))
                    }
                }
                .addOnFailureListener { e ->
                    continuation.resume(Result.failure(e))
                }
        }
    }

    // Maps a Firestore document into the AppUser domain model.
    private fun DocumentSnapshot.toAppUser(uid: String): AppUser? {
        val role = getString("role")?.toUserRole() ?: return null
        return AppUser(
            uid = uid,
            email = getString("email").orEmpty(),
            role = role,
            approved = getBoolean("approved") ?: false,
            isAdminPrivilege = getBoolean("isAdminPrivilege") ?: false
        )
    }

    // Safely converts the stored role string into the UserRole enum.
    private fun String.toUserRole(): UserRole? = when (lowercase()) {
        "student" -> UserRole.STUDENT
        "lecturer" -> UserRole.LECTURER
        "admin" -> UserRole.ADMIN
        else -> null
    }
}