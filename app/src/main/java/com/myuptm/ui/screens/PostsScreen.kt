package com.myuptm.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.myuptm.domain.model.AppUser
import com.myuptm.domain.model.toPermissions
import com.myuptm.navigation.MyUptmRoutes
import com.myuptm.ui.components.PostCard
import com.myuptm.viewmodel.PostsViewModel

@Composable
fun PostsScreen(
    viewModel: PostsViewModel = viewModel(),
    navController: NavController,
    user: AppUser
) {
    val posts by viewModel.posts.collectAsState()
    // Sprint 7B: only Admin and Lecturer-with-Admin may add posts.
    val canAddPost = user.toPermissions().canAddPost

    // Sprint 9: the list ALWAYS opens at the TOP. The reset is keyed to the feed
    // emission as well — the Firestore flow can emit after composition (or after a
    // notification-triggered post insert), which used to leave the viewport
    // anchored mid-list below the Important section.
    val listState = rememberLazyListState()
    LaunchedEffect(posts) { listState.scrollToItem(0) }

    // Box allows us to layer the FAB over the LazyColumn
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp)) {
                Text(
                    "Posts",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Faculty news and announcements",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Sprint 9: tier sections mirror the notifications screen —
                // Important (red) pinned on top, Updates (yellow first, then blue).
                val important = posts.filter { it.importance == "important" }
                val attention = posts.filter { it.importance == "attention" }
                val normal = posts.filter { it.importance != "important" && it.importance != "attention" }

                if (important.isNotEmpty()) {
                    item(key = "important_header") {
                        Text(
                            "Important",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = androidx.compose.ui.graphics.Color(0xFFE53935)
                        )
                    }
                    items(important, key = { it.id }) { post ->
                        PostCard(
                            post = post,
                            onClick = { navController.navigate(MyUptmRoutes.postDetail(post.id)) }
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
                items(attention + normal, key = { it.id }) { post ->
                    PostCard(
                        post = post,
                        onClick = { navController.navigate(MyUptmRoutes.postDetail(post.id)) }
                    )
                }
            }
        }

        if (canAddPost) {
            FloatingActionButton(
                onClick = { navController.navigate(MyUptmRoutes.ADD_POST) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Post")
            }
        }
    }
}