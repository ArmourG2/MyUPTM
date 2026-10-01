package com.myuptm.domain.repository

import com.myuptm.domain.model.Post

interface PostsRepository {
    fun getPosts(): List<Post>

    // Sprint 7B: gated to Admin + Lecturer-with-Admin via UserPermissions.
    fun removePost(postId: String): Boolean
}