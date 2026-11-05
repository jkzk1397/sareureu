package com.capstone.pause.core.data.insight

import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.MeltStage
import java.time.DayOfWeek
import java.time.ZonedDateTime

/** 오늘 탭이 그리는 값. 모두 `core:insights`가 계산했다(화면은 계산하지 않는다). */
data class TodaySummary(
    val now: ZonedDateTime,
    val isDemo: Boolean,
    /** 시간(0~23)별 맥락별 분. */
    val minutesByHour: List<Map<ContextSlot, Int>>,
    val totalMinutes: Int,
    /** 맥락 순서대로, 0분 제외. */
    val byContext: Map<ContextSlot, Int>,
    /** 어제 같은 시각까지의 합계. 기록이 없으면 null. */
    val yesterdayMinutes: Int?,
    val baselineMinutes: Double?,
    val meltStage: MeltStage,
    /** 오늘 요일의 위험 시간대(시 범위). */
    val riskHours: List<IntRange>,
    /** 오늘 요일에서 가장 위험한 칸. */
    val topRisk: RiskSummary?,
    /** 오늘 카드가 뜬 세션 수와 그중 멈춘 세션 수. */
    val nudgedSessions: Int,
    val stoppedSessions: Int,
    /** 지금 보는 중인 세션. */
    val live: LiveSummary?,
) {
    /** 어제보다 줄었으면 양수. */
    val lessThanYesterday: Int? get() = yesterdayMinutes?.let { it - totalMinutes }
}

/** 위험 시간대 한 칸. */
data class RiskSummary(val dayOfWeek: DayOfWeek, val hour: Int, val averageMinutes: Int)

/** 보는 중 카드 값. [packageName]은 표시용 앱 이름을 고를 때만 쓴다. */
data class LiveSummary(
    val packageName: String?,
    val context: ContextSlot,
    val elapsedMinutes: Int,
    val thresholdMinutes: Int,
)
