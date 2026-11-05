package com.capstone.pause.core.insights

import com.capstone.pause.core.common.DayBoundary
import com.capstone.pause.core.model.ShortformSession
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 하루 합계. [coverage]는 그날 기록된 비율(0~1). */
data class DayStat(val date: LocalDate, val minutes: Int, val coverage: Double)

/** 세션 시간 합계 계산. */
object ShortformMinutes {
    /** [from]~[to] 구간에 겹치는 시청 분(진행 중 세션은 [now]까지). */
    fun between(sessions: List<ShortformSession>, from: Instant, to: Instant, now: Instant): Int {
        val seconds = sessions.sumOf { s ->
            val start = maxOf(s.start, from)
            val end = minOf(s.end ?: now, to)
            if (end.isAfter(start)) Duration.between(start, end).seconds else 0L
        }
        return Math.round(seconds / SECONDS_PER_MINUTE).toInt()
    }

    /** 논리적 날짜(새벽 4시 경계) 하루의 시청 분. */
    fun onDay(sessions: List<ShortformSession>, date: LocalDate, zone: ZoneId, now: Instant): Int =
        between(sessions, DayBoundary.startOf(date, zone), DayBoundary.endOf(date, zone), now)

    /** 세션 하나의 길이(분, 진행 중이면 [now]까지). */
    fun lengthMinutes(session: ShortformSession, now: Instant): Double =
        Duration.between(session.start, session.end ?: now).seconds / SECONDS_PER_MINUTE

    private const val SECONDS_PER_MINUTE = 60.0
}

/** 기준선: 처음 7일(기록률 80% 이상인 날)의 하루 평균(PRD 지표 표). */
object Baseline {
    /** 기준선에 쓰는 날 수. */
    const val DAYS = 7

    /** 기준선으로 인정하는 최소 기록률. */
    const val MIN_COVERAGE = 0.8

    /** 날짜순 [days]에서 기준선(하루 평균 분)을 구한다. 인정되는 날이 7일 미만이면 null. */
    fun compute(days: List<DayStat>): Double? {
        val valid = days.sortedBy { it.date }.filter { it.coverage >= MIN_COVERAGE }.take(DAYS)
        return if (valid.size < DAYS) null else valid.sumOf { it.minutes }.toDouble() / DAYS
    }
}

/** 감소율 = 1 - (이번 기간 하루 평균 ÷ 기준선). 늘었으면 음수. */
object ReductionRate {
    /** 기준선이 없거나 0이면 null. */
    fun compute(averageMinutes: Double, baselineMinutes: Double?): Double? =
        if (baselineMinutes == null || baselineMinutes <= 0.0) null else 1.0 - averageMinutes / baselineMinutes

    /** 기록률 80% 이상인 날만 평균한다(ADR-0005). 그런 날이 없으면 null. */
    fun averageOfCoveredDays(days: List<DayStat>): Double? {
        val valid = days.filter { it.coverage >= Baseline.MIN_COVERAGE }
        return if (valid.isEmpty()) null else valid.sumOf { it.minutes }.toDouble() / valid.size
    }
}
