package com.capstone.pause.core.insights

import com.capstone.pause.core.common.DayBoundary
import com.capstone.pause.core.model.ShortformSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 목표 이하 연속 일수(ADR-0012, 친구 랭킹). 순수 함수.
 * - 끝난 날(어제부터 거꾸로)만 센다. 하루 시청 분이 [goalMinutes] 이하면 달성(새벽 4시 경계).
 * - 첫 기록보다 앞선 날(설치 전)은 세지 않는다. 기록이 있는 기간 안의 0분인 날은 달성이다.
 * - 오늘 이미 목표를 넘겼으면 연속이 끊긴 것이라 0.
 */
object GoalStreak {
    /** 최대로 세는 날(서버 한도와 같음). */
    const val MAX_DAYS = 3650

    /** 연속 일수. 기록이 없거나 목표가 0 이하면 0. */
    fun compute(sessions: List<ShortformSession>, today: LocalDate, zone: ZoneId, now: Instant, goalMinutes: Int): Int {
        if (goalMinutes <= 0) return 0
        val first = sessions.minOfOrNull { it.start }?.let { DayBoundary.logicalDate(it, zone) } ?: return 0
        if (ShortformMinutes.onDay(sessions, today, zone, now) > goalMinutes) return 0
        var streak = 0
        var day = today.minusDays(1)
        while (streak < MAX_DAYS && !day.isBefore(first) &&
            ShortformMinutes.onDay(sessions, day, zone, now) <= goalMinutes
        ) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }
}
