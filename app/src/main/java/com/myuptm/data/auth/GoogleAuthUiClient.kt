package com.myuptm.data.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

class GoogleAuthUiClient(
    private val context: Context
) {
    private val credentialManager = CredentialManager.create(context)

    // IMPORTANT: You must replace this with your actual Web Client ID
    // Find it in google-services.json -> client -> oauth_client -> client_id (where client_type is 3)
    private val webClientId = "897619036861-2qfsfvork0c99flmk9tv11di20c71dfl.apps.googleusercontent.com"

    suspend fun signIn(activity: Activity): Result<String> {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false) // Show all accounts for POC testing
                .setServerClientId(webClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                context = activity,
                request = request
            )

            val credential = result.credential

            if (credential is GoogleIdTokenCredential) {
                return Result.success(credential.idToken)
            }

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                return Result.success(googleCredential.idToken)
            }

            return Result.failure(
                Exception("Unexpected credential type: ${credential::class.simpleName} / ${credential.type}")
            )

        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}