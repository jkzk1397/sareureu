package com.capstone.pause.core.insights

import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.ShortformSession
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** 하루 시계용 시간별·맥락별 분(06 문서 7절). [minutesByHour]의 인덱스 = 시(0~23). */
data class ClockBins(val minutesByHour: List<Map<ContextSlot, Int>>) {
    /** 전체 분. */
    val totalMinutes: Int get() = minutesByHour.sumOf { it.values.sum() }

    /** 맥락별 합계(맥락 순서대로, 0분 제외). */
    fun byContext(): Map<ContextSlot, Int> = ContextSlot.entries
        .associateWith { slot -> minutesByHour.sumOf { it[slot] ?: 0 } }
        .filterValues { it > 0 }

    companion object {
        /** 빈 시계. */
        val EMPTY = ClockBins(List(HOURS) { emptyMap() })

        /**
         * [sessions]를 [from]~[to] 구간에서 시간 칸별로 나눈다. 진행 중 세션은 [now]까지 센다.
         * 칸 경계를 넘는 세션은 초 단위로 나눠 담고, 마지막에 분으로 반올림한다.
         */
        fun compute(
            sessions: List<ShortformSession>,
            from: Instant,
            to: Instant,
            now: Instant,
            zone: ZoneId,
        ): ClockBins {
            val seconds = Array(HOURS) { DoubleArray(ContextSlot.entries.size) }
            for (s in sessions) {
                val start = maxOf(s.start, from)
                val end = minOf(s.end ?: now, to)
                if (!end.isAfter(start)) continue
                var cursor = start
                while (cursor.isBefore(end)) {
                    val local = ZonedDateTime.ofInstant(cursor, zone)
                    val nextHour = local.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant()
                    val sliceEnd = minOf(nextHour, end)
                    seconds[local.hour][s.context.ordinal] += Duration.between(cursor, sliceEnd).seconds.toDouble()
                    cursor = sliceEnd
                }
            }
            return ClockBins(
                seconds.map { row ->
                    ContextSlot.entries
                        .associateWith { Math.round(row[it.ordinal] / SECONDS_PER_MINUTE).toInt() }
                        .filterValues { it > 0 }
                },
            )
        }

        private const val HOURS = 24
        private const val SECONDS_PER_MINUTE = 60.0
    }
}
