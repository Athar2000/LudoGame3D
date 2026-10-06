package com.athar.ludo3d.di

import androidx.credentials.CredentialManager
import android.content.Context
import com.athar.ludo3d.data.firebase.FirebaseAuthRepository
import com.athar.ludo3d.data.firebase.FirebaseChatRepository
import com.athar.ludo3d.data.firebase.FirebaseGameSyncRepository
import com.athar.ludo3d.data.firebase.FirebaseLeaderboardRepository
import com.athar.ludo3d.data.firebase.FirebaseRewardsRepository
import com.athar.ludo3d.data.firebase.FirebaseRoomRepository
import com.athar.ludo3d.data.firebase.FirebaseUserProfileRepository
import com.athar.ludo3d.domain.repository.AuthRepository
import com.athar.ludo3d.domain.repository.ChatRepository
import com.athar.ludo3d.domain.repository.GameSyncRepository
import com.athar.ludo3d.domain.repository.LeaderboardRepository
import com.athar.ludo3d.domain.repository.RewardsRepository
import com.athar.ludo3d.domain.repository.RoomRepository
import com.athar.ludo3d.domain.repository.UserProfileRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Added by Athar
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase =
        FirebaseDatabase.getInstance().apply {
            // Keep the room the user is currently in synced locally so brief
            // network blips don't wipe local UI state (helps section 14: reconnection).
            setPersistenceEnabled(true)
        }

    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions = FirebaseFunctions.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()

    @Provides
    @Singleton
    fun provideCredentialManager(@ApplicationContext context: Context): CredentialManager =
        CredentialManager.create(context)
}

// Added by Athar
// Separate @Binds module so AuthRepository stays an interface everywhere else
// in the app (ViewModels never see FirebaseAuthRepository directly).
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindRoomRepository(impl: FirebaseRoomRepository): RoomRepository

    @Binds
    @Singleton
    abstract fun bindGameSyncRepository(impl: FirebaseGameSyncRepository): GameSyncRepository

    @Binds
    @Singleton
    abstract fun bindUserProfileRepository(impl: FirebaseUserProfileRepository): UserProfileRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: FirebaseChatRepository): ChatRepository

    @Binds
    @Singleton
    abstract fun bindLeaderboardRepository(impl: FirebaseLeaderboardRepository): LeaderboardRepository

    @Binds
    @Singleton
    abstract fun bindRewardsRepository(impl: FirebaseRewardsRepository): RewardsRepository
}
