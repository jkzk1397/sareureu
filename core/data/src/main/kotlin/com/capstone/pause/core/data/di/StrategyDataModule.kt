package com.capstone.pause.core.data.di

import com.capstone.pause.core.data.aicall.AiCallRepository
import com.capstone.pause.core.data.aicall.RoomAiCallRepository
import com.capstone.pause.core.data.strategy.AssetStrategyRepository
import com.capstone.pause.core.data.strategy.StrategyRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** AI(4단계)에 쓰는 데이터 바인딩: 전략 라이브러리, AI 감사 로그. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class StrategyDataModule {
    @Binds
    abstract fun strategyRepository(impl: AssetStrategyRepository): StrategyRepository

    @Binds
    abstract fun aiCallRepository(impl: RoomAiCallRepository): AiCallRepository
}
