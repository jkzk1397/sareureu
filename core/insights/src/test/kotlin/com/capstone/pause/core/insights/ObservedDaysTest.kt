package com.capstone.pause.core.insights

import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Test

class ObservedDaysTest {
    private val today = LocalDate.of(2026, 11, 9)
    private val weekStart = today.minusDays(6)

    private fun count(vararg starts: String) =
        ObservedDays.count(starts.map { session(it, 10) }, weekStart, today, zone)

    @Test
    fun noRecordsCountsOneDay() {
        assertEquals(1, ObservedDays.count(emptyList(), weekStart, today, zone))
    }

    @Test
    fun countsFromFirstRecordedLogicalDay() {
        // 그제 밤 10시가 첫 기록 → 그제·어제·오늘 = 3일.
        assertEquals(3, count("2026-11-07T22:00:00", "2026-11-09T12:00:00"))
        // 오늘 새벽 3시는 논리적으로 어제(4시 경계) → 2일.
        assertEquals(2, count("2026-11-09T03:00:00"))
        // 오늘 5시 → 1일.
        assertEquals(1, count("2026-11-09T05:00:00"))
    }

    @Test
    fun recordsBeforeWeekAreCappedAtSeven() {
        assertEquals(7, count("2026-10-01T22:00:00"))
    }
}
