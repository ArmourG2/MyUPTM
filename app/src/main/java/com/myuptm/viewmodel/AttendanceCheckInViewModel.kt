package com.myuptm.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myuptm.data.repository.FirestoreAttendanceSessionRepository
import com.myuptm.data.repository.FirestoreProfileRepository
import com.myuptm.data.repository.MockAttendanceRoster
import com.myuptm.domain.model.AttendanceCheckIn
import com.myuptm.domain.model.AttendanceSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
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
        // Instant on-stage scan feedback ("Checking code…", cached failure
        // reasons on rate-limited re-scans). Deliberately NOT a snackbar —
        // it must appear the moment a decode lands, and repeat codes must
        // update it without spamming popups or Firestore.
        val scanNotice: String? = null,
        val scanNoticeAt: Long? = null,
        // Recent verifications by this student (newest first).
        val myCheckIns: List<AttendanceCheckIn> = emptyList()
    )

    private val _uiState = MutableStateFlow(CheckInUiState())
    val uiState: StateFlow<CheckInUiState> = _uiState.asStateFlow()

    // The account-backed student identity (POC: falls back to the demo matric,
    // same chain as the absence-letter flow).
    private var matric: String? = null
    private var displayName: String? = null

    // Same-code rate limit: the live scanner re-decodes a QR sitting in front
    // of it every ~2.5 s. Re-verifying the SAME code that just failed hammers
    // Firestore and floods popups; inside the window we answer instantly from
    // the cached result instead.
    private var lastAttemptPayload: String? = null
    private var lastAttemptAt = 0L
    private var lastAttemptNotice: String? = null

    // Owner-requested fast pickup: a FAILED code auto-retries every ~1 s in
    // the background (e.g. the lecturer starts the session right after the
    // student scans — it verifies within ~1 s, no user action). The loop stops
    // after [MAX_AUTO_RETRIES] to prevent runaway Firestore chatter; the
    // camera's own decodes re-trigger it after the rate-limit window.
    private var retryJob: Job? = null
    private var retryCount = 0

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
    // 6-digit code. Feedback is INSTANT: every call updates [scanNotice] on
    // the stage the moment it runs, and repeat codes inside the rate-limit
    // window answer from the cached result without touching Firestore.
    // [autoRetry] keeps retrying failed codes every ~1 s in the background
    // (QR scan = true; typed PIN = false — the user re-taps Verify).
    fun verify(rawCode: String, autoRetry: Boolean) {
        if (_uiState.value.isVerifying) return
        val code = rawCode.filter { it.isDigit() }
        val now = System.currentTimeMillis()
        if (code.length != 6) {
            _uiState.value = _uiState.value.copy(
                scanNotice = "The code must be 6 digits",
                scanNoticeAt = now
            )
            return
        }
        val repeat = code == lastAttemptPayload && now - lastAttemptAt < SAME_PAYLOAD_RETRY_MS
        lastAttemptPayload = code
        lastAttemptAt = now
        if (repeat) {
            // Acknowledge the re-scan instantly with the cached outcome —
            // the user always knows the scan registered.
            _uiState.value = _uiState.value.copy(
                scanNotice = lastAttemptNotice
                    ?: "Code read — no active session matches this code yet",
                scanNoticeAt = now
            )
            return
        }
        retryCount = 0
        runVerification(code, autoRetry)
    }

    // Shared Firestore verify path. Fresh attempts announce "Checking code…"
    // on the stage; scheduled retries run silently — the failure reason stays
    // visible and only its timestamp refreshes (keeps the auto-dismiss timer
    // alive without any flicker).
    private fun runVerification(code: String, autoRetry: Boolean, announceChecking: Boolean = true) {
        if (_uiState.value.isVerifying) return
        val myMatric = matric ?: MockAttendanceRoster.DEMO_MATRIC
        val name = displayName ?: myMatric
        val now = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(
            isVerifying = true,
            scanNotice = if (announceChecking) "Checking code…" else _uiState.value.scanNotice,
            scanNoticeAt = now
        )
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
                val stamp = System.currentTimeMillis()
                repository.addCheckIn(
                    AttendanceCheckIn(
                        id = "", // document id assigned by Firestore
                        sessionId = session.id,
                        classId = session.classId,
                        displayCode = session.displayCode,
                        matric = myMatric,
                        studentName = name,
                        verifiedAt = stamp
                    )
                ).getOrThrow()
                session to stamp
            }.onSuccess { (session, stamp) ->
                retryJob?.cancel()
                lastAttemptNotice = "Code already verified — attendance recorded"
                _uiState.value = _uiState.value.copy(
                    isVerifying = false,
                    verifiedSession = session,
                    verifiedAt = stamp,
                    scanNotice = null
                )
            }.onFailure { e ->
                when (e) {
                    is AlreadyVerifiedException -> {
                        lastAttemptNotice = "Already verified — attendance recorded"
                        _uiState.value = _uiState.value.copy(
                            isVerifying = false,
                            // Already-verified is a benign state — surface the
                            // session confirmation again rather than an error.
                            verifiedSession = e.session,
                            verifiedAt = null,
                            scanNotice = null
                        )
                    }
                    else -> {
                        val message = e.message ?: "Verification failed — try again"
                        lastAttemptNotice = message
                        _uiState.value = _uiState.value.copy(
                            isVerifying = false,
                            scanNotice = message,
                            scanNoticeAt = System.currentTimeMillis()
                        )
                        if (autoRetry) scheduleRetry(code, autoRetry)
                    }
                }
            }
        }
    }

    // Re-attempts the same code after ~1 s — this is the owner-requested fast
    // pickup: once the lecturer starts the session, the student's code in view
    // verifies within ≤1 s with no user action. Capped so a code that never
    // becomes active can't chatter Firestore forever; the camera's own decodes
    // restart the cycle after the rate-limit window.
    private fun scheduleRetry(code: String, autoRetry: Boolean) {
        retryJob?.cancel()
        retryJob = viewModelScope.launch {
            delay(1_000L)
            if (lastAttemptPayload == code && !_uiState.value.isVerifying) {
                if (retryCount < MAX_AUTO_RETRIES) {
                    retryCount++
                    runVerification(code, autoRetry, announceChecking = false)
                } else {
                    lastAttemptNotice = "Verification paused — rescan the code to try again"
                    _uiState.value = _uiState.value.copy(
                        scanNotice = lastAttemptNotice,
                        scanNoticeAt = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    // Resets the confirmation panel (e.g. when toggling modes). Cancels any
    // pending auto-retry — a mode toggle means the user moved on.
    fun consumeResult() {
        retryJob?.cancel()
        _uiState.value = _uiState.value.copy(
            verifiedSession = null,
            verifiedAt = null,
            scanNotice = null,
            scanNoticeAt = null
        )
    }

    // Clears the on-stage scan notice once its auto-dismiss window elapses.
    // Cancels the retry loop too — with the notice gone there is no pending
    // outcome left to finalize.
    fun consumeNotice() {
        retryJob?.cancel()
        _uiState.value = _uiState.value.copy(scanNotice = null, scanNoticeAt = null)
    }

    private class AlreadyVerifiedException(val session: AttendanceSession) : Exception()

    private companion object {
        const val SAME_PAYLOAD_RETRY_MS = 15_000L

        // Background auto-retry budget (~45 s of 1 s retries); the camera's
        // own decodes restart the cycle after the rate-limit window.
        const val MAX_AUTO_RETRIES = 45
    }
}
