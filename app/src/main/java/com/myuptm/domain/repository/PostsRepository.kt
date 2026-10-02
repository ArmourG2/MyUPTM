package com.myuptm.domain.repository

import com.myuptm.domain.model.Post
import kotlinx.coroutines.flow.Flow

interface PostsRepository {
    // Live post feed (latest first) — reactive so new/removed posts update everywhere.
    fun observePosts(): Flow<List<Post>>

    // Sprint 8 Task 4: authoring (Firestore) — replaces the mock's one-shot getPosts.
    suspend fun addPost(post: Post): Result<Unit>

    // Sprint 7B: gated to Admin + Lecturer-with-Admin via UserPermissions.
    suspend fun removePost(postId: String): Result<Unit>
}
