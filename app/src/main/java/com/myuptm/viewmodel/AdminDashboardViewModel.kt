package com.myuptm.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.myuptm.data.repository.FirestoreClassRepository
import com.myuptm.data.repository.FirestorePostsRepository
import com.myuptm.data.repository.MockAttendanceRoster
import com.myuptm.domain.model.ClassSession
import com.myuptm.domain.model.Post
import com.myuptm.domain.model.UserRole
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

    // ---- Sprint 13C: Admin directory rows (tile lists) ----
    // One StudentRow = one Firestore `students` doc.
    data class StudentRow(val name: String, val studentId: String, val faculty: String, val semester: Int?)

    // Lecturers = registered lecturer accounts + timetable class-owners.
    data class LecturerRow(val name: String, val email: String, var classCount: Int)

    // Classes in "SUBJECT CODE; NAME;" form (code = display code, name = lecturer).
    data class ClassRow(val code: String, val name: String)

    private val db = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    // Announcements feed (read-only) — same repository the Posts screen uses.
    val posts: StateFlow<List<Post>> = FirestorePostsRepository()
        .observePosts()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // One-line operation feedback (Sprint 13: approval writes) + busy state.
    private val _approvalBusy = MutableStateFlow(false)
    val approvalBusy: StateFlow<Boolean> = _approvalBusy.asStateFlow()
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    // Classes count — reuses the global repository (auto-seeds on first read, too).
    val classes: StateFlow<List<ClassSession>> = FirestoreClassRepository()
        .observeClasses()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Sprint 13C directory lists (drive the clickable tiles).
    private val _students = MutableStateFlow<List<StudentRow>>(emptyList())
    val students: StateFlow<List<StudentRow>> = _students.asStateFlow()
    private val _lecturers = MutableStateFlow<List<LecturerRow>>(emptyList())
    val lecturers: StateFlow<List<LecturerRow>> = _lecturers.asStateFlow()
    private val _classRows = MutableStateFlow<List<ClassRow>>(emptyList())
    val classRows: StateFlow<List<ClassRow>> = _classRows.asStateFlow()

    // One-shot seeding guard for the `students` directory.
    private var studentsSeeded = false

    init {
        // POC rosters are baked demo data (single account-backed matric) — the
        // students tile is a MOCK figure by owner decision.
        viewModelScope.launch {
            classes.collect { rows ->
                _state.value = _state.value.copy(classCount = rows.size)
                // Sprint 13C: classes directory in "SUBJECT CODE; NAME;" form.
                _classRows.value = rows
                    .groupBy { it.displayCode }
                    .map { (code, slots) ->
                        ClassRow(code = code, name = slots.first().lecturerName)
                    }
                    .sortedBy { it.code.lowercase() }
            }
        }
        // Firestore `students` directory (Database mark): seeded on first read
        // from the owner's real section lists when the collection is empty.
        db.collection(STUDENTS).addSnapshotListener { snapshot, _ ->
            if (!studentsSeeded && snapshot?.isEmpty == true) {
                studentsSeeded = true
                seedStudentsDatabase()
            }
            val rows = snapshot?.documents?.mapNotNull { doc ->
                val name = doc.getString("name") ?: return@mapNotNull null
                val id = doc.getString("studentId") ?: return@mapNotNull null
                StudentRow(
                    name = name,
                    studentId = id,
                    faculty = doc.getString("faculty") ?: "—",
                    semester = doc.getLong("semester")?.toInt()
                )
            }?.sortedBy { it.name.uppercase() } ?: emptyList()
            _students.value = rows
        }
        // Live users watch: lecturers count + pending (unassigned) UPTM emails,
        // plus the lecturer directory (registered lecturer accounts).
        db.collection(USERS).addSnapshotListener { snapshot, _ ->
            val docs = snapshot?.documents ?: emptyList()
            var lecturers = 0
            val pending = mutableListOf<PendingAccount>()
            val lecturerRows = mutableListOf<LecturerRow>()
            docs.forEach { doc ->
                val role = doc.getString("role")
                val approved = doc.getBoolean("approved") ?: false
                if (role.equals("lecturer", ignoreCase = true)) {
                    lecturers++
                    lecturerRows.add(
                        LecturerRow(
                            name = doc.getString("fullName")?.takeIf { it.isNotBlank() }
                                ?: doc.getString("email") ?: "Lecturer",
                            email = doc.getString("email") ?: "",
                            classCount = 0
                        )
                    )
                }
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
            // Class owners that aren't registered lecturer accounts (the demo
            // lecturer was console-keyed before the fullName field existed) —
            // their name comes straight from the timetable slots.
            val ownerEmailToName = classes.value
                .filter { !it.ownerEmail.isNullOrBlank() }
                .associate { it.ownerEmail!! to it.lecturerName }
            ownerEmailToName.forEach { (email, name) ->
                if (lecturerRows.none { it.email.equals(email, ignoreCase = true) }) {
                    lecturerRows.add(LecturerRow(name, email, 0))
                }
            }
            // Class-count per lecturer (by owner email).
            val ownerCounts = classes.value
                .filter { it.ownerEmail != null }
                .groupingBy { it.ownerEmail!!.lowercase() }
                .eachCount()
            lecturerRows.forEach { it.classCount = ownerCounts[it.email.lowercase()] ?: 0 }
            _state.value = _state.value.copy(
                lecturerCount = lecturers,
                pendingAccounts = pending.sortedBy { it.email.lowercase() },
                isLoadingUsers = false
            )
            _lecturers.value = lecturerRows
                .distinctBy { it.email.lowercase() }
                .sortedBy { it.name.uppercase() }
        }
    }

    // Sprint 13C: one-time seeding of the Firestore `students` collection from
    // the real section lists — the examiner can open Firebase and SEE it.
    private fun seedStudentsDatabase() {
        val faculty = FACULTY_OPTIONS[0]
        MockAttendanceRoster.ALL_ROSTER_STUDENTS.forEach { (name, matric) ->
            db.collection(STUDENTS).add(
                mapOf(
                    "name" to name,
                    "studentId" to matric,
                    "faculty" to faculty,
                    "semester" to 6L,
                    "seededAt" to System.currentTimeMillis()
                )
            )
        }
    }

    // Sprint 13C (approval v2): assigns the role + approval AND, for students,
    // the faculty/programme/semester/studentId fields the admin picks or the
    // email auto-derives; lecturers get faculty only. The client watching its
    // own doc resumes automatically — the admin's only job is this write.
    fun approveAccount(
        uid: String,
        role: UserRole,
        adminPrivilege: Boolean,
        faculty: String?,
        programme: String?,
        semester: Int?,
        studentId: String?
    ) {
        _approvalBusy.value = true
        val updates = buildMap<String, Any?> {
            put("role", role.name.lowercase())
            put("approved", true)
            put("isAdminPrivilege", adminPrivilege)
            put("approvedAt", System.currentTimeMillis())
            faculty?.takeIf { it.isNotBlank() }?.let { put("faculty", it) }
            programme?.takeIf { it.isNotBlank() }?.let { put("programme", it) }
            semester?.let { put("semester", it.toLong()) }
            studentId?.takeIf { it.isNotBlank() }?.let { put("studentId", it.trim().uppercase()) }
        }
        db.collection(USERS).document(uid)
            .set(updates, SetOptions.merge())
            .addOnCompleteListener { task ->
                _approvalBusy.value = false
                _status.value = if (task.isSuccessful) {
                    "Account approved — the client will pick it up automatically."
                } else {
                    task.exception?.message ?: "Could not approve the account"
                }
            }
    }

    // Sprint 13C: student ID auto-derivation — kl154721@student.uptm.edu.my
    // → the digit run after "kl" → "AM" + digits (editable in the dialog).
    fun deriveStudentId(email: String): String {
        val digits = UPTM_EMAIL_MATRIC.find(email)?.groupValues?.get(1)
        return digits?.let { "AM$it" } ?: ""
    }

    companion object {
        // Owner decision: students are ambiguous in the POC (mock rosters, one real
        // account) — show the roster population figure instead of a live count.
        const val MOCK_STUDENT_COUNT = 128
        private const val USERS = "users"
        private const val STUDENTS = "students"

        // Sprint 13C: the faculty dropdown the admin assigns from. Stored as the
        // full display string on the user doc (Profile echoes it verbatim).
        val FACULTY_OPTIONS = listOf(
            "Faculty of Computing & Multimedia (FCOM)",
            "Faculty of Business and Accountancy (FABA)",
            "Faculty of Education, Social Sciences & Humanities (FESSH)",
            "Institute of Professional Studies (IPS)",
            "Institute of Graduate Studies (IGS)"
        )

        // Semesters 1..8; brand-new registrations default to 6 (owner ruling).
        val SEMESTER_OPTIONS = (1..8).toList().map { it.toLong() }
        const val DEFAULT_SEMESTER = 6L

        private val UPTM_EMAIL_MATRIC =
            Regex("kl(\\d+)@student\\.uptm\\.edu\\.my", RegexOption.IGNORE_CASE)
    }
}
