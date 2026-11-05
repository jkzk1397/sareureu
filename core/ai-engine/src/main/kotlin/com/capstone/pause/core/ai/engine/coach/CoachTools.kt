package com.capstone.pause.core.ai.engine.coach

import com.capstone.pause.core.model.ContextSlot
import kotlinx.serialization.Serializable

/** 조회 기간(05 문서 5.4절). */
enum class Period { TODAY, THIS_WEEK, LAST_WEEK, LAST_4_WEEKS }

/**
 * 코치 채팅 도구(모두 읽기 전용). 구현은 `core:ai`가 저장소·`core:insights`로 만든다.
 * 결과 안의 `key`는 통계 근거 키, 세션의 `id`는 세션 근거, 숫자는 답에 쓸 수 있는 숫자가 된다.
 * 비율은 0~100 정수(퍼센트)로 준다.
 */
interface CoachTools {
    /** 총·하루 평균·기준선·감소율·기록률·세션 수·멈춘 비율. */
    suspend fun getSummary(period: Period): SummaryResult

    /** 맥락별 분·세션 수·중앙값. */
    suspend fun getContextBreakdown(period: Period): ContextBreakdownResult

    /** 세션 목록(최대 15개). */
    suspend fun getSessions(query: SessionQuery): SessionsResult

    /** 진입 → 앱 → 이탈 상위(최대 5개). */
    suspend fun getFlows(period: Period, limit: Int): FlowsResult

    /** 요일×시간 상위 3칸. */
    suspend fun getRiskWindows(): RiskWindowsResult

    /** 내 계획. */
    suspend fun getPlans(): PlansResult

    /** 내 연령대·신분 기준선(출처·시연 여부 포함). */
    suspend fun getPeerBaseline(): PeerBaselineResult

    /** 맥락별 전략 라이브러리 항목. */
    suspend fun suggestStrategies(context: ContextSlot?): StrategiesResult
}

/** `get_sessions` 인자. */
@Serializable
data class SessionQuery(
    val period: Period = Period.THIS_WEEK,
    val context: ContextSlot? = null,
    val app: String? = null,
    val minMinutes: Int? = null,
    val limit: Int = MAX_SESSIONS,
) {
    /** 상수. */
    companion object {
        /** 한 번에 돌려주는 최대 세션 수. */
        const val MAX_SESSIONS = 15
    }
}

/** 요약. */
@Serializable
data class SummaryResult(
    val key: String,
    val period: Period,
    val totalMinutes: Int,
    val avgMinPerDay: Int,
    val baselineMinPerDay: Int? = null,
    val reductionPercent: Int? = null,
    val coveragePercent: Int? = null,
    val sessionCount: Int,
    val stopRatePercent: Int? = null,
)

/** 맥락 하나. */
@Serializable
data class ContextItem(
    val key: String,
    val context: ContextSlot,
    val minutes: Int,
    val sessions: Int,
    val medianMinutes: Int,
)

/** 맥락별 결과. */
@Serializable
data class ContextBreakdownResult(val period: Period, val items: List<ContextItem>)

/** 세션 하나. 앱은 표시용 종류(유튜브 등)이고 시각은 숫자로도 준다. */
@Serializable
data class SessionItem(
    val id: Long,
    val dayOfWeek: String,
    val startHour: Int,
    val startMinute: Int,
    val minutes: Int,
    val app: String,
    val context: ContextSlot,
    val entry: String,
    val exit: String?,
    val maxLevel: Int,
)

/** 세션 목록. */
@Serializable
data class SessionsResult(val sessions: List<SessionItem>)

/** 흐름 하나. */
@Serializable
data class FlowItem(
    val key: String,
    val entry: String,
    val app: String,
    val exit: String,
    val count: Int,
    val medianMinutes: Int,
)

/** 흐름 목록. */
@Serializable
data class FlowsResult(val period: Period, val flows: List<FlowItem>)

/** 위험 시간대 칸. */
@Serializable
data class RiskItem(val key: String, val dayOfWeek: String, val hour: Int, val averageMinutes: Int)

/** 위험 시간대 목록. */
@Serializable
data class RiskWindowsResult(val windows: List<RiskItem>)

/** 계획 하나. */
@Serializable
data class PlanItem(val key: String, val context: ContextSlot?, val ifText: String, val thenText: String)

/** 계획 목록. */
@Serializable
data class PlansResult(val plans: List<PlanItem>)

/** 또래 기준선. [isDemo]면 화면·답에 "시연용"을 밝힌다. */
@Serializable
data class PeerBaselineResult(
    val key: String,
    val ageBand: String?,
    val occupation: String?,
    val peerMedianMinPerDay: Int?,
    val myAvgMinPerDay: Int?,
    val source: String,
    val isDemo: Boolean,
)

/** 전략 하나(라이브러리 그대로). */
@Serializable
data class StrategyItem(val key: String, val context: ContextSlot?, val ifText: String, val thenText: String)

/** 전략 목록. */
@Serializable
data class StrategiesResult(val strategies: List<StrategyItem>)
