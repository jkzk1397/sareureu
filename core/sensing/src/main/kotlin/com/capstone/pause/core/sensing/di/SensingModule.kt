package com.capstone.pause.core.sensing.di

import com.capstone.pause.core.data.demo.DemoDataSeeder
import com.capstone.pause.core.sensing.demo.AssetDemoDataSeeder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 감지 모듈 바인딩. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class SensingModule {
    @Binds
    abstract fun demoDataSeeder(impl: AssetDemoDataSeeder): DemoDataSeeder
}
