package com.myuptm.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myuptm.domain.model.AppNotification
import com.myuptm.domain.model.AppUser
import com.myuptm.domain.model.NotificationType
import com.myuptm.viewmodel.InboxViewModel
import com.myuptm.viewmodel.NotificationTier
import com.myuptm.viewmodel.NotificationsViewModel
import com.myuptm.viewmodel.PostsViewModel
import com.myuptm.viewmodel.notificationTier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Wireframe tier colors: red important / yellow reminder / blue normal.
private val TierRed = Color(0xFFE53935)
private val TierYellow = Color(0xFFF9A825)
private val TierBlue = Color(0xFF42A5F5)

// Sprint 9: tiered inbox per the wireframes —
//  - "Important" section pinned on top (RED): never cleared by Clear; the swipe
//    reveal only shows the trash whose confirm REMINDS to upload (uploading a
//    letter auto-clears the pinned items),
//  - "Updates" (YELLOW reminders + BLUE normal): swipe-left reveals the red trash;
//    confirming clears; the Clear button clears yellow+blue.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    user: AppUser?,
    onBackClick: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenPost: (String) -> Unit,
    viewModel: NotificationsViewModel = viewModel()
) {
    LaunchedEffect(user) { viewModel.bindUser(user) }

    // The inbox comes from the ACTIVITY-scoped InboxViewModel (shared listener).
    val inboxViewModel: InboxViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as ViewModelStoreOwner
    )
    val inbox by inboxViewModel.inbox.collectAsState()
    val notifications = inbox.list

    // Sprint 9: posts feed — resolves LEGACY post notifications (created before
    // the postId field existed) by matching the notification title.
    val postsViewModel: PostsViewModel = viewModel()
    val posts by postsViewModel.posts.collectAsState()

    // Sprint 9: POST notifications are POINTERS — tapping one jumps straight to
    // the post detail. IMPORTANT posts stay pinned in the inbox (never consumed
    // by a tap); attention/normal are consumed. Legacy docs (postId == null) are
    // resolved by title match against the posts feed. Letter-submission docs and
    // anything without a matching post open the notification page instead.
    val openNotification: (AppNotification) -> Unit = { notification ->
        val letterSubmission = notification.title.startsWith("Absence letter submitted")
        val postId = notification.postId
            ?: posts.find { it.title == notification.title }?.id
        if (!letterSubmission && postId != null) {
            if (notification.notificationTier() != NotificationTier.IMPORTANT) {
                inboxViewModel.markRead(notification.id)
            }
            onOpenPost(postId)
        } else {
            onOpenDetail(notification.id)
        }
    }

    LaunchedEffect(Unit) { inboxViewModel.markScreenSeen() }

    val clashWarnings by viewModel.clashWarnings.collectAsState()

    var swipeTarget by remember { mutableStateOf<Pair<NotificationTier, AppNotification>?>(null) }

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        // Campus-photo hero banner (design language from sign-in).
        com.myuptm.ui.components.CampusHeroBackdrop()
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                com.myuptm.ui.components.CampusHeroHeader(
                    title = "Notifications",
                    subtitle = "Latest updates & letters",
                    navigationIcon = {
                        com.myuptm.ui.components.CampusBackButton(onBackClick)
                    },
                    actions = {
                        // Clear hides YELLOW + BLUE (RED stays until upload). The old
                        // "Notify students" composer was removed (obsolete — posts push).
                        if (notifications.any { it.notificationTier() != NotificationTier.IMPORTANT }) {
                            TextButton(
                                onClick = { inboxViewModel.clearUpdates() },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = androidx.compose.ui.graphics.Color.White
                                )
                            ) {
                                Text("Clear")
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (clashWarnings.isNotEmpty()) {
                item(key = "warnings_header") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Clash warnings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { viewModel.clearWarnings() }) {
                            Text("Clear", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                items(clashWarnings, key = { it.id }) { warning ->
                    ClashWarningRow(warning)
                }
            }

            val important = notifications.filter { it.notificationTier() == NotificationTier.IMPORTANT }
            // Updates: attention (yellow) first, then normal (blue); newest first.
            val updates = notifications
                .filter { it.notificationTier() != NotificationTier.IMPORTANT }
                .sortedWith(
                    compareBy(
                        { it.notificationTier() == NotificationTier.NORMAL },
                        { -it.createdAtEpochMs }
                    )
                )

            if (important.isNotEmpty()) {
                item(key = "important_header") {
                    Text(
                        "Important",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TierRed
                    )
                }
                items(important, key = { it.id }) { notification ->
                    TieredSwipeRow(
                        notification = notification,
                        onOpen = { openNotification(notification) },
                        onTrash = { swipeTarget = NotificationTier.IMPORTANT to notification }
                    )
                }
            }

            item(key = "updates_header") {
                Text(
                    "Updates",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (updates.isEmpty() && important.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "No notifications yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(updates, key = { it.id }) { notification ->
                    TieredSwipeRow(
                        notification = notification,
                        onOpen = { openNotification(notification) },
                        onTrash = { swipeTarget = notification.notificationTier() to notification }
                    )
                }
            }
        }
    }
    }

    // Wireframe dialog: "Are you sure?" — swipe-confirm narrative per tier.
    // RED/YELLOW: "silenced for 1 week, re-posted if unresolved" (POC: statement
    // only, no re-post engine). YES clears; RED warns is unchanged otherwise.
    swipeTarget?.let { (tier, notification) ->
        val isAbsenceRelated = notification.message.contains("attendance", ignoreCase = true) ||
            notification.title.startsWith("Warning letter", ignoreCase = true)
        AlertDialog(
            onDismissRequest = { swipeTarget = null },
            title = { Text(if (tier == NotificationTier.IMPORTANT) "Important" else "Are you sure?") },
            text = {
                Text(
                    when (tier) {
                        NotificationTier.IMPORTANT ->
                            buildString {
                                append(notification.title)
                                append(" will be silenced for 1 week; if it is still unresolved")
                                if (isAbsenceRelated) {
                                    append(" (e.g. your absence letter wasn't uploaded)")
                                }
                                append(" then, it will be re-posted next week.")
                            }
                        NotificationTier.REMINDER ->
                            buildString {
                                append("This attention notice will be silenced for 1 week")
                                if (isAbsenceRelated) {
                                    append(" — remember to upload your absence letter if you missed the class")
                                }
                                append(", and re-posted if it is still unresolved by next week.")
                            }
                        else ->
                            "Remove this notification from your inbox?"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        inboxViewModel.markRead(notification.id)
                        swipeTarget = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (tier == NotificationTier.NORMAL) MaterialTheme.colorScheme.primary else TierRed
                    )
                ) { Text("YES") }
            },
            dismissButton = {
                TextButton(
                    onClick = { swipeTarget = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = TierBlue)
                ) { Text("Cancel") }
            }
        )
    }
}

// Swipe-left gesture container: dragging hides the card to the left; the red trash
// button appears ONLY once the drag passes the threshold (wireframe), with the
// slide-back animation. State is keyed per notification so rows never leak.
private const val REVEAL_PX = 96f
private const val REVEAL_THRESHOLD_PX = 60f

@Composable
private fun TieredSwipeRow(
    notification: AppNotification,
    onOpen: () -> Unit,
    onTrash: () -> Unit
) {
    var revealed by remember(notification.id) { mutableStateOf(false) }
    var dragging by remember(notification.id) { mutableStateOf(false) }
    var dragAccum by remember(notification.id) { mutableFloatStateOf(0f) }
    val slideTargetPx = when {
        dragging -> dragAccum.coerceIn(-REVEAL_PX, 0f)
        revealed -> -REVEAL_PX
        else -> 0f
    }
    val offsetX by animateFloatAsState(
        targetValue = slideTargetPx,
        // Instant during the finger drag, spring-back/tween on release.
        animationSpec = if (dragging) tween(0) else tween(durationMillis = 180),
        label = "swipeReveal"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Trash ONLY exists while revealed — absolutely invisible otherwise.
        if (revealed) {
            Button(
                onClick = onTrash,
                colors = ButtonDefaults.buttonColors(containerColor = TierRed),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(86.dp)
                    .height(IntrinsicSize.Min)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Color.White)
            }
        }
        // Sliding card body + horizontal drag detector.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = offsetX.dp)
                .pointerInput(notification.id) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true; dragAccum = 0f },
                        onDragEnd = {
                            dragging = false
                            revealed = dragAccum <= -REVEAL_THRESHOLD_PX
                            dragAccum = 0f
                        },
                        onDragCancel = {
                            dragging = false
                            dragAccum = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            // Only leftward reveals and closing drags count.
                            val next = (dragAccum + dragAmount).coerceIn(-REVEAL_PX, 0f)
                            if (dragAmount < 0 || dragAccum < 0) {
                                dragAccum = next
                                change.consume()
                            }
                        }
                    )
                }
        ) {
            TieredNotificationRow(notification, onOpen)
        }
    }
}

@Composable
private fun TieredNotificationRow(
    notification: AppNotification,
    onOpen: () -> Unit
) {
    val tier = notification.notificationTier()
    val tierColor = when (tier) {
        NotificationTier.IMPORTANT -> TierRed
        NotificationTier.REMINDER -> TierYellow
        else -> TierBlue
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(14.dp),
        // OPAQUE container — the swipe trash lives behind this card and must never
        // bleed through a translucent background.
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Left tier bar (wireframe).
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .fillMaxHeight()
                    .background(tierColor)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circled icon (the diamond circle in the wireframe).
                Icon(
                    imageVector = when (notification.type) {
                        NotificationType.CLASS_UPDATE -> Icons.Filled.DateRange
                        else -> Icons.Filled.Notifications
                    },
                    contentDescription = null,
                    tint = tierColor,
                    modifier = Modifier
                        .size(34.dp)
                        .background(tierColor.copy(alpha = 0.14f), CircleShape)
                        .padding(7.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        notification.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        notification.message.lineSequence().firstOrNull().orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${notification.senderName} · ${formatTimestamp(notification.createdAtEpochMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ClashWarningRow(warning: AppNotification) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    warning.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    warning.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

// Locale can change while the app runs — build the formatter per call.
private fun formatTimestamp(epochMs: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(epochMs))
