package com.capstone.pause.core.intervention.nudge

import com.capstone.pause.core.model.CardCopy
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.CopyLine
import com.capstone.pause.core.model.CopyPack
import com.capstone.pause.core.model.IfThenPlan
import com.capstone.pause.core.model.NudgeResponse
import com.capstone.pause.core.model.Tone
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** 계획을 카드에 이어도 사다리·5분만 더·그만 볼래·오늘은 그만 알려 줘가 그대로인지(실제 문구 작성기로). */
class NudgeEnginePlanTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val start = LocalDateTime.of(2026, 11, 3, 22, 30).atZone(zone).toInstant()
    private fun at(minutes: Double): Instant = start.plusMillis((minutes * 60_000).toLong())
    private fun live() = LiveInput(start, ContextSlot.PRE_SLEEP, "유튜브", 15)

    private fun line(id: String, text: String) = CopyLine(id, text, setOf(Tone.PLAYFUL))
    private val pack = CopyPack(
        version = "t",
        card = mapOf(
            ContextSlot.OTHER to CardCopy(
                l1 = listOf(line("l1", "{app} {min}분째")),
                l2 = listOf(line("l2", "이유는?")),
                l3 = listOf(line("l3", "숨")),
                l4Question = listOf(line("q", "다음 할 일은?")),
            ),
        ),
        chipReply = emptyMap(),
        buddyMessage = listOf(line("m", "보고 있어?")),
        praise = listOf(line("pr", "잘했어")),
        predictive = emptyMap(),
    )
    private val texts = FixedTexts(
        buddyName = "르르",
        buddySender = "르르 · 사르르",
        friendSenderFormat = "%1\$s · 맡겨 둔 잔소리",
        contextLabels = mapOf(ContextSlot.PRE_SLEEP to "자기 전"),
        reasonChips = listOf(ReasonChip("BORED", "심심해서")),
        typeToExtend = mapOf(1 to "5분만 더 볼게", 2 to "알면서도 5분만 더 볼게", 3 to "그래도 5분만 더 볼게"),
    )
    private val plan = IfThenPlan(1, ContextSlot.PRE_SLEEP, "누우면", "폰은 책상 충전기에", Instant.EPOCH, true)

    private fun engine(plans: List<IfThenPlan>) =
        NudgeEngine(zone, CopyNudgeWriter(pack, Tone.PLAYFUL, texts, null, plans) { null }, 1.0)

    private fun List<NudgeCommand>.card() = filterIsInstance<NudgeCommand.ShowCard>().singleOrNull()?.card

    /** 같은 순서로 누른 결과. 계획 줄을 빼면 계획이 있을 때와 없을 때가 같아야 한다. */
    private fun run(plans: List<IfThenPlan>): Pair<List<NudgeCommand>, List<NudgeResponse>> {
        val e = engine(plans)
        val out = mutableListOf<NudgeCommand>()
        out += e.tick(at(15.0), live())
        out += e.onAction(at(15.1), NudgeUserAction.Extend(1, answer = "5분만 더 볼게"), live())
        out += e.tick(at(20.1), live())
        out += e.onAction(at(20.2), NudgeUserAction.SnoozeToday(2), live())
        out += e.tick(at(40.0), live())
        e.tick(at(41.0), null)
        e.tick(at(43.0), null)
        return out to e.drainFinished().single().events.map { it.response }
    }

    @Test
    fun extendAndSnoozeBehaveTheSameWithOrWithoutPlan() {
        val (withPlan, withEvents) = run(listOf(plan))
        val (noPlan, noEvents) = run(emptyList())
        assertEquals(listOf(NudgeResponse.EXTEND, NudgeResponse.SNOOZE_TODAY), withEvents)
        assertEquals(noEvents, withEvents)
        val stripped = withPlan.map {
            if (it is NudgeCommand.ShowCard) NudgeCommand.ShowCard(it.card.copy(planText = null)) else it
        }
        assertEquals(noPlan, stripped)
        assertEquals(
            listOf("폰은 책상 충전기에", "폰은 책상 충전기에"),
            withPlan.mapNotNull {
                (it as? NudgeCommand.ShowCard)?.card?.planText
            },
        )
        assertTrue(noPlan.filterIsInstance<NudgeCommand.ShowCard>().all { it.card.planText == null })
    }

    @Test
    fun stopStillHidesAndPraisesWithPlan() {
        val e = engine(listOf(plan))
        assertEquals("폰은 책상 충전기에", e.tick(at(15.0), live()).card()?.planText)
        val out = e.onAction(at(15.1), NudgeUserAction.Stop(1), live())
        assertEquals(NudgeCommand.Hide, out.first())
        assertEquals("잘했어", (out.last() as NudgeCommand.ShowPraise).text)
        e.tick(at(16.0), null)
        e.tick(at(18.0), null)
        assertEquals(listOf(NudgeResponse.STOP), e.drainFinished().single().events.map { it.response })
    }

    @Test
    fun noPlanCardIsUnchanged() {
        assertNull(engine(emptyList()).tick(at(15.0), live()).card()?.planText)
    }
}
