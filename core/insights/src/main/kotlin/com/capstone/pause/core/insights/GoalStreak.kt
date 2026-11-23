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
 * - 감지를 꺼 둔 시간이 길어 기록된 비율이 [Baseline.MIN_COVERAGE] 아래인 날은 달성으로 세지 않는다(거기서 끊긴다).
 *   감지를 끄면 0분으로 보이는 것을 연속 일수에 유리하게 쓰지 못하게 하려는 것이다.
 */
object GoalStreak {
    /** 최대로 세는 날(서버 한도와 같음). */
    const val MAX_DAYS = 3650

    /** 연속 일수. 기록이 없거나 목표가 0 이하면 0. */
    fun compute(
        sessions: List<ShortformSession>,
        today: LocalDate,
        zone: ZoneId,
        now: Instant,
        goalMinutes: Int,
        /** 사용자가 감지를 꺼 둔 구간(아직 꺼져 있으면 끝 = 지금). */
        off: List<TimeSpan> = emptyList(),
    ): Int {
        if (goalMinutes <= 0) return 0
        fun recorded(day: LocalDate): Boolean = off.isEmpty() ||
            Coverage.ofDay(TimeSpan(DayBoundary.startOf(day, zone), DayBoundary.endOf(day, zone)), off, now) >=
            Baseline.MIN_COVERAGE
        val first = sessions.minOfOrNull { it.start }?.let { DayBoundary.logicalDate(it, zone) } ?: return 0
        if (ShortformMinutes.onDay(sessions, today, zone, now) > goalMinutes) return 0
        var streak = 0
        var day = today.minusDays(1)
        while (streak < MAX_DAYS && !day.isBefore(first) && recorded(day) &&
            ShortformMinutes.onDay(sessions, day, zone, now) <= goalMinutes
        ) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }
}
