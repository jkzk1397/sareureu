package com.capstone.pause.core.insights

import com.capstone.pause.core.model.Confidence
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.EntryPoint
import com.capstone.pause.core.model.MeltStage
import com.capstone.pause.core.model.ShortformSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

internal val zone: ZoneId = ZoneId.of("Asia/Seoul")

internal fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant()

internal fun session(start: String, minutes: Long, slot: ContextSlot = ContextSlot.OTHER, id: Long = 0) =
    at(start).let { s ->
        ShortformSession(
            id = id, start = s, end = s.plusSeconds(minutes * 60), segments = emptyList(),
            context = slot, contextConfidence = Confidence.HIGH, contextConfirmed = false,
            entry = EntryPoint.Unknown, exit = null, interaction = null, maxFrictionLevel = 0,
            extensions = 0, stoppedByNudge = false, checkIn = null, source = DataSource.DEMO,
        )
    }

class ClockBinsTest {
    private val day = LocalDate.of(2026, 11, 3)

    @Test
    fun sessionAcrossHourBoundaryIsSplit() {
        val bins = ClockBins.compute(
            listOf(session("2026-11-03T22:50:00", 20, ContextSlot.PRE_SLEEP)),
            from = at("2026-11-03T04:00:00"),
            to = at("2026-11-04T04:00:00"),
            now = at("2026-11-04T03:00:00"),
            zone = zone,
        )
        assertEquals(10, bins.minutesByHour[22][ContextSlot.PRE_SLEEP])
        assertEquals(10, bins.minutesByHour[23][ContextSlot.PRE_SLEEP])
        assertEquals(20, bins.totalMinutes)
    }

    @Test
    fun ongoingSessionCountsUntilNowAndClipsToRange() {
        val ongoing = session("2026-11-03T03:50:00", 0).copy(end = null)
        val bins = ClockBins.compute(
            listOf(ongoing),
            from = at("2026-11-03T04:00:00"),
            to = at("2026-11-04T04:00:00"),
            now = at("2026-11-03T04:15:00"),
            zone = zone,
        )
        assertEquals(15, bins.totalMinutes)
        assertEquals(15, bins.minutesByHour[4][ContextSlot.OTHER])
    }

    @Test
    fun byContextKeepsSlotOrderAndDropsZero() {
        val bins = ClockBins.compute(
            listOf(
                session("2026-11-03T12:00:00", 15, ContextSlot.MEAL),
                session("2026-11-03T08:00:00", 22, ContextSlot.COMMUTE),
            ),
            at("2026-11-03T04:00:00"),
            at("2026-11-04T04:00:00"),
            at("2026-11-03T21:40:00"),
            zone,
        )
        assertEquals(listOf(ContextSlot.COMMUTE, ContextSlot.MEAL), bins.byContext().keys.toList())
        assertEquals(day.dayOfWeek, DayOfWeek.TUESDAY)
    }
}

class MeltStageCalculatorTest {
    @Test
    fun boundaries() {
        val cases = listOf(
            0 to MeltStage.NONE,
            29 to MeltStage.NONE,
            30 to MeltStage.SOFT,
            59 to MeltStage.SOFT,
            60 to MeltStage.PUDDLE,
            89 to MeltStage.PUDDLE,
            90 to MeltStage.MELTED,
            500 to MeltStage.MELTED,
        )
        cases.forEach { (minutes, expected) ->
            assertEquals(expected, MeltStageCalculator.compute(minutes, 60.0), "$minutes")
        }
    }

    @Test
    fun noReferenceMeansNoMelt() {
        assertEquals(MeltStage.NONE, MeltStageCalculator.compute(100, 0.0))
    }
}

class BaselineAndReductionTest {
    private fun day(i: Long, minutes: Int, coverage: Double = 1.0) =
        DayStat(LocalDate.of(2026, 10, 1).plusDays(i), minutes, coverage)

    @Test
    fun baselineUsesFirstSevenCoveredDays() {
        val days = listOf(day(0, 999, coverage = 0.5)) + (1L..8L).map { day(it, if (it == 8L) 999 else 120) }
        assertEquals(120.0, Baseline.compute(days))
    }

    @Test
    fun baselineNeedsSevenDays() {
        assertNull(Baseline.compute((0L..5L).map { day(it, 100) }))
    }

    @Test
    fun reductionRateMatchesMockup() {
        val rate = ReductionRate.compute(96.0, 872.0 / 7)
        assertTrue(rate != null && Math.round(rate * 100) == 23L)
        assertNull(ReductionRate.compute(96.0, null))
    }
}

class RiskWindowsTest {
    @Test
    fun picksHeaviestWeekdayHourSeenTwice() {
        val sessions = listOf(
            session("2026-10-20T22:00:00", 40), // 화
            session("2026-10-27T22:00:00", 36), // 화
            session("2026-10-28T23:00:00", 50), // 수, 한 번만 → 제외
            session("2026-10-22T08:00:00", 5),
            session("2026-10-29T08:00:00", 5),
        )
        val windows = RiskWindows.compute(sessions, LocalDate.of(2026, 11, 3), zone, at("2026-11-03T21:00:00"))
        assertEquals(DayOfWeek.TUESDAY, windows.first().dayOfWeek)
        assertEquals(22, windows.first().hour)
        assertEquals(38.0, windows.first().averageMinutes)
        assertTrue(windows.none { it.dayOfWeek == DayOfWeek.WEDNESDAY && it.hour == 23 })
    }
}
