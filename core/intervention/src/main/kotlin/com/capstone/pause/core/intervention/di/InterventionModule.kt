package com.capstone.pause.core.intervention.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.capstone.pause.core.common.ApplicationScope
import com.capstone.pause.core.data.demo.DemoController
import com.capstone.pause.core.data.monitoring.MonitoringStatus
import com.capstone.pause.core.intervention.demo.DemoControllerImpl
import com.capstone.pause.core.intervention.service.MonitoringController
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

/** 감지 켜 둠·예약 알림 시각 DataStore(파일 `monitoring`). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MonitoringStore

@Module
@InstallIn(SingletonComponent::class)
internal object InterventionProvidesModule {
    @Provides
    @Singleton
    @MonitoringStore
    fun monitoringStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
        context.preferencesDataStoreFile("monitoring")
    }
}

/** `NudgeUi` 바인딩은 오버레이 쪽(`overlay/`)이 따로 둔다. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class InterventionBindsModule {
    @Binds
    abstract fun demoController(impl: DemoControllerImpl): DemoController

    @Binds
    abstract fun monitoringStatus(impl: MonitoringController): MonitoringStatus
}
