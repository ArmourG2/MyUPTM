package com.myuptm.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myuptm.data.repository.FirestoreAbsenceLetterRepository
import com.myuptm.data.repository.FirestoreProfileRepository
import com.myuptm.data.repository.MockAttendanceRoster
import com.myuptm.data.repository.RealCloudinaryRepository
import com.myuptm.data.repository.matchesMatric
import com.myuptm.domain.model.AbsenceLetter
import com.myuptm.domain.model.UserProfile
import com.myuptm.domain.repository.AbsenceLetterRepository
import com.myuptm.domain.repository.FileType
import com.myuptm.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

// Sprint 8 Task 4 (part 2): the student side of absence letters — real Cloudinary PDF
// upload + Firestore record so lecturers can review who submitted what.
class AttendanceViewModel(application: Application) : AndroidViewModel(application) {

    private val cloudinaryRepository = RealCloudinaryRepository(application)
    private val profileRepository: ProfileRepository = FirestoreProfileRepository()
    private val lettersRepository: AbsenceLetterRepository = FirestoreAbsenceLetterRepository()
    // Sprint 9: submitting a letter NOW notifies the lecturers (staffOnly routing).
    private val notificationsRepository: com.myuptm.domain.repository.NotificationsRepository =
        com.myuptm.data.repository.FirestoreNotificationsRepository()

    data class AttendanceUiState(
        val profile: UserProfile? = null,
        val isSubmitting: Boolean = false,
        val submitError: String? = null,
        val submitSuccess: Boolean = false,
        val myLetters: List<AbsenceLetter> = emptyList()
    )

    private val _uiState = MutableStateFlow(AttendanceUiState())
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profileResult = profileRepository.getUserProfile()
            _uiState.value = _uiState.value.copy(profile = profileResult.getOrNull())
            // Letters live-filter to this student (matric, falling back to email).
            // Sprint 9: last-resort fallback to the POC demo matric when the profile
            // is missing studentId/email-stamped ids — keeps the single-account demo
            // roster binding intact even with a half-configured Firestore profile.
            val matric = _uiState.value.profile?.studentId
                ?: _uiState.value.profile?.email
                ?: MockAttendanceRoster.DEMO_MATRIC
            if (matric == null) {
                _uiState.value = _uiState.value.copy(submitError = "Profile not loaded — cannot submit absence letters")
            }
            lettersRepository.observeLetters().collect { letters ->
                // Sprint 9: match by matric via MatricMatcher (email-prefix tolerant).
                _uiState.value = _uiState.value.copy(
                    myLetters = letters.filter { it.studentMatric.matchesMatric(matric) }
                )
            }
        }
    }

    fun clearSubmitError() {
        _uiState.value = _uiState.value.copy(submitError = null, submitSuccess = false)
    }

    // Upload the PDF to Cloudinary (raw) + write an absence_letters document.
    // Sprint 9: `subjectName` binds the letter to ONE class (per-class excuse) —
    // null stays legacy/global.
    fun submitAbsenceLetter(uri: Uri, subjectName: String? = null) {
        if (_uiState.value.isSubmitting) return
        val profile = _uiState.value.profile ?: run {
            _uiState.value = _uiState.value.copy(submitError = "Profile not loaded — try again")
            return
        }
        val matric = profile.studentId
            ?: profile.email.ifBlank { null }
            ?: MockAttendanceRoster.DEMO_MATRIC
        _uiState.value = _uiState.value.copy(isSubmitting = true, submitError = null, submitSuccess = false)
        viewModelScope.launch {
            runCatching {
                val url = cloudinaryRepository.uploadFile(uri, FileType.PDF).getOrThrow()
                val letter = AbsenceLetter(
                    id = UUID.randomUUID().toString(),
                    studentMatric = matric,
                    studentName = profile.fullName,
                    classId = null,
                    fileName = "${matric}_absence_${System.currentTimeMillis()}.pdf",
                    fileUrl = url,
                    submittedAt = System.currentTimeMillis(),
                    subjectName = subjectName
                )
                lettersRepository.submitLetter(letter).getOrThrow()
                // The COMPLIMENTARY side of the demo — the student's submission
                // immediately surfaces to staff (shade + bell dot) for review.
                // postId = letter id: tapping the notification goes straight to the
                // submission where staff can Approve/Decline it inline.
                notificationsRepository.sendNotification(
                    com.myuptm.domain.model.NotificationType.ANNOUNCEMENT,
                    "Absence letter submitted",
                    "${letter.studentName} (${matric}) submitted an absence letter" +
                        (subjectName?.let { " for $it" } ?: "") +
                        " — pending your review.",
                    senderName = letter.studentName,
                    staffOnly = true,
                    postId = letter.id
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isSubmitting = false, submitSuccess = true)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    submitError = e.message ?: "Could not submit absence letter"
                )
            }
        }
    }
}
