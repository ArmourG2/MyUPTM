package com.myuptm.domain.repository

import com.myuptm.domain.model.AppNotification
import com.myuptm.domain.model.NotificationType
import kotlinx.coroutines.flow.Flow

// Sprint 7B: POC notification pipeline backed by the Firestore "notifications"
// collection (no FCM — local notifications only, per the 7A decision).
interface NotificationsRepository {

    // Live stream of the latest notifications (Firestore snapshot listener).
    fun observeNotifications(): Flow<List<AppNotification>>

    // Writes a new notification document that every device will pick up.
    suspend fun sendNotification(
        type: NotificationType,
        title: String,
        message: String,
        senderName: String
    ): Result<Unit>
}