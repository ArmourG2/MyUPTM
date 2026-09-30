package com.myuptm.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myuptm.data.auth.GoogleAuthUiClient
import com.myuptm.data.repository.FirebaseAuthRepository
import com.myuptm.data.repository.FirestoreUserRepository
import com.myuptm.domain.model.AppUser
import com.myuptm.domain.model.UserRole
import com.myuptm.domain.repository.UserRepository
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

    private val userRepository: UserRepository = FirestoreUserRepository()


    val isLoggedIn: Boolean
        get() = authRepository.currentUser != null

    private val _authState = MutableStateFlow(AuthState.IDLE)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _userRole = MutableStateFlow<UserRole?>(null)
    val userRole: StateFlow<UserRole?> = _userRole.asStateFlow()
    private val _userRecord = MutableStateFlow<AppUser?>(null)
    val userRecord: StateFlow<AppUser?> = _userRecord.asStateFlow()


    fun signIn(activity: Activity) {
        viewModelScope.launch {
            _authState.value = AuthState.LOADING
            _errorMessage.value = null

            // Step 1: Get Google ID token
            val idTokenResult = googleAuthUiClient.signIn(activity)
            if (idTokenResult.isFailure) {
                val e = idTokenResult.exceptionOrNull()
                android.util.Log.e("AuthDebug", "Credential Manager failed", e)
                _errorMessage.value = "Sign-in failed: ${e?.message ?: "cancelled"}"
                _authState.value = AuthState.ERROR
                return@launch
            }
            val idToken = idTokenResult.getOrThrow()

            // Step 2: Firebase handshake
            val userResult = authRepository.signInWithGoogle(idToken)
            if (userResult.isFailure) {
                _errorMessage.value = userResult.exceptionOrNull()?.message ?: "Unknown"
                _authState.value = AuthState.ERROR
                return@launch
            }
            val firebaseUser = userResult.getOrThrow()

            val recordResult = userRepository.fetchUser(firebaseUser.uid)
            if (recordResult.isFailure) {
                authRepository.signOut()
                _errorMessage.value = "Unable to verify your account right now. Please try again."
                _authState.value = AuthState.ERROR
                return@launch
            }

            val record = recordResult.getOrNull()

            if (record == null) {
                authRepository.signOut()
                _errorMessage.value = "This account is not authorized for MyUPTM"
                _authState.value = AuthState.ERROR
                return@launch
            }
            if (!record.approved) {
                authRepository.signOut()
                _errorMessage.value = "Your account is pending approval."
                _authState.value = AuthState.ERROR
                return@launch
            }


            _userRecord.value = record
            _userRole.value = record.role
            _authState.value = AuthState.SUCCESS
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
    init {
        // If user is already logged in (e.g., app restart), fetch their role from Firestore
        if (authRepository.currentUser != null) {
            viewModelScope.launch {
                val uid = authRepository.currentUser!!.uid
                val result = userRepository.fetchUser(uid)
                result.onSuccess { record ->
                    if (record != null && record.approved) {
                        _userRole.value = record.role
                        _userRecord.value = record
                    } else {
                        signOut() // Not approved or not found
                    }
                }
            }
        }
    }
}