package com.athar.ludo3d.data.firebase

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.athar.ludo3d.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

// Added by Athar
// Real Credential Manager flow (the current Android-recommended API for Google
// Sign-In, replacing the deprecated GoogleSignInClient). Requires
// R.string.default_web_client_id to be set to the "Web client ID" from your
// Firebase project's Google sign-in provider config (see strings.xml comment).
@Singleton
class GoogleSignInHelper @Inject constructor(
    private val credentialManager: CredentialManager
) {
    /** Returns the Google ID token to hand to FirebaseAuthRepository.signInWithGoogleIdToken(). */
    suspend fun requestGoogleIdToken(context: Context): String {
        val webClientId = context.getString(R.string.default_web_client_id)
        require(webClientId != "REPLACE_WITH_YOUR_FIREBASE_WEB_CLIENT_ID") {
            "Google Sign-In is not configured yet: set default_web_client_id in strings.xml " +
                "to your Firebase project's Web client ID."
        }

        val nonceBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes)

        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setNonce(nonce)
            .build()

        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = credentialManager.getCredential(context, request)

        val credential = response.credential
        require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential type returned by Credential Manager"
        }

        return try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GoogleIdTokenParsingException) {
            error("Failed to parse Google ID token: ${e.message}")
        }
    }
}
