package com.capstone.pause.core.ai.engine

import com.capstone.pause.core.ai.engine.coach.CoachEngine
import com.capstone.pause.core.ai.engine.coach.CoachTurn
import com.capstone.pause.core.ai.engine.coach.ToolNames
import com.capstone.pause.core.ai.engine.llm.ChatMessage
import com.capstone.pause.core.ai.engine.llm.ChatResult
import com.capstone.pause.core.ai.engine.llm.Provider
import com.capstone.pause.core.ai.engine.llm.ToolCall
import com.capstone.pause.core.ai.engine.mock.MockLlmClient
import com.capstone.pause.core.ai.engine.prompt.PromptRegistry
import com.capstone.pause.core.ai.engine.run.CallOutcome
import com.capstone.pause.core.ai.engine.schema.ProfileSummary
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class CoachEngineTest {
    private val mock = MockLlmClient(TestData.defaultPack, TestData.strategies)

    private fun engine(tools: FakeCoachTools) =
        CoachEngine(tools, PromptRegistry(), mock, TestData.clock, TestData.BUDDY)

    private val summaryCall = ChatResult.ToolCalls(
        listOf(ToolCall("c1", ToolNames.SUMMARY, JsonObject(mapOf("period" to JsonPrimitive("THIS_WEEK"))))),
        "fake",
    )

    @Test
    fun toolLoopThenValidatedAnswer() = runTest {
        val tools = FakeCoachTools()
        val client = ScriptedClient(
            chats = listOf(
                summaryCall,
                ChatResult.Final(
                    """{"answer":"이번 주 하루 평균 96분이야. 기준보다 23% 줄었어.","evidence":[{"type":"stat","key":"THIS_WEEK.summary"}]}""",
                    "fake",
                ),
            ),
        )
        val turn = engine(tools).reply(client, emptyList(), "이번 주 어땠어?", ProfileSummary())
        assertIs<CoachTurn.Reply>(turn)
        assertEquals(CallOutcome.PASSED, turn.record.outcome)
        assertEquals(listOf("get_summary"), tools.calls)
        assertEquals(1, turn.toolResults.size)
        // 모델에 도구 결과가 다음 요청으로 넘어갔다.
        assertTrue(client.chatRequests[1].messages.any { it is ChatMessage.ToolResult })
    }

    @Test
    fun hallucinatedNumberRetriesThenFallsBackToMock() = runTest {
        val bad = ChatResult.Final("""{"answer":"하루 평균 150분이야."}""", "fake")
        val client = ScriptedClient(chats = listOf(summaryCall, bad, summaryCall, bad))
        val turn = engine(FakeCoachTools()).reply(client, emptyList(), "나 언제 제일 많이 봐?", ProfileSummary())
        assertIs<CoachTurn.Reply>(turn)
        assertEquals(CallOutcome.MOCK_FALLBACK, turn.record.outcome)
        assertEquals(Provider.MOCK, turn.record.provider)
        assertTrue("208" in turn.reply.answer)
    }

    @Test
    fun crisisNeverCallsTheModel() = runTest {
        val client = ScriptedClient(chats = listOf(summaryCall))
        val turn = engine(FakeCoachTools()).reply(client, emptyList(), "요즘 죽고 싶어", ProfileSummary())
        assertIs<CoachTurn.Crisis>(turn)
        assertEquals(0, client.chatCalls)
    }

    @Test
    fun toolCallsAreCappedAtThree() = runTest {
        val tools = FakeCoachTools()
        val many = ChatResult.ToolCalls(
            List(5) {
                ToolCall("c$it", ToolNames.RISK_WINDOWS, JsonObject(emptyMap()))
            },
            "fake",
        )
        val client = ScriptedClient(chats = listOf(many, many, many, many))
        engine(tools).reply(client, emptyList(), "나 언제 제일 많이 봐?", ProfileSummary())
        // 실제 제공자 두 번(각 최대 3번) + 모의 대체 루프. 한 루프 안에서 3번을 넘지 않는다.
        assertTrue(client.chatRequests.all { req -> req.messages.count { it is ChatMessage.ToolResult } <= 3 })
    }

    @Test
    fun mockWritesWeekdayInKoreanEvenForShortCodes() = runTest {
        // 앱의 도구는 요일을 "TUE"처럼 줄여 준다. 모의 답에 영어 코드가 그대로 나오면 안 된다.
        val turn = engine(FakeCoachTools()).reply(mock, emptyList(), "나 언제 제일 많이 봐?", ProfileSummary())
        assertIs<CoachTurn.Reply>(turn)
        assertTrue("화요일" in turn.reply.answer, turn.reply.answer)
        assertTrue("TUE" !in turn.reply.answer, turn.reply.answer)
    }

    @Test
    fun mockAnswersSuggestedQuestionsWithEvidence() = runTest {
        listOf("나 언제 제일 많이 봐?", "왜 자기 전에 못 끊을까?", "출근길엔 뭐 하면 좋아?", "지난주랑 뭐가 달라졌어?", "내 유형은 뭐야?").forEach { q ->
            val turn = engine(FakeCoachTools()).reply(mock, emptyList(), q, ProfileSummary())
            assertIs<CoachTurn.Reply>(turn)
            assertEquals(CallOutcome.PASSED, turn.record.outcome, q)
            assertTrue(turn.reply.evidence.isNotEmpty(), q)
        }
    }

    @Test
    fun phoneNumberIsMaskedBeforeSending() = runTest {
        val turn = engine(FakeCoachTools()).reply(mock, emptyList(), "내 번호 010-1234-5678 기억해", ProfileSummary())
        assertIs<CoachTurn.Reply>(turn)
        assertTrue("010-1234-5678" !in turn.maskedUserText)
    }
}
