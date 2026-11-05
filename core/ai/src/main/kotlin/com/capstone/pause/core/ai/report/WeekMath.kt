package com.capstone.pause.core.ai.report

import com.capstone.pause.core.ai.engine.llm.Provider
import com.capstone.pause.core.ai.engine.run.CallOutcome
import com.capstone.pause.core.common.DayBoundary
import com.capstone.pause.core.data.report.StoredHypothesis
import com.capstone.pause.core.insights.Baseline
import com.capstone.pause.core.insights.DayStat
import com.capstone.pause.core.insights.RiskWindows
import com.capstone.pause.core.insights.ShortformMinutes
import com.capstone.pause.core.insights.WeekInput
import com.capstone.pause.core.insights.WeeklyObservations
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.NudgeEvent
import com.capstone.pause.core.model.ShortformSession
import com.capstone.pause.core.model.Strategy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 리포트 주: [start] ~ [endExclusive] 전날(논리적 날짜, 새벽 4시 경계). */
data class WeekWindow(val today: LocalDate, val start: LocalDate, val endExclusive: LocalDate) {
    /** 주 첫날 0시가 아니라 04시. */
    fun startInstant(zone: ZoneId): Instant = DayBoundary.startOf(start, zone)

    /** 배타적 끝. */
    fun endInstant(zone: ZoneId): Instant = DayBoundary.startOf(endExclusive, zone)

    /** 기준선을 찾을 때 거슬러 보는 첫날. */
    val historyStart: LocalDate get() = start.minusDays(HISTORY_DAYS)

    companion object {
        /** 리포트 주 길이. */
        const val DAYS = 7L

        /** 기준선 탐색 범위(리포트 주 이전). */
        const val HISTORY_DAYS = 28L

        /** 지난 7일(오늘 제외). */
        fun lastSevenDays(today: LocalDate) = WeekWindow(today, today.minusDays(DAYS), today)
    }
}

/** 주간 계산 묶음(순수). 숫자는 모두 `core:insights` 함수가 만든다. */
object WeekMath {
    /**
     * [sessions](기준선 탐색 범위부터 오늘까지)로 리포트 주 관찰을 계산한다.
     * 기준선은 리포트 주 이전에 기록이 처음 생긴 날부터 7일. 기록률은 아직 측정하지 않으므로 null(관찰 o11 없음).
     */
    fun compute(
        window: WeekWindow,
        sessions: List<ShortformSession>,
        nudges: List<NudgeEvent>,
        zone: ZoneId,
        now: Instant,
    ): WeeklyObservations {
        val weekStart = window.startInstant(zone)
        val weekEnd = window.endInstant(zone)
        val inWeek = sessions.filter { !it.start.isBefore(weekStart) && it.start.isBefore(weekEnd) }
        val weekNudges = nudges.filter { !it.at.isBefore(weekStart) && it.at.isBefore(weekEnd) }
        val pastDays = generateSequence(window.historyStart) { it.plusDays(1) }
            .takeWhile { it.isBefore(window.start) }
            .map { DayStat(it, ShortformMinutes.onDay(sessions, it, zone, now), coverage = 1.0) }
            .toList()
        val first = pastDays.indexOfFirst { it.minutes > 0 }
        val baseline = if (first < 0) null else Baseline.compute(pastDays.drop(first))
        val risks = RiskWindows.compute(sessions, window.endExclusive, zone, now)
        return WeeklyObservations.compute(
            WeekInput(
                sessions = inWeek,
                nudges = weekNudges,
                days = WeekWindow.DAYS.toInt(),
                now = now,
                baselineMinPerDay = baseline,
                coverage = null,
                riskWindows = risks,
            ),
        )
    }

    /** 화면용 관찰 목록. */
    fun observations(week: WeeklyObservations): List<ReportObservation> =
        week.observations.map { ReportObservation(it.id, it.kind.name, it.numbers, it.labels) }

    /** 가설 ID: 주 + 만든 시각 + 순서. 다시 만들면 새 ID라 예전 판정이 섞이지 않는다. */
    fun hypothesisId(weekStart: LocalDate, createdAtMs: Long, index: Int) = "$weekStart#$createdAtMs#$index"

    /** 저장된 리포트 JSON + 저장된 판정 → 화면용 AI 리포트. */
    internal fun toAiReport(
        saved: SavedWeeklyReport,
        weekStart: LocalDate,
        stored: List<StoredHypothesis>,
        strategies: List<Strategy>,
        thresholds: Map<ContextSlot, Int>,
    ): AiReport {
        val verdicts = stored.associateBy { it.id }
        val library = strategies.associateBy { it.id }
        return AiReport(
            summaryLine = saved.out.summaryLine,
            hypotheses = saved.out.hypotheses.mapIndexed { i, h ->
                val id = hypothesisId(weekStart, saved.createdAtMs, i)
                AiHypothesis(id, h.text, h.confidence, h.evidence, h.confirmQuestion, verdicts[id]?.verdict)
            },
            strategies = saved.out.strategies.mapNotNull { pick ->
                library[pick.strategyId]?.let { s ->
                    AiStrategy(s.id, s.context, s.ifText, pick.personalizedThen.ifBlank { s.thenText }, pick.why)
                }
            },
            viewerTypeLine = saved.out.viewerTypeLine,
            thresholds = saved.out.thresholdSuggestions
                .filter { thresholds[it.slot] != it.minutes }
                .map { AiThreshold(it.slot, it.minutes, thresholds[it.slot] ?: it.minutes, it.why) },
            callId = saved.callId,
            isMock = saved.provider == Provider.MOCK.name || saved.outcome == CallOutcome.MOCK_FALLBACK.name,
            createdAt = Instant.ofEpochMilli(saved.createdAtMs),
        )
    }

    /** 실제 AI가 검증을 통과했을 때만 다음 주 문구 팩을 바꾼다(모의 결과는 기본 팩과 같으니 저장하지 않음). */
    fun shouldActivateCopyPack(provider: Provider, outcome: CallOutcome): Boolean =
        (provider == Provider.CLOUD || provider == Provider.ON_DEVICE) &&
            (outcome == CallOutcome.PASSED || outcome == CallOutcome.RETRIED_PASSED)
}
