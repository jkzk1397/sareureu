package com.capstone.pause.core.ai.coach

import android.content.Context
import com.capstone.pause.core.ai.engine.coach.CoachTools
import com.capstone.pause.core.ai.engine.coach.ContextBreakdownResult
import com.capstone.pause.core.ai.engine.coach.ContextItem
import com.capstone.pause.core.ai.engine.coach.FlowItem
import com.capstone.pause.core.ai.engine.coach.FlowsResult
import com.capstone.pause.core.ai.engine.coach.PeerBaselineResult
import com.capstone.pause.core.ai.engine.coach.Period
import com.capstone.pause.core.ai.engine.coach.PlanItem
import com.capstone.pause.core.ai.engine.coach.PlansResult
import com.capstone.pause.core.ai.engine.coach.RiskItem
import com.capstone.pause.core.ai.engine.coach.RiskWindowsResult
import com.capstone.pause.core.ai.engine.coach.SessionItem
import com.capstone.pause.core.ai.engine.coach.SessionQuery
import com.capstone.pause.core.ai.engine.coach.SessionsResult
import com.capstone.pause.core.ai.engine.coach.StrategiesResult
import com.capstone.pause.core.ai.engine.coach.StrategyItem
import com.capstone.pause.core.ai.engine.coach.SummaryResult
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.common.DayBoundary
import com.capstone.pause.core.data.plan.PlanRepository
import com.capstone.pause.core.data.profile.ProfileRepository
import com.capstone.pause.core.data.session.SessionRepository
import com.capstone.pause.core.data.strategy.StrategyRepository
import com.capstone.pause.core.insights.FlowKeys
import com.capstone.pause.core.insights.RiskWindows
import com.capstone.pause.core.insights.SessionStats
import com.capstone.pause.core.insights.ShortformMinutes
import com.capstone.pause.core.insights.StopRate
import com.capstone.pause.core.model.ContextSlot
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 코치 도구 구현(모두 읽기 전용). 숫자는 전부 `core:insights` 함수로 센다 — AI는 이 결과만 근거로 쓴다.
 * 기간: 오늘 / 이번 주(오늘 포함 7일) / 지난주(그 앞 7일) / 최근 4주(오늘 포함 28일). 날짜 경계는 새벽 4시.
 */
@Singleton
internal class RepositoryCoachTools @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionRepository: SessionRepository,
    private val planRepository: PlanRepository,
    private val profileRepository: ProfileRepository,
    private val strategyRepository: StrategyRepository,
    private val clock: AppClock,
) : CoachTools {

    override suspend fun getSummary(period: Period): SummaryResult {
        val w = window(period)
        val sessions = sessions(w.from, w.to)
        val nudges = sessionRepository.observeNudges(w.from, w.to).first()
        val total = ShortformMinutes.between(sessions, w.from, w.to, w.now)
        val avg = (total.toDouble() / w.days).roundToInt()
        val baseline = baselinePerDay(w.now)
        val outcome = StopRate.compute(sessions, nudges)
        return SummaryResult(
            key = "summary_${period.name}",
            period = period,
            totalMinutes = total,
            avgMinPerDay = avg,
            baselineMinPerDay = baseline?.roundToInt(),
            reductionPercent = baseline?.takeIf { it > 0 }?.let { ((1 - avg / it) * PERCENT).roundToInt() },
            // TODO(BUILD_REPORT#3): 감지가 살아 있던 구간을 기록하면 실제 기록률로.
            coveragePercent = null,
            sessionCount = sessions.size,
            stopRatePercent = outcome.stopRate?.let { (it * PERCENT).roundToInt() },
        )
    }

    override suspend fun getContextBreakdown(period: Period): ContextBreakdownResult {
        val w = window(period)
        val items = SessionStats.contextTotals(sessions(w.from, w.to), w.now).map {
            ContextItem(
                "ctx_${it.slot.name}_${period.name}",
                it.slot,
                it.minutes,
                it.sessions,
                it.medianMinutes.roundToInt(),
            )
        }
        return ContextBreakdownResult(period, items)
    }

    override suspend fun getSessions(query: SessionQuery): SessionsResult {
        val w = window(query.period)
        val app = query.app?.uppercase()
        val minMinutes = query.minMinutes
        val list = sessions(w.from, w.to)
            .filter { query.context == null || it.context == query.context }
            .filter { s -> app == null || appKey(s.segments.firstOrNull()?.packageName) == app }
            .map { it to ShortformMinutes.lengthMinutes(it, w.now) }
            .filter { (_, minutes) -> minMinutes == null || minutes >= minMinutes }
            .sortedByDescending { it.first.start }
            .take(query.limit.coerceIn(1, SessionQuery.MAX_SESSIONS))
        return SessionsResult(
            list.map { (s, minutes) ->
                val local = ZonedDateTime.ofInstant(s.start, clock.zone)
                SessionItem(
                    id = s.id,
                    dayOfWeek = DayBoundary.logicalDate(s.start, clock.zone).dayOfWeek.name.take(DOW_CHARS),
                    startHour = local.hour,
                    startMinute = local.minute,
                    minutes = minutes.roundToInt(),
                    app = appKey(s.segments.firstOrNull()?.packageName),
                    context = s.context,
                    entry = FlowKeys.entry(s.entry),
                    exit = s.exit?.let { FlowKeys.exit(it) },
                    maxLevel = s.maxFrictionLevel,
                )
            },
        )
    }

    override suspend fun getFlows(period: Period, limit: Int): FlowsResult {
        val w = window(period)
        val flows = SessionStats.topFlows(sessions(w.from, w.to), w.now, limit.coerceIn(1, MAX_FLOWS))
        return FlowsResult(
            period,
            flows.mapIndexed { i, f ->
                FlowItem(
                    "flow_${period.name}_${i + 1}",
                    f.entry,
                    appKey(f.app),
                    f.exit,
                    f.count,
                    f.medianMinutes.roundToInt(),
                )
            },
        )
    }

    override suspend fun getRiskWindows(): RiskWindowsResult {
        val now = clock.now()
        val today = DayBoundary.logicalDate(now, clock.zone)
        val from = DayBoundary.startOf(today.minusDays(RISK_DAYS), clock.zone)
        val windows = RiskWindows.compute(
            sessions(from, DayBoundary.startOf(today, clock.zone)),
            today,
            clock.zone,
            now,
        )
        return RiskWindowsResult(
            windows.map {
                val dow = it.dayOfWeek.name.take(DOW_CHARS)
                RiskItem("risk_${dow}_${it.hour}", dow, it.hour, it.averageMinutes.roundToInt())
            },
        )
    }

    override suspend fun getPlans(): PlansResult = PlansResult(
        planRepository.observeActivePlans().first().map {
            PlanItem("plan_${it.id}", it.context, it.ifText, it.thenText)
        },
    )

    override suspend fun getPeerBaseline(): PeerBaselineResult {
        val profile = profileRepository.profile.first()
        val w = window(Period.THIS_WEEK)
        val my = (
            ShortformMinutes.between(
                sessions(w.from, w.to),
                w.from,
                w.to,
                w.now,
            ).toDouble() / w.days
            ).roundToInt()
        val cohort = runCatching { loadCohort() }.getOrNull()
        val group = cohort?.groups?.firstOrNull {
            it.ageBand == profile?.ageBand?.name && it.occupation == profile.occupation.name
        }
        return PeerBaselineResult(
            key = "peer",
            ageBand = profile?.ageBand?.name,
            occupation = profile?.occupation?.name,
            peerMedianMinPerDay = group?.p50,
            myAvgMinPerDay = my,
            source = if (cohort?.isDemo == true) SOURCE_DEMO else SOURCE_NONE,
            isDemo = cohort?.isDemo ?: true,
        )
    }

    override suspend fun suggestStrategies(context: ContextSlot?): StrategiesResult = StrategiesResult(
        strategyRepository.strategies()
            .filter { context == null || it.context == context || it.context == null }
            .take(MAX_STRATEGIES)
            .map { StrategyItem("strategy_${it.id}", it.context, it.ifText, it.thenText) },
    )

    private suspend fun sessions(from: java.time.Instant, to: java.time.Instant) =
        sessionRepository.observeSessions(from, to).first()

    /** 처음 기록된 날부터 7일 평균(기준선). 기록이 7일 미만이면 null. */
    private suspend fun baselinePerDay(now: java.time.Instant): Double? {
        val today = DayBoundary.logicalDate(now, clock.zone)
        val from = DayBoundary.startOf(today.minusDays(BASELINE_LOOKBACK), clock.zone)
        val all = sessions(from, DayBoundary.startOf(today, clock.zone))
        val days = (BASELINE_LOOKBACK downTo 1).map { back ->
            com.capstone.pause.core.insights.DayStat(
                today.minusDays(back),
                ShortformMinutes.onDay(all, today.minusDays(back), clock.zone, now),
                coverage = 1.0,
            )
        }
        val first = days.indexOfFirst { it.minutes > 0 }
        return if (first < 0) null else com.capstone.pause.core.insights.Baseline.compute(days.drop(first))
    }

    private data class Window(
        val from: java.time.Instant,
        val to: java.time.Instant,
        val now: java.time.Instant,
        val days: Int,
    )

    private fun window(period: Period): Window {
        val now = clock.now()
        val zone = clock.zone
        val today = DayBoundary.logicalDate(now, zone)
        val end = DayBoundary.endOf(today, zone)
        return when (period) {
            Period.TODAY -> Window(DayBoundary.startOf(today, zone), end, now, 1)

            Period.THIS_WEEK -> Window(DayBoundary.startOf(today.minusDays(WEEK - 1), zone), end, now, WEEK.toInt())

            Period.LAST_WEEK -> Window(
                DayBoundary.startOf(today.minusDays(2 * WEEK - 1), zone),
                DayBoundary.startOf(today.minusDays(WEEK - 1), zone),
                now,
                WEEK.toInt(),
            )

            Period.LAST_4_WEEKS -> Window(
                DayBoundary.startOf(today.minusDays(MONTH - 1), zone),
                end,
                now,
                MONTH.toInt(),
            )
        }
    }

    private fun loadCohort(): CohortFile = context.assets.open(COHORT_ASSET).bufferedReader().use {
        json.decodeFromString(CohortFile.serializer(), it.readText())
    }

    @Serializable
    private data class CohortFile(val isDemo: Boolean = true, val groups: List<CohortGroup> = emptyList())

    @Serializable
    private data class CohortGroup(val ageBand: String, val occupation: String, val n: Int, val p50: Int, val p75: Int)

    private companion object {
        const val PERCENT = 100.0
        const val DOW_CHARS = 3
        const val MAX_FLOWS = 5
        const val MAX_STRATEGIES = 5
        const val RISK_DAYS = 14L
        const val BASELINE_LOOKBACK = 28L
        const val WEEK = 7L
        const val MONTH = 28L
        const val COHORT_ASSET = "demo/cohort.json"
        const val SOURCE_DEMO = "DEMO_COHORT"
        const val SOURCE_NONE = "NONE"
        val json = Json { ignoreUnknownKeys = true }

        /** 패키지 → 앱 키(화면 문구가 아니라 코드). */
        fun appKey(packageName: String?): String = when {
            packageName == null -> "UNKNOWN"
            packageName.startsWith("com.google.android.youtube") -> "YOUTUBE"
            packageName.startsWith("com.instagram") -> "INSTAGRAM"
            packageName.startsWith("com.ss.android.ugc") || packageName.startsWith("com.zhiliaoapp") -> "TIKTOK"
            else -> "OTHER"
        }
    }
}
