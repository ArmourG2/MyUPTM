package com.myuptm.domain.model

data class User(
    val uid: String,
    val name: String,
    val email: String,
    val role: Role
)