package com.myuptm.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.android.gms.tasks.Task
import com.myuptm.domain.model.ClassLevel
import com.myuptm.domain.model.ClassSession
import com.myuptm.domain.model.TeachingMedium
import com.myuptm.domain.repository.ClassRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// Sprint 7B: global classes stored in the Firestore "classes" collection.
// - Every device observes the same list (snapshot listener), so lecturer edits
//   propagate to all students.
// - Ownership: a lecturer may only modify classes they created (ownerEmail == their
//   email) or the unowned demo seeds (ownerEmail == null). Client-side POC check.
// - On first read, if the collection is empty, the demo classes are seeded with
//   fixed document ids (idempotent) and ownerEmail = null.
class FirestoreClassRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ClassRepository {

    @Volatile
    private var seedAttempted = false

    // Live stream of global classes; seeds the demo data if the collection is empty.
    override fun observeClasses(): Flow<List<ClassSession>> = callbackFlow {
        val registration: ListenerRegistration = db.collection(COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents ?: emptyList()
                if (docs.isEmpty() && !seedAttempted) {
                    seedAttempted = true
                    seedDemoClasses()
                    trySend(emptyList())
                } else {
                    trySend(docs.mapNotNull { it.toClassSession() }.sortedWith(
                        compareBy({ it.dayIndex }, { it.startTime })
                    ))
                }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun addClass(session: ClassSession): Result<Unit> {
        val task = db.collection(COLLECTION)
            .add(session.toFirestoreMap(ownerEmail = auth.currentUser?.email))
        return task.awaitResult().map { }
    }

    override suspend fun updateClass(session: ClassSession): Result<Unit> {
        val currentEmail = auth.currentUser?.email
        // Ownership re-check on write (defense in depth; UI also gates the entry).
        val owned = session.ownerEmail == null || session.ownerEmail == currentEmail
        if (!owned) return Result.failure(IllegalStateException("You can only modify your own classes"))

        val task = db.collection(COLLECTION)
            .document(session.id)
            .set(session.toFirestoreMap(ownerEmail = session.ownerEmail))
        return task.awaitResult().map { }
    }

    override suspend fun removeClass(classId: String): Result<Unit> {
        // Fetch first so we can enforce ownership on the stored record.
        val fetch = db.collection(COLLECTION).document(classId).get().awaitResult()
        val doc = fetch.getOrNull() ?: return Result.failure(IllegalStateException("Class not found"))
        val ownerEmail = doc.getString("ownerEmail")
        val currentEmail = auth.currentUser?.email
        if (ownerEmail != null && ownerEmail != currentEmail) {
            return Result.failure(IllegalStateException("You can only modify your own classes"))
        }
        val task = db.collection(COLLECTION).document(classId).delete()
        return task.awaitResult().map { }
    }

    // Idempotent demo seed with fixed document ids ("1"…"10"); unowned so any
    // lecturer may edit/adopt them during the POC. Sprint 8: seeds carry varied
    // sections + levels so the Class Management filters demo meaningfully.
    @Suppress("DEPRECATION")
    private fun seedDemoClasses() {
        val demo = MockTimetableRepository().getWeeklyTimetable()
        demo.forEachIndexed { index, session ->
            val enriched = session.copy(
                section = "Section ${(index % 3) + 1}",
                level = when (index % 3) {
                    1 -> ClassLevel.DEGREE
                    2 -> ClassLevel.MASTER
                    else -> ClassLevel.DIPLOMA
                }
            )
            db.collection(COLLECTION)
                .document(enriched.id)
                .set(enriched.toFirestoreMap(ownerEmail = null))
        }
    }

    private fun ClassSession.toFirestoreMap(ownerEmail: String?): Map<String, Any?> = mapOf(
        "subjectName" to subjectName,
        "lecturerName" to lecturerName,
        "venue" to venue,
        "medium" to teachingMedium.name,
        "dayIndex" to dayIndex,
        "startTime" to startTime,
        "endTime" to endTime,
        "section" to section,
        "level" to level.name,
        "ownerEmail" to ownerEmail,
        "updatedAt" to System.currentTimeMillis()
    )

    private fun DocumentSnapshot.toClassSession(): ClassSession? {
        val subjectName = getString("subjectName") ?: return null
        return ClassSession(
            id = id,
            dayIndex = getLong("dayIndex")?.toInt() ?: 0,
            subjectName = subjectName,
            lecturerName = getString("lecturerName") ?: "",
            venue = getString("venue") ?: "",
            teachingMedium = getString("medium")
                ?.let { medium -> runCatching { TeachingMedium.valueOf(medium) }.getOrNull() }
                ?: TeachingMedium.OFFLINE,
            startTime = getString("startTime") ?: "00:00",
            endTime = getString("endTime") ?: "00:00",
            section = getString("section") ?: "",
            level = getString("level")
                ?.let { lvl -> runCatching { ClassLevel.valueOf(lvl) }.getOrNull() }
                ?: ClassLevel.DIPLOMA,
            ownerEmail = getString("ownerEmail"),
            updatedAt = getLong("updatedAt")
        )
    }

    // Bridges Firebase Tasks into Kotlin coroutines (project pattern: no extra deps).
    private suspend fun <T> Task<T>.awaitResult(): Result<T> =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(Result.success(result)) }
            addOnFailureListener { e -> if (continuation.isActive) continuation.resume(Result.failure(e)) }
        }

    private companion object {
        const val COLLECTION = "classes"
    }
}