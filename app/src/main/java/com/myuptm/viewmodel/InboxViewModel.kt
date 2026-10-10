package com.myuptm.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myuptm.data.notifications.NotificationHelper
import com.myuptm.data.repository.DataStoreInboxSeenRepository
import com.myuptm.data.repository.FirestoreAbsenceLetterRepository
import com.myuptm.data.repository.FirestoreNotificationsRepository
import com.myuptm.data.repository.FirestoreProfileRepository
import com.myuptm.data.repository.MockAttendanceRoster
import com.myuptm.data.repository.matchesMatric
import com.myuptm.domain.model.AppNotification
import com.myuptm.domain.model.AppUser
import com.myuptm.domain.model.NotificationType
import com.myuptm.domain.model.UserRole
import com.myuptm.domain.repository.AbsenceLetterRepository
import com.myuptm.domain.repository.NotificationsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Sprint 9 tiers (owner wireframe), DATA-driven —
//   RED   = importance "important" OR legacy warning letters (pinned until upload;
//           swipe-confirm now silences it for 1 week with a re-post statement),
//   YELLOW= importance "attention" (lecturer's class-change toggle, missed-class
//           notices, or admin's Attention posts),
//   BLUE  = everything else (normal posts, plain announcements).
enum class NotificationTier { IMPORTANT, REMINDER, NORMAL }

fun AppNotification.notificationTier(): NotificationTier = when {
    importance == "important" || title.startsWith("Warning letter", ignoreCase = true) ->
        NotificationTier.IMPORTANT
    importance == "attention" -> NotificationTier.REMINDER
    else -> NotificationTier.NORMAL
}

// Sprint 9: ACTIVITY-scoped inbox (created once at the app root, shared by every
// screen via the activity ViewModelStoreOwner). Fixes the previous fault: the
// unread computation is now a fully REACTIVE combine (identity included), so the
// first Firestore snapshot correctly lights the bell dot / open-popup even before
// the profile fetch resolves.
//  - every NEW notification for the signed-in user pops in the notification shade
//    while the app is open (foreground-only — no FCM),
//  - opened notifications auto-disappear from the inbox (per-account readIds),
//  - the Notifications screen has a Clear button (marks the list read),
//  - student/staff routing: students see global + matric-targeted docs; staff also
//    receive staffOnly docs (e.g. letter submissions).
class InboxViewModel(application: Application) : AndroidViewModel(application) {

    private val notificationsRepository: NotificationsRepository =
        FirestoreNotificationsRepository()
    private val profileRepository: FirestoreProfileRepository =
        FirestoreProfileRepository()

    // Reactive identity: the combine below recomputes whenever ANY of these change,
    // so a slow profile fetch can no longer freeze wrong visibility/unread values.
    private data class InboxIdentity(
        val email: String?,
        val isStudent: Boolean,
        val isLecturer: Boolean,
        // Sprint 11: admins are neither students nor lecturers — the letter-submission
        // docs (lecturerOnly) must never reach them (admins don't teach).
        val isAdmin: Boolean,
        val myMatric: String?,
        val loaded: Boolean
    )

    private val _identity = MutableStateFlow(InboxIdentity(null, false, false, false, null, false))

    // Sprint 14 (owner ask): fresh registrations (no classes yet) must NOT receive
    // CLASS_UPDATE timetable news. The nav-upgrade flag (sticky DataStore, see
    // StudentNavViewModel) decides; re-read when the collector re-runs.
    @Volatile
    private var studentEngaged: Boolean = true
    @Volatile
    private var studentRoleActive: Boolean = false

    // Docs already present on the first snapshot are history — never re-notify for them.
    private val seenIds = mutableSetOf<String>()
    private var baselineSeen = false

    // RAW Firestore docs (no read-filtering) — used by the detail page and the
    // "officially issued" warning-letters records view.
    val rawList: StateFlow<List<AppNotification>> = notificationsRepository
        .observeNotifications()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    data class InboxState(
        val list: List<AppNotification>,
        val unread: Int,
        val unreadPost: AppNotification? = null,
        // Sprint 9: the newest IMPORTANT post notification — the Home bell opens
        // this one ALWAYS (read or unread); it's a pinned announcement, not a
        // consumable message.
        val importantPost: AppNotification? = null
    )

    // A notification that points at a Post detail (any tier) — never the
    // letter-submission docs (their postId is a LETTER id).
    private fun isPostPointer(n: AppNotification): Boolean =
        n.postId != null && !n.title.startsWith("Absence letter submitted", ignoreCase = true)

    @OptIn(ExperimentalCoroutinesApi::class)
    val inbox: StateFlow<InboxState> = _identity.flatMapLatest { identity ->
        val email = identity.email
        if (email == null) {
            flowOf(InboxState(emptyList(), 0))
        } else {
            val seenRepository = DataStoreInboxSeenRepository(getApplication(), email)
            combine(
                notificationsRepository.observeNotifications(),
                seenRepository.observeLastSeen(),
                seenRepository.observeReadIds()
            ) { docs, lastSeen, readIds ->
                val visible = visibleToMe(docs, identity)
                val unreadItems = if (identity.loaded) {
                    visible.filter {
                        isNewsworthy(it, identity) &&
                            it.createdAtEpochMs > lastSeen && it.createdAtEpochMs > 0 &&
                            it.id !in readIds
                    }
                } else emptyList() // not loaded yet — no flash of a false dot
                // Sprint 9: the newest unread POST pointer — the Home bell opens
                // it directly (skip-the-inbox) when the user presses the bell.
                val unreadPost = unreadItems
                    .filter { isPostPointer(it) }
                    .maxByOrNull { it.createdAtEpochMs }
                // The newest IMPORTANT post pointer — ALWAYS opened by the bell,
                // even if it was already read (it stays pinned in the inbox until
                // the user swipe-clears it with the 1-week statement).
                val importantPost = visible
                    .filter {
                        it.notificationTier() == com.myuptm.viewmodel.NotificationTier.IMPORTANT &&
                            isPostPointer(it)
                    }
                    .maxByOrNull { it.createdAtEpochMs }
                InboxState(
                    list = visible.filter { it.id !in readIds },
                    unread = unreadItems.size,
                    unreadPost = unreadPost,
                    importantPost = importantPost
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, InboxState(emptyList(), 0))

    // "News" = something the account hasn't authored and isn't merely a record.
    // Student-targeted docs (warning letters, miss notices) are RECORDS for staff —
    // visible in the list but never counted as the staff account's unread news
    // (the issuer can't act on them → they must not pop the open-app card / dot).
    private fun isNewsworthy(n: AppNotification, identity: InboxIdentity): Boolean =
        identity.isStudent || n.targetMatric == null

    init {
        viewModelScope.launch {
            notificationsRepository.observeNotifications().collect { docs ->
                // Shade push for documents that arrive while the inbox is listening.
                if (baselineSeen) {
                    visibleToMe(docs, _identity.value)
                        .filter {
                            it.id !in seenIds && it.type != NotificationType.CLASH_WARNING &&
                                isNewsworthy(it, _identity.value) &&
                                classUpdateStudentFilter(it)
                        }
                        .forEach { notification ->
                            NotificationHelper.show(
                                getApplication(),
                                notification.title,
                                notification.message
                            )
                        }
                }
                baselineSeen = true
                seenIds.addAll(docs.map { it.id })
            }
        }
        // Sprint 9 (tier rule): a RED letter stays pinned UNTIL the student's
        // extra-proof is resolved — every newly SUBMITTED (not-declined) letter
        // resolves the pinned items for the demo alias.
        // Sprint 14 FIX: for REAL students this resolution never fired (the
        // counter above only looks at the demo matric), so a warning letter
        // stayed pinned even after the lecturer APPROVED the student's letter.
        // Now: when the count of MY APPROVED letters grows, all IMPORTANT docs
        // TARGETED at me (my warning letters) are resolved from the inbox.
        val lettersRepository: AbsenceLetterRepository = FirestoreAbsenceLetterRepository()
        viewModelScope.launch {
            var previousExcused = 0
            var previousApproved = 0
            lettersRepository.observeLetters().collect { letters ->
                val identity = _identity.value
                if (!identity.isStudent) return@collect
                val excused = letters.count {
                    it.studentMatric.matchesMatric(MockAttendanceRoster.DEMO_MATRIC) &&
                        it.status != com.myuptm.domain.model.LetterStatus.DECLINED
                }
                if (excused > previousExcused && previousExcused >= 0) {
                    markImportantDocsResolved(targetedOnly = false)
                }
                // MY letters (matric-or-email match, same rule the inbox
                // visibility uses): a NEW approval by the lecturer clears my
                // targeted warning letters.
                val myMatric = identity.myMatric
                val approved = if (myMatric == null) 0 else letters.count {
                    it.studentMatric.matchesMatric(myMatric) &&
                        it.status == com.myuptm.domain.model.LetterStatus.APPROVED
                }
                if (approved > previousApproved && previousApproved >= 0) {
                    // targetedOnly: never silence untargeted pinned posts —
                    // only warning letters addressed to THIS student resolve.
                    markImportantDocsResolved(targetedOnly = true)
                }
                previousExcused = excused
                previousApproved = approved
            }
        }
    }

    private suspend fun markImportantDocsResolved(targetedOnly: Boolean) {
        val ids = rawList.value
            .filter { it.notificationTier() == NotificationTier.IMPORTANT }
            .filter { !targetedOnly || it.targetMatric != null }
            .map { it.id }
        if (ids.isNotEmpty()) {
            DataStoreInboxSeenRepository(getApplication(), _identity.value.email ?: "")
                .markRead("letter-submission", ids)
        }
    }

    // Visibility (Sprint 9 audience rules, owner ruling):
    //  - STUDENTS: staff-targeted docs are hidden; targeted docs must match their
    //    matric (MatricMatcher) or the demo-matric alias.
    //  - LECTURERS (the warning issuer): everything except student-audience docs —
    //    including the warning letters they sent (records).
    //  - ADMINS: not students and not issuers — class updates, miss notices and
    //    ANY student-targeted doc (warning letters) are hidden; they receive
    //    staffOnly submissions + untargeted Important posts.
    //  Class changes are ALWAYS student news: staff never see them, even for
    //  legacy docs that predate the `studentOnly` field.
    private fun isStudentAudience(n: AppNotification): Boolean =
        n.studentOnly || n.type == NotificationType.CLASS_UPDATE

    // Sprint 14: class-update news (timetable changes) is only relevant to
    // students who have (or had) classes. A fresh registration does not receive
    // it until their first class appears (StudentNavViewModel marks the upgrade).
    private fun classUpdateStudentFilter(n: AppNotification): Boolean =
        !(studentRoleActive && !studentEngaged && n.type == NotificationType.CLASS_UPDATE)

    private fun visibleToMe(
        list: List<AppNotification>,
        identity: InboxIdentity
    ): List<AppNotification> {
        if (!identity.isStudent) {
            return list.filter {
                !isStudentAudience(it) &&
                    (identity.isLecturer || it.targetMatric == null) &&
                    // Sprint 11 (owner ruling): letter submissions are TEACHING-STAFF
                    // news — admins never receive them, current or legacy.
                    !(identity.isAdmin && it.lecturerOnly)
            }
        }
        return list.filter {
            !it.staffOnly && (
                it.targetMatric == null ||
                    it.targetMatric.matchesMatric(identity.myMatric) ||
                    it.targetMatric.matchesMatric(MockAttendanceRoster.DEMO_MATRIC)
                ) &&
                classUpdateStudentFilter(it)
        }
    }

    // Re-binds to the current signed-in user (account switches on one device).
    fun bindUser(user: AppUser?) {
        val current = _identity.value
        if (current.email == user?.email && user != null) return
        _identity.value = InboxIdentity(
            email = user?.email,
            isStudent = user?.role == UserRole.STUDENT,
            isLecturer = user?.role == UserRole.LECTURER,
            isAdmin = user?.role == UserRole.ADMIN,
            myMatric = null,
            loaded = false
        )
        updateEngagement(user)
        if (user != null) {
            viewModelScope.launch {
                val matric = profileRepository.getUserProfile().getOrNull()
                    ?.studentId ?: user.email
                _identity.value = InboxIdentity(
                    email = user.email,
                    isStudent = user.role == UserRole.STUDENT,
                    isLecturer = user.role == UserRole.LECTURER,
                    isAdmin = user.role == UserRole.ADMIN,
                    myMatric = matric,
                    loaded = true
                )
            }
        }
    }

    // Sprint 14: refreshes the engagement flags for the current student account
    // (class-update news filtered while the account has no classes / not yet
    // upgraded). Lecturers/admins are always engaged.
    private fun updateEngagement(user: AppUser?) {
        studentRoleActive = user?.role == UserRole.STUDENT
        if (!studentRoleActive) {
            studentEngaged = true
            return
        }
        val email = user?.email ?: return
        viewModelScope.launch {
            val rosterEnrolled = com.myuptm.data.repository.MockAttendanceRoster.isEnrolledDemoStudent(
                user.studentId, user.email
            )
            studentEngaged = rosterEnrolled ||
                com.myuptm.data.repository.DataStoreStudentNavRepository(
                    getApplication(), email
                ).observeUpgraded().first()
            // Also re-checked once per rebind (rebind happens on app open).
        }
    }

    // Opening a notification's detail page removes it from the inbox (per account).
    fun markRead(notifId: String) {
        viewModelScope.launch {
            DataStoreInboxSeenRepository(getApplication(), _identity.value.email ?: "").markRead(notifId)
        }
    }

    // Notifications screen "Clear": hides yellow + blue only — RED stays pinned
    // until the student uploads their letter.
    fun clearUpdates() {
        val email = _identity.value.email ?: return
        val clearableIds = inbox.value.list
            .filter { it.notificationTier() != NotificationTier.IMPORTANT }
            .map { it.id }
        if (clearableIds.isEmpty()) return
        viewModelScope.launch {
            DataStoreInboxSeenRepository(getApplication(), email).markRead("clear", clearableIds)
        }
    }

    // Opening the Notifications screen records the reading position (the bell dot
    // calms down); the list itself still shows until items are dismissed/cleared.
    fun markScreenSeen() {
        val email = _identity.value.email ?: return
        viewModelScope.launch {
            DataStoreInboxSeenRepository(getApplication(), email).markSeen(System.currentTimeMillis())
        }
    }
}
