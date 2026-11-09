package com.capstone.pause.core.data.insight

import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.common.DayBoundary
import com.capstone.pause.core.data.monitoring.LiveSessionSource
import com.capstone.pause.core.data.monitoring.mergeLiveSession
import com.capstone.pause.core.data.profile.ProfileRepository
import com.capstone.pause.core.data.session.SessionRepository
import com.capstone.pause.core.data.settings.AppSettings
import com.capstone.pause.core.data.settings.SettingsRepository
import com.capstone.pause.core.insights.Baseline
import com.capstone.pause.core.insights.ClockBins
import com.capstone.pause.core.insights.DayStat
import com.capstone.pause.core.insights.MeltStageCalculator
import com.capstone.pause.core.insights.RiskWindows
import com.capstone.pause.core.insights.ShortformMinutes
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.NudgeEvent
import com.capstone.pause.core.model.ShortformSession
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/** 계산된 값 저장소. 숫자는 여기(= core:insights)서만 나온다. */
interface InsightRepository {
    /** 오늘 탭 요약. 저장된 세션 + 지금 보고 있는 세션으로 계산하고, 보는 동안에도 30초마다 다시 센다. */
    fun observeToday(): Flow<TodaySummary>
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
internal class DefaultInsightRepository @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val profileRepository: ProfileRepository,
    private val liveSessionSource: LiveSessionSource,
    private val clock: AppClock,
) : InsightRepository {

    private val minuteTicks: Flow<Instant> = flow {
        while (true) {
            emit(clock.now())
            delay(TICK_MS)
        }
    }

    override fun observeToday(): Flow<TodaySummary> = minuteTicks.flatMapLatest { now ->
        val today = DayBoundary.logicalDate(now, clock.zone)
        val from = DayBoundary.startOf(today.minusDays(HISTORY_DAYS), clock.zone)
        val to = DayBoundary.endOf(today, clock.zone)
        combine(
            sessionRepository.observeSessions(from, to),
            sessionRepository.observeNudges(DayBoundary.startOf(today, clock.zone), to),
            settingsRepository.settings,
            profileRepository.profile,
            liveSessionSource.current,
        ) { sessions, nudges, settings, profile, live ->
            val source = if (settings.demoMode) DataSource.DEMO else DataSource.REAL
            // 진행 중 세션은 아직 저장 전이라 따로 더한다(그래야 보는 중인 시간이 오늘 합계에 바로 들어간다).
            val all = mergeLiveSession(sessions, live, source)
            val goal = profile?.dailyGoalMinutes?.toDouble() ?: DEFAULT_DAILY_GOAL_MIN
            // 틱 시각이 아니라 지금 시각으로 센다(보는 중 세션이 틱 사이에도 늘어나게).
            summarize(maxOf(now, clock.now()), all, nudges, settings, goal)
        }
    }

    private fun summarize(
        now: Instant,
        sessions: List<ShortformSession>,
        todayNudges: List<NudgeEvent>,
        settings: AppSettings,
        dailyGoalMinutes: Double,
    ): TodaySummary {
        val zone = clock.zone
        val today = DayBoundary.logicalDate(now, zone)
        val bins = ClockBins.compute(
            sessions,
            DayBoundary.startOf(today, zone),
            DayBoundary.endOf(today, zone),
            now,
            zone,
        )
        // TODO(BUILD_REPORT#3): 감지가 살아 있던 구간 기록이 쌓이면 Coverage.compute로 바꾼다.
        // 지금은 화면 켜짐·감지 생존 구간을 저장하지 않아 기록이 있는 날을 100%로 본다.
        val pastDays = (HISTORY_DAYS downTo 1).map { back ->
            val date = today.minusDays(back)
            DayStat(date, ShortformMinutes.onDay(sessions, date, zone, now), coverage = 1.0)
        }
        val firstRecorded = pastDays.indexOfFirst { it.minutes > 0 }
        val baseline = if (firstRecorded < 0) null else Baseline.compute(pastDays.drop(firstRecorded))
        // 공정하게 비교하려고 어제도 '지금과 같은 시각까지'만 센다.
        val yesterday = if (firstRecorded < 0) {
            null
        } else {
            ShortformMinutes.between(
                sessions,
                DayBoundary.startOf(today.minusDays(1), zone),
                now.minus(Duration.ofDays(1)),
                now,
            )
        }
        val total = bins.totalMinutes
        // 오늘 요일 칸 중 가장 위험한 칸과, 그 절반 이상인 칸까지(최대 3칸).
        val risks = RiskWindows.compute(sessions, today, zone, now, top = RISK_CELLS)
            .filter { it.dayOfWeek == today.dayOfWeek }
            .let { list -> list.filter { it.averageMinutes >= (list.firstOrNull()?.averageMinutes ?: 0.0) / 2 } }
            .take(MAX_RISK_HOURS)
        val todaySessionIds = todayNudges.map { it.sessionId }.toSet()
        val todaysSessions = sessions.filter { !it.start.isBefore(DayBoundary.startOf(today, zone)) }
        val live = sessions.lastOrNull { it.end == null }?.let { s ->
            LiveSummary(
                packageName = s.segments.lastOrNull()?.packageName,
                context = s.context,
                elapsedMinutes = ShortformMinutes.lengthMinutes(s, now).toInt(),
                thresholdMinutes = settings.thresholdFor(s.context),
            )
        }
        return TodaySummary(
            now = ZonedDateTime.ofInstant(now, zone),
            isDemo = settings.demoMode,
            minutesByHour = bins.minutesByHour,
            totalMinutes = total,
            byContext = bins.byContext(),
            yesterdayMinutes = yesterday,
            baselineMinutes = baseline,
            meltStage = MeltStageCalculator.compute(total, baseline ?: dailyGoalMinutes),
            riskHours = mergeHours(risks.map { it.hour }),
            topRisk = risks.firstOrNull()?.let {
                RiskSummary(it.dayOfWeek, it.hour, Math.round(it.averageMinutes).toInt())
            },
            nudgedSessions = todaySessionIds.size,
            stoppedSessions = todaysSessions.count { it.stoppedByNudge },
            live = live,
        )
    }

    /** 이어지는 시를 범위로 묶는다: [22, 23, 1] → [22..23, 1..1]. */
    private fun mergeHours(hours: List<Int>): List<IntRange> = hours.sorted().fold(mutableListOf()) { acc, h ->
        val last = acc.lastOrNull()
        if (last != null && last.last + 1 == h) acc[acc.lastIndex] = last.first..h else acc += h..h
        acc
    }

    private companion object {
        const val TICK_MS = 30_000L
        const val HISTORY_DAYS = 14L
        const val RISK_CELLS = 24 * 7
        const val MAX_RISK_HOURS = 3
        const val DEFAULT_DAILY_GOAL_MIN = 60.0
    }
}
