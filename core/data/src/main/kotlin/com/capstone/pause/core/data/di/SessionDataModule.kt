package com.capstone.pause.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.capstone.pause.core.common.ApplicationScope
import com.capstone.pause.core.data.trackedapp.DataStoreTrackedAppRepository
import com.capstone.pause.core.data.trackedapp.TrackedAppRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

/** 추적 앱 DataStore 한정자(파일 `tracked_apps`). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TrackedAppsStore

/** 감지 관련 데이터 바인딩. */
@Module
@InstallIn(SingletonComponent::class)
internal object SessionDataProvidesModule {
    @Provides
    @Singleton
    @TrackedAppsStore
    fun trackedAppsStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
        context.preferencesDataStoreFile("tracked_apps")
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SessionDataBindsModule {
    @Binds
    abstract fun trackedAppRepository(impl: DataStoreTrackedAppRepository): TrackedAppRepository
}
