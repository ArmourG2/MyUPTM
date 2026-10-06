package com.myuptm.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myuptm.data.repository.MockAttendanceRoster
import com.myuptm.data.repository.matchesMatric
import com.myuptm.domain.model.AbsenceLetter
import com.myuptm.domain.model.ClassSession
import com.myuptm.domain.model.LetterStatus
import com.myuptm.domain.model.UserProfile
import com.myuptm.domain.model.UserRole
import com.myuptm.domain.repository.AbsenceLetterRepository
import com.myuptm.domain.repository.CloudinaryRepository
import com.myuptm.domain.repository.FileType
import com.myuptm.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val userProfile: UserProfile? = null,
    val isUploading: Boolean = false,
    val uploadedImageUrl: String? = null,
    val uploadError: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    // Sprint 9 (student profiles): attendance statistics. Lecturers have none.
    val attendance: AttendanceStats? = null,
    // Sprint 9: per-class letter submission from the missed-classes popup.
    val letterUploading: Boolean = false,
    val letterError: String? = null,
    val letterSubmitted: Boolean = false
)

// Attended/missed totals + the per-subject breakdown (drives the popup when the
// MISSED tile is pressed). Sprint 9: only APPROVED letters excuse; PENDING letters
// keep the miss counted but flip its record row to "Pending" until reviewed.
data class SubjectAttendance(
    val subject: String,
    val missed: Int,      // still-absent sessions (no approved/pending letter)
    val pending: Int,     // letters submitted, waiting on the lecturer
    val approved: Int,    // excused sessions
    val base: Int         // base misses this subject started with
)

data class AttendanceStats(
    val attended: Int,
    val missed: Int,
    val subjects: List<SubjectAttendance>,
    val sessionsPerSubject: Int
)

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val cloudinaryRepository: CloudinaryRepository,
    private val lettersRepository: AbsenceLetterRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    // Sprint 9: the class feed (students read it for the timetable) — used by the
    // missed-classes popup to derive WHICH occurrences were missed per subject.
    private val classesRepository = com.myuptm.data.repository.FirestoreClassRepository()
    val classes: StateFlow<List<ClassSession>> = classesRepository.observeClasses()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    data class MissedRecordRow(
        val date: java.time.LocalDate,
        val venue: String,
        val status: MockAttendanceRoster.SessionStatus
    )

    // Latest per-subject attendance state (updated by observeAttendance) — the
    // record rows for a subject derive dates from its class doc and status from
    // these approved/pending/missed counts.
    private var subjectStates: Map<String, SubjectAttendance> = emptyMap()

    // Same derivation as the lecturer's attendance record: the subject's class slot
    // (dayIndex/venue) generates the past 15 occurrences; approved excused rows are
    // Attend, the next `pending` flip to Pending, the rest of the base misses stay
    // Missed. Subjects without a matching class doc return empty → "No schedule".
    fun recordRowsFor(subject: String, classes: List<ClassSession>): List<MissedRecordRow> {
        val session = classes.firstOrNull { it.subjectName == subject }
            ?: return emptyList()
        val state = subjectStates[subject]
            ?: return MockAttendanceRoster.recordSlots(session).map {
                MissedRecordRow(it, session.venue, MockAttendanceRoster.SessionStatus.ATTEND)
            }
        val statuses = MockAttendanceRoster.sessionStatuses(state.base, state.approved, state.pending)
        return MockAttendanceRoster.recordSlots(session).mapIndexed { index, date ->
            MissedRecordRow(
                date = date,
                venue = session.venue,
                status = statuses.getOrElse(index) { MockAttendanceRoster.SessionStatus.ATTEND }
            )
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = profileRepository.getUserProfile()
            result.onSuccess { profile ->
                _uiState.value = _uiState.value.copy(isLoading = false, userProfile = profile)
                // Sprint 9: only STUDENT profiles carry attendance statistics.
                if (profile.role != UserRole.STUDENT) {
                    _uiState.value = _uiState.value.copy(attendance = null)
                } else {
                    // Sprint 13C (enrollment gate): the demo attendance model is
                    // roster-backed — an un-enrolled registration (fresh
                    // accounts) has NO attendance data at all, instead of
                    // silently falling back to the demo user's statistics.
                    if (MockAttendanceRoster.isEnrolledDemoStudent(profile.studentId, profile.email)) {
                        observeAttendance()
                    } else {
                        _uiState.value = _uiState.value.copy(attendance = null)
                    }
                }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load profile"
                )
            }
        }
    }

    // Live statistics: base per-subject misses (MockAttendanceRoster map, the same
    // numbers the Class Management rosters show). Sprint 9: only APPROVED letters
    // excuse a miss; PENDING letters mark rows in the record but leave the count
    // (a declined letter restores the absence). Legacy subject-less letters excuse
    // head-first (SWC first) so pre-per-class letters keep working.
    private fun observeAttendance() {
        val repo = lettersRepository ?: return
        viewModelScope.launch {
            repo.observeLetters().collect { letters ->
                val nonDeclined = letters.filter {
                    it.studentMatric.matchesMatric(MockAttendanceRoster.DEMO_MATRIC) &&
                        it.status != LetterStatus.DECLINED
                }
                val approvedBySubject = nonDeclined
                    .filter { it.status == LetterStatus.APPROVED }
                    .groupingBy { it.subjectName }.eachCount()
                val pendingBySubject = nonDeclined
                    .filter { it.status == LetterStatus.PENDING }
                    .groupingBy { it.subjectName }.eachCount()
                var legacyApprovedRemaining = approvedBySubject[null] ?: 0
                var legacyPendingRemaining = pendingBySubject[null] ?: 0
                val states = linkedMapOf<String, SubjectAttendance>()
                MockAttendanceRoster.DEMO_BASE_MISS_BY_SUBJECT.forEach { (subject, base) ->
                    var approved = (approvedBySubject[subject] ?: 0).coerceAtMost(base)
                    val takeA = minOf(legacyApprovedRemaining, base - approved)
                    approved += takeA
                    legacyApprovedRemaining -= takeA
                    var pending = (pendingBySubject[subject] ?: 0)
                        .coerceAtMost(base - approved)
                    val takeP = minOf(legacyPendingRemaining, base - approved - pending)
                    pending += takeP
                    legacyPendingRemaining -= takeP
                    val missed = base - approved - pending
                    states[subject] = SubjectAttendance(
                        subject = subject,
                        missed = missed,
                        pending = pending,
                        approved = approved,
                        base = base
                    )
                }
                subjectStates = states
                val sessions = MockAttendanceRoster.totalSessionsPerSubject
                val missed = states.values.sumOf { it.missed }
                _uiState.value = _uiState.value.copy(
                    attendance = AttendanceStats(
                        attended = sessions * states.size - missed,
                        missed = missed,
                        subjects = states.values.filter { it.missed > 0 || it.pending > 0 },
                        sessionsPerSubject = sessions
                    )
                )
            }
        }
    }

    // Sprint 9: per-class excuse submission from the missed-classes popup — the PDF
    // uploads to Cloudinary and the letter binds to that SUBJECT, so the lecturer of
    // THAT class reviews it and only THAT subject's missed count reduces.
    fun submitAbsenceLetter(uri: android.net.Uri, subjectName: String) {
        if (_uiState.value.letterUploading) return
        _uiState.value = _uiState.value.copy(letterUploading = true, letterError = null, letterSubmitted = false)
        viewModelScope.launch {
            runCatching {
                val profile = _uiState.value.userProfile
                    ?: throw IllegalStateException("Profile not loaded — try again")
                val matric = profile.studentId
                    ?: profile.email.ifBlank { null }
                    ?: MockAttendanceRoster.DEMO_MATRIC
                val url = cloudinaryRepository.uploadFile(uri, FileType.PDF).getOrThrow()
                val letter = AbsenceLetter(
                    id = java.util.UUID.randomUUID().toString(),
                    studentMatric = matric,
                    studentName = profile.fullName,
                    classId = null,
                    fileName = "${matric}_absence_${subjectName.replace("/", "-")}_${System.currentTimeMillis()}.pdf",
                    fileUrl = url,
                    submittedAt = System.currentTimeMillis(),
                    subjectName = subjectName
                )
                lettersRepository?.submitLetter(letter)?.getOrThrow()
                // Alert staff for review (staffOnly routing).
                com.myuptm.data.repository.FirestoreNotificationsRepository().sendNotification(
                    com.myuptm.domain.model.NotificationType.ANNOUNCEMENT,
                    "Absence letter submitted",
                    "${letter.studentName} (${matric}) submitted an absence letter for $subjectName — pending your review.",
                    senderName = letter.studentName,
                    staffOnly = true
                ).getOrThrow()
            }.onSuccess {
                _uiState.value = _uiState.value.copy(letterUploading = false, letterSubmitted = true)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    letterUploading = false,
                    letterError = e.message ?: "Could not submit absence letter"
                )
            }
        }
    }

    fun clearLetterStates() {
        _uiState.value = _uiState.value.copy(letterSubmitted = false, letterError = null)
    }

    fun uploadAvatar(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, uploadError = null, uploadedImageUrl = null)
            val result = cloudinaryRepository.uploadFile(uri, FileType.IMAGE)
            result.onSuccess { url ->
                // Sprint 8 Task 3: persist the URL, then refresh so the avatar renders.
                val saveResult = profileRepository.updateAvatarUrl(url)
                if (saveResult.isSuccess) {
                    _uiState.value = _uiState.value.copy(isUploading = false, uploadedImageUrl = url, uploadError = null)
                    loadProfile()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        uploadError = "Image uploaded but not saved: ${saveResult.exceptionOrNull()?.message}"
                    )
                }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isUploading = false, uploadError = e.message)
            }
        }
    }
}