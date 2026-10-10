package com.myuptm.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myuptm.data.auth.GoogleAuthUiClient
import com.myuptm.data.notifications.NotificationHelper
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
    ERROR,
    // Sprint 13: Firebase auth OK but the account has no users/{uid} doc (or it
    // is still unapproved). A waiting screen shows + a snapshot listener on the
    // account doc auto-enters the app the moment the admin assigns a role.
    WAITING_APPROVAL
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

    // Sprint 13: email shown on the waiting screen + the approval watcher.
    private val _pendingEmail = MutableStateFlow<String?>(null)
    val pendingEmail: StateFlow<String?> = _pendingEmail.asStateFlow()
    private var approvalJob: kotlinx.coroutines.Job? = null

    // Sprint 13C: true once the signed-in account's identity fetch has SETTLED
    // (role known / waiting parked / bounced). MainActivity holds the NavHost
    // behind a loading gate until then — kills the bar-less Home flash on
    // cold starts.
    private val _identityReady = MutableStateFlow(false)
    val identityReady: StateFlow<Boolean> = _identityReady.asStateFlow()


    // Sprint 14 (access rule): only UPTM addresses may use the app — staff
    // (@uptm.edu.my) and students (@student.uptm.edu.my). Legacy exceptions:
    // zjosh9053@gmail.com is a registered LECTURER, akmal2kembar@gmail.com is a
    // registered ADMIN — both keep access before the rule existed.
    private val legacyAccountEmails = setOf(
        "zjosh9053@gmail.com",      // lecturer
        "akmal2kembar@gmail.com"    // admin
    )

    private fun isAllowedEmail(email: String?): Boolean {
        val e = email?.trim()?.lowercase() ?: return false
        if (e in legacyAccountEmails) return true
        return e.endsWith("@uptm.edu.my") || e.endsWith("@student.uptm.edu.my")
    }

    private fun rejectNonUptmEmail(email: String?) {
        authRepository.signOut()
        _errorMessage.value =
            "Sign in with your UPTM email (@uptm.edu.my for staff or @student.uptm.edu.my for students)."
        _authState.value = AuthState.ERROR
    }


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

            // Sprint 14 (access rule): block 3rd-party emails BEFORE any
            // user-doc lookup/registration — the pending-approval path used to
            // accept any Gmail.
            if (!isAllowedEmail(firebaseUser.email)) {
                rejectNonUptmEmail(firebaseUser.email)
                return@launch
            }

            val recordResult = userRepository.fetchUser(firebaseUser.uid)
            if (recordResult.isFailure) {
                authRepository.signOut()
                _errorMessage.value = "Unable to verify your account right now. Please try again."
                _authState.value = AuthState.ERROR
                return@launch
            }

            val record = recordResult.getOrNull()
            _identityReady.value = true

            // Sprint 13 (pending-approval flow): an account with NO users/{uid}
            // doc registers a PENDING skeleton and waits — the Admin Dashboard
            // receives it for role assignment. Auth stays ALIVE so the doc
            // listener can auto-enter (no sign-out retry loop anymore).
            if (record == null) {
                val registered = userRepository.registerPendingAccount(
                    uid = firebaseUser.uid,
                    email = firebaseUser.email.orEmpty(),
                    fullName = firebaseUser.displayName
                )
                if (registered.isFailure) {
                    authRepository.signOut()
                    _errorMessage.value = "Could not register your request. Please try again."
                    _authState.value = AuthState.ERROR
                    return@launch
                }
                enterWaitingApproval(firebaseUser.uid, firebaseUser.email.orEmpty())
                return@launch
            }
            if (!record.approved) {
                // Doc exists but is still pending (admin already saw it) — wait.
                enterWaitingApproval(firebaseUser.uid, record.email)
                return@launch
            }


            _userRecord.value = record
            _userRole.value = record.role
            _authState.value = AuthState.SUCCESS
        }
    }

    // Sprint 13: parks the signed-in account on the waiting screen and watches
    // its own user doc; the moment the admin writes approved = true the app
    // resumes automatically (no re-login, no retry loop) + a shade notification.
    private fun enterWaitingApproval(uid: String, email: String) {
        _pendingEmail.value = email
        _authState.value = AuthState.WAITING_APPROVAL
        approvalJob?.cancel()
        approvalJob = viewModelScope.launch {
            userRepository.observeAccountApproval(uid).collect { approval ->
                if (!approval.approved) return@collect
                val refreshed = userRepository.fetchUser(uid).getOrNull() ?: return@collect
                approvalJob?.cancel() // stop this collector before navigating
                NotificationHelper.show(
                    getApplication<Application>(),
                    title = "Account approved",
                    message = "${refreshed.email} can now use MyUPTM as ${refreshed.role.name.lowercase()}."
                )
                _userRecord.value = refreshed
                _userRole.value = refreshed.role
                _pendingEmail.value = null
                _authState.value = AuthState.SUCCESS
            }
        }
    }

    fun signOut() {
        approvalJob?.cancel()
        authRepository.signOut()
        _userRole.value = null
        _userRecord.value = null
        _pendingEmail.value = null
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
                val user = authRepository.currentUser!!
                // Sprint 14 (access rule): a persisted non-UPTM session (from
                // before the rule existed) is bounced on restart too.
                if (!isAllowedEmail(user.email)) {
                    rejectNonUptmEmail(user.email)
                    _identityReady.value = true
                    return@launch
                }
                val result = userRepository.fetchUser(uid)
                result.onSuccess { record ->
                    when {
                        record != null && record.approved -> {
                            _userRole.value = record.role
                            _userRecord.value = record
                        }
                        // Sprint 13: restart while an account is pending/missing —
                        // park on the waiting screen (listener re-arms there).
                        record != null -> enterWaitingApproval(user.uid, record.email)
                        else -> {
                            userRepository.registerPendingAccount(
                                uid = user.uid,
                                email = user.email.orEmpty(),
                                fullName = user.displayName
                            )
                            enterWaitingApproval(user.uid, user.email.orEmpty())
                        }
                    }
                }.onFailure {
                    signOut() // Network/read problem — back to the sign-in screen
                }.also {
                    _identityReady.value = true
                }
            }
        }
    }
}