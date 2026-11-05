package com.capstone.pause.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 하루 경계는 새벽 4시다. 밤샘 세션이 이틀로 쪼개지지 않게 한다(04 문서 2절).
 * 03:59는 전날, 04:00은 오늘.
 */
object DayBoundary {
    /** 하루가 바뀌는 시각. */
    val START: LocalTime = LocalTime.of(4, 0)

    /** [instant]가 속한 '논리적 날짜'. */
    fun logicalDate(instant: Instant, zone: ZoneId): LocalDate {
        val local = ZonedDateTime.ofInstant(instant, zone)
        val date = local.toLocalDate()
        return if (local.toLocalTime() < START) date.minusDays(1) else date
    }

    /** 논리적 날짜 [date]가 시작하는 순간(그날 04:00). */
    fun startOf(date: LocalDate, zone: ZoneId): Instant = date.atTime(START).atZone(zone).toInstant()

    /** 논리적 날짜 [date]가 끝나는 순간(다음 날 04:00, 배타적). */
    fun endOf(date: LocalDate, zone: ZoneId): Instant = startOf(date.plusDays(1), zone)
}
