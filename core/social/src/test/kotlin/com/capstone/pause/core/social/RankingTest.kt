package com.capstone.pause.core.social

import com.capstone.pause.core.model.Confidence
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.EntryPoint
import com.capstone.pause.core.model.ShortformSession
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class RankingTest {
    private fun s(id: String, rate: Double?, coverage: Double? = 0.9, days: Int = 7, me: Boolean = false) =
        Standing(id, id, rate, coverage, days, me)

    @Test
    fun measuringRule() {
        assertTrue(s("a", 0.2, days = 4).measuring)
        assertTrue(s("a", 0.2, coverage = 0.79).measuring)
        assertTrue(s("a", null).measuring)
        assertFalse(s("a", 0.2, coverage = 0.8, days = 5).measuring)
    }

    @Test
    fun topThreePlusMeAndLastHiddenWhenFourOrMore() {
        val view = Ranking.build(
            listOf(s("a", 0.4), s("b", 0.3), s("c", 0.2), s("d", 0.1), s("e", 0.05), s("me", 0.01, me = true)),
        )
        assertEquals(listOf("a", "b", "c", "me"), view.rows.map { it.standing.id })
        assertEquals(6, view.rows.last().rank)
        assertTrue(view.rows.none { it.standing.id == "e" })
    }

    @Test
    fun smallGroupShowsEveryoneAndMeasuringIsSeparate() {
        val view = Ranking.build(
            listOf(s("하은", 0.31), s("me", 0.23, me = true), s("민지", 0.18), s("준호", 0.12, coverage = 0.71, days = 4)),
        )
        assertEquals(listOf("하은", "me", "민지"), view.rows.map { it.standing.id })
        assertEquals(listOf(1, 2, 3), view.rows.map { it.rank })
        assertEquals(listOf("준호"), view.measuring.map { it.id })
    }

    @Test
    fun tiesShareRank() {
        val view = Ranking.build(listOf(s("a", 0.2), s("b", 0.2), s("c", 0.1)))
        assertEquals(listOf(1, 1, 3), view.rows.map { it.rank })
    }
}

class MyStandingTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 11, 5)
    private val now = today.atTime(21, 0).atZone(zone).toInstant()

    private fun session(date: LocalDate, minutes: Long, slot: ContextSlot = ContextSlot.BREAK): ShortformSession {
        val start = date.atTime(15, 0).atZone(zone).toInstant()
        return ShortformSession(
            0, start, start.plusSeconds(minutes * 60), emptyList(), slot, Confidence.HIGH, false,
            EntryPoint.Unknown, null, null, 0, 0, false, null, DataSource.DEMO,
        )
    }

    @Test
    fun twoWeeksGiveReductionAgainstFirstWeek() {
        val sessions = (14 downTo 8).map { session(today.minusDays(it.toLong()), 100) } +
            (7 downTo 1).map { session(today.minusDays(it.toLong()), 75) }
        val me = MyStanding.compute(sessions, today, zone, now, "지수")
        assertEquals(7, me.validDays)
        assertEquals(0.25, me.reductionRate ?: 0.0, 1e-9)
        assertFalse(me.measuring)
    }

    @Test
    fun withoutBaselineIsMeasuring() {
        val sessions = (3 downTo 1).map { session(today.minusDays(it.toLong()), 30) }
        val me = MyStanding.compute(sessions, today, zone, now, "지수")
        assertNull(me.reductionRate)
        assertTrue(me.measuring)
    }

    @Test
    fun lateNightChallengeMarksFutureAsNull() {
        // 2026-11-05는 목요일. 수요일 한밤중에 봤음.
        val sessions = listOf(
            session(LocalDate.of(2026, 11, 2), 10),
            session(LocalDate.of(2026, 11, 4), 10, ContextSlot.LATE_NIGHT),
        )
        val days = MyStanding.lateNightZeroDays(sessions, today, zone, now)
        assertEquals(listOf(true, true, false, true, null, null, null), days)
    }
}
