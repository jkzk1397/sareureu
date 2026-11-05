package com.capstone.pause.core.sensing.demo

import com.capstone.pause.core.common.DayBoundary
import com.capstone.pause.core.data.demo.DemoPattern
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.ExitPoint
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class DemoScenarioTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 11, 3)
    private val now = LocalDateTime.of(2026, 11, 3, 21, 40).atZone(zone).toInstant()

    private fun generate(pattern: DemoPattern = DemoPattern.NIGHT_OWL) =
        DemoScenario.generate(pattern, today, zone, now)

    private fun minutes(plans: List<DemoSessionPlan>) =
        plans.sumOf { Duration.between(it.session.start, it.session.end).toMinutes() }

    private fun inDays(plans: List<DemoSessionPlan>, fromOffset: Long, toOffset: Long) = plans.filter {
        val start = DayBoundary.startOf(today.minusDays(fromOffset), zone)
        val end = DayBoundary.startOf(today.minusDays(toOffset), zone)
        !it.session.start.isBefore(start) && it.session.start.isBefore(end)
    }

    @Test
    fun sameSeedGivesSameSessions() {
        DemoPattern.entries.forEach { pattern ->
            assertEquals(generate(pattern), generate(pattern), pattern.name)
        }
    }

    @Test
    fun nightOwlReportWeekMatchesMockup05() {
        val week = inDays(generate(), fromOffset = 7, toOffset = 0)
        assertEquals(58, week.size)
        assertEquals(672L, minutes(week))
        val byContext = week.groupBy { it.session.context }.mapValues { (_, v) -> minutes(v) }
        assertEquals(208L, byContext[ContextSlot.PRE_SLEEP])
        assertEquals(161L, byContext[ContextSlot.COMMUTE])
        assertEquals(114L, byContext[ContextSlot.BREAK])
        assertEquals(81L, byContext[ContextSlot.MEAL])
        assertEquals(60L, byContext[ContextSlot.LATE_NIGHT])
        assertEquals(34L, byContext[ContextSlot.CLASS_WORK])
        assertEquals(14L, byContext[ContextSlot.WAKE_UP])
        val nudged = week.filter { it.nudges.isNotEmpty() }
        assertEquals(14, nudged.size)
        assertEquals(6, week.count { it.session.stoppedByNudge })
        assertEquals(6, week.count { it.session.exit == ExitPoint.AfterNudgeStop })
    }

    @Test
    fun baselineWeekGivesTwentyThreePercentReduction() {
        val baseline = inDays(generate(), fromOffset = 14, toOffset = 7)
        assertEquals(872L, minutes(baseline))
        assertEquals(23L, Math.round((1 - 96.0 / (872.0 / 7)) * 100))
    }

    @Test
    fun todayMatchesMockup01AndStopsAtNow() {
        val todays = inDays(generate(), fromOffset = 0, toOffset = -1)
        assertEquals(72L, minutes(todays))
        assertEquals(3, todays.sumOf { it.nudges.size })
        assertEquals(2, todays.count { it.session.stoppedByNudge })
        assertTrue(todays.all { !it.session.start.isAfter(now) })
    }

    @Test
    fun everythingIsDemoAndSessionsNeverOverlap() {
        DemoPattern.entries.forEach { pattern ->
            val plans = generate(pattern)
            assertTrue(plans.all { it.session.source == DataSource.DEMO })
            assertTrue(plans.all { it.session.end?.isAfter(it.session.start) == true })
            plans.map { it.session }.sortedBy { it.start }.zipWithNext().forEach { (a, b) ->
                assertTrue(
                    a.end?.let {
                        !it.isAfter(b.start)
                    } ?: false,
                    "${pattern.name}: ${a.start}~${a.end} / ${b.start}",
                )
            }
        }
    }
}
