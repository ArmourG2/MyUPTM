package com.myuptm.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myuptm.data.repository.FirestorePostsRepository
import com.myuptm.data.repository.RealCloudinaryRepository
import com.myuptm.domain.model.Post
import com.myuptm.domain.repository.FileType
import com.myuptm.domain.repository.PostsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

// Sprint 8 Task 4: AndroidViewModel (like TimetableViewModel) so the post authoring flow
// owns an application context for real Cloudinary uploads.
class PostsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PostsRepository = FirestorePostsRepository()
    private val cloudinaryRepository = RealCloudinaryRepository(application)

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts.asStateFlow()

    // Add-post submit state: uploading image → saving doc → done/error.
    data class SubmitState(
        val isSubmitting: Boolean = false,
        val error: String? = null,
        val submitted: Boolean = false
    )
    private val _submitState = MutableStateFlow(SubmitState())
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    init {
        // Firestore feed — live updates replace the one-shot mock load.
        viewModelScope.launch {
            repository.observePosts().collect { posts ->
                _posts.value = posts
            }
        }
    }

    fun clearSubmitError() {
        _submitState.value = _submitState.value.copy(error = null)
    }

    // Full authoring: optional Cloudinary image upload, then the Firestore document.
    // Failures surface inline (Add-post screen shows submitState.error).
    fun addPost(
        title: String,
        body: String,
        tag: String,
        imageUri: Uri?,
        authorEmail: String?
    ) {
        if (_submitState.value.isSubmitting) return
        _submitState.value = SubmitState(isSubmitting = true)
        viewModelScope.launch {
            runCatching {
                val imageUrl = imageUri?.let {
                    cloudinaryRepository.uploadFile(it, FileType.IMAGE).getOrThrow()
                }
                val post = Post(
                    id = UUID.randomUUID().toString(),
                    title = title.trim(),
                    snippet = body.trim(),
                    date = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)),
                    facultyTag = tag.trim().ifEmpty { "MyUPTM" },
                    imageUrl = imageUrl,
                    authorEmail = authorEmail,
                    createdAt = System.currentTimeMillis()
                )
                repository.addPost(post).getOrThrow()
            }.onSuccess {
                _submitState.value = SubmitState(submitted = true)
            }.onFailure { e ->
                _submitState.value = SubmitState(error = e.message ?: "Post failed")
            }
        }
    }

    // Sprint 7B: entry gated to Admin + Lecturer-with-Admin via UserPermissions.
    fun removePost(postId: String) {
        viewModelScope.launch { repository.removePost(postId) }
    }
}
