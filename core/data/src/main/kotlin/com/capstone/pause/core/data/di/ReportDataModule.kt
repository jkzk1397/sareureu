package com.capstone.pause.core.data.di

import com.capstone.pause.core.data.report.CopyPackWriter
import com.capstone.pause.core.data.report.RoomCopyPackWriter
import com.capstone.pause.core.data.report.RoomWeeklyReportRepository
import com.capstone.pause.core.data.report.WeeklyReportRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 주간 리포트·문구 팩 쓰기 바인딩. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ReportDataModule {
    @Binds
    abstract fun weeklyReportRepository(impl: RoomWeeklyReportRepository): WeeklyReportRepository

    @Binds
    abstract fun copyPackWriter(impl: RoomCopyPackWriter): CopyPackWriter
}
