package com.athar.ludo3d.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Added by Athar
// Reserved for app-wide bindings that don't belong in FirebaseModule
// (e.g. LudoGameEngine factory, CoroutineDispatchers) — added in Phase 2/4.
@Module
@InstallIn(SingletonComponent::class)
object AppModule
