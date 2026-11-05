package com.capstone.pause.core.sensing.context

import com.capstone.pause.core.model.Confidence
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.DailyWindow
import com.capstone.pause.core.model.DayRhythm
import com.capstone.pause.core.model.WeeklyBlock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

/** 기기 신호. 모르면 false/null. */
data class DeviceSignals(
    val charging: Boolean = false,
    val headphones: Boolean = false,
    /** 그날(새벽 4시 경계) 첫 잠금 해제 시각. */
    val firstUnlockToday: LocalDateTime? = null,
)

/** 분류 결과. */
data class ContextResult(val slot: ContextSlot, val confidence: Confidence)

/**
 * 맥락 분류(PRD 8절, 우선순위 순). 순수 함수.
 * 1 한밤중: 취침+30분 ~ 기상 / 2 자기 전: 취침−60분 ~ +30분, 또는 21시 이후 충전 중 /
 * 3 기상 직후: 첫 잠금 해제 후 45분(기상 ±2시간) / 4 이동 / 5 식사 / 6 수업·업무 / 7 쉬는 시간(9~21시) / 8 기타.
 * 주말 값: 기상은 그날이 토·일이면, 취침은 다음 날이 토·일이면(금·토 밤) 주말 리듬을 쓴다.
 * 취침 시각이 정오 전이면(예: 01:00) 다음 달력 날짜로 본다.
 */
object ContextClassifier {
    private val LATE_NIGHT_AFTER_SLEEP = Duration.ofMinutes(30)
    private val PRE_SLEEP_BEFORE = Duration.ofMinutes(60)
    private val WAKE_WINDOW = Duration.ofMinutes(45)
    private val WAKE_TOLERANCE = Duration.ofHours(2)
    private val NOON: LocalTime = LocalTime.NOON
    private val CHARGING_PRE_SLEEP_FROM: LocalTime = LocalTime.of(21, 0)
    private val BREAK_FROM: LocalTime = LocalTime.of(9, 0)
    private val BREAK_UNTIL: LocalTime = LocalTime.of(21, 0)

    /** [at]에 시작한 세션의 맥락. */
    fun classify(at: ZonedDateTime, rhythm: DayRhythm, signals: DeviceSignals = DeviceSignals()): ContextResult {
        val t = at.toLocalDateTime()
        night(t, rhythm)?.let { return it }
        if (signals.charging && t.toLocalTime() >= CHARGING_PRE_SLEEP_FROM) {
            return ContextResult(ContextSlot.PRE_SLEEP, Confidence.MEDIUM)
        }
        wakeUp(t, rhythm, signals.firstUnlockToday)?.let { return it }
        if (rhythm.commute.any { it.contains(t) }) {
            return ContextResult(ContextSlot.COMMUTE, if (signals.headphones) Confidence.HIGH else Confidence.MEDIUM)
        }
        if (rhythm.meals.any { it.contains(t.toLocalTime()) }) return ContextResult(ContextSlot.MEAL, Confidence.LOW)
        if (rhythm.classOrWork.any { it.contains(t) }) return ContextResult(ContextSlot.CLASS_WORK, Confidence.MEDIUM)
        val time = t.toLocalTime()
        if (time >= BREAK_FROM && time < BREAK_UNTIL) return ContextResult(ContextSlot.BREAK, Confidence.MEDIUM)
        return ContextResult(ContextSlot.OTHER, Confidence.LOW)
    }

    /** 한밤중 또는 자기 전. 전날 밤과 오늘 밤을 모두 본다. */
    private fun night(t: LocalDateTime, rhythm: DayRhythm): ContextResult? {
        for (d in listOf(t.toLocalDate().minusDays(1), t.toLocalDate())) {
            val sleep = sleepAt(d, rhythm)
            val wake = wakeAt(d.plusDays(1), rhythm).let { if (it.isAfter(sleep)) it else it.plusDays(1) }
            val lateFrom = sleep.plus(LATE_NIGHT_AFTER_SLEEP)
            if (!t.isBefore(lateFrom) && t.isBefore(wake)) return ContextResult(ContextSlot.LATE_NIGHT, Confidence.HIGH)
            if (!t.isBefore(sleep.minus(PRE_SLEEP_BEFORE)) && t.isBefore(lateFrom)) {
                return ContextResult(ContextSlot.PRE_SLEEP, Confidence.HIGH)
            }
        }
        return null
    }

    private fun wakeUp(t: LocalDateTime, rhythm: DayRhythm, firstUnlock: LocalDateTime?): ContextResult? {
        val wake = wakeAt(t.toLocalDate(), rhythm)
        if (Duration.between(wake, t).abs() > WAKE_TOLERANCE) return null
        if (firstUnlock != null) {
            val since = Duration.between(firstUnlock, t)
            return if (!since.isNegative &&
                since < WAKE_WINDOW
            ) {
                ContextResult(ContextSlot.WAKE_UP, Confidence.HIGH)
            } else {
                null
            }
        }
        val sinceWake = Duration.between(wake, t)
        return if (!sinceWake.isNegative && sinceWake < WAKE_WINDOW) {
            ContextResult(ContextSlot.WAKE_UP, Confidence.MEDIUM)
        } else {
            null
        }
    }

    private fun isWeekend(date: LocalDate) = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    /** [date] 밤의 취침 시각(정오 전 값이면 다음 날). */
    private fun sleepAt(date: LocalDate, rhythm: DayRhythm): LocalDateTime {
        val time = if (isWeekend(date.plusDays(1))) rhythm.weekendSleep else rhythm.weekdaySleep
        return if (time < NOON) date.plusDays(1).atTime(time) else date.atTime(time)
    }

    private fun wakeAt(date: LocalDate, rhythm: DayRhythm): LocalDateTime =
        date.atTime(if (isWeekend(date)) rhythm.weekendWake else rhythm.weekdayWake)
}

/** 구간 안(시작 포함, 끝 제외). 끝이 시작보다 이르면 자정을 넘는 구간. */
internal fun inRange(time: LocalTime, start: LocalTime, end: LocalTime): Boolean =
    if (end > start) time >= start && time < end else time >= start || time < end

/** 요일·시각이 이 구간 안인지. */
internal fun WeeklyBlock.contains(t: LocalDateTime): Boolean =
    t.dayOfWeek in days && inRange(t.toLocalTime(), start, end)

/** 시각이 이 구간 안인지. */
internal fun DailyWindow.contains(time: LocalTime): Boolean = inRange(time, start, end)

/** 프로필이 없을 때 쓰는 기본 하루 리듬(대학생 기준 가정). */
object DefaultDayRhythm {
    private val WEEKDAYS = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    )

    /** 평일 07:30 기상·00:30 취침, 주말 09:30·01:30, 평일 08~09·18~19시 이동, 09~18시 수업·업무, 12~13·18:30~19:30 식사. */
    val VALUE = DayRhythm(
        weekdayWake = LocalTime.of(7, 30),
        weekdaySleep = LocalTime.of(0, 30),
        weekendWake = LocalTime.of(9, 30),
        weekendSleep = LocalTime.of(1, 30),
        classOrWork = listOf(WeeklyBlock(WEEKDAYS, LocalTime.of(9, 0), LocalTime.of(18, 0))),
        commute = listOf(
            WeeklyBlock(WEEKDAYS, LocalTime.of(8, 0), LocalTime.of(9, 0)),
            WeeklyBlock(WEEKDAYS, LocalTime.of(18, 0), LocalTime.of(19, 0)),
        ),
        meals = listOf(
            DailyWindow(LocalTime.of(12, 0), LocalTime.of(13, 0)),
            DailyWindow(LocalTime.of(18, 30), LocalTime.of(19, 30)),
        ),
    )
}
