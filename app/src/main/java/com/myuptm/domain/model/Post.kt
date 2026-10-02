package com.myuptm.domain.model

data class Post(
    val id: String,
    val title: String,
    val snippet: String,
    val date: String,          // display date, e.g. "25 Sep 2026"
    val facultyTag: String,
    // Sprint 8 Task 4: real posts carry an optional Cloudinary image + ownership stamp
    // (authorEmail gates deletion client-side) + createdAt for ordering.
    val imageUrl: String? = null,
    val authorEmail: String? = null,
    val createdAt: Long? = null
)
