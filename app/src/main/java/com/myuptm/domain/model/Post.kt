package com.myuptm.domain.model

data class Post(
    val id: String,
    val title: String,
    val snippet: String,
    val date: String,               // "25 Sep 2026"
    val facultyTag: String          // "Faculty of Computing"
)