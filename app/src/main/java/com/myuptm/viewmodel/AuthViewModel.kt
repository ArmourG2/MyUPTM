package com.myuptm.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myuptm.data.auth.GoogleAuthUiClient
import com.myuptm.data.repository.FirebaseAuthRepository
import com.myuptm.domain.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AuthState {
    IDLE,
    LOADING,
    SUCCESS,
    ERROR
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val googleAuthUiClient = GoogleAuthUiClient(application.applicationContext)
    private val authRepository = FirebaseAuthRepository()

    private val _authState = MutableStateFlow(AuthState.IDLE)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _userRole = MutableStateFlow<UserRole?>(null)
    val userRole: StateFlow<UserRole?> = _userRole.asStateFlow()

    fun signIn(activity: Activity) {
        viewModelScope.launch {
            _authState.value = AuthState.LOADING
            _errorMessage.value = null

            // Step 1: Get Google ID token
            val idTokenResult = googleAuthUiClient.signIn(activity)
            if (idTokenResult.isFailure) {
                _errorMessage.value = "Sign-in cancelled or failed"
                _authState.value = AuthState.ERROR
                return@launch
            }
            val idToken = idTokenResult.getOrThrow()

            // Step 2: Firebase handshake
            val userResult = authRepository.signInWithGoogle(idToken)
            if (userResult.isFailure) {
                _errorMessage.value = "Firebase authentication failed"
                _authState.value = AuthState.ERROR
                return@launch
            }
            val user = userResult.getOrThrow()

            // Step 3: Determine role from email
            val email = user.email.orEmpty()
            val role = determineRole(email)

            if (role == null) {
                authRepository.signOut()
                _errorMessage.value = "This email is not authorized for MyUPTM"
                _authState.value = AuthState.ERROR
                return@launch
            }

            _userRole.value = role
            _authState.value = AuthState.SUCCESS
        }
    }

    private fun determineRole(email: String): UserRole? {
        val adminEmails = listOf("akmal2kembar@gmail.com", "akmal2kembar@proton.me")

        return when {
            email in adminEmails -> UserRole.ADMIN
            email.endsWith("@uptm.edu.my") -> UserRole.LECTURER
            email.endsWith("@student.uptm.edu.my") -> UserRole.STUDENT
            else -> null
        }
    }

    fun signOut() {
        authRepository.signOut()
        _userRole.value = null
        _authState.value = AuthState.IDLE
    }

    fun resetState() {
        _authState.value = AuthState.IDLE
        _errorMessage.value = null
    }
}