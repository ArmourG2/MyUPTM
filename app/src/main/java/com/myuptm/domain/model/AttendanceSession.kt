package com.myuptm.domain.model

// Sprint 11: QR/PIN attendance — ONE simple 6-digit code is BOTH the QR payload
// and the manual PIN (owner decision: POC-simple, no time-based TOTP). The code
// lives 5 minutes (SESSION_DURATION_MS) after the lecturer starts the session;
// sharing it is largely pointless because it dies with the window.
data class AttendanceSession(
    val id: String,
    val code: String,                     // the shared 6-digit QR/PIN value
    val classId: String,                  // ClassSession.id the session belongs to
    val displayCode: String,              // "SWC3403/3713_01" for display
    val subjectName: String,
    val lecturerEmail: String,            // scoping: only this lecturer sees/controls it
    val lecturerName: String,
    val startedAt: Long,
    val expiresAt: Long,
    // active = accepting verifications. Lazily closed on expiry or explicit end.
    val active: Boolean = true
) {
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean = now >= expiresAt
    fun remainingMs(now: Long = System.currentTimeMillis()): Long = (expiresAt - now).coerceAtLeast(0)

    companion object {
        // Owner decision: default 5-minute verification window.
        const val SESSION_DURATION_MS: Long = 5 * 60 * 1000L
    }
}

// One student's verified attendance against a session. Only the POC demo student
// has an account, so in practice this is the single account-backed matric.
data class AttendanceCheckIn(
    val id: String,
    val sessionId: String,
    val classId: String,
    val displayCode: String,
    val matric: String,
    val studentName: String,
    val verifiedAt: Long
)
