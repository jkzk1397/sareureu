package com.capstone.pause.core.intervention.di

import com.capstone.pause.core.intervention.nudge.NudgeUi
import com.capstone.pause.core.intervention.overlay.OverlayController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 개입 화면 바인딩: [NudgeUi] = 오버레이(권한 없으면 미리보기 액티비티). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class NudgeUiModule {
    @Binds
    abstract fun nudgeUi(impl: OverlayController): NudgeUi
}
