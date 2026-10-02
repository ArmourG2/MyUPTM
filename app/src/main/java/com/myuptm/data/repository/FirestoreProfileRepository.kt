package com.myuptm.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.myuptm.domain.model.UserProfile
import com.myuptm.domain.model.UserRole
import com.myuptm.domain.repository.ProfileRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// Reads the current user's profile from the Firestore "users" collection.
class FirestoreProfileRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ProfileRepository {

    // Fetches the profile document for the currently signed-in Firebase user.
    override suspend fun getUserProfile(): Result<UserProfile> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("User not logged in"))

        return suspendCancellableCoroutine { continuation ->
            db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val profile = doc.toUserProfile(uid)
                        if (profile != null) {
                            continuation.resume(Result.success(profile))
                        } else {
                            continuation.resume(Result.failure(Exception("Invalid profile data")))
                        }
                    } else {
                        continuation.resume(Result.failure(Exception("Profile not found")))
                    }
                }
                .addOnFailureListener { e ->
                    continuation.resume(Result.failure(e))
                }
        }
    }

    // Sprint 8 Task 3: writes the uploaded avatar URL to users/{uid}.avatarUrl.
    override suspend fun updateAvatarUrl(url: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("User not logged in"))

        return suspendCancellableCoroutine { continuation ->
            db.collection("users")
                .document(uid)
                .update("avatarUrl", url)
                .addOnSuccessListener {
                    continuation.resume(Result.success(Unit))
                }
                .addOnFailureListener { e ->
                    continuation.resume(Result.failure(e))
                }
        }
    }

    // Maps a Firestore document into the UserProfile domain model.
    private fun DocumentSnapshot.toUserProfile(uid: String): UserProfile? {
        val roleStr = getString("role") ?: return null
        val role = when (roleStr.lowercase()) {
            "student" -> UserRole.STUDENT
            "lecturer" -> UserRole.LECTURER
            "admin" -> UserRole.ADMIN
            else -> return null
        }

        return UserProfile(
            id = uid,
            fullName = getString("fullName") ?: "Unknown User",
            email = getString("email") ?: "",
            studentId = getString("studentId"),
            faculty = getString("faculty") ?: "Unknown Faculty",
            programme = getString("programme"),
            semester = getLong("semester")?.toInt(),
            role = role,
            avatarUrl = getString("avatarUrl")
        )
    }
}