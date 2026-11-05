package com.capstone.pause.core.data.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.common.AppLog
import com.capstone.pause.core.common.ApplicationScope
import com.capstone.pause.core.common.Dispatcher
import com.capstone.pause.core.common.PauseDispatchers
import com.capstone.pause.core.data.profile.DataStoreProfileRepository
import com.capstone.pause.core.data.profile.ProfileRepository
import com.capstone.pause.core.data.settings.DataStoreSettingsRepository
import com.capstone.pause.core.data.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 설정 DataStore 한정자. 파일당 인스턴스 하나. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsStore

/** 프로필·동의 DataStore 한정자. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ProfileStore

@Module
@InstallIn(SingletonComponent::class)
internal object DataProvidesModule {
    @Provides
    @Singleton
    fun appClock(): AppClock = AppClock.system()

    @Provides
    @Dispatcher(PauseDispatchers.IO)
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Dispatcher(PauseDispatchers.DEFAULT)
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(@Dispatcher(PauseDispatchers.DEFAULT) dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)

    @Provides
    @Singleton
    fun appLog(): AppLog = AndroidAppLog

    @Provides
    @Singleton
    @SettingsStore
    fun settingsStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
        context.preferencesDataStoreFile("settings")
    }

    @Provides
    @Singleton
    @ProfileStore
    fun profileStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
        context.preferencesDataStoreFile("profile")
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataBindsModule {
    @Binds
    abstract fun settingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    abstract fun profileRepository(impl: DataStoreProfileRepository): ProfileRepository
}

/** android.util.Log로 남기는 [AppLog]. 개인정보를 넣지 않는다. */
private object AndroidAppLog : AppLog {
    override fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        Log.w(tag, message, throwable)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
    }
}
