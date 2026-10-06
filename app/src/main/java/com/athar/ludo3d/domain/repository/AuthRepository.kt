package com.athar.ludo3d.domain.repository

// Added by Athar
// Kept in the domain layer so ViewModels depend on this abstraction, never on
// FirebaseAuth directly (section 22: UI -> ViewModel -> UseCase -> Repository -> Firebase).
data class AuthUser(
    val uid: String,
    val displayName: String,
    val email: String?,
    val isAnonymous: Boolean
)

sealed class AuthResult {
    data class Success(val user: AuthUser) : AuthResult()
    data class Failure(val message: String) : AuthResult()
}

interface AuthRepository {
    suspend fun signUpWithEmail(email: String, password: String, displayName: String): AuthResult
    suspend fun signInWithEmail(email: String, password: String): AuthResult
    suspend fun signInAsGuest(): AuthResult
    suspend fun signInWithGoogleIdToken(idToken: String): AuthResult
    fun currentUser(): AuthUser?
    fun signOut()
}
