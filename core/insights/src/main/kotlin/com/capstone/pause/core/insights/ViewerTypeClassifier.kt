package com.capstone.pause.core.insights

import com.capstone.pause.core.model.AppKind
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.EntryPoint
import com.capstone.pause.core.model.ShortformSession
import java.time.Instant

/** 숏폼 유형(05 문서 5.6절). 판정은 코드, AI는 한 줄 설명만. */
enum class ViewerType { NIGHT_OWL, COMMUTER, MEAL_BUDDY, DOMINO, GAP_FILLER, MORNING, BALANCED }

/** 05 문서 5.6절 표를 우선순위대로 적용한다. */
object ViewerTypeClassifier {
    /** 하루 평균이 이 값 이하이면 균형형. */
    const val BALANCED_MAX_DAILY_MIN = 30.0

    /** [sessions]는 그 주의 세션, [days]는 하루 평균을 낼 날 수. */
    fun classify(sessions: List<ShortformSession>, days: Int, now: Instant): ViewerType {
        // 같은 값의 세션이 여러 개일 수 있어 Map이 아니라 목록으로 센다.
        val lengths = sessions.map { it to ShortformMinutes.lengthMinutes(it, now) }
        val total = lengths.sumOf { it.second }
        if (total <= 0.0 || days <= 0 || total / days <= BALANCED_MAX_DAILY_MIN) return ViewerType.BALANCED
        fun share(vararg slots: ContextSlot) = lengths.filter { it.first.context in slots }.sumOf { it.second } / total
        val fromChat = sessions.count { e ->
            val entry = e.entry
            entry is EntryPoint.FromApp && (entry.kind == AppKind.MESSENGER || entry.kind == AppKind.SOCIAL)
        }.toDouble() / sessions.size
        val gapMedian = SessionStats.median(
            lengths.filter {
                it.first.context == ContextSlot.CLASS_WORK || it.first.context == ContextSlot.BREAK
            }.map { it.second },
        )
        return when {
            share(ContextSlot.PRE_SLEEP, ContextSlot.LATE_NIGHT) >= NIGHT -> ViewerType.NIGHT_OWL

            share(ContextSlot.COMMUTE) >= COMMUTE -> ViewerType.COMMUTER

            share(ContextSlot.MEAL) >= MEAL -> ViewerType.MEAL_BUDDY

            fromChat >= DOMINO -> ViewerType.DOMINO

            share(
                ContextSlot.CLASS_WORK,
                ContextSlot.BREAK,
            ) >= GAP && gapMedian <= GAP_MEDIAN_MAX -> ViewerType.GAP_FILLER

            share(ContextSlot.WAKE_UP) >= MORNING -> ViewerType.MORNING

            else -> ViewerType.BALANCED
        }
    }

    private const val NIGHT = 0.40
    private const val COMMUTE = 0.35
    private const val MEAL = 0.30
    private const val DOMINO = 0.40
    private const val GAP = 0.50
    private const val GAP_MEDIAN_MAX = 8.0
    private const val MORNING = 0.25
}
