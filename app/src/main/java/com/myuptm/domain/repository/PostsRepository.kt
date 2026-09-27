package com.myuptm.domain.repository

import com.myuptm.domain.model.Post

interface PostsRepository {
    fun getPosts(): List<Post>
}