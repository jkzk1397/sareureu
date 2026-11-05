package com.capstone.pause.core.insights

import com.capstone.pause.core.model.AppKind
import com.capstone.pause.core.model.AppSegment
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.EntryPoint
import com.capstone.pause.core.model.ExitPoint
import com.capstone.pause.core.model.ShortformSession
import com.capstone.pause.core.model.Surface
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class WeekChartsTest {
    private val now = at("2026-11-05T12:00:00")
    private val dates = (7 downTo 1).map { LocalDate.of(2026, 11, 5).minusDays(it.toLong()) }

    private fun s(
        start: String,
        min: Long,
        entry: EntryPoint = EntryPoint.Unknown,
        exit: ExitPoint? = ExitPoint.ScreenOff,
        pkg: String = "com.google.android.youtube",
        slot: ContextSlot = ContextSlot.OTHER,
    ): ShortformSession = session(start, min, slot).let {
        it.copy(
            entry = entry,
            exit = exit,
            segments = listOf(
                AppSegment(
                    pkg,
                    Surface.APP_UNKNOWN,
                    it.start,
                    it.end ?: it.start,
                ),
            ),
        )
    }

    @Test
    fun heatmapPutsMinutesInWeekdayTwoHourCell() {
        // 10월 29일 = 목요일, 23시 → 열 11. 새벽 1시(다음 달력 날)도 같은 논리적 날짜(목) 열 0.
        val grid = WeekCharts.heatmap(
            listOf(s("2026-10-29T23:10:00", 20), s("2026-10-30T01:00:00", 10)),
            dates,
            zone,
            now,
        )
        assertEquals(DayOfWeek.MONDAY, grid.days.first())
        val thu = DayOfWeek.THURSDAY.ordinal
        assertEquals(20, grid.cells[thu][11])
        assertEquals(10, grid.cells[thu][0])
        assertEquals(30, grid.cells.sumOf { it.sum() })
    }

    @Test
    fun peakSpanWrapsMidnightAndIsNullWhenEmpty() {
        val clock = WeekCharts.weekClock(
            listOf(s("2026-10-29T23:00:00", 60, slot = ContextSlot.PRE_SLEEP), s("2026-10-30T00:00:00", 30)),
            dates,
            zone,
            now,
        )
        assertEquals(PeakSpan(23, 90), WeekCharts.peakSpan(clock))
        assertNull(WeekCharts.peakSpan(ClockBins.EMPTY))
    }

    @Test
    fun flowChartFoldsBeyondTopFiveIntoOther() {
        val kinds =
            listOf(
                AppKind.MESSENGER,
                AppKind.SOCIAL,
                AppKind.VIDEO,
                AppKind.BROWSER,
                AppKind.GAME,
                AppKind.SHOPPING,
                AppKind.STUDY_WORK,
            )
        val sessions = kinds.flatMapIndexed { i, kind ->
            List(kinds.size - i) { s("2026-10-30T10:00:00", 5, entry = EntryPoint.FromApp(kind)) }
        }
        val chart = WeekCharts.flowChart(sessions)
        assertEquals(WeekCharts.TOP + 1, chart.entries.size)
        assertEquals(WeekCharts.OTHER_KEY, chart.entries.last().first)
        assertEquals(1 + 2, chart.entries.last().second)
        assertEquals(sessions.size, chart.entryToApp.sumOf { it.third })
        assertEquals(listOf(AppKeys.YOUTUBE to sessions.size), chart.apps)
    }

    @Test
    fun longestEntryNeedsEnoughSessions() {
        val messenger = EntryPoint.FromApp(AppKind.MESSENGER)
        val sessions = List(3) { s("2026-10-30T22:00:00", 18, entry = messenger) } +
            List(2) { s("2026-10-30T10:00:00", 60, entry = EntryPoint.Unlock) } +
            List(4) { s("2026-10-30T12:00:00", 4, entry = EntryPoint.Home) }
        val longest = WeekCharts.longestEntry(sessions, now)
        assertEquals("MESSENGER", longest?.key)
        assertEquals(18.0, longest?.medianMinutes)
    }

    @Test
    fun appKeys() {
        assertEquals(AppKeys.TIKTOK, AppKeys.of("com.ss.android.ugc.trill"))
        assertEquals(AppKeys.INSTAGRAM, AppKeys.of("com.instagram.lite"))
        assertEquals(AppKeys.OTHER, AppKeys.of(null))
    }
}
