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
}
