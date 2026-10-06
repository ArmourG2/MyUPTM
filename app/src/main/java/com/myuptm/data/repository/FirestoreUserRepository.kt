package com.myuptm.data.repository

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.myuptm.domain.model.AppUser
import com.myuptm.domain.model.UserRole
import com.myuptm.domain.repository.AccountApproval
import com.myuptm.domain.repository.UserRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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

    // Sprint 13: pending skeleton — merge-refreshes email/contact fields only.
    // For a missed doc the merge CREATES it; approved defaults to false (never
    // written app-side), so the dashboard treats it as pending immediately.
    override suspend fun registerPendingAccount(
        uid: String,
        email: String,
        fullName: String?
    ): Result<Unit> {
        return suspendCancellableCoroutine { continuation ->
            val updates = buildMap<String, Any?> {
                put("email", email)
                fullName?.takeIf { it.isNotBlank() }?.let { put("fullName", it) }
                put("requestedAt", System.currentTimeMillis())
            }
            db.collection("users").document(uid).set(updates, SetOptions.merge())
                .addOnSuccessListener {
                    if (continuation.isActive) continuation.resume(Result.success(Unit))
                }
                .addOnFailureListener { e ->
                    if (continuation.isActive) continuation.resume(Result.failure(e))
                }
        }
    }

    // Sprint 13: live approval watch — one snapshot listener on this account's doc.
    override fun observeAccountApproval(uid: String): Flow<AccountApproval> = callbackFlow {
        val registration = db.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ ->
                val approved = snapshot?.getBoolean("approved") ?: false
                trySend(
                    AccountApproval(
                        exists = snapshot?.exists() ?: false,
                        approved = approved,
                        roleRaw = snapshot?.getString("role")
                    )
                )
            }
        awaitClose { registration.remove() }
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