package com.capstone.pause.core.ai.report

import com.capstone.pause.core.ai.engine.llm.Provider
import com.capstone.pause.core.ai.engine.run.CallOutcome
import com.capstone.pause.core.ai.engine.schema.HypothesisOut
import com.capstone.pause.core.ai.engine.schema.StrategyPickOut
import com.capstone.pause.core.ai.engine.schema.ThresholdSuggestionOut
import com.capstone.pause.core.ai.engine.schema.WeeklyReportOut
import com.capstone.pause.core.data.report.StoredHypothesis
import com.capstone.pause.core.model.Confidence
import com.capstone.pause.core.model.Confidence.MEDIUM
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.EntryPoint
import com.capstone.pause.core.model.ShortformSession
import com.capstone.pause.core.model.Strategy
import com.capstone.pause.core.model.Verdict
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class WeekMathTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 11, 3)
    private val weekStart = LocalDate.of(2026, 10, 27)

    private fun saved(provider: Provider = Provider.CLOUD, outcome: CallOutcome = CallOutcome.PASSED) =
        SavedWeeklyReport(
            out = WeeklyReportOut(
                summaryLine = "요약",
                hypotheses = listOf(
                    HypothesisOut("가설 1", MEDIUM, listOf("o3"), "맞아?"),
                    HypothesisOut("가설 2", Confidence.LOW, listOf("o6"), "그래?"),
                ),
                strategies = listOf(
                    StrategyPickOut("PRE_SLEEP_1", "침대 밖 충전기에", "o3 때문"),
                    StrategyPickOut("UNKNOWN_ID", "없는 전략", "버려져야 함"),
                ),
                viewerTypeLine = "밤 산책형!",
                thresholdSuggestions = listOf(
                    ThresholdSuggestionOut(ContextSlot.PRE_SLEEP, 10, "길어져서"),
                    ThresholdSuggestionOut(ContextSlot.LATE_NIGHT, 10, "이미 같은 값"),
                ),
            ),
            callId = 7,
            provider = provider.name,
            outcome = outcome.name,
            createdAtMs = 1_000,
        )

    private val strategies = listOf(Strategy("PRE_SLEEP_1", ContextSlot.PRE_SLEEP, "잠자리에 누우면", "폰은 침대 밖 충전기에 둔다"))
    private val thresholds = mapOf(ContextSlot.PRE_SLEEP to 15, ContextSlot.LATE_NIGHT to 10)

    @Test
    fun windowIsLastSevenDaysExcludingToday() {
        val w = WeekWindow.lastSevenDays(today)
        assertEquals(weekStart, w.start)
        assertEquals(today, w.endExclusive)
        assertEquals(weekStart.minusDays(28), w.historyStart)
    }

    @Test
    fun verdictsAttachByStableIdAndOldIdsAreIgnored() {
        val stored = listOf(
            StoredHypothesis(WeekMath.hypothesisId(weekStart, 1_000, 0), "가설 1", MEDIUM, listOf("o3"), Verdict.PARTLY),
            StoredHypothesis(WeekMath.hypothesisId(weekStart, 999, 1), "예전 가설", MEDIUM, emptyList(), Verdict.NO),
        )
        val ai = WeekMath.toAiReport(saved(), weekStart, stored, strategies, thresholds)
        assertEquals(Verdict.PARTLY, ai.hypotheses[0].verdict)
        assertNull(ai.hypotheses[1].verdict)
    }

    @Test
    fun unknownStrategiesAreDroppedAndPersonalizedThenIsUsed() {
        val ai = WeekMath.toAiReport(saved(), weekStart, emptyList(), strategies, thresholds)
        assertEquals(1, ai.strategies.size)
        assertEquals("잠자리에 누우면", ai.strategies.single().ifText)
        assertEquals("침대 밖 충전기에", ai.strategies.single().thenText)
    }

    @Test
    fun thresholdSuggestionsEqualToCurrentAreHidden() {
        val ai = WeekMath.toAiReport(saved(), weekStart, emptyList(), strategies, thresholds)
        assertEquals(listOf(ContextSlot.PRE_SLEEP), ai.thresholds.map { it.slot })
        assertEquals(15, ai.thresholds.single().currentMinutes)
    }

    @Test
    fun mockFlagFromProviderOrFallback() {
        assertFalse(WeekMath.toAiReport(saved(), weekStart, emptyList(), strategies, thresholds).isMock)
        assertTrue(WeekMath.toAiReport(saved(Provider.MOCK), weekStart, emptyList(), strategies, thresholds).isMock)
        assertTrue(
            WeekMath.toAiReport(
                saved(outcome = CallOutcome.MOCK_FALLBACK),
                weekStart,
                emptyList(),
                strategies,
                thresholds,
            ).isMock,
        )
    }

    @Test
    fun copyPackOnlyFromRealAiThatPassed() {
        assertTrue(WeekMath.shouldActivateCopyPack(Provider.CLOUD, CallOutcome.RETRIED_PASSED))
        assertFalse(WeekMath.shouldActivateCopyPack(Provider.MOCK, CallOutcome.PASSED))
        assertFalse(WeekMath.shouldActivateCopyPack(Provider.CLOUD, CallOutcome.MOCK_FALLBACK))
    }

    @Test
    fun computeUsesOnlyWeekSessionsAndBaselineFromEarlierDays() {
        fun s(day: LocalDate, minutes: Long) =
            LocalDateTime.of(day, java.time.LocalTime.of(22, 0)).atZone(zone).toInstant().let {
                ShortformSession(
                    id = 0, start = it, end = it.plusSeconds(minutes * 60), segments = emptyList(),
                    context = ContextSlot.PRE_SLEEP, contextConfidence = MEDIUM, contextConfirmed = false,
                    entry = EntryPoint.Unknown, exit = null, interaction = null, maxFrictionLevel = 0,
                    extensions = 0, stoppedByNudge = false, checkIn = null, source = DataSource.DEMO,
                )
            }
        val baselineDays = (8L..14L).map { s(today.minusDays(it), 100) }
        val weekDays = (1L..7L).map { s(today.minusDays(it), 50) }
        val todays = listOf(s(today, 500))
        val week = WeekMath.compute(
            WeekWindow.lastSevenDays(today),
            baselineDays + weekDays + todays,
            emptyList(),
            zone,
            LocalDateTime.of(today, java.time.LocalTime.of(23, 59)).atZone(zone).toInstant(),
        )
        assertEquals(350, week.totalMinutes)
        assertEquals(0.5, week.reductionRate)
    }
}
