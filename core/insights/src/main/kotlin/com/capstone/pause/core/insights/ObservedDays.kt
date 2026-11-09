package com.capstone.pause.core.insights

import com.capstone.pause.core.common.DayBoundary
import com.capstone.pause.core.model.ShortformSession
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 하루 평균을 낼 날 수. 기록이 시작되기 전 날까지 나누면 쓴 지 며칠 안 된 사용자의 평균이 작게 나온다. */
object ObservedDays {
    /**
     * 기록이 시작된 날(가장 이른 세션의 논리적 날짜, 새벽 4시 경계)부터 [weekEndInclusive]까지 센 날 수.
     * [weekStart]~[weekEndInclusive] 길이로 자르고 최소 1. [sessions]에는 기준선 탐색 범위가 들어 있어도 된다.
     */
    fun count(sessions: List<ShortformSession>, weekStart: LocalDate, weekEndInclusive: LocalDate, zone: ZoneId): Int {
        val weekLength = ChronoUnit.DAYS.between(weekStart, weekEndInclusive).toInt() + 1
        val first = sessions.minOfOrNull { it.start }?.let { DayBoundary.logicalDate(it, zone) } ?: return 1
        val from = maxOf(first, weekStart)
        val days = ChronoUnit.DAYS.between(from, weekEndInclusive).toInt() + 1
        return days.coerceIn(1, weekLength.coerceAtLeast(1))
    }
}
