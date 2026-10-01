package com.myuptm.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myuptm.data.repository.FirestoreClassRepository
import com.myuptm.data.repository.FirestoreNotificationsRepository
import com.myuptm.domain.model.ClassSession
import com.myuptm.domain.model.NotificationType
import com.myuptm.domain.model.TeachingMedium
import com.myuptm.domain.repository.ClassRepository
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

    // Global classes grouped by day (index 0 = Monday).
    private val _weeklyClasses = MutableStateFlow<List<List<ClassSession>>>(emptyList())
    val weeklyClasses: StateFlow<List<List<ClassSession>>> = _weeklyClasses.asStateFlow()

    // One-line operation feedback ("Class added" / validation / failure reason).
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    init {
        viewModelScope.launch {
            classRepository.observeClasses().collect { classes ->
                _weeklyClasses.value = List(7) { day -> classes.filter { it.dayIndex == day } }
            }
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
        medium: TeachingMedium
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
                endTime = endTime
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
        medium: TeachingMedium
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
                teachingMedium = medium
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