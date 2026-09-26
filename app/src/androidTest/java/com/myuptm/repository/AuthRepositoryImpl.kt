package com.myuptm.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.myuptm.domain.model.Role
import com.myuptm.domain.model.User
import com.myuptm.domain.repository.AuthRepository
import kotlinx.coroutines.tasks.await

class AuthRepositoryImpl : AuthRepository {

    private val firebaseAuth = FirebaseAuth.getInstance()

    //Logic for scaffolding
    private fun determineRole(email: String): Role {

        val cleanEmail = email.lowercase()

        return when {

            cleanEmail.startsWith("am") && cleanEmail.endsWith("@student.uptm.edu.my") -> Role.STUDENT

            cleanEmail.endsWith("@uptm.edu.my") -> Role.LECTURER

            cleanEmail == "adminirfan@uptm.edu.my" -> Role.ADMIN

            else -> Role.UNKNOWN
        }

    }


    //Contract for Interface AuthRepostory.kt
    override val currentUserId: String?
        get() = firebaseAuth.currentUser?.uid

    override suspend fun signInWithGoogle(idToken: String): Result<User> {
        val credential = GoogleAuthProvider.getCredential(idToken, null)

        return try {
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user ?: return Result.failure(Exception("Login Failed, Firebase user is null"))

            val email = firebaseUser.email ?: return Result.failure(Exception("Email not Provided by Google"))
            val role = determineRole(email)

            if (role == Role.UNKNOWN) {
                firebaseAuth.signOut()
                return Result.failure(Exception("Unauthorized email domain. UPTM emails only."))
            }

            val domainUser = User(
                uid = firebaseUser.uid,
                name = firebaseUser.displayName ?: "No Name",
                email = email,
                role = role
            )

            Result.success(domainUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        firebaseAuth.signOut()
    }

}