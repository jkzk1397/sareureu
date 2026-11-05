package com.capstone.pause.core.sensing.device

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import com.capstone.pause.core.data.profile.ProfileRepository
import com.capstone.pause.core.model.DayRhythm
import com.capstone.pause.core.sensing.context.DefaultDayRhythm
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** 충전·이어폰 상태(맥락 확신도용). 첫 잠금 해제는 `SessionTracker`가 센다. */
interface DeviceSignalReader {
    /** 충전 중인지. */
    fun charging(): Boolean

    /** 이어폰(유선·블루투스)이 연결돼 있는지. */
    fun headphones(): Boolean
}

/** 안드로이드 구현. */
@Singleton
internal class AndroidDeviceSignalReader @Inject constructor(@ApplicationContext private val context: Context) :
    DeviceSignalReader {
    override fun charging(): Boolean = context.getSystemService(BatteryManager::class.java)?.isCharging ?: false

    override fun headphones(): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in HEADPHONE_TYPES }
    }

    private companion object {
        val HEADPHONE_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
    }
}

/** 맥락 분류에 쓸 하루 리듬. 프로필이 없으면 [DefaultDayRhythm]. */
fun interface DayRhythmProvider {
    /** 지금 리듬. */
    suspend fun current(): DayRhythm
}

/** 프로필의 하루 리듬. 프로필이 없으면(온보딩 전) [DefaultDayRhythm]. */
@Singleton
internal class ProfileDayRhythmProvider @Inject constructor(private val profileRepository: ProfileRepository) :
    DayRhythmProvider {
    override suspend fun current(): DayRhythm = profileRepository.profile.first()?.rhythm ?: DefaultDayRhythm.VALUE
}
