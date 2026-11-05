package com.capstone.pause.core.sensing.di

import com.capstone.pause.core.data.demo.DemoDataSeeder
import com.capstone.pause.core.sensing.demo.AssetDemoDataSeeder
import com.capstone.pause.core.sensing.device.AndroidDeviceSignalReader
import com.capstone.pause.core.sensing.device.DayRhythmProvider
import com.capstone.pause.core.sensing.device.DeviceSignalReader
import com.capstone.pause.core.sensing.device.ProfileDayRhythmProvider
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

    @Binds
    abstract fun deviceSignalReader(impl: AndroidDeviceSignalReader): DeviceSignalReader

    @Binds
    abstract fun dayRhythmProvider(impl: ProfileDayRhythmProvider): DayRhythmProvider
}
