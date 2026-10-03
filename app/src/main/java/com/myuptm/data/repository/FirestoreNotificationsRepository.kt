package com.myuptm.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
import com.google.android.gms.tasks.Task
import com.myuptm.domain.model.AppNotification
import com.myuptm.domain.model.NotificationType
import com.myuptm.domain.repository.NotificationsRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// Sprint 7B: POC notification inbox backed by the Firestore "notifications" collection.
// Writers are staff actions (notify button, class changes); every device observes the
// latest documents via a snapshot listener.
class FirestoreNotificationsRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : NotificationsRepository {

    // Latest ~20 notifications, newest first.
    override fun observeNotifications(): Flow<List<AppNotification>> = callbackFlow {
        val registration: ListenerRegistration = db.collection(COLLECTION)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend((snapshot?.documents ?: emptyList()).mapNotNull { it.toAppNotification() })
            }
        awaitClose { registration.remove() }
    }

    override suspend fun sendNotification(
        type: NotificationType,
        title: String,
        message: String,
        senderName: String,
        targetMatric: String?,
        attachmentUrl: String?,
        attachmentName: String?
    ): Result<Unit> {
        val task = db.collection(COLLECTION).add(
            mapOf(
                "type" to type.name,
                "title" to title,
                "message" to message,
                "senderName" to senderName,
                "createdAt" to FieldValue.serverTimestamp(),
                "targetMatric" to targetMatric,
                "attachmentUrl" to attachmentUrl,
                "attachmentName" to attachmentName
            )
        )
        return task.awaitResult().map { }
    }

    private fun DocumentSnapshot.toAppNotification(): AppNotification? {
        val title = getString("title") ?: return null
        return AppNotification(
            id = id,
            type = getString("type")
                ?.let { value -> runCatching { NotificationType.valueOf(value) }.getOrNull() }
                ?: NotificationType.ANNOUNCEMENT,
            title = title,
            message = getString("message").orEmpty(),
            senderName = getString("senderName").orEmpty(),
            createdAtEpochMs = getTimestamp("createdAt")?.toDate()?.time ?: 0L,
            targetMatric = getString("targetMatric"),
            attachmentUrl = getString("attachmentUrl"),
            attachmentName = getString("attachmentName")
        )
    }

    private suspend fun <T> Task<T>.awaitResult(): Result<T> =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(Result.success(result)) }
            addOnFailureListener { e -> if (continuation.isActive) continuation.resume(Result.failure(e)) }
        }

    private companion object {
        const val COLLECTION = "notifications"
    }
}