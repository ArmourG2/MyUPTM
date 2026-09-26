package com.myuptm.auth

import android.content.Context
import androidx.browser.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption

@Composable
fun SignInScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Welcome to MyUPTM")

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Please sign in with your UPTM Google Account")

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = {
            println("Google Sign-In button clicked")
        }){
            Text(text = "Sign in with Google")
        }
    }
}

private suspend fun getGoogleIdToken(context: Context) :String {
    val credentialManager = CredentialManager.create((context))

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(context.getString(R.string.default_web_client_id))
        .setAutoSelectEnabled(false)
        .build()

    val result = credentialManager.getCredential(context, GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build())

    val credential = result.credential
    if (credential is CustomCredential &&
        credential.type == GetGoogleIdToken.TYPE_GOOGLE_TO_TOKEN_CREDENTIAL
    ) {
        return GetGoogleIdTokenResult.createForm(credential.data).idToken
    }
    throw Exception("Unexpected credential type")
}