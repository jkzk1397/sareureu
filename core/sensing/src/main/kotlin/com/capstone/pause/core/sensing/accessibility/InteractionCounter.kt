package com.capstone.pause.core.sensing.accessibility

import com.capstone.pause.core.model.InteractionStats
import java.time.Duration
import java.time.Instant

/**
 * 숏폼 화면에서 넘김·누름 '횟수'만 센다(순수). 무엇을 넘겼는지·눌렀는지는 모른다(ADR-0004, 규칙 5).
 * 넘김 한 번에 스크롤 이벤트가 여러 개 오므로 [SWIPE_GAP] 안의 것은 한 번으로 친다.
 */
class InteractionCounter(private val keep: Duration = Duration.ofDays(2)) {
    private enum class Kind { SWIPE, LIKE, CLICK }

    private data class Mark(val at: Instant, val kind: Kind)

    private val marks = ArrayDeque<Mark>()
    private var lastSwipe: Instant? = null

    /** 스크롤 이벤트 하나. 넘김으로 셌으면 true. */
    @Synchronized
    fun onScroll(at: Instant): Boolean {
        val last = lastSwipe
        if (last != null && Duration.between(last, at) < SWIPE_GAP) return false
        lastSwipe = at
        add(Mark(at, Kind.SWIPE))
        return true
    }

    /** 누름 하나([like]면 좋아요 버튼). */
    @Synchronized
    fun onClick(at: Instant, like: Boolean) = add(Mark(at, if (like) Kind.LIKE else Kind.CLICK))

    /** [start]부터 [end]까지의 통계. */
    @Synchronized
    fun between(start: Instant, end: Instant): InteractionStats {
        val inRange = marks.filter { !it.at.isBefore(start) && !it.at.isAfter(end) }
        val swipes = inRange.filter { it.kind == Kind.SWIPE }.map { it.at }
        val gaps = swipes.zipWithNext { a, b -> Duration.between(a, b).toMillis() / MS_PER_SEC }
        return InteractionStats(
            swipes = swipes.size,
            medianSwipeIntervalSec = median(gaps),
            likes = inRange.count { it.kind == Kind.LIKE },
            otherClicks = inRange.count { it.kind == Kind.CLICK },
        )
    }

    private fun add(mark: Mark) {
        marks.addLast(mark)
        val cutoff = mark.at.minus(keep)
        while (marks.isNotEmpty() && marks.first().at.isBefore(cutoff)) marks.removeFirst()
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2
    }

    private companion object {
        val SWIPE_GAP: Duration = Duration.ofMillis(700)
        const val MS_PER_SEC = 1000.0
    }
}
