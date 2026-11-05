package com.capstone.pause.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.capstone.pause.core.common.AppLog
import com.capstone.pause.core.data.friend.FriendLocalStore
import com.capstone.pause.core.data.privacy.DefaultPrivacyRepository
import com.capstone.pause.core.data.privacy.EraseOrchestrator
import com.capstone.pause.core.data.privacy.EraseStep
import com.capstone.pause.core.data.privacy.PrivacyRepository
import com.capstone.pause.core.database.PauseDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 개인정보(내보내기·모든 기록 삭제) 바인딩. */
@Module
@InstallIn(SingletonComponent::class)
internal object PrivacyProvidesModule {
    /**
     * 지울 것: Room 전체(시연·실제 모두), core:data의 DataStore 파일(설정·프로필·동의·추적 앱·친구), 캐시 폴더.
     * DataStore 파일을 새로 만들면 여기에 단계를 더해야 한다.
     * 감지 상태·예약 알림(core:intervention의 `monitoring` 파일)과 WorkManager는 설정 화면이
     * `MonitoringController.stop()`·`ReminderScheduler`·`WorkManager.cancelAllWork()`로 따로 정리한다.
     */
    @Provides
    @Singleton
    fun eraseOrchestrator(
        @ApplicationContext context: Context,
        db: PauseDatabase,
        @SettingsStore settings: DataStore<Preferences>,
        @ProfileStore profile: DataStore<Preferences>,
        @TrackedAppsStore trackedApps: DataStore<Preferences>,
        friends: FriendLocalStore,
        log: AppLog,
    ): EraseOrchestrator = EraseOrchestrator(
        steps = listOf(
            EraseStep("room") { db.clearAllTables() },
            EraseStep("settings") { settings.edit { it.clear() } },
            EraseStep("profile") { profile.edit { it.clear() } },
            EraseStep("tracked_apps") { trackedApps.edit { it.clear() } },
            EraseStep("friends") { friends.clear() },
            EraseStep("cache") { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } },
        ),
        log = log,
    )
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PrivacyBindsModule {
    @Binds
    abstract fun privacyRepository(impl: DefaultPrivacyRepository): PrivacyRepository
}
