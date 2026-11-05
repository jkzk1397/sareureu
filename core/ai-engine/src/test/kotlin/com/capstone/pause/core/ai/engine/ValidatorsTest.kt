package com.capstone.pause.core.ai.engine

import com.capstone.pause.core.ai.engine.schema.CoachReplyOut
import com.capstone.pause.core.ai.engine.schema.ContextCheckOut
import com.capstone.pause.core.ai.engine.schema.EvidenceOut
import com.capstone.pause.core.ai.engine.schema.HypothesisOut
import com.capstone.pause.core.ai.engine.schema.PokeDraftOut
import com.capstone.pause.core.ai.engine.schema.StrategyPickOut
import com.capstone.pause.core.ai.engine.schema.ThresholdSuggestionOut
import com.capstone.pause.core.ai.engine.schema.WeeklyReportOut
import com.capstone.pause.core.ai.engine.validate.CoachReplyValidator
import com.capstone.pause.core.ai.engine.validate.ContextCheckValidator
import com.capstone.pause.core.ai.engine.validate.CopyPackValidator
import com.capstone.pause.core.ai.engine.validate.PokeDraftValidator
import com.capstone.pause.core.ai.engine.validate.ToolEvidence
import com.capstone.pause.core.ai.engine.validate.Validation
import com.capstone.pause.core.ai.engine.validate.WeeklyReportValidator
import com.capstone.pause.core.model.Confidence
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.CopyLine
import com.capstone.pause.core.model.Tone
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Test

class WeeklyReportValidatorTest {
    private val validator = WeeklyReportValidator(TestData.strategies.map { it.id }.toSet())
    private val input = TestData.weeklyInput()
    private val good = WeeklyReportOut(
        summaryLine = "이번 주는 {topContext} 때가 제일 길었어. 기준보다 {reduction} 줄었어!",
        hypotheses = listOf(HypothesisOut("자기 전엔 메신저 보다가 넘어가는 것 같아", Confidence.MEDIUM, listOf("o3", "o12"), "맞아?")),
        strategies = listOf(StrategyPickOut("PRE_SLEEP_1", "폰은 책상 충전기에 꽂아 두기", "자기 전이 제일 길어서")),
        viewerTypeLine = "밤 산책형!",
        thresholdSuggestions = listOf(ThresholdSuggestionOut(ContextSlot.PRE_SLEEP, 10, "자기 전이 길어서")),
    )

    @Test
    fun goodPasses() {
        assertIs<Validation.Valid<WeeklyReportOut>>(validator.validate(good, input))
    }

    @Test
    fun badCasesFail() {
        val bad = listOf(
            good.copy(summaryLine = "이번 주 37분 줄었어"),
            good.copy(hypotheses = listOf(good.hypotheses[0].copy(evidence = listOf("o3")))),
            good.copy(hypotheses = listOf(good.hypotheses[0].copy(evidence = listOf("o3", "o99")))),
            good.copy(hypotheses = listOf(good.hypotheses[0].copy(confidence = Confidence.HIGH))),
            good.copy(hypotheses = List(4) { good.hypotheses[0] }),
            good.copy(strategies = listOf(StrategyPickOut("MADE_UP", "폰 끄기", "그냥"))),
            good.copy(thresholdSuggestions = listOf(ThresholdSuggestionOut(ContextSlot.PRE_SLEEP, 7, "길어서"))),
            good.copy(viewerTypeLine = "완전 중독형"),
            good.copy(summaryLine = "{secret} 봤어"),
        )
        bad.forEach { assertIs<Validation.Invalid>(validator.validate(it, input), it.toString()) }
    }
}

class SmallValidatorsTest {
    @Test
    fun contextCheck() {
        assertIs<Validation.Valid<ContextCheckOut>>(
            ContextCheckValidator.validate(ContextCheckOut("{timeRange}에 밥 먹으면서야?", listOf("맞아", "아니"))),
        )
        assertIs<Validation.Invalid>(
            ContextCheckValidator.validate(ContextCheckOut("12시쯤 밥 먹으면서야?", listOf("맞아", "아니"))),
        )
        assertIs<Validation.Invalid>(ContextCheckValidator.validate(ContextCheckOut("{timeRange}에 밥?", listOf("맞아"))))
    }

    @Test
    fun pokeKeepsOnlyGoodOptions() {
        val v = PokeDraftValidator.validate(
            PokeDraftOut(listOf("{friend}! 이제 자러 가 🙃", "{friend} 벌써 40분째", "{nickname} 그만", "넌 한심해")),
        )
        assertIs<Validation.Valid<PokeDraftOut>>(v)
        assertEquals(listOf("{friend}! 이제 자러 가 🙃"), v.value.options)
        assertIs<Validation.Invalid>(PokeDraftValidator.validate(PokeDraftOut(listOf("가".repeat(41)))))
        assertTrue(!PokeDraftValidator.acceptsReceived("병신아 그만 봐"))
        assertTrue(PokeDraftValidator.acceptsReceived("내일 9시 수업이야 자!"))
    }
}

class CopyPackValidatorTest {
    private val validator = CopyPackValidator(TestData.defaultPack)

    @Test
    fun defaultPackPasses() {
        assertIs<Validation.Valid<*>>(validator.validate(TestData.defaultPack))
    }

    @Test
    fun failingLinesAreReplacedFromDefault() {
        val pre = TestData.defaultPack.card.getValue(ContextSlot.PRE_SLEEP)
        val broken = pre.copy(l1 = listOf(CopyLine("bad1", "벌써 37분째야", setOf(Tone.PLAYFUL)), pre.l1[0], pre.l1[1]))
        val pack = TestData.defaultPack.copy(card = TestData.defaultPack.card + (ContextSlot.PRE_SLEEP to broken))
        val v = validator.validate(pack)
        assertIs<Validation.Valid<*>>(v)
        val l1 = (v.value as com.capstone.pause.core.model.CopyPack).card.getValue(ContextSlot.PRE_SLEEP).l1
        assertTrue(l1.none { it.id == "bad1" })
        assertTrue(l1.size >= CopyPackValidator.MIN_L1)
    }

    @Test
    fun mostlyBrokenPackIsInvalid() {
        val bad = CopyLine("x", "중독이야 37분", setOf(Tone.PLAIN))
        val broken = TestData.defaultPack.copy(
            card = TestData.defaultPack.card.mapValues { (_, c) ->
                c.copy(l1 = listOf(bad, bad), l2 = listOf(bad), l3 = listOf(bad), l4Question = listOf(bad))
            },
            buddyMessage = listOf(bad),
            praise = listOf(bad),
            chipReply = TestData.defaultPack.chipReply.mapValues { listOf(bad) },
        )
        assertIs<Validation.Invalid>(validator.validate(broken))
    }
}

class CoachReplyValidatorTest {
    private val evidence = ToolEvidence.from(
        listOf(
            """{"key":"THIS_WEEK.summary","avgMinPerDay":96,"reductionPercent":23}""",
            """{"sessions":[{"id":101,"minutes":32}]}""",
        ),
    )

    @Test
    fun numbersFromToolsPass() {
        val ok = CoachReplyOut(
            "이번 주 하루 평균 96분이야. 1시간 36분이지. 기준보다 23% 줄었어.",
            listOf(EvidenceOut("stat", key = "THIS_WEEK.summary"), EvidenceOut("session", id = 101)),
        )
        assertIs<Validation.Valid<CoachReplyOut>>(CoachReplyValidator.validate(ok, evidence))
    }

    @Test
    fun hallucinatedNumbersAndEvidenceFail() {
        assertIs<Validation.Invalid>(CoachReplyValidator.validate(CoachReplyOut("하루 평균 150분이야."), evidence))
        assertIs<Validation.Invalid>(
            CoachReplyValidator.validate(CoachReplyOut("봐 봐.", listOf(EvidenceOut("session", id = 999))), evidence),
        )
        assertIs<Validation.Invalid>(
            CoachReplyValidator.validate(CoachReplyOut("봐 봐.", listOf(EvidenceOut("stat", key = "made.up"))), evidence),
        )
        assertIs<Validation.Invalid>(CoachReplyValidator.validate(CoachReplyOut("하나. 둘. 셋. 넷. 다섯."), evidence))
        assertIs<Validation.Invalid>(CoachReplyValidator.validate(CoachReplyOut("넌 중독이야."), evidence))
    }
}
