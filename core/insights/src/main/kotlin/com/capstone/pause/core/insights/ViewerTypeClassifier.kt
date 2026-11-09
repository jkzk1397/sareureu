package com.capstone.pause.core.insights

import com.capstone.pause.core.model.AppKind
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.EntryPoint
import com.capstone.pause.core.model.ShortformSession
import java.time.Instant

/** 숏폼 유형(05 문서 5.6절). 판정은 코드, AI는 한 줄 설명만. */
enum class ViewerType { NIGHT_OWL, COMMUTER, MEAL_BUDDY, DOMINO, GAP_FILLER, MORNING, BALANCED }

/**
 * 05 문서 5.6절. 하루 평균 30분 이하면 균형형. 넘으면 패턴마다 `값 / 기준`을 구해 가장 큰 유형을 고른다.
 * 기준을 못 넘어도 가장 가까운 유형을 준다(많이 보는 사람이 균형형이 되지 않게). 같으면 표 순서(밤 > 통학 > 식사 > 도미노 > 자투리 > 아침).
 */
object ViewerTypeClassifier {
    /** 하루 평균이 이 값 이하이면 균형형. */
    const val BALANCED_MAX_DAILY_MIN = 30.0

    /** [sessions]는 그 주의 세션, [days]는 하루 평균을 낼 날 수([ObservedDays]). */
    fun classify(sessions: List<ShortformSession>, days: Int, now: Instant): ViewerType {
        // 같은 값의 세션이 여러 개일 수 있어 Map이 아니라 목록으로 센다.
        val lengths = sessions.map { it to ShortformMinutes.lengthMinutes(it, now) }
        val total = lengths.sumOf { it.second }
        if (total <= 0.0 || days <= 0 || total / days <= BALANCED_MAX_DAILY_MIN) return ViewerType.BALANCED
        // 비율은 화면에 보이는 정수 퍼센트로 반올림해 비교한다(39.9%가 화면엔 40%인데 유형은 아니라고 하지 않게).
        fun pct(x: Double): Double = Math.round(x * PERCENT) / PERCENT
        fun share(vararg slots: ContextSlot) = pct(
            lengths.filter { it.first.context in slots }.sumOf { it.second } / total,
        )
        val fromChat = pct(
            sessions.count { e ->
                val entry = e.entry
                entry is EntryPoint.FromApp && (entry.kind == AppKind.MESSENGER || entry.kind == AppKind.SOCIAL)
            }.toDouble() / sessions.size,
        )
        val gapMedian = SessionStats.median(
            lengths.filter {
                it.first.context == ContextSlot.CLASS_WORK || it.first.context == ContextSlot.BREAK
            }.map { it.second },
        )
        val gap = if (gapMedian <= GAP_MEDIAN_MAX) share(ContextSlot.CLASS_WORK, ContextSlot.BREAK) else 0.0
        // 표 순서 = 같은 점수일 때의 우선순위.
        val scores = listOf(
            ViewerType.NIGHT_OWL to share(ContextSlot.PRE_SLEEP, ContextSlot.LATE_NIGHT) / NIGHT,
            ViewerType.COMMUTER to share(ContextSlot.COMMUTE) / COMMUTE,
            ViewerType.MEAL_BUDDY to share(ContextSlot.MEAL) / MEAL,
            ViewerType.DOMINO to fromChat / DOMINO,
            ViewerType.GAP_FILLER to gap / GAP,
            ViewerType.MORNING to share(ContextSlot.WAKE_UP) / MORNING,
        )
        val best = scores.fold(scores.first()) { acc, next -> if (next.second > acc.second) next else acc }
        return if (best.second <= 0.0) ViewerType.BALANCED else best.first
    }

    private const val PERCENT = 100.0
    private const val NIGHT = 0.40
    private const val COMMUTE = 0.35
    private const val MEAL = 0.30
    private const val DOMINO = 0.40
    private const val GAP = 0.50
    private const val GAP_MEDIAN_MAX = 8.0
    private const val MORNING = 0.25
}
