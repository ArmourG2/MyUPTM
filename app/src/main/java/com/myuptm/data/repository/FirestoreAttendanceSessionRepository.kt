package com.myuptm.data.repository

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.myuptm.domain.model.AttendanceCheckIn
import com.myuptm.domain.model.AttendanceSession
import com.myuptm.domain.repository.AttendanceSessionRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// Sprint 11: QR/PIN attendance on Firestore.
//  - attendance_sessions: one live doc per started session (code, window, owner).
//  - attendance_checkins: one doc per verified student.
// All queries are equality-only so Firestore needs NO composite indexes (POC).
class FirestoreAttendanceSessionRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : AttendanceSessionRepository {

    override fun observeActiveSessionForLecturer(lecturerEmail: String): Flow<AttendanceSession?> =
        callbackFlow {
            val registration: ListenerRegistration = db.collection(SESSIONS)
                .whereEqualTo("lecturerEmail", lecturerEmail)
                .whereEqualTo("active", true)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(null)
                        return@addSnapshotListener
                    }
                    val session = snapshot?.documents
                        ?.mapNotNull { it.toSession() }
                        ?.maxByOrNull { it.startedAt }
                    trySend(session)
                }
            awaitClose { registration.remove() }
        }

    override fun observeCheckIns(sessionId: String): Flow<List<AttendanceCheckIn>> =
        callbackFlow {
            val registration: ListenerRegistration = db.collection(CHECKINS)
                .whereEqualTo("sessionId", sessionId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val rows = snapshot?.documents
                        ?.mapNotNull { it.toCheckIn() }
                        ?.sortedByDescending { it.verifiedAt }
                        ?: emptyList()
                    trySend(rows)
                }
            awaitClose { registration.remove() }
        }

    override fun observeMyCheckIns(matric: String): Flow<List<AttendanceCheckIn>> =
        callbackFlow {
            val registration: ListenerRegistration = db.collection(CHECKINS)
                .whereEqualTo("matric", matric)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val rows = snapshot?.documents
                        ?.mapNotNull { it.toCheckIn() }
                        ?.sortedByDescending { it.verifiedAt }
                        ?: emptyList()
                    trySend(rows)
                }
            awaitClose { registration.remove() }
        }

    override suspend fun startSession(session: AttendanceSession): Result<Unit> {
        val task = db.collection(SESSIONS).add(session.toFirestoreMap())
        return task.awaitResult().map { }
    }

    override suspend fun endSession(sessionId: String): Result<Unit> {
        val updates = mapOf(
            "active" to false,
            "endedAt" to System.currentTimeMillis()
        )
        val task = db.collection(SESSIONS).document(sessionId).update(updates)
        return task.awaitResult().map { }
    }

    // Equality-only query (code + active) → served by Firestore's automatic
    // single-field indexes. Expiry is checked CLIENT-side by the caller.
    override suspend fun findActiveSessionByCode(code: String): Result<AttendanceSession?> {
        return suspendCancellableCoroutine { continuation ->
            db.collection(SESSIONS)
                .whereEqualTo("code", code)
                .whereEqualTo("active", true)
                .limit(1)
                .get()
                .addOnSuccessListener { snapshot ->
                    val session = snapshot.documents.firstOrNull()?.toSession()
                    if (continuation.isActive) continuation.resume(Result.success(session))
                }
                .addOnFailureListener { e ->
                    if (continuation.isActive) continuation.resume(Result.failure(e))
                }
        }
    }

    override suspend fun hasCheckedIn(sessionId: String, matric: String): Result<Boolean> {
        return suspendCancellableCoroutine { continuation ->
            db.collection(CHECKINS)
                .whereEqualTo("sessionId", sessionId)
                .whereEqualTo("matric", matric)
                .limit(1)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (continuation.isActive) continuation.resume(Result.success(!snapshot.isEmpty))
                }
                .addOnFailureListener { e ->
                    if (continuation.isActive) continuation.resume(Result.failure(e))
                }
        }
    }

    override suspend fun addCheckIn(checkIn: AttendanceCheckIn): Result<Unit> {
        val task = db.collection(CHECKINS).add(checkIn.toFirestoreMap())
        return task.awaitResult().map { }
    }

    private fun AttendanceSession.toFirestoreMap(): Map<String, Any?> = mapOf(
        "code" to code,
        "classId" to classId,
        "displayCode" to displayCode,
        "subjectName" to subjectName,
        "lecturerEmail" to lecturerEmail,
        "lecturerName" to lecturerName,
        "startedAt" to startedAt,
        "expiresAt" to expiresAt,
        "active" to active
    )

    private fun DocumentSnapshot.toSession(): AttendanceSession? {
        val code = getString("code") ?: return null
        return AttendanceSession(
            id = id,
            code = code,
            classId = getString("classId") ?: "",
            displayCode = getString("displayCode") ?: getString("subjectName") ?: "",
            subjectName = getString("subjectName") ?: "",
            lecturerEmail = getString("lecturerEmail") ?: "",
            lecturerName = getString("lecturerName") ?: "",
            startedAt = getLong("startedAt") ?: 0L,
            expiresAt = getLong("expiresAt") ?: 0L,
            active = getBoolean("active") ?: false
        )
    }

    private fun AttendanceCheckIn.toFirestoreMap(): Map<String, Any?> = mapOf(
        "sessionId" to sessionId,
        "classId" to classId,
        "displayCode" to displayCode,
        "matric" to matric,
        "studentName" to studentName,
        "verifiedAt" to verifiedAt
    )

    private fun DocumentSnapshot.toCheckIn(): AttendanceCheckIn? {
        val matric = getString("matric") ?: return null
        return AttendanceCheckIn(
            id = id,
            sessionId = getString("sessionId") ?: "",
            classId = getString("classId") ?: "",
            displayCode = getString("displayCode") ?: "",
            matric = matric,
            studentName = getString("studentName") ?: "Unknown student",
            verifiedAt = getLong("verifiedAt") ?: 0L
        )
    }

    // Bridges Firebase Tasks into Kotlin coroutines (project pattern: no extra deps).
    private suspend fun <T> Task<T>.awaitResult(): Result<T> =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(Result.success(result)) }
            addOnFailureListener { e -> if (continuation.isActive) continuation.resume(Result.failure(e)) }
        }

    private companion object {
        const val SESSIONS = "attendance_sessions"
        const val CHECKINS = "attendance_checkins"
    }
}
