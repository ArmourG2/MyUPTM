package com.myuptm.domain.repository

import com.myuptm.domain.model.AttendanceCheckIn
import com.myuptm.domain.model.AttendanceSession
import kotlinx.coroutines.flow.Flow

// Sprint 11: QR/PIN attendance persistence (Firestore "attendance_sessions" +
// "attendance_checkins"). Equality-only queries only — no composite indexes.
interface AttendanceSessionRepository {

    // Lecturer: the single live session they own (or null). Snapshot-backed.
    fun observeActiveSessionForLecturer(lecturerEmail: String): Flow<AttendanceSession?>

    // Student: verifications recorded against one session (live count for the lecturer UI).
    fun observeCheckIns(sessionId: String): Flow<List<AttendanceCheckIn>>

    // Student: this student's recent verifications (newest first) for the
    // "last verified" panel on the Attendance screen.
    fun observeMyCheckIns(matric: String): Flow<List<AttendanceCheckIn>>

    // Lecturer: opens a 5-minute verification window with a fresh 6-digit code.
    suspend fun startSession(session: AttendanceSession): Result<Unit>

    // Lecturer: closes the window immediately (active = false).
    suspend fun endSession(sessionId: String): Result<Unit>

    // Student: resolves an ACTIVE, NON-EXPIRED session by its 6-digit code.
    suspend fun findActiveSessionByCode(code: String): Result<AttendanceSession?>

    // Student: records the verification. One-shot duplicate check is the caller's
    // concern (hasCheckedIn) so the UI can show the right message.
    suspend fun hasCheckedIn(sessionId: String, matric: String): Result<Boolean>

    suspend fun addCheckIn(checkIn: AttendanceCheckIn): Result<Unit>

    // Sprint 13 (P0 backlog): one-shot, ALL sessions owned by [lecturerEmail]
    // for [classId] (equality-only; the caller picks the latest + filters "today"
    // client-side). Backs the absentee-notice join: verified students never get
    // a miss notice.
    suspend fun sessionsForLecturerAndClass(lecturerEmail: String, classId: String): Result<List<AttendanceSession>>

    // Sprint 13: one-shot, ALL check-ins recorded against one session.
    suspend fun checkInsForSession(sessionId: String): Result<List<AttendanceCheckIn>>
}
