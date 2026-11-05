package com.capstone.pause.core.insights

import com.capstone.pause.core.common.DayBoundary
import com.capstone.pause.core.model.ShortformSession
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 위험 시간대: 요일 × 1시간 칸. [averageMinutes]는 그 요일이 나온 날들의 평균. */
data class RiskWindow(val dayOfWeek: DayOfWeek, val hour: Int, val averageMinutes: Double, val daysSeen: Int)

/**
 * 지난 [days]일 요일×시간 칸 중 숏폼 시간 상위 [top]칸(PRD 지표 표).
 * 14일이면 같은 요일은 두 번뿐이라 '최소 등장 일수'는 [minDaysSeen](기본 2)로 둔다.
 * 요일은 새벽 4시 경계의 논리적 날짜 기준이다(화요일 밤 1시 = 화요일).
 */
object RiskWindows {
    /** 계산. [today]는 포함하지 않는다(어제까지). */
    fun compute(
        sessions: List<ShortformSession>,
        today: LocalDate,
        zone: ZoneId,
        now: Instant,
        days: Int = 14,
        top: Int = 3,
        minDaysSeen: Int = 2,
    ): List<RiskWindow> {
        val first = today.minusDays(days.toLong())
        val sums = HashMap<Pair<DayOfWeek, Int>, Int>()
        val seen = HashMap<Pair<DayOfWeek, Int>, Int>()
        var date = first
        while (date.isBefore(today)) {
            val dayStart = DayBoundary.startOf(date, zone)
            val bins = ClockBins.compute(sessions, dayStart, DayBoundary.endOf(date, zone), now, zone)
            bins.minutesByHour.forEachIndexed { hour, byContext ->
                val minutes = byContext.values.sum()
                if (minutes > 0) {
                    val key = date.dayOfWeek to hour
                    sums[key] = (sums[key] ?: 0) + minutes
                    seen[key] = (seen[key] ?: 0) + 1
                }
            }
            date = date.plusDays(1)
        }
        val occurrences = (0 until days).map { first.plusDays(it.toLong()).dayOfWeek }.groupingBy { it }.eachCount()
        return sums.entries
            .filter { (seen[it.key] ?: 0) >= minDaysSeen }
            .map { (key, total) ->
                RiskWindow(
                    dayOfWeek = key.first,
                    hour = key.second,
                    averageMinutes = total.toDouble() / (occurrences[key.first] ?: 1),
                    daysSeen = seen[key] ?: 0,
                )
            }
            .sortedWith(
                compareByDescending<RiskWindow> {
                    it.averageMinutes
                }.thenBy { it.dayOfWeek }.thenBy { it.hour },
            )
            .take(top)
    }
}
