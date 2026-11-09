package com.capstone.pause.feature.onboarding

import com.capstone.pause.core.designsystem.component.rhythm.RhythmDraft
import com.capstone.pause.core.designsystem.component.rhythm.TimeRange
import com.capstone.pause.core.model.Occupation
import java.time.LocalTime

/**
 * 신분별 기본 하루 리듬(순수 함수). 온보딩은 자는 시간만 묻고 나머지(기상·통학·수업/업무·식사)는 여기서 채운다.
 * 주말 취침은 평일 +1시간, 주말 기상은 평일 +2시간. 나중에 설정 › 하루 리듬에서 고친다.
 */
internal object DefaultRhythms {
    /** 신분의 기본 리듬. [weekdaySleep]이 있으면 그 시각으로 자는 시간을 바꾼다. */
    fun of(occupation: Occupation, weekdaySleep: LocalTime? = null): RhythmDraft {
        val base = base(occupation)
        val sleep = weekdaySleep ?: base.weekdaySleep
        return base.copy(
            weekdaySleep = sleep,
            weekendSleep = sleep.plusHours(WEEKEND_SLEEP_LATER_H),
            weekendWake = base.weekdayWake.plusHours(WEEKEND_WAKE_LATER_H),
        )
    }

    /** 신분의 기본 평일 취침 시각(화면 첫 값). */
    fun sleepOf(occupation: Occupation): LocalTime = base(occupation).weekdaySleep

    private fun base(occupation: Occupation): RhythmDraft = when (occupation) {
        Occupation.UNIVERSITY -> RhythmDraft(
            weekdayWake = t(8, 0),
            weekdaySleep = t(1, 0),
            commuteMorning = r(8, 30, 9, 15),
            commuteEvening = r(18, 0, 18, 45),
            work = r(9, 30, 17, 0),
            lunch = r(12, 0, 13, 0),
            dinner = r(18, 30, 19, 30),
        )

        Occupation.GRAD_OR_JOBSEEKER -> RhythmDraft(
            weekdayWake = t(8, 30),
            weekdaySleep = t(1, 0),
            commuteMorning = null,
            commuteEvening = null,
            work = r(10, 0, 18, 0),
            lunch = r(12, 30, 13, 30),
            dinner = r(18, 30, 19, 30),
        )

        Occupation.OFFICE_WORKER -> RhythmDraft(
            weekdayWake = t(7, 0),
            weekdaySleep = t(0, 0),
            commuteMorning = r(7, 50, 8, 50),
            commuteEvening = r(18, 0, 19, 0),
            work = r(9, 0, 18, 0),
            lunch = r(12, 0, 13, 0),
            dinner = r(19, 30, 20, 30),
        )

        Occupation.SHIFT_OR_FREELANCE -> RhythmDraft(
            weekdayWake = t(9, 0),
            weekdaySleep = t(1, 30),
            commuteMorning = null,
            commuteEvening = null,
            work = null,
            lunch = r(12, 30, 13, 30),
            dinner = r(19, 0, 20, 0),
        )

        Occupation.OTHER -> RhythmDraft()
    }

    private fun t(h: Int, m: Int) = LocalTime.of(h, m)

    private fun r(h1: Int, m1: Int, h2: Int, m2: Int) = TimeRange(t(h1, m1), t(h2, m2))

    private const val WEEKEND_SLEEP_LATER_H = 1L
    private const val WEEKEND_WAKE_LATER_H = 2L
}
