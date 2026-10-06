package com.myuptm.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myuptm.data.repository.FirestoreAttendanceSessionRepository
import com.myuptm.data.repository.FirestoreProfileRepository
import com.myuptm.data.repository.MockAttendanceRoster
import com.myuptm.domain.model.AttendanceCheckIn
import com.myuptm.domain.model.AttendanceSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Sprint 11: STUDENT side of the QR/PIN attendance. The student scans the
// lecturer's QR (the payload IS the 6-digit code) or types the PIN; both run the
// same verification: find the ACTIVE session for that code, reject expired
// windows, prevent double check-in, then record the verification to Firestore.
class AttendanceCheckInViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FirestoreAttendanceSessionRepository()
    private val profileRepository: FirestoreProfileRepository = FirestoreProfileRepository()

    data class CheckInUiState(
        val isVerifying: Boolean = false,
        // Last successful verification (drives the green confirmation panel).
        val verifiedSession: AttendanceSession? = null,
        val verifiedAt: Long? = null,
        val error: String? = null,
        // Recent verifications by this student (newest first).
        val myCheckIns: List<AttendanceCheckIn> = emptyList()
    )

    private val _uiState = MutableStateFlow(CheckInUiState())
    val uiState: StateFlow<CheckInUiState> = _uiState.asStateFlow()

    // The account-backed student identity (POC: falls back to the demo matric,
    // same chain as the absence-letter flow).
    private var matric: String? = null
    private var displayName: String? = null

    init {
        viewModelScope.launch {
            val profile = profileRepository.getUserProfile().getOrNull()
            matric = profile?.studentId
                ?: profile?.email
                ?: MockAttendanceRoster.DEMO_MATRIC
            displayName = profile?.fullName
            // Live feed of this student's verifications (drives "last verified").
            matric?.let { resolved ->
                repository.observeMyCheckIns(resolved).collect { rows ->
                    _uiState.value = _uiState.value.copy(myCheckIns = rows)
                }
            }
        }
    }

    // Accepts the raw scan payload OR the typed PIN — both normalize to the
    // 6-digit code. Returns via the UI state (success panel / error line).
    fun verify(rawCode: String) {
        if (_uiState.value.isVerifying) return
        val code = rawCode.filter { it.isDigit() }
        if (code.length != 6) {
            _uiState.value = _uiState.value.copy(error = "The code must be 6 digits")
            return
        }
        val myMatric = matric ?: MockAttendanceRoster.DEMO_MATRIC
        val name = displayName ?: myMatric
        _uiState.value = _uiState.value.copy(isVerifying = true, error = null)
        viewModelScope.launch {
            runCatching {
                val session = repository.findActiveSessionByCode(code).getOrThrow()
                    ?: throw IllegalStateException("No active attendance session matches this code")
                if (session.isExpired()) {
                    throw IllegalStateException("This code expired — ask your lecturer for the current one")
                }
                val already = repository.hasCheckedIn(session.id, myMatric).getOrDefault(false)
                if (already) {
                    throw AlreadyVerifiedException(session)
                }
                val now = System.currentTimeMillis()
                repository.addCheckIn(
                    AttendanceCheckIn(
                        id = "", // document id assigned by Firestore
                        sessionId = session.id,
                        classId = session.classId,
                        displayCode = session.displayCode,
                        matric = myMatric,
                        studentName = name,
                        verifiedAt = now
                    )
                ).getOrThrow()
                session to now
            }.onSuccess { (session, now) ->
                _uiState.value = _uiState.value.copy(
                    isVerifying = false,
                    verifiedSession = session,
                    verifiedAt = now,
                    error = null
                )
            }.onFailure { e ->
                when (e) {
                    is AlreadyVerifiedException -> _uiState.value = _uiState.value.copy(
                        isVerifying = false,
                        // Already-verified is a benign state — surface the session
                        // confirmation again rather than an error.
                        verifiedSession = e.session,
                        verifiedAt = null,
                        error = null
                    )
                    else -> _uiState.value = _uiState.value.copy(
                        isVerifying = false,
                        error = e.message ?: "Verification failed — try again"
                    )
                }
            }
        }
    }

    // Resets the confirmation panel (e.g. when toggling modes).
    fun consumeResult() {
        _uiState.value = _uiState.value.copy(verifiedSession = null, verifiedAt = null, error = null)
    }

    private class AlreadyVerifiedException(val session: AttendanceSession) : Exception()
}
