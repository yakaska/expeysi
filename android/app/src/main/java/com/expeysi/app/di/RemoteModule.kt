package com.expeysi.app.di

import com.expeysi.app.data.remote.AuthProvider
import com.expeysi.app.data.remote.FamilyRemote
import com.expeysi.app.data.remote.SyncRemote
import com.expeysi.app.data.remote.firebase.FirebaseAuthProvider
import com.expeysi.app.data.remote.firebase.FirebaseFamilyRemote
import com.expeysi.app.data.remote.firebase.FirebaseSyncRemote
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Привязки транспортно-нейтральных интерфейсов к FirebaseAuth-реализациям. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RemoteModule {

    @Binds
    @Singleton
    abstract fun bindAuthProvider(impl: FirebaseAuthProvider): AuthProvider

    @Binds
    @Singleton
    abstract fun bindFamilyRemote(impl: FirebaseFamilyRemote): FamilyRemote

    @Binds
    @Singleton
    abstract fun bindSyncRemote(impl: FirebaseSyncRemote): SyncRemote
}
