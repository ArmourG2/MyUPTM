package com.myuptm.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myuptm.data.repository.FirestoreClassRepository
import com.myuptm.data.repository.FirestoreNotificationsRepository
import com.myuptm.data.repository.FirestoreAbsenceLetterRepository
import com.myuptm.data.repository.FirestoreAttendanceSessionRepository
import com.myuptm.data.repository.FirestoreProfileRepository
import com.myuptm.data.repository.MockAttendanceRoster
import com.myuptm.data.repository.matchesMatric
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Sprint 7B: global class CRUD for lecturers. Ownership rule: colleges may only
// modify the classes they own (ownerEmail == their email). Every successful change
// fires a CLASS_UPDATE notification so students stay in sync.
// Sprint 9: lecturers see ONLY their own classes (scoping in the collector), the
// lecturer name comes from the ACCOUNT profile (no field in the editor), classes
// carry a SET of programme levels (merged Diploma/Degree sections), and rosters
// compute LIVE missed counts from the student's submitted letters.
class ClassManagementViewModel(application: Application) : AndroidViewModel(application) {

    // Sprint 9: lecturer email for classes scoping — must be bound by the screen
    // (activity-scoped VM safety, same pattern as TimetableViewModel.bindUser).
    private val classRepository: ClassRepository = FirestoreClassRepository()
    private val notificationsRepository: NotificationsRepository = FirestoreNotificationsRepository()
    private val lettersRepository: AbsenceLetterRepository = FirestoreAbsenceLetterRepository()
    private val profileRepository: FirestoreProfileRepository = FirestoreProfileRepository()
    private val cloudinaryRepository = RealCloudinaryRepository(application)
    // Sprint 13: attendance-session reads for the absentee-notice join.
    private val sessionRepository = FirestoreAttendanceSessionRepository()

    // The signed-in lecturer's email; drives "only MY classes" scoping.
    private var ownerEmail: String? = null

    // Sprint 9: the class owner's display name comes straight from the account
    // profile — the Add/Edit editor no longer asks for a lecturer name.
    private val _lecturerDisplayName = MutableStateFlow<String?>(null)
    private val lecturerDisplayName: String? get() = _lecturerDisplayName.value

    // Global classes grouped by day (index 0 = Monday), scoped to the owner.
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

    // Sprint 13D: per-week cancellations of recurring classes — key
    // "classId|weekStartEpochDay" → reason (null = automatic preset notice).
    // Everyone greys cancelled slots out; a lecturer can reinstate the week.
    val cancellations: StateFlow<Map<String, String?>> = classRepository
        .observeClassCancellations()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        viewModelScope.launch {
            classRepository.observeClasses().collect { classes ->
                // Sprint 9: lecturer scope — ONLY classes they teach
                // (ownerEmail == ownerEmail). Department seeds (ownerEmail == null) are
                // hidden from every lecturer; they own their own classes.
                val scoped = ownerEmail?.let { email ->
                    classes.filter { it.ownerEmail == email }
                } ?: classes
                _weeklyClasses.value = List(7) { day -> scoped.filter { it.dayIndex == day } }
            }
        }
        viewModelScope.launch {
            lettersRepository.observeLetters().collect { letters ->
                _letters.value = letters
            }
        }
    }

    // Sprint 9 roster binding: rosters carry REAL student names/matrics baked from the
    // owner's section lists (display-only; only
    // AM2412018161 = AKMAL IRFAN has an account). The demo student's counts are LIVE:
    // every PENDING/APPROVED letter they submitted reduces their missed count by 1
    // (a DECLINED letter restores the absence). Sprint 9: letters are PER-CLASS —
    // only letters bound to THIS class's subject (or legacy subject-less ones) count.
    fun rosterFor(classSession: ClassSession, letters: List<AbsenceLetter>): List<RosterStudent> {
        val approved = lettersFor(MockAttendanceRoster.DEMO_MATRIC, classSession, letters)
            .count { it.status == LetterStatus.APPROVED }
        return MockAttendanceRoster.studentsFor(
            classSession.section,
            classSession.subjectName,
            approved
        )
    }

    // Letters relevant to ONE student in ONE class (subject-bound; legacy
    // subject-less letters stay visible everywhere for backward compatibility).
    fun lettersFor(studentMatric: String, classSession: ClassSession, letters: List<AbsenceLetter>): List<AbsenceLetter> =
        letters.filter {
            it.studentMatric.matchesMatric(studentMatric) &&
                (it.subjectName == null || it.subjectName == classSession.subjectName)
        }

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

    // Sprint 8 Task 5: WARNING LETTERS. Red-only rule (Sprint 13D: >= 5 missed
    // HOURS, not occurrences) enforced via [RosterStudent.severity]; the UI
    // (buttons disabled otherwise); targeted to ONE student or a batch in one class.
    // Delivered as ANNOUNCEMENT docs stamped with the student's matric; students see
    // only global + their own warnings in the Home bell inbox.
    fun canWarn(student: RosterStudent): Boolean = student.severity == 2

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
                            append(classSession.displayCode)
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

    // Re-binds to the signed-in lecturer (activity-scoped VM safety) + their profile.
    fun bindOwner(email: String?) {
        ownerEmail = email
        if (email != null && _lecturerDisplayName.value == null) {
            viewModelScope.launch {
                _lecturerDisplayName.value = profileRepository.getUserProfile().getOrNull()
                    ?.fullName ?: email
            }
        }
    }

    fun addClass(
        subjectName: String,
        venue: String,
        dayIndex: Int,
        startTime: String,
        endTime: String,
        medium: TeachingMedium,
        section: String,
        levels: List<ClassLevel>,
        attention: Boolean,
        reason: String?,
        imageUri: android.net.Uri?,
        // Sprint 13D (owner ruling): custom classes carry the anchor date +
        // repeat rule (oneOffWeeks: null = every week, 1 = this week only,
        // 2–14 = a couple of weeks). null date = recurring.
        oneOffDate: java.time.LocalDate? = null,
        oneOffWeeks: Int? = null
    ) {
        if (!validate(subjectName, startTime, endTime)) return
        viewModelScope.launch {
            val session = ClassSession(
                id = "", // document id is assigned by Firestore
                dayIndex = dayIndex,
                subjectName = subjectName.trim(),
                lecturerName = lecturerDisplayName ?: ownerEmail ?: "",
                venue = venue.trim(),
                teachingMedium = medium,
                startTime = startTime,
                endTime = endTime,
                section = section.trim(),
                levels = levels.ifEmpty { listOf(ClassLevel.DIPLOMA) },
                date = oneOffDate?.toEpochDay(),
                weeks = if (oneOffDate != null) oneOffWeeks else null
            )
            classRepository.addClass(session)
                .onSuccess {
                    _status.value = "Class added"
                    val repeatNote = when {
                        oneOffWeeks != null && oneOffWeeks > 1 -> " for $oneOffWeeks weeks"
                        oneOffDate != null -> " (this week only)"
                        else -> ""
                    }
                    notifyChange(
                        title = "New class added",
                        message = "${session.displayCode}: $startTime–$endTime on ${dayName(dayIndex)} " +
                            "at ${session.venue}.$repeatNote",
                        senderName = session.lecturerName,
                        attention = attention,
                        reason = reason,
                        imageUri = imageUri
                    )
                }
                .onFailure { _status.value = it.message ?: "Could not add class" }
        }
    }

    fun updateClass(
        original: ClassSession,
        subjectName: String,
        venue: String,
        dayIndex: Int,
        startTime: String,
        endTime: String,
        medium: TeachingMedium,
        section: String,
        levels: List<ClassLevel>,
        attention: Boolean,
        reason: String?,
        imageUri: android.net.Uri?,
        // Sprint 13D: repeat-rule payload (custom classes may switch between
        // this-week-only / N weeks / every week). Nulls = recurring.
        slotDate: java.time.LocalDate? = null,
        oneOffWeeks: Int? = null
    ) {
        if (!validate(subjectName, startTime, endTime)) return
        viewModelScope.launch {
            val updated = original.copy(
                subjectName = subjectName.trim(),
                venue = venue.trim(),
                dayIndex = dayIndex,
                startTime = startTime,
                endTime = endTime,
                teachingMedium = medium,
                section = section.trim(),
                levels = levels.ifEmpty { listOf(ClassLevel.DIPLOMA) },
                date = slotDate?.toEpochDay(),
                weeks = slotDate?.let { oneOffWeeks }
            )
            classRepository.updateClass(updated)
                .onSuccess {
                    _status.value = "Class updated"
                    val slotChanged = original.dayIndex != dayIndex || original.startTime != startTime
                    val message = if (slotChanged) {
                        "${updated.displayCode} moved from ${dayName(original.dayIndex)} " +
                            "${original.startTime}–${original.endTime} to ${dayName(dayIndex)} $startTime–$endTime."
                    } else {
                        "${updated.displayCode} updated: ${dayName(dayIndex)} $startTime–$endTime at ${updated.venue}."
                    }
                    notifyChange(
                        title = "Class update",
                        message = message,
                        senderName = updated.lecturerName,
                        attention = attention,
                        reason = reason,
                        imageUri = imageUri
                    )
                }
                .onFailure { _status.value = it.message ?: "Could not update class" }
        }
    }

    fun removeClass(session: ClassSession) {
        viewModelScope.launch {
            classRepository.removeClass(session.id)
                .onSuccess {
                    _status.value = "Class removed"
                    val title = "Class cancelled"
                    val message = "${session.displayCode} on ${dayName(session.dayIndex)} " +
                        "${session.startTime}–${session.endTime} has been cancelled."
                    // Students + lecturer copy (Sprint 14 receipts).
                    notificationsRepository.sendNotification(
                        NotificationType.ANNOUNCEMENT,
                        title,
                        message,
                        senderName = session.lecturerName
                    )
                    notificationsRepository.sendNotification(
                        NotificationType.ANNOUNCEMENT,
                        title,
                        message,
                        senderName = session.lecturerName,
                        lecturerOnly = true
                    )
                }
                .onFailure { _status.value = it.message ?: "Could not remove class" }
        }
    }

    // Sprint 13D (owner rules): cancelling a RECURRING (faculty-preset) class is
    // per-week and visible to EVERYONE — the slot greys out on their timetables,
    // and students get an automatic notification: a preset message UNLESS the
    // lecturer states a reason (then the reason rides the notification).
    fun cancelClassForWeek(
        session: ClassSession,
        anchorDate: java.time.LocalDate,
        reason: String?
    ) {
        viewModelScope.launch {
            // Guard (Sprint 13D): an attendance session running for this class
            // must end before the class can be cancelled — students mid-scanning
            // would otherwise verify for a greyed slot.
            val liveSession = ownerEmail?.let { email ->
                sessionRepository.sessionsForLecturerAndClass(email, session.id)
                    .getOrDefault(emptyList())
                    .any { it.active }
            } ?: false
            if (liveSession) {
                _status.value = "An attendance session is running for this class — end it first."
                return@launch
            }
            val weekMonday = anchorDate.minusDays(((anchorDate.dayOfWeek.value) - 1).toLong())
            classRepository.cancelClassForWeek(
                classId = session.id,
                weekStartEpochDay = weekMonday.toEpochDay(),
                reason = reason?.trim()?.ifBlank { null },
                lecturerEmail = ownerEmail
            ).onSuccess {
                _status.value = "Class cancelled for this week"
                notificationsRepository.sendNotification(
                    type = NotificationType.CLASS_UPDATE,
                    title = "Class cancelled",
                    message = buildString {
                        append("${session.displayCode} (${dayName(session.dayIndex)}, ")
                        append("${session.startTime}–${session.endTime}) is cancelled for this week.")
                        if (!reason.isNullOrBlank()) {
                            append("\nReason: ")
                            append(reason.trim())
                        }
                    },
                    senderName = session.lecturerName,
                    studentOnly = true,
                    importance = "attention"
                )
                // Sprint 14: the acting lecturer gets the same receipt.
                notificationsRepository.sendNotification(
                    type = NotificationType.CLASS_UPDATE,
                    title = "Class cancelled",
                    message = buildString {
                        append("${session.displayCode} (${dayName(session.dayIndex)}, ")
                        append("${session.startTime}–${session.endTime}) is cancelled for this week.")
                        if (!reason.isNullOrBlank()) {
                            append("\nReason: ")
                            append(reason.trim())
                        }
                    },
                    senderName = session.lecturerName,
                    lecturerOnly = true,
                    importance = "attention"
                )
            }.onFailure { _status.value = it.message ?: "Could not cancel class" }
        }
    }

    // Brings a cancelled class back for that week (removes the cancellation).
    fun reinstateClassForWeek(session: ClassSession, anchorDate: java.time.LocalDate) {
        viewModelScope.launch {
            val weekMonday = anchorDate.minusDays(((anchorDate.dayOfWeek.value) - 1).toLong())
            classRepository.reinstateClassForWeek(session.id, weekMonday.toEpochDay())
                .onSuccess {
                    _status.value = "Class reinstated for this week"
                    notificationsRepository.sendNotification(
                        type = NotificationType.CLASS_UPDATE,
                        title = "Class is back on",
                        message = "${session.displayCode} (${dayName(session.dayIndex)}, " +
                            "${session.startTime}–${session.endTime}) will run as scheduled this week.",
                        senderName = session.lecturerName,
                        studentOnly = true
                    )
                    notificationsRepository.sendNotification(
                        type = NotificationType.CLASS_UPDATE,
                        title = "Class is back on",
                        message = "${session.displayCode} (${dayName(session.dayIndex)}, " +
                            "${session.startTime}–${session.endTime}) will run as scheduled this week.",
                        senderName = session.lecturerName,
                        lecturerOnly = true
                    )
                }
                .onFailure { _status.value = it.message ?: "Could not reinstate class" }
        }
    }

    private suspend fun notifyChange(
        title: String,
        message: String,
        senderName: String,
        attention: Boolean = false,
        reason: String? = null,
        imageUri: android.net.Uri? = null
    ) {
        // Sprint 9: the class editor's toggle promotes this change to an attention
        // (yellow) notification; otherwise it stays a normal (blue) update.
        // Changes are STUDENT-ONLY news — lecturers wrote them, they don't need
        // them in their own inbox. Optional reason/context + optional image
        // (Cloudinary, like posts) enrich the detail page; no filler when omitted.
        // Sprint 14 (owner ask): the ACTING LECTURER also receives a copy
        // (lecturerOnly receipt) so a timetable change never silently vanishes.
        notificationsRepository.sendNotification(
            NotificationType.CLASS_UPDATE,
            title,
            buildString {
                append(message)
                if (!reason.isNullOrBlank()) {
                    append("\n\n")
                    append(reason.trim())
                }
            },
            senderName,
            studentOnly = true,
            importance = if (attention) "attention" else "normal",
            imageUrl = imageUri?.let { uri ->
                cloudinaryRepository.uploadFile(uri, FileType.IMAGE).getOrNull()
            }
        )
        notificationsRepository.sendNotification(
            NotificationType.CLASS_UPDATE,
            title,
            buildString {
                append(message)
                if (!reason.isNullOrBlank()) {
                    append("\n\n")
                    append(reason.trim())
                }
            },
            senderName,
            lecturerOnly = true,
            importance = if (attention) "attention" else "normal"
        )
    }

    // Sprint 9 (Option A): "End class & notify absentees" — students who did not
    // verify attendance receive a YELLOW missed-class notice. Submitting a letter
    // excuse = the red/yellow clearing path already wired for the demo.
    // Sprint 13 (P0 backlog FIX): the notice list is joined against
    // attendance_checkins — students who VERIFIED (scan or PIN) during today's
    // sessions for this class are SKIPPED; only true absentees are notified.
    fun closeAttendance(classSession: ClassSession) {
        viewModelScope.launch {
            runCatching {
                val roster = MockAttendanceRoster.studentsFor(
                    classSession.section,
                    classSession.subjectName,
                    approvedLetters = 0
                )
                // Collect every matric that verified attendance TODAY for this
                // class (scan or PIN) from the lecturer's session history.
                // All queries equality-only; "today" + "latest session" are
                // decided client-side to avoid composite indexes.
                val verifiedMatrics = mutableListOf<String>()
                val dayStart = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
                ownerEmail?.let { email ->
                    sessionRepository.sessionsForLecturerAndClass(
                        email, classSession.id
                    ).getOrDefault(emptyList())
                        .filter { it.startedAt >= dayStart }
                        .forEach { session ->
                            sessionRepository.checkInsForSession(session.id)
                                .getOrDefault(emptyList())
                                .forEach { verifiedMatrics.add(it.matric) }
                        }
                }
                val absent = roster.filter { student ->
                    verifiedMatrics.none { verified -> verified.matchesMatric(student.matric) }
                }
                absent.forEach { student ->
                    notificationsRepository.sendNotification(
                        NotificationType.ANNOUNCEMENT,
                        title = "You missed ${classSession.displayCode}",
                        message = "Your attendance was not verified for ${classSession.displayCode} " +
                            "(${dayName(classSession.dayIndex)} ${classSession.startTime}–${classSession.endTime}). " +
                            "If your absence is excused, open this notification and submit your absence letter.",
                        senderName = classSession.lecturerName,
                        targetMatric = student.matric,
                        importance = "attention",
                        studentOnly = true
                    ).getOrThrow()
                }
                absent.size to (roster.size - absent.size)
            }.onSuccess { (notified, skipped) ->
                _status.value = "Session ended — $notified missed-class notice(s) sent; " +
                    "$skipped verified student(s) skipped"
            }.onFailure { e ->
                _status.value = e.message ?: "Could not end the attendance session"
            }
        }
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
