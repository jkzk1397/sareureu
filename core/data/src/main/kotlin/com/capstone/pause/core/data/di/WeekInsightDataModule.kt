package com.capstone.pause.core.data.di

import com.capstone.pause.core.data.insight.DefaultWeekChartRepository
import com.capstone.pause.core.data.insight.WeekChartRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 리포트 차트 저장소 바인딩. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class WeekInsightDataModule {
    @Binds
    abstract fun weekChartRepository(impl: DefaultWeekChartRepository): WeekChartRepository
}
