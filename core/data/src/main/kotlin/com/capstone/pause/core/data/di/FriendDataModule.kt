package com.capstone.pause.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.capstone.pause.core.common.ApplicationScope
import com.capstone.pause.core.data.friend.DataStoreFriendLocalStore
import com.capstone.pause.core.data.friend.FriendLocalStore
import com.capstone.pause.core.data.friend.FriendStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

/** 친구 로컬 기록 DataStore(`friends`)와 저장소 바인딩. */
@Module
@InstallIn(SingletonComponent::class)
internal object FriendDataProvidesModule {
    @Provides
    @Singleton
    @FriendStore
    fun friendStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
        context.preferencesDataStoreFile("friends")
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class FriendDataBindsModule {
    @Binds
    abstract fun friendLocalStore(impl: DataStoreFriendLocalStore): FriendLocalStore
}
