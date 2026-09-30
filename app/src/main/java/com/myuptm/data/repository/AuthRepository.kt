package com.myuptm.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

interface AuthRepository {
    val currentUser: FirebaseUser?
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser>
    fun signOut()
}

class FirebaseAuthRepository (
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : AuthRepository {

    override val currentUser: FirebaseUser?
        get() = auth.currentUser

    override suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> {
        return suspendCancellableCoroutine { continuation ->
            val credential = GoogleAuthProvider.getCredential(idToken, null)

            auth.signInWithCredential(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = task.result?.user
                        if (user != null) {
                            continuation.resume(Result.success(user))
                        } else {
                            continuation.resume(Result.failure(Exception("Firebase user is null after sign-in")))
                        }
                    } else {
                        continuation.resume(Result.failure(task.exception ?: Exception("Unknown sign-in error")))
                    }
                }
        }
    }

    override fun signOut() {
        auth.signOut()
    }
}