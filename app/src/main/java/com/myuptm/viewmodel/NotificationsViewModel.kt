package com.myuptm.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myuptm.data.repository.DataStorePersonalPlanRepository
import com.myuptm.domain.model.AppUser
import com.myuptm.domain.repository.PersonalPlanRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Sprint 7B: device-local clash warnings for the bound student.
// Sprint 9: the Firestore notification LIST lives in the activity-scoped
// InboxViewModel (shared listener: shade push + unread dot). The old "Notify
// students" composer was REMOVED as obsolete — admins reach students through
// Posts (Normal/Attention/Important tiers now push notifications).
class NotificationsViewModel(application: Application) : AndroidViewModel(application) {

    private val _boundUser = MutableStateFlow<AppUser?>(null)
    private var planRepository: PersonalPlanRepository? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val warningsFlow = _boundUser.flatMapLatest { user ->
        val repo = planRepository
        if (user == null || repo == null) flowOf(emptyList()) else repo.observeWarnings()
    }

    val clashWarnings: StateFlow<List<com.myuptm.domain.model.AppNotification>> = warningsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Re-binds to the current signed-in user (activity-scoped VM safety).
    fun bindUser(user: AppUser?) {
        if (_boundUser.value?.email == user?.email) return
        planRepository = user?.let { DataStorePersonalPlanRepository(getApplication(), it.email) }
        _boundUser.value = user
    }

    fun clearWarnings() {
        val repo = planRepository ?: return
        viewModelScope.launch { repo.clearWarnings() }
    }
}
