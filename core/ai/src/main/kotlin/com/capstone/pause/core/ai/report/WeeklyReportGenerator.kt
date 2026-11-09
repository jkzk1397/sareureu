package com.capstone.pause.core.ai.report

import com.capstone.pause.core.ai.AiCallLogger
import com.capstone.pause.core.ai.AiRouter
import com.capstone.pause.core.ai.engine.llm.AiFeature
import com.capstone.pause.core.ai.engine.run.AiQuota
import com.capstone.pause.core.ai.engine.run.AiRunner
import com.capstone.pause.core.ai.engine.schema.AiJson
import com.capstone.pause.core.ai.engine.schema.CopyPackInput
import com.capstone.pause.core.ai.engine.schema.ObservationIn
import com.capstone.pause.core.ai.engine.schema.ProfileSummary
import com.capstone.pause.core.ai.engine.schema.WeeklyReportInput
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.common.AppLog
import com.capstone.pause.core.common.DayBoundary
import com.capstone.pause.core.data.profile.ProfileRepository
import com.capstone.pause.core.data.report.CopyPackWriter
import com.capstone.pause.core.data.report.StoredHypothesis
import com.capstone.pause.core.data.report.StoredWeeklyReport
import com.capstone.pause.core.data.report.WeeklyReportRepository
import com.capstone.pause.core.data.session.SessionRepository
import com.capstone.pause.core.data.settings.SettingsRepository
import com.capstone.pause.core.data.strategy.StrategyRepository
import com.capstone.pause.core.model.CopyPack
import com.capstone.pause.core.model.Verdict
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * AI 주간 분석(03 문서 3.3절). 화면이 열렸을 때 [generate]를 불러서만 만든다(앱이 화면에 있을 때만 LLM).
 * 관찰은 코드가 세고, AI 출력은 검증 통과본만 저장한다. 성공하면 다음 주 문구 팩도 만든다.
 */
interface WeeklyReportGenerator {
    /** 지난 7일 리포트 상태. */
    fun observe(): Flow<WeeklyReportState>

    /** 만들기. 이미 있으면 [force]일 때만 다시(하루 [AiQuota.reportRegenerationsPerDay]번까지). */
    suspend fun generate(force: Boolean): GenerateResult

    /** 가설 판정(맞아/반쯤/아니야). */
    suspend fun setVerdict(hypothesisId: String, verdict: Verdict?)
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
internal class DefaultWeeklyReportGenerator @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val reportRepository: WeeklyReportRepository,
    private val copyPackWriter: CopyPackWriter,
    private val strategyRepository: StrategyRepository,
    private val router: AiRouter,
    private val logger: AiCallLogger,
    private val runner: AiRunner,
    private val clock: AppClock,
    private val log: AppLog,
) : WeeklyReportGenerator {

    private val quota = AiQuota()
    private val status = MutableStateFlow(GenerationStatus.IDLE)

    /** 날짜별 다시 만들기 횟수(앱을 다시 켜면 0부터, BUILD_REPORT 가정). */
    private val regenerations = ConcurrentHashMap<LocalDate, Int>()
    private val regenerationTick = MutableStateFlow(0)

    override fun observe(): Flow<WeeklyReportState> = flow {
        val now = clock.now()
        val zone = clock.zone
        val window = WeekWindow.lastSevenDays(DayBoundary.logicalDate(now, zone))
        val strategies = strategyRepository.strategies()
        val sessions = sessionRepository.observeSessions(
            DayBoundary.startOf(window.historyStart, zone),
            DayBoundary.startOf(window.endExclusive, zone),
        )
        val nudges = sessionRepository.observeNudges(window.startInstant(zone), window.endInstant(zone))
        val stored = combine(
            reportRepository.observeReport(window.start),
            reportRepository.observeHypotheses(window.start),
        ) { report, hypotheses -> report to hypotheses }
        val flags = combine(status, regenerationTick, router.usingRealAi) { s, _, real -> s to real }
        emitAll(
            combine(sessions, nudges, stored, settingsRepository.settings, flags) {
                    ss,
                    ns,
                    (report, hyps),
                    settings,
                    (st, real),
                ->
                val week = WeekMath.compute(window, ss, ns, zone, now)
                val saved = report?.let { decode(it.json) }
                WeeklyReportState(
                    weekStart = window.start,
                    weekEndInclusive = window.endExclusive.minusDays(1),
                    isDemo = settings.demoMode,
                    observations = WeekMath.observations(week),
                    totalMinutes = week.totalMinutes,
                    avgMinPerDay = week.avgMinPerDay,
                    reductionRate = week.reductionRate,
                    stopRate = week.nudges.stopRate,
                    sessionCount = week.sessionCount,
                    viewerType = week.viewerType.name,
                    enoughForAi = week.enoughForAi,
                    typeReady = week.typeReady,
                    ai = saved?.let { WeekMath.toAiReport(it, window.start, hyps, strategies, settings.thresholds) },
                    status = st,
                    regenerationsLeft = quota.reportRegenerationsPerDay - (regenerations[window.today] ?: 0),
                    usingRealAi = real,
                )
            },
        )
    }

    override suspend fun generate(force: Boolean): GenerateResult {
        if (status.value == GenerationStatus.RUNNING) return GenerateResult.CACHED
        val now = clock.now()
        val zone = clock.zone
        val window = WeekWindow.lastSevenDays(DayBoundary.logicalDate(now, zone))
        val existing = reportRepository.observeReport(window.start).first()
        if (existing != null && !force) return GenerateResult.CACHED
        if (existing != null && !quota.canRegenerateReport(regenerations[window.today] ?: 0)) {
            return GenerateResult.QUOTA_REACHED
        }
        val sessions = sessionRepository.observeSessions(
            DayBoundary.startOf(window.historyStart, zone),
            DayBoundary.startOf(window.endExclusive, zone),
        ).first()
        val nudges = sessionRepository.observeNudges(window.startInstant(zone), window.endInstant(zone)).first()
        val week = WeekMath.compute(window, sessions, nudges, zone, now)
        if (!week.enoughForAi) return GenerateResult.NOT_ENOUGH_DATA
        status.value = GenerationStatus.RUNNING
        return runCatching {
            val profile = ProfileSummary.from(profileRepository.profile.first())
            val input = WeeklyReportInput.from(week, profile, coverage = null)
            val outcome = runner.weeklyReport(router.clientFor(AiFeature.WEEKLY_REPORT), input)
            val callId = logger.log(outcome.record)
            val createdAtMs = clock.now().toEpochMilli()
            val saved = SavedWeeklyReport(
                out = outcome.value,
                callId = callId,
                provider = outcome.record.provider.name,
                outcome = outcome.record.outcome.name,
                createdAtMs = createdAtMs,
            )
            reportRepository.save(
                StoredWeeklyReport(
                    window.start,
                    AiJson.encodeToString(SavedWeeklyReport.serializer(), saved),
                    clock.now(),
                ),
                outcome.value.hypotheses.mapIndexed { i, h ->
                    StoredHypothesis(
                        WeekMath.hypothesisId(window.start, createdAtMs, i),
                        h.text,
                        h.confidence,
                        h.evidence,
                        null,
                    )
                },
            )
            if (existing != null) {
                regenerations.merge(window.today, 1, Int::plus)
                regenerationTick.value += 1
            }
            makeCopyPack(
                window.start,
                profile,
                week.observations.map {
                    ObservationIn(it.id, it.kind.name, it.numbers, it.labels)
                },
                week,
            )
            status.value = GenerationStatus.IDLE
            GenerateResult.CREATED
        }.getOrElse { e ->
            log.w(TAG, "weekly report failed", e)
            status.value = GenerationStatus.FAILED
            GenerateResult.FAILED
        }
    }

    private suspend fun makeCopyPack(
        weekStart: LocalDate,
        profile: ProfileSummary,
        observations: List<ObservationIn>,
        week: com.capstone.pause.core.insights.WeeklyObservations,
    ) {
        runCatching {
            val input = CopyPackInput(profile, week.contextTotals.map { it.slot }, observations)
            val outcome = runner.copyPack(router.clientFor(AiFeature.COPY_PACK), input)
            logger.log(outcome.record)
            if (WeekMath.shouldActivateCopyPack(outcome.record.provider, outcome.record.outcome)) {
                val version = "ai-$weekStart-${clock.now().toEpochMilli()}"
                val pack = outcome.value.copy(version = version)
                copyPackWriter.activate(version, AiJson.encodeToString(CopyPack.serializer(), pack), clock.now())
            }
        }.onFailure { log.w(TAG, "copy pack failed", it) }
    }

    override suspend fun setVerdict(hypothesisId: String, verdict: Verdict?) =
        reportRepository.setVerdict(hypothesisId, verdict)

    private fun decode(json: String): SavedWeeklyReport? =
        runCatching { AiJson.decodeFromString(SavedWeeklyReport.serializer(), json) }.getOrNull()

    private companion object {
        const val TAG = "WeeklyReport"
    }
}
