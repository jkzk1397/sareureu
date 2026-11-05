package com.capstone.pause.core.common

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 앱 전체가 쓰는 시계. `System.currentTimeMillis()` 대신 이것을 주입받는다.
 * 테스트에서는 고정 [Clock]을 넣는다.
 */
class AppClock(val clock: Clock) {
    /** 지금 시각. */
    fun now(): Instant = clock.instant()

    /** 표시·맥락 계산용 시간대. */
    val zone: ZoneId get() = clock.zone

    /** 지금 로컬 시각. */
    fun nowLocal(): ZonedDateTime = ZonedDateTime.ofInstant(now(), zone)

    /** 새벽 4시 경계 기준 '오늘'. */
    fun today(): LocalDate = DayBoundary.logicalDate(now(), zone)

    companion object {
        /** 시스템 기본 시간대의 실제 시계. */
        fun system(): AppClock = AppClock(Clock.systemDefaultZone())
    }
}
