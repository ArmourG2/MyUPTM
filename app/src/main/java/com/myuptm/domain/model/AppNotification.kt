package com.myuptm.domain.model

// Sprint 7B: application-level notification shown in the Home bell inbox.
// ANNOUNCEMENT / CLASS_UPDATE come from Firestore; CLASH_WARNING is generated
// locally on the student's device and never leaves it.
// Sprint 8 (Task 5): warning letters ride the same inbox — targetMatric delivers a
// notification ONLY to that student (null = everyone); attachments link to Cloudinary.
// Sprint 9: staffOnly docs (e.g. "absence letter submitted") are routed to
// LECTURER/ADMIN accounts only — students never see them, even globally.
data class AppNotification(
    val id: String,
    val type: NotificationType = NotificationType.ANNOUNCEMENT,
    val title: String,
    val message: String,
    val senderName: String,
    val createdAtEpochMs: Long,
    val targetMatric: String? = null,
    val attachmentUrl: String? = null,
    val attachmentName: String? = null,
    val staffOnly: Boolean = false,
    // Sprint 9: audience routing — staffOnly docs never reach students, and
    // studentOnly docs (class updates, miss notices) never reach staff.
    val studentOnly: Boolean = false,
    // Sprint 9 polish: optional illustration (Cloudinary image, like posts) and a
    // free-form "context" note the lecturer may add; blank stays blank.
    val imageUrl: String? = null,
    // Sprint 9 tiering (data-driven): "important" (red, pinned) / "attention"
    // (yellow reminder) / "normal" (blue). Null = legacy docs → derived rules.
    val importance: String? = null
)

enum class NotificationType {
    ANNOUNCEMENT,
    CLASS_UPDATE,
    CLASH_WARNING
}