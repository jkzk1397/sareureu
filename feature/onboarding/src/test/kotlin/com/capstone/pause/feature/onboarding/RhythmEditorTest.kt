package com.capstone.pause.feature.onboarding

import com.capstone.pause.core.model.ContextSlot
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class RhythmEditorTest {
    private fun slotAt(draft: RhythmDraft, weekend: Boolean, hour: Int, minute: Int = 0): ContextSlot? {
        val m = hour * 60 + minute
        return RhythmEditor.preview(draft, weekend).firstOrNull { m >= it.startMinute && m < it.endMinute }?.slot
    }

    @Test
    fun previewFollowsPrdRules() {
        val d = RhythmDraft() // 평일 기상 8:00, 취침 1:00
        assertEquals(ContextSlot.LATE_NIGHT, slotAt(d, false, 3))
        assertEquals(ContextSlot.PRE_SLEEP, slotAt(d, false, 0, 15))
        assertEquals(ContextSlot.PRE_SLEEP, slotAt(d, false, 1, 15))
        assertEquals(ContextSlot.WAKE_UP, slotAt(d, false, 8, 10))
        assertEquals(ContextSlot.COMMUTE, slotAt(d, false, 8, 50))
        assertEquals(ContextSlot.CLASS_WORK, slotAt(d, false, 10))
        assertEquals(ContextSlot.MEAL, slotAt(d, false, 12, 30))
        assertEquals(ContextSlot.BREAK, slotAt(d, false, 20, 30))
        assertNull(slotAt(d, false, 22))
    }

    @Test
    fun weekendHasNoCommuteOrWork() {
        val segments = RhythmEditor.preview(RhythmDraft(), weekend = true)
        assertTrue(segments.none { it.slot == ContextSlot.COMMUTE || it.slot == ContextSlot.CLASS_WORK })
        assertEquals(ContextSlot.WAKE_UP, slotAt(RhythmDraft(), true, 10, 10))
    }

    @Test
    fun shiftAndToggle() {
        val moved = RhythmEditor.shift(RhythmDraft(), RhythmField.WAKE, weekend = false, end = false, delta = 30)
        assertEquals(LocalTime.of(8, 30), moved.weekdayWake)
        assertEquals(LocalTime.of(10, 0), moved.weekendWake)
        val lunchEnd = RhythmEditor.shift(RhythmDraft(), RhythmField.LUNCH, weekend = false, end = true, delta = -30)
        assertEquals(LocalTime.of(12, 30), lunchEnd.lunch?.end)
        val off = RhythmEditor.toggle(RhythmDraft(), RhythmField.WORK, enabled = false)
        assertNull(off.work)
        assertEquals(RhythmDraft().work, RhythmEditor.toggle(off, RhythmField.WORK, enabled = true).work)
        assertTrue(off.toRhythm().classOrWork.isEmpty())
    }

    @Test
    fun flowSkipsUnderageAndBackGoesToAge() {
        assertEquals(OnboardingStep.TERMS, OnboardingFlow.next(OnboardingStep.AGE))
        assertEquals(OnboardingStep.AGE, OnboardingFlow.previous(OnboardingStep.UNDERAGE))
        assertEquals(OnboardingStep.AGE, OnboardingFlow.previous(OnboardingStep.AGE))
        assertEquals(OnboardingStep.DONE, OnboardingFlow.next(OnboardingStep.DONE))
    }
}
