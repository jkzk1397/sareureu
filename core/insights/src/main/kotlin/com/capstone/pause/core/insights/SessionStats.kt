package com.capstone.pause.core.insights

import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.EntryPoint
import com.capstone.pause.core.model.ExitPoint
import com.capstone.pause.core.model.NudgeEvent
import com.capstone.pause.core.model.ShortformSession
import java.time.Instant

/** 세션 길이 구간(분): 0–5, 5–10, 10–20, 20–40, 40+. */
enum class LengthBucket(val fromMin: Double, val toMin: Double) {
    UNDER_5(0.0, 5.0),
    FROM_5_TO_10(5.0, 10.0),
    FROM_10_TO_20(10.0, 20.0),
    FROM_20_TO_40(20.0, 40.0),
    OVER_40(40.0, Double.MAX_VALUE),
    ;

    companion object {
        /** 길이(분)가 들어갈 구간. */
        fun of(minutes: Double): LengthBucket = entries.first { minutes < it.toMin }
    }
}

/** 맥락 하나의 합계. */
data class ContextTotal(val slot: ContextSlot, val minutes: Int, val sessions: Int, val medianMinutes: Double)

/** 진입 → 앱 → 이탈 흐름 하나. 키는 [FlowKeys] 규칙. */
data class Flow(val entry: String, val app: String, val exit: String, val count: Int, val medianMinutes: Double)

/** 흐름·관찰에 쓰는 진입·이탈 키(화면 문구가 아니라 코드). */
object FlowKeys {
    const val UNLOCK = "UNLOCK"
    const val HOME = "HOME"
    const val SHORTFORM = "SHORTFORM"
    const val SCREEN_OFF = "SCREEN_OFF"
    const val NUDGE_STOP = "NUDGE_STOP"
    const val UNKNOWN = "UNKNOWN"

    /** 진입 키: 잠금 해제·홈·다른 숏폼·앱 종류 이름(MESSENGER 등)·알 수 없음. */
    fun entry(entry: EntryPoint): String = when (entry) {
        EntryPoint.Unlock -> UNLOCK
        EntryPoint.Home -> HOME
        is EntryPoint.FromApp -> entry.kind.name
        is EntryPoint.FromShortform -> SHORTFORM
        EntryPoint.Unknown -> UNKNOWN
    }

    /** 이탈 키. 진행 중이면 알 수 없음. */
    fun exit(exit: ExitPoint?): String = when (exit) {
        ExitPoint.ScreenOff -> SCREEN_OFF
        ExitPoint.Home -> HOME
        is ExitPoint.ToApp -> exit.kind.name
        ExitPoint.AfterNudgeStop -> NUDGE_STOP
        ExitPoint.Unknown, null -> UNKNOWN
    }
}

/** 세션 목록 통계(순수 함수). */
object SessionStats {
    /** 중앙값. 비어 있으면 0. */
    fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2
    }

    /** 세션 길이(분) 목록. */
    fun lengths(sessions: List<ShortformSession>, now: Instant): List<Double> =
        sessions.map { ShortformMinutes.lengthMinutes(it, now) }

    /** 길이 구간별 비율(0~1, 구간 순서대로, 세션이 없으면 모두 0). */
    fun lengthDistribution(sessions: List<ShortformSession>, now: Instant): Map<LengthBucket, Double> {
        val counts = lengths(sessions, now).groupingBy { LengthBucket.of(it) }.eachCount()
        val n = sessions.size
        return LengthBucket.entries.associateWith { if (n == 0) 0.0 else (counts[it] ?: 0).toDouble() / n }
    }

    /** 맥락별 합계(분이 많은 순, 0분 제외). */
    fun contextTotals(sessions: List<ShortformSession>, now: Instant): List<ContextTotal> = sessions
        .groupBy { it.context }
        .map { (slot, list) ->
            val lens = lengths(list, now)
            ContextTotal(slot, Math.round(lens.sum()).toInt(), list.size, median(lens))
        }
        .filter { it.minutes > 0 }
        .sortedWith(compareByDescending<ContextTotal> { it.minutes }.thenBy { it.slot.ordinal })

    /** 상위 흐름(많은 순). 앱은 첫 구간의 패키지. */
    fun topFlows(sessions: List<ShortformSession>, now: Instant, limit: Int = 5): List<Flow> = sessions
        .groupBy {
            Triple(
                FlowKeys.entry(it.entry),
                it.segments.firstOrNull()?.packageName ?: FlowKeys.UNKNOWN,
                FlowKeys.exit(it.exit),
            )
        }
        .map { (key, list) -> Flow(key.first, key.second, key.third, list.size, median(lengths(list, now))) }
        .sortedWith(compareByDescending<Flow> { it.count }.thenByDescending { it.medianMinutes })
        .take(limit)

    /** 진입 키별 세션 수(많은 순). */
    fun entryCounts(sessions: List<ShortformSession>): List<Pair<String, Int>> =
        sessions.groupingBy { FlowKeys.entry(it.entry) }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }

    /** 이탈 키별 세션 수(많은 순). */
    fun exitCounts(sessions: List<ShortformSession>): List<Pair<String, Int>> =
        sessions.groupingBy { FlowKeys.exit(it.exit) }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }
}

/** 개입 결과: 카드가 뜬 세션 수, 그중 멈춘 세션 수, 연장 수. */
data class NudgeOutcome(val shownSessions: Int, val stoppedSessions: Int, val extensions: Int) {
    /** 멈춘 비율(PRD 9절). 카드가 없었으면 null. */
    val stopRate: Double? get() = if (shownSessions == 0) null else stoppedSessions.toDouble() / shownSessions
}

/** stopRate: 카드가 뜬 세션 중 '그만'으로 멈춘 비율. */
object StopRate {
    /** [nudges]의 세션 ID 기준으로 [sessions]에서 센다. */
    fun compute(sessions: List<ShortformSession>, nudges: List<NudgeEvent>): NudgeOutcome {
        val shownIds = nudges.map { it.sessionId }.toSet()
        val shown = sessions.filter { it.id in shownIds }
        return NudgeOutcome(
            shownSessions = shown.size,
            stoppedSessions = shown.count { it.stoppedByNudge },
            extensions = nudges.count { it.response == com.capstone.pause.core.model.NudgeResponse.EXTEND },
        )
    }
}
