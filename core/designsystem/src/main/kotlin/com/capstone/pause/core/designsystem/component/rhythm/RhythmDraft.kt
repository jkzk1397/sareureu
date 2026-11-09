package com.capstone.pause.core.designsystem.component.rhythm

import com.capstone.pause.core.model.DailyWindow
import com.capstone.pause.core.model.DayRhythm
import com.capstone.pause.core.model.WeeklyBlock
import java.time.DayOfWeek
import java.time.LocalTime

/** 시작~끝 시각. */
data class TimeRange(val start: LocalTime, val end: LocalTime)

/** 하루 리듬 초안(온보딩·설정 편집 화면 공용). 구간이 null이면 '없음'. */
data class RhythmDraft(
    val weekdayWake: LocalTime = LocalTime.of(8, 0),
    val weekdaySleep: LocalTime = LocalTime.of(1, 0),
    val weekendWake: LocalTime = LocalTime.of(10, 0),
    val weekendSleep: LocalTime = LocalTime.of(2, 0),
    val commuteMorning: TimeRange? = TimeRange(LocalTime.of(8, 15), LocalTime.of(9, 0)),
    val commuteEvening: TimeRange? = TimeRange(LocalTime.of(18, 0), LocalTime.of(18, 50)),
    val work: TimeRange? = TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 30)),
    val lunch: TimeRange? = TimeRange(LocalTime.of(12, 0), LocalTime.of(13, 0)),
    val dinner: TimeRange? = TimeRange(LocalTime.of(19, 0), LocalTime.of(20, 0)),
) {
    /** 계약 타입으로. 수업·근무·통학은 평일(월~금)만. */
    fun toRhythm(): DayRhythm = DayRhythm(
        weekdayWake = weekdayWake,
        weekdaySleep = weekdaySleep,
        weekendWake = weekendWake,
        weekendSleep = weekendSleep,
        classOrWork = listOfNotNull(work).map { WeeklyBlock(WEEKDAYS, it.start, it.end) },
        commute = listOfNotNull(commuteMorning, commuteEvening).map { WeeklyBlock(WEEKDAYS, it.start, it.end) },
        meals = listOfNotNull(lunch, dinner).map { DailyWindow(it.start, it.end) },
    )

    companion object {
        /** 월~금. */
        val WEEKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )

        /**
         * 저장된 리듬을 편집용 초안으로. 통학·식사는 시작 시각 순으로 앞을 아침·점심, 다음을 저녁에 둔다.
         * 편집기가 다루지 않는 셋째 구간 이후는 버린다(편집기에서 만든 리듬은 최대 두 개).
         */
        fun from(rhythm: DayRhythm): RhythmDraft {
            val commute = rhythm.commute.sortedBy { it.start }.map { TimeRange(it.start, it.end) }
            val meals = rhythm.meals.sortedBy { it.start }.map { TimeRange(it.start, it.end) }
            return RhythmDraft(
                weekdayWake = rhythm.weekdayWake,
                weekdaySleep = rhythm.weekdaySleep,
                weekendWake = rhythm.weekendWake,
                weekendSleep = rhythm.weekendSleep,
                commuteMorning = commute.getOrNull(0),
                commuteEvening = commute.getOrNull(1),
                work = rhythm.classOrWork.firstOrNull()?.let { TimeRange(it.start, it.end) },
                lunch = meals.getOrNull(0),
                dinner = meals.getOrNull(1),
            )
        }
    }
}

/** 리듬 편집기에서 고칠 수 있는 칸. */
enum class RhythmField { WAKE, SLEEP, COMMUTE_MORNING, COMMUTE_EVENING, WORK, LUNCH, DINNER }
