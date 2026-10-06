package com.athar.ludo3d.data.firebase

import com.athar.ludo3d.domain.repository.AuthRepository
import com.athar.ludo3d.domain.repository.AuthResult
import com.athar.ludo3d.domain.repository.AuthUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// Added by Athar
// Real FirebaseAuth-backed implementation.
@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : AuthRepository {

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String
    ): AuthResult =
        runCatching {
            // Added by Athar: remove accidental spaces from email
            val cleanEmail = email.trim()

            val result = firebaseAuth
                .createUserWithEmailAndPassword(cleanEmail, password)
                .await()

            result.user?.updateProfile(
                UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .build()
            )?.await()

            val user = requireNotNull(firebaseAuth.currentUser) {
                "Sign up succeeded but no current user"
            }

            AuthResult.Success(
                user.toAuthUser(fallbackName = displayName)
            )
        }.getOrElse { e ->
            AuthResult.Failure(e.message ?: "Sign up failed")
        }

    override suspend fun signInWithEmail(
        email: String,
        password: String
    ): AuthResult =
        runCatching {
            // Added by Athar: remove accidental spaces from email
            val cleanEmail = email.trim()

            val result = firebaseAuth
                .signInWithEmailAndPassword(cleanEmail, password)
                .await()

            val user = requireNotNull(result.user) {
                "Sign in succeeded but no user returned"
            }

            AuthResult.Success(user.toAuthUser())
        }.getOrElse { e ->
            AuthResult.Failure(e.message ?: "Sign in failed")
        }

    override suspend fun signInAsGuest(): AuthResult =
        runCatching {
            val result = firebaseAuth
                .signInAnonymously()
                .await()

            val user = requireNotNull(result.user) {
                "Guest sign-in returned no user"
            }

            AuthResult.Success(
                user.toAuthUser(
                    fallbackName = "Guest-${user.uid.take(6)}"
                )
            )
        }.getOrElse { e ->
            AuthResult.Failure(e.message ?: "Guest sign-in failed")
        }

    override suspend fun signInWithGoogleIdToken(
        idToken: String
    ): AuthResult =
        runCatching {
            val credential = GoogleAuthProvider.getCredential(
                idToken,
                null
            )

            val result = firebaseAuth
                .signInWithCredential(credential)
                .await()

            val user = requireNotNull(result.user) {
                "Google sign-in returned no user"
            }

            AuthResult.Success(user.toAuthUser())
        }.getOrElse { e ->
            AuthResult.Failure(e.message ?: "Google sign-in failed")
        }

    override fun currentUser(): AuthUser? =
        firebaseAuth.currentUser?.toAuthUser()

    override fun signOut() {
        firebaseAuth.signOut()
    }

    private fun com.google.firebase.auth.FirebaseUser.toAuthUser(
        fallbackName: String? = null
    ) = AuthUser(
        uid = uid,
        displayName = displayName
            ?.takeIf { it.isNotBlank() }
            ?: fallbackName
            ?: "Player-${uid.take(6)}",
        email = email,
        isAnonymous = isAnonymous
    )
}