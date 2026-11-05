package com.capstone.pause.core.data.insight

import android.content.Context
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.common.AppLog
import com.capstone.pause.core.common.DayBoundary
import com.capstone.pause.core.common.Dispatcher
import com.capstone.pause.core.common.PauseDispatchers
import com.capstone.pause.core.data.profile.ProfileRepository
import com.capstone.pause.core.data.session.SessionRepository
import com.capstone.pause.core.data.settings.SettingsRepository
import com.capstone.pause.core.insights.Baseline
import com.capstone.pause.core.insights.DayStat
import com.capstone.pause.core.insights.LengthBucket
import com.capstone.pause.core.insights.RiskWindows
import com.capstone.pause.core.insights.SessionStats
import com.capstone.pause.core.insights.ShortformMinutes
import com.capstone.pause.core.insights.WeekCharts
import com.capstone.pause.core.model.Profile
import com.capstone.pause.core.model.ShortformSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 리포트 차트 값 저장소. */
interface WeekChartRepository {
    /** 지난 7일 차트 값. 기록이 하나도 없으면 null. */
    fun observeWeek(): Flow<WeekChartData?>
}

/** `assets/demo/cohort.json` 모양(시연용 가상 분포). */
@Serializable
internal data class CohortFile(val isDemo: Boolean = true, val groups: List<CohortGroup> = emptyList())

/** 연령대·신분 한 그룹. */
@Serializable
internal data class CohortGroup(val ageBand: String, val occupation: String, val n: Int, val p50: Int, val p75: Int)

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
internal class DefaultWeekChartRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val profileRepository: ProfileRepository,
    private val clock: AppClock,
    private val log: AppLog,
    @Dispatcher(PauseDispatchers.DEFAULT) private val dispatcher: CoroutineDispatcher,
) : WeekChartRepository {

    private val cohort: CohortFile? by lazy {
        runCatching {
            context.assets.open(COHORT_ASSET).bufferedReader().use { json.decodeFromString<CohortFile>(it.readText()) }
        }.onFailure { log.w(TAG, "cohort asset missing", it) }.getOrNull()
    }

    override fun observeWeek(): Flow<WeekChartData?> = flowOf(clock.now()).flatMapLatest { now ->
        val zone = clock.zone
        val today = DayBoundary.logicalDate(now, zone)
        // 기준선을 찾으려고 리포트 주 앞 28일까지 읽는다(AI 주간 분석과 같은 규칙).
        val from = DayBoundary.startOf(today.minusDays(WEEK + BASELINE_LOOKBACK), zone)
        val to = DayBoundary.startOf(today, zone)
        combine(
            sessionRepository.observeSessions(from, to),
            settingsRepository.settings,
            profileRepository.profile,
        ) { sessions, settings, profile ->
            compute(sessions, today, now, settings.demoMode, profile)
        }
    }.flowOn(dispatcher)

    private fun compute(
        all: List<ShortformSession>,
        today: LocalDate,
        now: Instant,
        isDemo: Boolean,
        profile: Profile?,
    ): WeekChartData? {
        val zone = clock.zone
        val dates = (WEEK downTo 1L).map { today.minusDays(it) }
        val weekStart = DayBoundary.startOf(dates.first(), zone)
        val weekEnd = DayBoundary.startOf(today, zone)
        val week = all.filter { !it.start.isBefore(weekStart) && it.start.isBefore(weekEnd) }
        if (week.isEmpty()) return null
        val clockBins = WeekCharts.weekClock(week, dates, zone, now)
        val heat = WeekCharts.heatmap(week, dates, zone, now)
        val risks = RiskWindows.compute(all, today, zone, now)
        val total = clockBins.totalMinutes
        val flow = WeekCharts.flowChart(week)
        val avg = total.toDouble() / WEEK
        return WeekChartData(
            weekStart = dates.first(),
            weekEndInclusive = dates.last(),
            isDemo = isDemo,
            totalMinutes = total,
            sessionCount = week.size,
            minutesByHour = clockBins.minutesByHour,
            riskHours = risks.map { it.hour..it.hour }.sortedBy { it.first },
            heatmapDays = heat.days,
            heatmap = heat.cells,
            riskCells = WeekCharts.riskCells(risks),
            peak = WeekCharts.peakSpan(clockBins)?.let { PeakSummary(it.startHour, it.minutes) },
            contexts = SessionStats.contextTotals(week, now).let { totals ->
                val top = totals.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
                totals.map {
                    ContextShare(
                        slot = it.slot,
                        minutes = it.minutes,
                        share = if (total == 0) 0.0 else it.minutes.toDouble() / total,
                        barFraction = it.minutes.toFloat() / top,
                    )
                }
            },
            flow = FlowSummary(flow.entries, flow.apps, flow.exits, flow.entryToApp, flow.appToExit),
            longestEntry = WeekCharts.longestEntry(week, now)?.let {
                EntryLengthSummary(it.key, it.sessions, it.medianMinutes.roundToInt())
            },
            lengthShares = SessionStats.lengthDistribution(week, now).let { dist ->
                LengthBucket.entries.map { dist[it] ?: 0.0 }
            },
            peer = peer(avg, baseline(all, dates.first(), now), profile, isDemo),
        )
    }

    /** 리포트 주 앞 28일 중 처음 기록된 7일의 하루 평균. */
    private fun baseline(all: List<ShortformSession>, weekStart: LocalDate, now: Instant): Double? {
        val days = (BASELINE_LOOKBACK downTo 1L).map { weekStart.minusDays(it) }
            .map { DayStat(it, ShortformMinutes.onDay(all, it, clock.zone, now), coverage = 1.0) }
        val first = days.indexOfFirst { it.minutes > 0 }
        return if (first < 0) null else Baseline.compute(days.drop(first))
    }

    private fun peer(avg: Double, baseline: Double?, profile: Profile?, isDemo: Boolean): PeerSummary? {
        val p = profile ?: return null
        val file = cohort ?: return null
        val group = file.groups.firstOrNull { it.ageBand == p.ageBand.name && it.occupation == p.occupation.name }
            ?.takeIf { it.n >= MIN_GROUP } ?: return null
        val largest = listOfNotNull(avg.roundToInt(), group.p75, baseline?.roundToInt()).max()
        val scale = maxOf(MIN_SCALE, ((largest + HOUR - 1) / HOUR + 1) * HOUR)
        return PeerSummary(
            myMinutesPerDay = avg.roundToInt(),
            peerMedian = group.p50,
            peerP75 = group.p75,
            myBaseline = baseline?.roundToInt(),
            groupSize = group.n,
            isDemo = file.isDemo || isDemo,
            scaleMaxMinutes = scale,
        )
    }

    private companion object {
        const val TAG = "WeekChart"
        const val COHORT_ASSET = "demo/cohort.json"
        const val WEEK = 7L
        const val BASELINE_LOOKBACK = 28L
        const val MIN_GROUP = 10
        const val HOUR = 60
        const val MIN_SCALE = 180
        val json = Json { ignoreUnknownKeys = true }
    }
}
