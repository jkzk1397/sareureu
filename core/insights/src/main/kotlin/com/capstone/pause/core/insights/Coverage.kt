package com.capstone.pause.core.insights

import java.time.Duration
import java.time.Instant

/** 시각 구간 [start, end). */
data class TimeSpan(val start: Instant, val end: Instant) {
    /** 길이(초). 거꾸로면 0. */
    val seconds: Long get() = if (end.isAfter(start)) Duration.between(start, end).seconds else 0L
}

/** 기록률(PRD 9절): 화면이 켜진 시간 중 감지가 살아 있던 비율(0~1). */
object Coverage {
    /** [screenOn] 구간 합 중 [alive] 구간과 겹치는 비율. 화면 켜짐 시간이 0이면 null. */
    fun compute(screenOn: List<TimeSpan>, alive: List<TimeSpan>): Double? {
        val on = merge(screenOn)
        val total = on.sumOf { it.seconds }
        if (total == 0L) return null
        val live = merge(alive)
        var covered = 0L
        for (a in on) {
            for (b in live) {
                val s = maxOf(a.start, b.start)
                val e = minOf(a.end, b.end)
                if (e.isAfter(s)) covered += Duration.between(s, e).seconds
            }
        }
        return (covered.toDouble() / total).coerceIn(0.0, 1.0)
    }

    /** 겹치는 구간을 합친다. */
    fun merge(spans: List<TimeSpan>): List<TimeSpan> {
        val sorted = spans.filter { it.seconds > 0 }.sortedBy { it.start }
        val out = mutableListOf<TimeSpan>()
        for (s in sorted) {
            val last = out.lastOrNull()
            if (last != null && !s.start.isAfter(last.end)) {
                out[out.lastIndex] = TimeSpan(last.start, maxOf(last.end, s.end))
            } else {
                out += s
            }
        }
        return out
    }
}
