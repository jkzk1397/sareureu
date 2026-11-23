package com.capstone.pause.core.insights

import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Test

class GoalStreakTest {
    private val today = LocalDate.of(2026, 11, 23)
    private val now = at("2026-11-23T20:00:00")

    private fun streak(vararg sessions: com.capstone.pause.core.model.ShortformSession, goal: Int = 60) =
        GoalStreak.compute(sessions.toList(), today, zone, now, goal)

    @Test
    fun noRecordsIsZero() {
        assertEquals(0, streak())
    }

    @Test
    fun countsFinishedDaysUnderGoalBackFromYesterday() {
        val result = streak(
            session("2026-11-19T12:00:00", 90), // 목표 넘김 → 여기서 끊긴다
            session("2026-11-20T12:00:00", 60), // 딱 목표: 달성
            session("2026-11-21T12:00:00", 10),
            // 11-22는 기록 없음 = 0분 = 달성
            session("2026-11-23T12:00:00", 30), // 오늘: 아직 목표 안 넘김(세지는 않음)
        )
        assertEquals(3, result)
    }

    @Test
    fun daysBeforeFirstRecordAreNotCounted() {
        assertEquals(2, streak(session("2026-11-21T12:00:00", 5)))
    }

    @Test
    fun overGoalTodayBreaksTheStreak() {
        assertEquals(0, streak(session("2026-11-21T12:00:00", 5), session("2026-11-23T12:00:00", 61)))
    }

    @Test
    fun ongoingSessionTodayCountsUntilNow() {
        val ongoing = session("2026-11-23T18:30:00", 0).copy(end = null)
        assertEquals(0, streak(session("2026-11-21T12:00:00", 5), ongoing))
    }

    @Test
    fun usesFourAmDayBoundary() {
        // 11-23 02:00은 논리적으로 11-22(어제). 70분이라 어제가 끊긴다.
        assertEquals(0, streak(session("2026-11-20T12:00:00", 5), session("2026-11-23T02:00:00", 70)))
    }

    @Test
    fun zeroGoalIsZero() {
        assertEquals(0, streak(session("2026-11-21T12:00:00", 0), goal = 0))
    }

    @Test
    fun dayWithMonitoringMostlyOffIsNotCountedAsKept() {
        val sessions = listOf(session("2026-11-19T12:00:00", 5))
        // 11-21 낮 12시~밤 10시(10시간) 감지를 껐다: 기록된 비율 58% → 그날은 달성이 아니다.
        val longOff = listOf(TimeSpan(at("2026-11-21T12:00:00"), at("2026-11-21T22:00:00")))
        assertEquals(1, GoalStreak.compute(sessions, today, zone, now, 60, longOff))
        // 30분만 껐으면 그대로 센다.
        val shortOff = listOf(TimeSpan(at("2026-11-21T12:00:00"), at("2026-11-21T12:30:00")))
        assertEquals(4, GoalStreak.compute(sessions, today, zone, now, 60, shortOff))
    }

    @Test
    fun coverageOfDayIsTimeNotSwitchedOff() {
        val day = TimeSpan(at("2026-11-21T04:00:00"), at("2026-11-22T04:00:00"))
        assertEquals(1.0, Coverage.ofDay(day, emptyList(), now))
        val off = listOf(TimeSpan(at("2026-11-21T10:00:00"), at("2026-11-21T16:00:00")))
        assertEquals(0.75, Coverage.ofDay(day, off, now))
        // 오늘은 지금까지 지난 시간만 본다: 04~20시(16시간) 중 4시간 끔.
        val todaySpan = TimeSpan(at("2026-11-23T04:00:00"), at("2026-11-24T04:00:00"))
        val offToday = listOf(TimeSpan(at("2026-11-23T16:00:00"), at("2026-11-23T23:00:00")))
        assertEquals(0.75, Coverage.ofDay(todaySpan, offToday, now))
    }
}
