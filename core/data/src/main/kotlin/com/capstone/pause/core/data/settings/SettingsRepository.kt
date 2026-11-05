package com.capstone.pause.core.data.settings

import com.capstone.pause.core.data.demo.DemoPattern
import com.capstone.pause.core.model.ContextSlot
import kotlinx.coroutines.flow.Flow

/** 테마 선택(설정 > 테마). */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** AI 사용 방식(04 문서 4절): 클라우드 / 기기 안 / 사용 안 함(모의 AI). */
enum class AiMode { CLOUD, ON_DEVICE, OFF }

/** 맥락별 개입 임계값 기본값(PRD 7.2절): 한밤중 10분, 나머지 15분. */
object DefaultThresholds {
    /** 한밤중. */
    const val LATE_NIGHT_MIN = 10

    /** 나머지 맥락. */
    const val DEFAULT_MIN = 15

    /** 고를 수 있는 값(05 문서 5.1절 제안 값과 같다). */
    val CHOICES = listOf(5, 10, 15, 20, 30)

    /** 기본 표. */
    val ALL: Map<ContextSlot, Int> =
        ContextSlot.entries.associateWith { if (it == ContextSlot.LATE_NIGHT) LATE_NIGHT_MIN else DEFAULT_MIN }
}

/** 앱 설정 묶음. 화면은 이 값을 그리기만 한다. */
data class AppSettings(
    val demoMode: Boolean = false,
    val demoPattern: DemoPattern = DemoPattern.NIGHT_OWL,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showMelt: Boolean = true,
    val aiMode: AiMode = AiMode.OFF,
    val thresholds: Map<ContextSlot, Int> = DefaultThresholds.ALL,
) {
    /** 맥락의 임계값(분). */
    fun thresholdFor(slot: ContextSlot): Int = thresholds[slot] ?: DefaultThresholds.ALL.getValue(slot)
}

/** 설정 저장소(DataStore). */
interface SettingsRepository {
    /** 현재 설정. */
    val settings: Flow<AppSettings>

    /** 시연 모드 켜기·끄기. */
    suspend fun setDemoMode(enabled: Boolean)

    /** 시연 패턴 고르기. */
    suspend fun setDemoPattern(pattern: DemoPattern)

    /** 테마 바꾸기. */
    suspend fun setThemeMode(mode: ThemeMode)

    /** 르르 녹는 표시 켜기·끄기. */
    suspend fun setShowMelt(enabled: Boolean)

    /** AI 사용 방식. 동의 기록은 [com.capstone.pause.core.data.profile.ConsentRepository]에 따로 남긴다. */
    suspend fun setAiMode(mode: AiMode)

    /** 맥락별 임계값(분). 사용자가 바꾸거나 AI 제안을 수락했을 때만. */
    suspend fun setThreshold(slot: ContextSlot, minutes: Int)

    /** 설정을 기본값으로(모든 기록 삭제). */
    suspend fun reset()
}
