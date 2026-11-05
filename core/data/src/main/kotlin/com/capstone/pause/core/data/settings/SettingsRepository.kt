package com.capstone.pause.core.data.settings

import kotlinx.coroutines.flow.Flow

/** 테마 선택(설정 > 테마). */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 앱 설정 묶음. 화면은 이 값을 그리기만 한다. */
data class AppSettings(
    val demoMode: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showMelt: Boolean = true,
)

/** 설정 저장소(DataStore). */
interface SettingsRepository {
    /** 현재 설정. */
    val settings: Flow<AppSettings>

    /** 시연 모드 켜기·끄기. */
    suspend fun setDemoMode(enabled: Boolean)

    /** 테마 바꾸기. */
    suspend fun setThemeMode(mode: ThemeMode)

    /** 르르 녹는 표시 켜기·끄기. */
    suspend fun setShowMelt(enabled: Boolean)
}
