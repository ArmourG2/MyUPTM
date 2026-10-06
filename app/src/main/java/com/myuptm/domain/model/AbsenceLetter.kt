package com.myuptm.domain.model

// Sprint 8 Task 4 (part 2): a student's submitted absence letter.
// fileUrl points to the real Cloudinary `raw` upload; the letter is bound to a
// concrete class ("Absence Letter" per class in Class Management).
data class AbsenceLetter(
    val id: String,
    val studentMatric: String,      // matric id, e.g. AM2412018161
    val studentName: String,
    val classId: String?,           // owning class, if the student picked one
    val fileName: String,
    val fileUrl: String,
    val submittedAt: Long,
    val status: LetterStatus = LetterStatus.PENDING,
    val declineReason: String? = null,
    val reviewedAt: Long? = null,
    // Sprint 9: per-class letters — the SUBJECT the letter explains (base code,
    // e.g. "SWC3403/3713"). Null = legacy global letter (shows under every class).
    val subjectName: String? = null
)

enum class LetterStatus {
    PENDING, APPROVED, DECLINED
}
