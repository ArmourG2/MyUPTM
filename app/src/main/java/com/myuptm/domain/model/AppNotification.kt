package com.myuptm.domain.model

// Sprint 7B: application-level notification shown in the Home bell inbox.
// ANNOUNCEMENT / CLASS_UPDATE come from Firestore; CLASH_WARNING is generated
// locally on the student's device and never leaves it.
// Sprint 8 (Task 5): warning letters ride the same inbox — targetMatric delivers a
// notification ONLY to that student (null = everyone); attachments link to Cloudinary.
data class AppNotification(
    val id: String,
    val type: NotificationType = NotificationType.ANNOUNCEMENT,
    val title: String,
    val message: String,
    val senderName: String,
    val createdAtEpochMs: Long,
    val targetMatric: String? = null,
    val attachmentUrl: String? = null,
    val attachmentName: String? = null
)

enum class NotificationType {
    ANNOUNCEMENT,
    CLASS_UPDATE,
    CLASH_WARNING
}