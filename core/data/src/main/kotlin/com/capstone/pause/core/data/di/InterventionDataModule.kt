package com.capstone.pause.core.data.di

import com.capstone.pause.core.data.copy.CopyPackRepository
import com.capstone.pause.core.data.copy.DefaultCopyPackRepository
import com.capstone.pause.core.data.maintenance.RetentionRepository
import com.capstone.pause.core.data.maintenance.RoomRetentionRepository
import com.capstone.pause.core.data.message.MessageRepository
import com.capstone.pause.core.data.message.RoomMessageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 개입(3단계)에 쓰는 데이터 바인딩: 문구 팩, 메시지, 보관 기간 정리. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class InterventionDataModule {
    @Binds
    abstract fun copyPackRepository(impl: DefaultCopyPackRepository): CopyPackRepository

    @Binds
    abstract fun messageRepository(impl: RoomMessageRepository): MessageRepository

    @Binds
    abstract fun retentionRepository(impl: RoomRetentionRepository): RetentionRepository
}
