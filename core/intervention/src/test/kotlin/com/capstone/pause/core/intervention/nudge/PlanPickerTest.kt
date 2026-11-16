package com.capstone.pause.core.intervention.nudge

import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.IfThenPlan
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class PlanPickerTest {
    private fun plan(id: Long, context: ContextSlot?, then: String, at: Long = id, active: Boolean = true) =
        IfThenPlan(id, context, "누우면", then, Instant.ofEpochSecond(at), active)

    @Test
    fun sameContextWinsOverContextFree() {
        val plans = listOf(plan(1, null, "물 한 잔", at = 50), plan(2, ContextSlot.PRE_SLEEP, "폰은 책상 충전기에", at = 10))
        assertEquals(2L, PlanPicker.pick(plans, ContextSlot.PRE_SLEEP)?.id)
    }

    @Test
    fun fallsBackToContextFreePlan() {
        val plans = listOf(plan(1, ContextSlot.COMMUTE, "이어폰 빼기"), plan(2, null, "물 한 잔"))
        assertEquals(2L, PlanPicker.pick(plans, ContextSlot.PRE_SLEEP)?.id)
    }

    @Test
    fun noMatchingPlanGivesNull() {
        assertNull(PlanPicker.pick(listOf(plan(1, ContextSlot.COMMUTE, "이어폰 빼기")), ContextSlot.PRE_SLEEP))
        assertNull(PlanPicker.pick(emptyList(), ContextSlot.PRE_SLEEP))
    }

    @Test
    fun demoFallsBackToNewestPlanOfAnyContext() {
        val plans =
            listOf(
                plan(1, ContextSlot.PRE_SLEEP, "폰은 책상 충전기에", at = 20),
                plan(2, ContextSlot.COMMUTE, "이어폰 빼기", at = 10),
            )
        assertNull(PlanPicker.pick(plans, ContextSlot.BREAK))
        assertEquals(1L, PlanPicker.pick(plans, ContextSlot.BREAK, anyContext = true)?.id)
        // 시연이어도 맥락이 맞는 계획이 있으면 그것이 먼저다.
        assertEquals(2L, PlanPicker.pick(plans, ContextSlot.COMMUTE, anyContext = true)?.id)
    }

    @Test
    fun skipsInactiveAndBlankPlans() {
        val plans = listOf(
            plan(1, ContextSlot.PRE_SLEEP, "끈 계획", active = false),
            plan(2, ContextSlot.PRE_SLEEP, "  "),
            plan(3, null, "물 한 잔"),
        )
        assertEquals(3L, PlanPicker.pick(plans, ContextSlot.PRE_SLEEP)?.id)
    }

    @Test
    fun newestWinsWithinSameRank() {
        val plans =
            listOf(plan(1, ContextSlot.PRE_SLEEP, "예전 계획", at = 10), plan(2, ContextSlot.PRE_SLEEP, "새 계획", at = 20))
        assertEquals("새 계획", PlanPicker.pick(plans, ContextSlot.PRE_SLEEP)?.thenText)
    }
}
