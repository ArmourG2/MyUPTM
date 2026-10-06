package com.myuptm.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.android.gms.tasks.Task
import com.myuptm.domain.model.Post
import com.myuptm.domain.repository.PostsRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// Sprint 8 Task 4: real posts stored in the Firestore "posts" collection.
// - Live snapshot feed, latest first (createdAt desc) — Home + Posts + Detail all reactive.
// - Authoring stamps authorEmail + createdAt; removal is client-side gated (POC).
// - On first read, if the collection is empty, 5 demo announcements are seeded with fixed
//   document ids (idempotent) and no author (so they are not deletable-owners confusion).
class FirestorePostsRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : PostsRepository {

    @Volatile
    private var seedAttempted = false

    override fun observePosts(): Flow<List<Post>> = callbackFlow {
        val registration: ListenerRegistration = db.collection(COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents ?: emptyList()
                if (docs.isEmpty() && !seedAttempted) {
                    seedAttempted = true
                    seedDemoPosts()
                    trySend(emptyList())
                } else {
                    trySend(docs.mapNotNull { it.toPost() }.sortedByDescending { it.createdAt ?: 0L })
                }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun addPost(post: Post): Result<Unit> {
        val task = db.collection(COLLECTION).document(post.id).set(post.toFirestoreMap())
        return task.awaitResult().map { }
    }

    override suspend fun removePost(postId: String): Result<Unit> {
        val task = db.collection(COLLECTION).document(postId).delete()
        return task.awaitResult().map { }
    }

    // Idempotent demo seed with fixed document ids ("1"…"5").
    private fun seedDemoPosts() {
        val now = System.currentTimeMillis()
        val demo = listOf(
            Post("1", "Mid-Semester Exam Timetable Released", "The mid-semester examination timetable for Semester 1 2026/2027 is now available. Check your schedule and venue.", "25 Sep 2026", "Academic Affairs", importance = "important", createdAt = now - 86400000L * 7),
            Post("2", "Faculty of Computing Career Fair", "Annual career fair featuring 30+ tech companies. Bring your CV and dress professionally.", "24 Sep 2026", "Faculty of Computing", importance = "attention", createdAt = now - 86400000L * 6),
            Post("3", "Library Extended Hours During Exam Week", "Main library operates 24 hours from 1 Oct to 7 Oct to support exam preparation.", "23 Sep 2026", "Library Services", importance = "important", createdAt = now - 86400000L * 5),
            Post("4", "Mobile App Development Workshop", "Hands-on workshop covering Jetpack Compose and Material 3. Open to Year 2 and 3 students.", "22 Sep 2026", "Faculty of Computing", createdAt = now - 86400000L * 4),
            Post("5", "Campus Wi-Fi Maintenance Notice", "Scheduled maintenance Saturday 28 Sep, 2:00 AM to 6:00 AM. Expect intermittent connectivity.", "21 Sep 2026", "IT Services", createdAt = now - 86400000L * 3)
        )
        demo.forEach { post ->
            db.collection(COLLECTION).document(post.id).set(post.toFirestoreMap())
        }
    }

    private fun Post.toFirestoreMap(): Map<String, Any?> = mapOf(
        "title" to title,
        "snippet" to snippet,
        "date" to date,
        "facultyTag" to facultyTag,
        "imageUrl" to imageUrl,
        "authorEmail" to authorEmail,
        "createdAt" to createdAt,
        "importance" to importance
    )

    private fun DocumentSnapshot.toPost(): Post? {
        val title = getString("title") ?: return null
        return Post(
            id = id,
            title = title,
            snippet = getString("snippet") ?: "",
            date = getString("date") ?: "",
            facultyTag = getString("facultyTag") ?: "MyUPTM",
            imageUrl = getString("imageUrl"),
            authorEmail = getString("authorEmail"),
            createdAt = getLong("createdAt"),
            importance = getString("importance")
        )
    }

    // Bridges Firebase Tasks into Kotlin coroutines (project pattern: no extra deps).
    private suspend fun <T> Task<T>.awaitResult(): Result<T> =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(Result.success(result)) }
            addOnFailureListener { e -> if (continuation.isActive) continuation.resume(Result.failure(e)) }
        }

    private companion object {
        const val COLLECTION = "posts"
    }
}
