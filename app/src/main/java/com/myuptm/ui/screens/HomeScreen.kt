package com.myuptm.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import com.myuptm.data.repository.FirestoreClassRepository
import com.myuptm.domain.model.SupportLinks
import com.myuptm.navigation.MyUptmRoutes
import com.myuptm.ui.components.AnnouncementRow
import com.myuptm.ui.components.NextClassCard
import com.myuptm.ui.components.SupportLinkTile
import com.myuptm.ui.components.openUrl
import com.myuptm.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val homeViewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                // Sprint 7B: classes come from Firestore (global); posts remain mock.
                HomeViewModel(FirestoreClassRepository(), com.myuptm.data.repository.FirestorePostsRepository())
            }
        }
    )
    val uiState by homeViewModel.uiState.collectAsState()

    // Sprint 7B: local notifications require POST_NOTIFICATIONS on Android 13+.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Denied -> in-app inbox still works */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Home") },
            actions = {
                IconButton(
                    // Sprint 7B: opens the notification inbox (POC of B-004).
                    onClick = { navController.navigate(MyUptmRoutes.NOTIFICATIONS) }
                ) {
                    Icon(Icons.Filled.Notifications, contentDescription = "Notifications")
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                NextClassCard(session = uiState.nextClass, label = uiState.countdownLabel)
            }
            item { SectionHeader("Announcements") }
            items(uiState.announcements) { post ->
                AnnouncementRow(
                    post = post,
                    // Use the same helper call your PostsScreen already uses if named differently
                    onClick = { navController.navigate(MyUptmRoutes.postDetail(post.id)) }
                )
            }
            item { SectionHeader("Student Support") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(uiState.supportLinks) { link ->
                        SupportLinkTile(link = link, onClick = { context.openUrl(link.url) })
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { context.openUrl(SupportLinks.PORTAL_URL) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Student Portal")
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}