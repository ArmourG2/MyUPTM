package com.myuptm.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myuptm.data.repository.DataStoreSettingsRepository
import com.myuptm.domain.model.AppFont
import com.myuptm.domain.model.AppSettings
import com.myuptm.domain.model.ThemeMode
import com.myuptm.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    // Cold Flow from DataStore -> hot StateFlow the UI can observe.
    // WhileSubscribed(5_000): keeps sharing alive for 5s after last collector
    // stops, so rotating the screen or navigating away/back doesn't restart it.
    val settings: StateFlow<AppSettings> = repository.getSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppSettings() // SYSTEM + DEFAULT until DataStore emits
        )

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.updateThemeMode(mode) }
    }

    fun updateAppFont(font: AppFont) {
        viewModelScope.launch { repository.updateAppFont(font) }
    }

    /**
     * Mock sign-out (Sprint 4): no auth session exists yet.
     * Navigation to SIGN_IN is triggered by the UI (T7).
     * TODO(Sprint 6): call Firebase Auth sign-out here.
     */
    fun signOut() {
        // Intentional empty seam for now.
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                // Application context from CreationExtras - no Activity leak.
                val context = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                SettingsViewModel(DataStoreSettingsRepository(context))
            }
        }
    }
}