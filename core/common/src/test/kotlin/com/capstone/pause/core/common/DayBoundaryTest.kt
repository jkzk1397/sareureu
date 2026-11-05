package com.capstone.pause.core.common

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import org.junit.Test

class DayBoundaryTest {
    private val zone = ZoneId.of("Asia/Seoul")

    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant()

    @Test
    fun before4amBelongsToPreviousDay() {
        assertEquals(LocalDate.of(2026, 11, 4), DayBoundary.logicalDate(at("2026-11-05T03:59:59"), zone))
    }

    @Test
    fun at4amStartsNewDay() {
        assertEquals(LocalDate.of(2026, 11, 5), DayBoundary.logicalDate(at("2026-11-05T04:00:00"), zone))
    }

    @Test
    fun lateEveningIsSameDay() {
        assertEquals(LocalDate.of(2026, 11, 5), DayBoundary.logicalDate(at("2026-11-05T23:30:00"), zone))
    }

    @Test
    fun startAndEndAreFourAm() {
        val day = LocalDate.of(2026, 11, 5)
        assertEquals(at("2026-11-05T04:00:00"), DayBoundary.startOf(day, zone))
        assertEquals(at("2026-11-06T04:00:00"), DayBoundary.endOf(day, zone))
    }
}
