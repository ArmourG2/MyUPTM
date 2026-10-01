package com.myuptm.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myuptm.data.notifications.NotificationHelper
import com.myuptm.data.repository.DataStorePersonalPlanRepository
import com.myuptm.data.repository.FirestoreNotificationsRepository
import com.myuptm.domain.model.AppNotification
import com.myuptm.domain.model.AppUser
import com.myuptm.domain.model.NotificationType
import com.myuptm.domain.model.toPermissions
import com.myuptm.domain.repository.NotificationsRepository
import com.myuptm.domain.repository.PersonalPlanRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Sprint 7B: Home bell inbox. Firestore notifications for everyone, plus the
// device-local clash warnings for the bound student.
class NotificationsViewModel(application: Application) : AndroidViewModel(application) {

    private val notificationsRepository: NotificationsRepository = FirestoreNotificationsRepository()

    private val _boundUser = MutableStateFlow<AppUser?>(null)
    private var planRepository: PersonalPlanRepository? = null

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val warningsFlow = _boundUser.flatMapLatest { user ->
        val repo = planRepository
        if (user == null || repo == null) flowOf(emptyList()) else repo.observeWarnings()
    }

    val clashWarnings: StateFlow<List<AppNotification>> = warningsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _sendState = MutableStateFlow<String?>(null)
    val sendState: StateFlow<String?> = _sendState.asStateFlow()

    // Manual "Notify Students" gate (Admin + Lecturer-with-Admin).
    val canNotify: StateFlow<Boolean> = _boundUser
        .map { it?.toPermissions()?.canNotifyStudents == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Docs already present on the first snapshot are history — never re-notify for them.
    private val seenIds = mutableSetOf<String>()
    private var baselineSeen = false

    init {
        viewModelScope.launch {
            notificationsRepository.observeNotifications().collect { list ->
                if (baselineSeen) {
                    list.filter { it.id !in seenIds && it.type != NotificationType.CLASH_WARNING }
                        .forEach { notification ->
                            NotificationHelper.show(getApplication(), notification.title, notification.message)
                        }
                }
                baselineSeen = true
                seenIds.addAll(list.map { it.id })
                _notifications.value = list
            }
        }
    }

    // Re-binds to the current signed-in user (activity-scoped VM safety).
    fun bindUser(user: AppUser?) {
        if (_boundUser.value?.email == user?.email) return
        planRepository = user?.let { DataStorePersonalPlanRepository(getApplication(), it.email) }
        _boundUser.value = user
    }

    fun sendNotification(title: String, message: String) {
        val user = _boundUser.value ?: return
        if (title.isBlank() || message.isBlank()) {
            _sendState.value = "Title and message are required"
            return
        }
        viewModelScope.launch {
            notificationsRepository.sendNotification(
                NotificationType.ANNOUNCEMENT,
                title.trim(),
                message.trim(),
                user.email
            )
                .onSuccess { _sendState.value = "Notification sent" }
                .onFailure { _sendState.value = it.message ?: "Could not send notification" }
        }
    }

    fun clearSendState() {
        _sendState.value = null
    }

    fun clearWarnings() {
        val repo = planRepository ?: return
        viewModelScope.launch { repo.clearWarnings() }
    }
}