package com.myuptm.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.myuptm.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
        val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun signInWithGoogle(idToken: String){
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            repository.signInWithGoogle(idToken)
                .onSuccess { user -> _uiState.value = AuthUiState.Success(user) }
                .onFailure { error -> _uiState.value = AuthUiState.Error(error.message ?: "Unauthorized email domain. UPTM emails only") }
        }
    }

    fun onSignInProblem(message: String) {
        _uiState.value = AuthUiState.Error(message)
    }
}

class AuthViewModelFactory(private val repository: AuthRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)){
            return AuthViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown Viewmodel class")
    }
}