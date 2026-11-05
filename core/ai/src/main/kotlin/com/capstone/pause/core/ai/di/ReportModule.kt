package com.capstone.pause.core.ai.di

import com.capstone.pause.core.ai.report.DefaultWeeklyReportGenerator
import com.capstone.pause.core.ai.report.WeeklyReportGenerator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 주간 리포트 생성기 바인딩. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ReportModule {
    @Binds
    abstract fun weeklyReportGenerator(impl: DefaultWeeklyReportGenerator): WeeklyReportGenerator
}
