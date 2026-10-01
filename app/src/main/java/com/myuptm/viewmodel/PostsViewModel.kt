package com.myuptm.viewmodel

import androidx.lifecycle.ViewModel
import com.myuptm.data.repository.MockPostsRepository
import com.myuptm.domain.model.Post
import com.myuptm.domain.repository.PostsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PostsViewModel : ViewModel() {

    private val repository: PostsRepository = MockPostsRepository()

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts.asStateFlow()

    init {
        loadPosts()
    }

    private fun loadPosts() {
        _posts.value = repository.getPosts()
    }

    // Sprint 7B: entry gated to Admin + Lecturer-with-Admin via UserPermissions.
    fun removePost(postId: String) {
        repository.removePost(postId)
        loadPosts()
    }
}