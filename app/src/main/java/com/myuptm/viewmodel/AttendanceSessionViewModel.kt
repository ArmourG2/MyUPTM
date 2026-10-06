package com.myuptm.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myuptm.data.repository.FirestoreAttendanceSessionRepository
import com.myuptm.domain.model.AttendanceCheckIn
import com.myuptm.domain.model.AttendanceSession
import com.myuptm.domain.model.ClassSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.SecureRandom

// Sprint 11: LECTURER side of the QR/PIN attendance. One activity-scoped VM:
//  - "Start attendance" opens a 5-minute window with a fresh 6-digit code that is
//    simultaneously the QR payload and the manual PIN (single shared value),
//  - the roster screen shows the code + QR + live countdown + verified count,
//  - "End session" (or expiry) closes the window; the EXISTING "End class &
//    notify absentees" flow stays as the separate manual miss-notice trigger.
class AttendanceSessionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FirestoreAttendanceSessionRepository()

    private val _activeSession = MutableStateFlow<AttendanceSession?>(null)
    val activeSession: StateFlow<AttendanceSession?> = _activeSession.asStateFlow()

    // Verifications recorded against the active session ("X verified" on the card).
    private val _sessionCheckIns = MutableStateFlow<List<AttendanceCheckIn>>(emptyList())
    val sessionCheckIns: StateFlow<List<AttendanceCheckIn>> = _sessionCheckIns.asStateFlow()

    private val _isStarting = MutableStateFlow(false)
    val isStarting: StateFlow<Boolean> = _isStarting.asStateFlow()

    // Error / feedback line (e.g. "Could not start session").
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private var boundEmail: String? = null
    private val random = SecureRandom()
    private var _observedCheckInSessionId: String? = null
    private var _checkInJob: kotlinx.coroutines.Job? = null
    private var _sessionJob: kotlinx.coroutines.Job? = null

    fun bindOwner(email: String?) {
        if (boundEmail == email) return
        boundEmail = email
        // Account switch: drop the previous lecturer's collectors entirely.
        _sessionJob?.cancel()
        _checkInJob?.cancel()
        _observedCheckInSessionId = null
        _sessionCheckIns.value = emptyList()
        if (email == null) return
        _sessionJob = viewModelScope.launch {
            repository.observeActiveSessionForLecturer(email).collect { session ->
                _activeSession.value = session
            }
        }
        viewModelScope.launch {
            _activeSession.collect { session ->
                if (session?.id != _observedCheckInSessionId) {
                    _observedCheckInSessionId = session?.id
                    _sessionCheckIns.value = emptyList()
                    _checkInJob?.cancel()
                    session?.let {
                        _checkInJob = launch {
                            repository.observeCheckIns(it.id).collect { rows ->
                                _sessionCheckIns.value = rows
                            }
                        }
                    }
                }
            }
        }
    }

    // Generates the shared 6-digit code (leading zeros allowed, zero-padded).
    fun startSession(classSession: ClassSession) {
        if (_isStarting.value || _activeSession.value != null) return
        _isStarting.value = true
        _status.value = null
        val now = System.currentTimeMillis()
        val session = AttendanceSession(
            id = "", // document id assigned by Firestore
            code = "%06d".format(random.nextInt(1_000_000)),
            classId = classSession.id,
            displayCode = classSession.displayCode,
            subjectName = classSession.subjectName,
            lecturerEmail = boundEmail.orEmpty(),
            lecturerName = classSession.lecturerName,
            startedAt = now,
            expiresAt = now + AttendanceSession.SESSION_DURATION_MS
        )
        viewModelScope.launch {
            repository.startSession(session)
                .onSuccess { _status.value = null }
                .onFailure { e -> _status.value = e.message ?: "Could not start the attendance session" }
            _isStarting.value = false
        }
    }

    fun endSession() {
        val session = _activeSession.value ?: return
        viewModelScope.launch {
            repository.endSession(session.id)
                .onSuccess { _status.value = "Attendance session ended" }
                .onFailure { e -> _status.value = e.message ?: "Could not end the session" }
        }
    }

    fun clearStatus() {
        _status.value = null
    }
}
