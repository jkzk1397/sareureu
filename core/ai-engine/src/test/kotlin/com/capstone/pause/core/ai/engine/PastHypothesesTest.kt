package com.capstone.pause.core.ai.engine

import com.capstone.pause.core.ai.engine.llm.AiFeature
import com.capstone.pause.core.ai.engine.mock.MockLlmClient
import com.capstone.pause.core.ai.engine.prompt.PromptRegistry
import com.capstone.pause.core.ai.engine.run.AiRunner
import com.capstone.pause.core.ai.engine.run.CallOutcome
import com.capstone.pause.core.ai.engine.schema.PastHypothesis
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** 지난 추측과 답(맞아/반쯤/아니야)을 다음 주간 리포트 입력으로(weekly_report v6). */
class PastHypothesesTest {
    private val mock = MockLlmClient(TestData.defaultPack, TestData.strategies)
    private val first = mock.weekly(TestData.weeklyInput()).hypotheses

    private fun withPast(vararg past: PastHypothesis) = TestData.weeklyInput().copy(pastHypotheses = past.toList())

    @Test
    fun mockDropsHypothesisAnsweredNo() {
        val rejected = first.first().text
        val out = mock.weekly(withPast(PastHypothesis(rejected, "NO"))).hypotheses
        assertTrue(out.isNotEmpty())
        assertFalse(out.any { it.text == rejected }, "아니야라고 한 추측은 되풀이하지 않는다")
    }

    @Test
    fun mockPutsConfirmedHypothesisFirst() {
        val confirmed = first.last().text
        val out = mock.weekly(withPast(PastHypothesis(confirmed, "YES"))).hypotheses
        assertEquals(confirmed, out.first().text)
        assertEquals(first.size, out.size)
    }

    @Test
    fun noFeedbackKeepsOutputUnchanged() {
        assertEquals(first, mock.weekly(withPast(PastHypothesis("상관없는 추측", null))).hypotheses)
    }

    @Test
    fun mockOutputWithFeedbackStillPassesValidator() = runTest {
        val runner =
            AiRunner(PromptRegistry(), mock, TestData.defaultPack, TestData.strategies, TestData.clock, TestData.BUDDY)
        val everyNo = withPast(*first.map { PastHypothesis(it.text, "NO") }.toTypedArray())
        assertEquals(CallOutcome.PASSED, runner.weeklyReport(mock, everyNo).record.outcome)
    }

    @Test
    fun currentWeeklyPromptCarriesFeedbackRules() {
        val prompt = PromptRegistry().current(AiFeature.WEEKLY_REPORT)
        assertEquals(6, prompt.version)
        assertTrue("NO라고 답한 추측은 되풀이하지 않는다" in prompt.body)
        assertTrue("YES라고 답한 추측은 이어 간다" in prompt.body)
    }
}
