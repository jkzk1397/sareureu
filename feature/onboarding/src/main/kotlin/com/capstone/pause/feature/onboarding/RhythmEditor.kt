package com.capstone.pause.feature.onboarding

import com.capstone.pause.core.designsystem.chart.RhythmSegment
import com.capstone.pause.core.model.ContextSlot
import java.time.LocalTime

/** 리듬 초안 고치기와 링 미리보기 계산(순수 함수, 화면 밖). */
internal object RhythmEditor {
    /** 칸 하나를 [delta]분 옮긴다. 기상·취침은 [weekend]에 따라 평일/주말 값을 고친다. */
    fun shift(draft: RhythmDraft, field: RhythmField, weekend: Boolean, end: Boolean, delta: Int): RhythmDraft {
        fun LocalTime.move() = plusMinutes(delta.toLong())
        fun TimeRange?.move(): TimeRange? = this?.let {
            if (end) copy(end = it.end.move()) else copy(start = it.start.move())
        }
        return when (field) {
            RhythmField.WAKE -> if (weekend) {
                draft.copy(
                    weekendWake = draft.weekendWake.move(),
                )
            } else {
                draft.copy(weekdayWake = draft.weekdayWake.move())
            }

            RhythmField.SLEEP -> if (weekend) {
                draft.copy(
                    weekendSleep = draft.weekendSleep.move(),
                )
            } else {
                draft.copy(weekdaySleep = draft.weekdaySleep.move())
            }

            RhythmField.COMMUTE_MORNING -> draft.copy(commuteMorning = draft.commuteMorning.move())

            RhythmField.COMMUTE_EVENING -> draft.copy(commuteEvening = draft.commuteEvening.move())

            RhythmField.WORK -> draft.copy(work = draft.work.move())

            RhythmField.LUNCH -> draft.copy(lunch = draft.lunch.move())

            RhythmField.DINNER -> draft.copy(dinner = draft.dinner.move())
        }
    }

    /** 구간 켜기(기본값으로)·끄기. 기상·취침은 끌 수 없다. */
    fun toggle(draft: RhythmDraft, field: RhythmField, enabled: Boolean): RhythmDraft {
        val defaults = RhythmDraft()
        fun pick(current: TimeRange?, default: TimeRange?) = if (enabled) current ?: default else null
        return when (field) {
            RhythmField.COMMUTE_MORNING -> draft.copy(
                commuteMorning = pick(draft.commuteMorning, defaults.commuteMorning),
            )

            RhythmField.COMMUTE_EVENING -> draft.copy(
                commuteEvening = pick(draft.commuteEvening, defaults.commuteEvening),
            )

            RhythmField.WORK -> draft.copy(work = pick(draft.work, defaults.work))

            RhythmField.LUNCH -> draft.copy(lunch = pick(draft.lunch, defaults.lunch))

            RhythmField.DINNER -> draft.copy(dinner = pick(draft.dinner, defaults.dinner))

            RhythmField.WAKE, RhythmField.SLEEP -> draft
        }
    }

    /**
     * 링 미리보기 구간(PRD 8절 규칙을 그림으로): 한밤중(취침+30분~기상), 자기 전(취침-60~+30분),
     * 기상 직후(기상~+45분), 그 위에 통학·수업·식사, 9~21시의 빈칸은 쉬는 시간. 주말은 통학·수업 없음.
     */
    fun preview(draft: RhythmDraft, weekend: Boolean): List<RhythmSegment> {
        val wake = minuteOf(if (weekend) draft.weekendWake else draft.weekdayWake)
        val sleep = minuteOf(if (weekend) draft.weekendSleep else draft.weekdaySleep)
        val slots = arrayOfNulls<ContextSlot>(DAY)
        fun paint(from: Int, to: Int, slot: ContextSlot) {
            var m = Math.floorMod(from, DAY)
            val length = Math.floorMod(to - from, DAY)
            repeat(length) {
                slots[m] = slot
                m = (m + 1) % DAY
            }
        }
        for (m in BREAK_START until BREAK_END) slots[m] = ContextSlot.BREAK
        if (!weekend) {
            listOfNotNull(draft.work).forEach { paint(minuteOf(it.start), minuteOf(it.end), ContextSlot.CLASS_WORK) }
            listOfNotNull(draft.commuteMorning, draft.commuteEvening).forEach {
                paint(minuteOf(it.start), minuteOf(it.end), ContextSlot.COMMUTE)
            }
        }
        listOfNotNull(draft.lunch, draft.dinner).forEach {
            paint(minuteOf(it.start), minuteOf(it.end), ContextSlot.MEAL)
        }
        paint(wake, wake + WAKE_UP_MIN, ContextSlot.WAKE_UP)
        paint(sleep - PRE_SLEEP_BEFORE, sleep + LATE_NIGHT_AFTER, ContextSlot.PRE_SLEEP)
        paint(sleep + LATE_NIGHT_AFTER, wake, ContextSlot.LATE_NIGHT)
        val out = mutableListOf<RhythmSegment>()
        var start = 0
        for (m in 1..DAY) {
            if (m == DAY || slots[m] != slots[start]) {
                slots[start]?.let { out += RhythmSegment(it, start, m) }
                start = m
            }
        }
        return out
    }

    private fun minuteOf(t: LocalTime) = t.hour * MIN_PER_HOUR + t.minute

    private const val DAY = 24 * 60
    private const val MIN_PER_HOUR = 60
    private const val BREAK_START = 9 * 60
    private const val BREAK_END = 21 * 60
    private const val WAKE_UP_MIN = 45
    private const val PRE_SLEEP_BEFORE = 60
    private const val LATE_NIGHT_AFTER = 30
}
