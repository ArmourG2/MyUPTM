package com.myuptm.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.myuptm.data.repository.FirestoreClassRepository
import com.myuptm.data.repository.FirestorePostsRepository
import com.myuptm.domain.model.ClassSession
import com.myuptm.domain.model.Post
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Sprint 11: admin overview data. Per the owner's POC ruling:
//  - LECTURERS: a REAL count (accounts are limited and meaningful),
//  - STUDENTS: a MOCK constant — roster matrics are baked demo data and no real
//    student accounts exist, so a live count would be misleading (always 1),
//  - PENDING UPTM EMAILS: user documents whose UPTM email has not been assigned
//    a role/approval yet (approved == false) — the admin's follow-up list,
//  - CLASSES: real count from the global "classes" collection,
//  - Announcements: the live posts feed (read-only).
class AdminDashboardViewModel(application: Application) : AndroidViewModel(application) {

    data class PendingAccount(
        val uid: String,
        val email: String,
        val role: String? // null = UPTM email not yet assigned lecturer/student/admin
    )

    data class DashboardState(
        val studentCount: Int = MOCK_STUDENT_COUNT,
        val lecturerCount: Int = 0,
        val classCount: Int = 0,
        val pendingAccounts: List<PendingAccount> = emptyList(),
        val isLoadingUsers: Boolean = true
    )

    private val db = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    // Announcements feed (read-only) — same repository the Posts screen uses.
    val posts: StateFlow<List<Post>> = FirestorePostsRepository()
        .observePosts()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Classes count — reuses the global repository (auto-seeds on first read, too).
    val classes: StateFlow<List<ClassSession>> = FirestoreClassRepository()
        .observeClasses()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // POC rosters are baked demo data (single account-backed matric) — the
        // students tile is a MOCK figure by owner decision.
        viewModelScope.launch {
            classes.collect { rows ->
                _state.value = _state.value.copy(classCount = rows.size)
            }
        }
        // Live users watch: lecturers count + pending (unassigned) UPTM emails.
        db.collection(USERS).addSnapshotListener { snapshot, _ ->
            val docs = snapshot?.documents ?: emptyList()
            var lecturers = 0
            val pending = mutableListOf<PendingAccount>()
            docs.forEach { doc ->
                val role = doc.getString("role")
                val approved = doc.getBoolean("approved") ?: false
                if (role.equals("lecturer", ignoreCase = true)) lecturers++
                if (!approved) {
                    pending.add(
                        PendingAccount(
                            uid = doc.id,
                            email = doc.getString("email") ?: "(no email on record)",
                            role = role
                        )
                    )
                }
            }
            _state.value = _state.value.copy(
                lecturerCount = lecturers,
                pendingAccounts = pending.sortedBy { it.email.lowercase() },
                isLoadingUsers = false
            )
        }
    }

    companion object {
        // Owner decision: students are ambiguous in the POC (mock rosters, one real
        // account) — show the roster population figure instead of a live count.
        const val MOCK_STUDENT_COUNT = 128
        private const val USERS = "users"
    }
}
