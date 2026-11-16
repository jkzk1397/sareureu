package com.capstone.pause.feature.settings.di

import com.capstone.pause.feature.settings.AccessibilityStatus
import com.capstone.pause.feature.settings.SystemAccessibilityStatus
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 설정 모듈 바인딩. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class SettingsModule {
    @Binds
    abstract fun accessibilityStatus(impl: SystemAccessibilityStatus): AccessibilityStatus
}
