package com.myuptm.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.android.gms.tasks.Task
import com.myuptm.domain.model.AbsenceLetter
import com.myuptm.domain.model.LetterStatus
import com.myuptm.domain.repository.AbsenceLetterRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// Sprint 8 Task 4 (part 2): absence letters live in the Firestore "absence_letters"
// collection — submitted by students (Attendance screen), reviewed by lecturers
// (Class Management). Live snapshot feed, newest first.
class FirestoreAbsenceLetterRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : AbsenceLetterRepository {

    override fun observeLetters(): Flow<List<AbsenceLetter>> = callbackFlow {
        val registration: ListenerRegistration = db.collection(COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents ?: emptyList()
                trySend(docs.mapNotNull { it.toLetter() }.sortedByDescending { it.submittedAt })
            }
        awaitClose { registration.remove() }
    }

    override suspend fun submitLetter(letter: AbsenceLetter): Result<Unit> {
        val task = db.collection(COLLECTION).document(letter.id).set(letter.toFirestoreMap())
        return task.awaitResult().map { }
    }

    override suspend fun setLetterStatus(
        letterId: String,
        status: LetterStatus,
        declineReason: String?
    ): Result<Unit> {
        val updates = mapOf(
            "status" to status.name,
            "declineReason" to declineReason,
            "reviewedAt" to System.currentTimeMillis()
        )
        val task = db.collection(COLLECTION).document(letterId).update(updates)
        return task.awaitResult().map { }
    }

    private fun AbsenceLetter.toFirestoreMap(): Map<String, Any?> = mapOf(
        "studentMatric" to studentMatric,
        "studentName" to studentName,
        "classId" to classId,
        "fileName" to fileName,
        "fileUrl" to fileUrl,
        "submittedAt" to submittedAt,
        "status" to status.name,
        "declineReason" to declineReason,
        "reviewedAt" to reviewedAt,
        "subjectName" to subjectName
    )

    private fun DocumentSnapshot.toLetter(): AbsenceLetter? {
        val matric = getString("studentMatric") ?: return null
        return AbsenceLetter(
            id = id,
            studentMatric = matric,
            studentName = getString("studentName") ?: "Unknown student",
            classId = getString("classId"),
            fileName = getString("fileName") ?: "absence_letter.pdf",
            fileUrl = getString("fileUrl") ?: "",
            submittedAt = getLong("submittedAt") ?: 0L,
            status = getString("status")
                ?.let { s -> runCatching { LetterStatus.valueOf(s) }.getOrNull() }
                ?: LetterStatus.PENDING,
            declineReason = getString("declineReason"),
            reviewedAt = getLong("reviewedAt"),
            subjectName = getString("subjectName")
        )
    }

    // Bridges Firebase Tasks into Kotlin coroutines (project pattern: no extra deps).
    private suspend fun <T> Task<T>.awaitResult(): Result<T> =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(Result.success(result)) }
            addOnFailureListener { e -> if (continuation.isActive) continuation.resume(Result.failure(e)) }
        }

    private companion object {
        const val COLLECTION = "absence_letters"
    }
}
