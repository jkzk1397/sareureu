package com.capstone.pause.core.insights

import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.NudgeEvent
import com.capstone.pause.core.model.ShortformSession
import java.time.Instant

/** 관찰 종류. ID는 종류마다 고정(o1..o12)이라 AI 근거 칩이 같은 뜻을 가리킨다. */
enum class ObservationKind(val id: String) {
    WEEK_TOTAL("o1"),
    REDUCTION("o2"),
    TOP_CONTEXT("o3"),
    SECOND_CONTEXT("o4"),
    NIGHT_SHARE("o5"),
    RISK_WINDOW("o6"),
    TOP_ENTRY("o7"),
    TOP_FLOW("o8"),
    SESSION_LENGTH("o9"),
    NUDGES("o10"),
    COVERAGE("o11"),
    TOP_CONTEXT_ENTRY("o12"),
}

/**
 * 관찰(코드가 센 것). 숫자는 [numbers], 범주는 [labels](맥락·앱·진입 키 등 코드 값).
 * 문장은 화면(strings.xml)이나 AI 입력 렌더러가 만든다. 숫자는 여기서만 나온다.
 */
data class Observation(
    val kind: ObservationKind,
    val numbers: Map<String, Double>,
    val labels: Map<String, String> = emptyMap(),
) {
    /** 관찰 ID(o1..o12). */
    val id: String get() = kind.id
}

/** 주간 관찰 계산 입력. [sessions]·[nudges]는 그 주(논리적 날짜 [days]일)만. */
data class WeekInput(
    val sessions: List<ShortformSession>,
    val nudges: List<NudgeEvent>,
    val days: Int,
    val now: Instant,
    val baselineMinPerDay: Double?,
    val coverage: Double?,
    val riskWindows: List<RiskWindow>,
)

/** 주간 관찰 묶음 + 리포트가 그리는 계산값. */
data class WeeklyObservations(
    val observations: List<Observation>,
    val totalMinutes: Int,
    val avgMinPerDay: Double,
    val reductionRate: Double?,
    val contextTotals: List<ContextTotal>,
    val lengthDistribution: Map<LengthBucket, Double>,
    val flows: List<Flow>,
    val entryCounts: List<Pair<String, Int>>,
    val exitCounts: List<Pair<String, Int>>,
    val nudges: NudgeOutcome,
    val viewerType: ViewerType,
    val sessionCount: Int,
) {
    /** ID로 찾기. */
    operator fun get(id: String): Observation? = observations.firstOrNull { it.id == id }

    companion object {
        /** AI를 부르는 최소 관찰 수(05 문서 5.1절). */
        const val MIN_FOR_AI = 3

        /** AI를 부르는 최소 기록률. */
        const val MIN_COVERAGE_FOR_AI = 0.5

        /** 관찰 계산. 데이터가 없는 관찰은 빠진다. */
        fun compute(input: WeekInput): WeeklyObservations {
            val now = input.now
            val sessions = input.sessions
            val lengths = SessionStats.lengths(sessions, now)
            val total = lengths.sum()
            val avg = if (input.days > 0) total / input.days else 0.0
            val reduction = ReductionRate.compute(avg, input.baselineMinPerDay)
            val contexts = SessionStats.contextTotals(sessions, now)
            val flows = SessionStats.topFlows(sessions, now)
            val entries = SessionStats.entryCounts(sessions)
            val outcome = StopRate.compute(sessions, input.nudges)
            val obs = mutableListOf<Observation>()
            if (sessions.isNotEmpty()) {
                obs += Observation(
                    ObservationKind.WEEK_TOTAL,
                    mapOf(
                        "avgMinPerDay" to avg,
                        "totalMin" to total,
                        "sessions" to sessions.size.toDouble(),
                        "days" to input.days.toDouble(),
                    ),
                )
            }
            if (reduction != null && input.baselineMinPerDay != null) {
                obs += Observation(
                    ObservationKind.REDUCTION,
                    mapOf(
                        "baselineMinPerDay" to input.baselineMinPerDay,
                        "avgMinPerDay" to avg,
                        "reductionRate" to reduction,
                    ),
                )
            }
            contexts.getOrNull(0)?.let { obs += contextObservation(ObservationKind.TOP_CONTEXT, it, total) }
            contexts.getOrNull(1)?.let { obs += contextObservation(ObservationKind.SECOND_CONTEXT, it, total) }
            if (total > 0) {
                val night = contexts.filter { it.slot == ContextSlot.PRE_SLEEP || it.slot == ContextSlot.LATE_NIGHT }
                val nightMin = night.sumOf { it.minutes }.toDouble()
                if (nightMin > 0) {
                    obs +=
                        Observation(
                            ObservationKind.NIGHT_SHARE,
                            mapOf(
                                "minutes" to nightMin,
                                "share" to nightMin / total,
                            ),
                        )
                }
            }
            input.riskWindows.firstOrNull()?.let { w ->
                obs += Observation(
                    ObservationKind.RISK_WINDOW,
                    mapOf(
                        "hour" to w.hour.toDouble(),
                        "avgMinutes" to w.averageMinutes,
                        "daysSeen" to w.daysSeen.toDouble(),
                    ),
                    mapOf("dayOfWeek" to w.dayOfWeek.name),
                )
            }
            entries.firstOrNull()?.let { (key, count) ->
                obs += Observation(
                    ObservationKind.TOP_ENTRY,
                    mapOf("count" to count.toDouble(), "share" to count.toDouble() / sessions.size),
                    mapOf("entry" to key),
                )
            }
            flows.firstOrNull()?.let { f ->
                obs += Observation(
                    ObservationKind.TOP_FLOW,
                    mapOf("count" to f.count.toDouble(), "medianMin" to f.medianMinutes),
                    mapOf("entry" to f.entry, "app" to f.app, "exit" to f.exit),
                )
            }
            if (sessions.isNotEmpty()) {
                val long = lengths.count { it >= LONG_SESSION_MIN }
                obs += Observation(
                    ObservationKind.SESSION_LENGTH,
                    mapOf(
                        "medianMin" to SessionStats.median(lengths),
                        "longCount" to long.toDouble(),
                        "longShare" to long.toDouble() / sessions.size,
                    ),
                )
            }
            outcome.stopRate?.let { rate ->
                obs += Observation(
                    ObservationKind.NUDGES,
                    mapOf(
                        "shown" to outcome.shownSessions.toDouble(),
                        "stopped" to outcome.stoppedSessions.toDouble(),
                        "stopRate" to rate,
                        "extensions" to outcome.extensions.toDouble(),
                    ),
                )
            }
            input.coverage?.let { obs += Observation(ObservationKind.COVERAGE, mapOf("coverage" to it)) }
            contexts.firstOrNull()?.let { top ->
                val inTop = sessions.filter { it.context == top.slot }
                SessionStats.entryCounts(inTop).firstOrNull()?.let { (key, count) ->
                    obs += Observation(
                        ObservationKind.TOP_CONTEXT_ENTRY,
                        mapOf("count" to count.toDouble(), "sessions" to inTop.size.toDouble()),
                        mapOf("context" to top.slot.name, "entry" to key),
                    )
                }
            }
            return WeeklyObservations(
                observations = obs.sortedBy { it.kind.ordinal },
                totalMinutes = Math.round(total).toInt(),
                avgMinPerDay = avg,
                reductionRate = reduction,
                contextTotals = contexts,
                lengthDistribution = SessionStats.lengthDistribution(sessions, now),
                flows = flows,
                entryCounts = entries,
                exitCounts = SessionStats.exitCounts(sessions),
                nudges = outcome,
                viewerType = ViewerTypeClassifier.classify(sessions, input.days, now),
                sessionCount = sessions.size,
            )
        }

        private fun contextObservation(kind: ObservationKind, c: ContextTotal, total: Double) = Observation(
            kind,
            mapOf(
                "minutes" to c.minutes.toDouble(),
                "share" to if (total > 0) c.minutes / total else 0.0,
                "sessions" to c.sessions.toDouble(),
                "medianMin" to c.medianMinutes,
            ),
            mapOf("context" to c.slot.name),
        )

        private const val LONG_SESSION_MIN = 20.0
    }

    /** AI를 불러도 되는지(관찰 3개 이상, 기록률 50% 이상). */
    val enoughForAi: Boolean
        get() = observations.size >= MIN_FOR_AI &&
            (this[ObservationKind.COVERAGE.id]?.numbers?.get("coverage") ?: 1.0) >= MIN_COVERAGE_FOR_AI
}
