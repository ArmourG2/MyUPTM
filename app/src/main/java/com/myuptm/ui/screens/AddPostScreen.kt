package com.myuptm.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.myuptm.viewmodel.PostsViewModel

// Sprint 8 Task 4: post authoring — title, tag, body and an optional Cloudinary image.
// Save = upload image (if any) → Firestore document; errors surface inline.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPostScreen(
    authorEmail: String?,
    onBackClick: () -> Unit,
    viewModel: PostsViewModel
) {
    var title by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    // Sprint 9: importance push — Normal (blue) / Attention (yellow) / Important
    // (red, pinned). Every published post also lands in the notification section
    // tiered by this choice.
    var importance by remember { mutableStateOf("normal") }
    var selectedImage by remember { mutableStateOf<Uri?>(null) }
    var imageError by remember { mutableStateOf<String?>(null) }
    val submitState by viewModel.submitState.collectAsState()
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageError = null
        selectedImage = uri
    }

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        // Campus-photo hero banner (design language from sign-in).
        com.myuptm.ui.components.CampusHeroBackdrop()
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                com.myuptm.ui.components.CampusHeroHeader(
                    title = "Add Post",
                    navigationIcon = {
                        com.myuptm.ui.components.CampusBackButton(onBackClick)
                    }
                )
            }
        ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = tag,
                onValueChange = { tag = it },
                label = { Text("Tag (optional)") },
                placeholder = { Text("e.g. Faculty of Computing") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Post content") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            // Sprint 9: notification tier of the published post.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "normal" to "Normal",
                    "attention" to "Attention",
                    "important" to "Important"
                ).forEach { (value, label) ->
                    androidx.compose.material3.FilterChip(
                        selected = importance == value,
                        onClick = { importance = value },
                        label = { Text(label) }
                    )
                }
            }

            // Optional image: pick from gallery → preview → uploaded on Save.
            selectedImage?.let { uri ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(uri).build(),
                        contentDescription = "Selected image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = { selectedImage = null },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove image")
                    }
                }
            } ?: run {
                Button(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add image (optional)")
                }
            }

            imageError?.let { message ->
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            submitState.error?.let { message ->
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    viewModel.clearSubmitError()
                    viewModel.addPost(
                        title = title,
                        body = body,
                        tag = tag,
                        imageUri = selectedImage,
                        authorEmail = authorEmail,
                        importance = importance
                    )
                },
                enabled = title.isNotBlank() && body.isNotBlank() && !submitState.isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (submitState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (selectedImage != null) "Uploading & posting..." else "Posting...")
                } else {
                    Text("Publish post")
                }
            }
        }
    }
    }

    // Success → close the screen (posts feed updates live).
    LaunchedEffect(submitState.submitted) {
        if (submitState.submitted) onBackClick()
    }
}
