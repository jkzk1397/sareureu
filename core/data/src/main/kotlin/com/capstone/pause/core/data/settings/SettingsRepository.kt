package com.capstone.pause.core.data.settings

import com.capstone.pause.core.data.demo.DemoPattern
import com.capstone.pause.core.model.ContextSlot
import kotlinx.coroutines.flow.Flow

/** 테마 선택(설정 > 테마). */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** AI 사용 방식(04 문서 4절): 클라우드 / 기기 안 / 사용 안 함(모의 AI). */
enum class AiMode { CLOUD, ON_DEVICE, OFF }

/** 사용자가 만지는 알림 기준 묶음(설정 화면 두 줄): 평소 / 밤(자기 전 + 한밤중). */
enum class ThresholdGroup { NORMAL, NIGHT }

/** 맥락별 개입 임계값 기본값(PRD 7.2절): 밤(자기 전·한밤중) 10분, 나머지 15분. */
object DefaultThresholds {
    /** 고를 수 있는 범위(분, 1분 단위). */
    val RANGE = 1..60

    /** '밤' 묶음 맥락. */
    val NIGHT_SLOTS: Set<ContextSlot> = setOf(ContextSlot.PRE_SLEEP, ContextSlot.LATE_NIGHT)

    /** 밤 기본값. */
    const val NIGHT_MIN = 10

    /** 평소 기본값. */
    const val DEFAULT_MIN = 15

    /** 기본 표. */
    val ALL: Map<ContextSlot, Int> =
        ContextSlot.entries.associateWith { if (it in NIGHT_SLOTS) NIGHT_MIN else DEFAULT_MIN }

    /** 묶음에 속한 맥락. */
    fun slotsOf(group: ThresholdGroup): Set<ContextSlot> = when (group) {
        ThresholdGroup.NIGHT -> NIGHT_SLOTS
        ThresholdGroup.NORMAL -> ContextSlot.entries.toSet() - NIGHT_SLOTS
    }

    /** 맥락이 속한 묶음. */
    fun groupOf(slot: ContextSlot): ThresholdGroup =
        if (slot in NIGHT_SLOTS) ThresholdGroup.NIGHT else ThresholdGroup.NORMAL

    /** 묶음의 표시값 = 묶음 안 최솟값(예전에 맥락별로 다르게 저장한 사용자 대비). */
    fun groupValue(thresholds: Map<ContextSlot, Int>, group: ThresholdGroup): Int =
        slotsOf(group).minOf { thresholds[it] ?: ALL.getValue(it) }
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

    /** 맥락별 임계값(분, [DefaultThresholds.RANGE]). 사용자가 바꾸거나 AI 제안을 수락했을 때만. */
    suspend fun setThreshold(slot: ContextSlot, minutes: Int)

    /** 여러 맥락의 임계값을 한 번에(설정 '평소'·'밤' 줄, AI 제안 수락). */
    suspend fun setThresholds(slots: Set<ContextSlot>, minutes: Int) {
        slots.forEach { setThreshold(it, minutes) }
    }

    /** 설정을 기본값으로(모든 기록 삭제). */
    suspend fun reset()
}
