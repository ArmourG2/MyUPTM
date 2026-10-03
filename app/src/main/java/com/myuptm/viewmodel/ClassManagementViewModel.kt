package com.myuptm.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myuptm.data.repository.FirestoreClassRepository
import com.myuptm.data.repository.FirestoreNotificationsRepository
import com.myuptm.data.repository.FirestoreAbsenceLetterRepository
import com.myuptm.data.repository.MockAttendanceRoster
import com.myuptm.data.repository.RosterStudent
import com.myuptm.data.repository.RealCloudinaryRepository
import com.myuptm.domain.model.AbsenceLetter
import com.myuptm.domain.model.ClassLevel
import com.myuptm.domain.model.ClassSession
import com.myuptm.domain.model.LetterStatus
import com.myuptm.domain.model.NotificationType
import com.myuptm.domain.model.TeachingMedium
import com.myuptm.domain.repository.AbsenceLetterRepository
import com.myuptm.domain.repository.ClassRepository
import com.myuptm.domain.repository.FileType
import com.myuptm.domain.repository.NotificationsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Sprint 7B: global class CRUD for lecturers. Ownership rule: a lecturer may only
// modify classes they created (ownerEmail == their email) or the unowned demo seeds.
// Every successful change fires a CLASS_UPDATE notification so students stay in sync.
class ClassManagementViewModel(application: Application) : AndroidViewModel(application) {

    private val classRepository: ClassRepository = FirestoreClassRepository()
    private val notificationsRepository: NotificationsRepository = FirestoreNotificationsRepository()
    private val lettersRepository: AbsenceLetterRepository = FirestoreAbsenceLetterRepository()
    private val cloudinaryRepository = RealCloudinaryRepository(application)

    // Global classes grouped by day (index 0 = Monday).
    private val _weeklyClasses = MutableStateFlow<List<List<ClassSession>>>(emptyList())
    val weeklyClasses: StateFlow<List<List<ClassSession>>> = _weeklyClasses.asStateFlow()

    // Live absence-letter feed (Sprint 8 Task 4 part 2) for the review flow.
    private val _letters = MutableStateFlow<List<AbsenceLetter>>(emptyList())
    val letters: StateFlow<List<AbsenceLetter>> = _letters.asStateFlow()

    // One-line operation feedback ("Class added" / validation / failure reason).
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    // Sprint 8 Task 5: warning-letter sending state (upload + Firestore write can take seconds).
    private val _warningBusy = MutableStateFlow(false)
    val warningBusy: StateFlow<Boolean> = _warningBusy.asStateFlow()

    init {
        viewModelScope.launch {
            classRepository.observeClasses().collect { classes ->
                _weeklyClasses.value = List(7) { day -> classes.filter { it.dayIndex == day } }
            }
        }
        viewModelScope.launch {
            lettersRepository.observeLetters().collect { letters ->
                _letters.value = letters
            }
        }
    }

    // POC mock roster (documented seam): deterministic students per class.
    fun rosterFor(classSession: ClassSession): List<RosterStudent> =
        MockAttendanceRoster.studentsFor(classSession.id)

    // APPROVE: letter status → APPROVED + notify the student (announcement channel).
    fun approveLetter(letter: AbsenceLetter) {
        viewModelScope.launch {
            lettersRepository.setLetterStatus(letter.id, LetterStatus.APPROVED, null)
                .onSuccess {
                    _status.value = "Letter approved"
                    notificationsRepository.sendNotification(
                        NotificationType.ANNOUNCEMENT,
                        "Absence letter approved",
                        "Your absence letter (${letter.fileName}) was approved.",
                        "Class Management"
                    )
                }
                .onFailure { _status.value = it.message ?: "Could not approve letter" }
        }
    }

    // DECLINE: letter status → DECLINED with a reason + notify the student.
    fun declineLetter(letter: AbsenceLetter, reason: String) {
        viewModelScope.launch {
            lettersRepository.setLetterStatus(letter.id, LetterStatus.DECLINED, reason)
                .onSuccess {
                    _status.value = "Letter declined"
                    notificationsRepository.sendNotification(
                        NotificationType.ANNOUNCEMENT,
                        "Absence letter declined",
                        "Your absence letter (${letter.fileName}) was declined. Reason: $reason",
                        "Class Management"
                    )
                }
                .onFailure { _status.value = it.message ?: "Could not decline letter" }
        }
    }

    // Sprint 8 Task 5: WARNING LETTERS. Red-only rule (missed >= 5) is enforced by the
    // UI (buttons disabled otherwise); targeted to ONE student or a batch in one class.
    // Delivered as ANNOUNCEMENT docs stamped with the student's matric; students see
    // only global + their own warnings in the Home bell inbox.
    fun canWarn(student: RosterStudent): Boolean = student.missed >= 5

    fun redStudents(students: List<RosterStudent>): Int = students.count { canWarn(it) }

    fun sendWarning(
        students: List<RosterStudent>,
        classSession: ClassSession,
        title: String,
        description: String,
        attachmentUri: android.net.Uri?
    ) {
        if (_warningBusy.value) return
        _warningBusy.value = true
        viewModelScope.launch {
            runCatching {
                val (url, name) = attachmentUri?.let { uri ->
                    // Warning attachment = a document (PDF) via the Cloudinary raw seam.
                    val uploaded = cloudinaryRepository.uploadFile(uri, FileType.PDF)
                        .getOrElse { throw it }
                    uploaded to uri.lastPathSegment.orEmpty().ifEmpty { "warning_attachment.pdf" }
                } ?: null to null
                students.forEach { student ->
                    notificationsRepository.sendNotification(
                        type = NotificationType.ANNOUNCEMENT,
                        title = "Warning letter: $title",
                        message = buildString {
                            append(description)
                            append("\n\nClass: ")
                            append(classSession.subjectName)
                            append(" (").append(classSession.section).append(")")
                            if (name != null) append("\nAttachment available in this inbox.")
                        },
                        senderName = classSession.lecturerName,
                        targetMatric = student.matric,
                        attachmentUrl = url,
                        attachmentName = name
                    ).getOrThrow()
                }
            }.onSuccess {
                _status.value = if (students.size == 1) "Warning sent" else "Warnings sent to ${students.size} students"
            }.onFailure { e ->
                _status.value = e.message ?: "Could not send warning"
            }
            _warningBusy.value = false
        }
    }

    // UI-level gate mirroring the repository write check.
    fun canModify(session: ClassSession, ownerEmail: String?): Boolean =
        session.ownerEmail == null || session.ownerEmail == ownerEmail

    fun clearStatus() {
        _status.value = null
    }

    fun addClass(
        subjectName: String,
        lecturerName: String,
        venue: String,
        dayIndex: Int,
        startTime: String,
        endTime: String,
        medium: TeachingMedium,
        section: String,
        level: ClassLevel
    ) {
        if (!validate(subjectName, startTime, endTime)) return
        viewModelScope.launch {
            val session = ClassSession(
                id = "", // document id is assigned by Firestore
                dayIndex = dayIndex,
                subjectName = subjectName.trim(),
                lecturerName = lecturerName.trim(),
                venue = venue.trim(),
                teachingMedium = medium,
                startTime = startTime,
                endTime = endTime,
                section = section.trim(),
                level = level
            )
            classRepository.addClass(session)
                .onSuccess {
                    _status.value = "Class added"
                    notifyChange(
                        title = "New class added",
                        message = "${session.subjectName}: ${dayName(dayIndex)} $startTime–$endTime at ${session.venue}.",
                        senderName = session.lecturerName
                    )
                }
                .onFailure { _status.value = it.message ?: "Could not add class" }
        }
    }

    fun updateClass(
        original: ClassSession,
        subjectName: String,
        lecturerName: String,
        venue: String,
        dayIndex: Int,
        startTime: String,
        endTime: String,
        medium: TeachingMedium,
        section: String,
        level: ClassLevel
    ) {
        if (!validate(subjectName, startTime, endTime)) return
        viewModelScope.launch {
            val updated = original.copy(
                subjectName = subjectName.trim(),
                lecturerName = lecturerName.trim(),
                venue = venue.trim(),
                dayIndex = dayIndex,
                startTime = startTime,
                endTime = endTime,
                teachingMedium = medium,
                section = section.trim(),
                level = level
            )
            classRepository.updateClass(updated)
                .onSuccess {
                    _status.value = "Class updated"
                    val slotChanged = original.dayIndex != dayIndex || original.startTime != startTime
                    val message = if (slotChanged) {
                        "${updated.subjectName} moved from ${dayName(original.dayIndex)} " +
                            "${original.startTime}–${original.endTime} to ${dayName(dayIndex)} $startTime–$endTime."
                    } else {
                        "${updated.subjectName} updated: ${dayName(dayIndex)} $startTime–$endTime at ${updated.venue}."
                    }
                    notifyChange(title = "Class update", message = message, senderName = updated.lecturerName)
                }
                .onFailure { _status.value = it.message ?: "Could not update class" }
        }
    }

    fun removeClass(session: ClassSession) {
        viewModelScope.launch {
            classRepository.removeClass(session.id)
                .onSuccess {
                    _status.value = "Class removed"
                    notifyChange(
                        title = "Class cancelled",
                        message = "${session.subjectName} on ${dayName(session.dayIndex)} " +
                            "${session.startTime}–${session.endTime} has been cancelled.",
                        senderName = session.lecturerName
                    )
                }
                .onFailure { _status.value = it.message ?: "Could not remove class" }
        }
    }

    private suspend fun notifyChange(title: String, message: String, senderName: String) {
        notificationsRepository.sendNotification(NotificationType.CLASS_UPDATE, title, message, senderName)
    }

    private fun validate(subjectName: String, startTime: String, endTime: String): Boolean {
        if (subjectName.isBlank()) {
            _status.value = "Subject name is required"
            return false
        }
        if (!TIME_REGEX.matches(startTime) || !TIME_REGEX.matches(endTime)) {
            _status.value = "Times must be HH:mm (e.g. 09:30)"
            return false
        }
        if (startTime >= endTime) {
            _status.value = "End time must be after start time"
            return false
        }
        return true
    }

    private fun dayName(dayIndex: Int): String = DAY_NAMES[dayIndex.coerceIn(0, 6)]

    private companion object {
        val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val TIME_REGEX = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
    }
}