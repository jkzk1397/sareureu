package com.capstone.pause.core.ai

import com.capstone.pause.core.ai.coach.EvidenceResolver
import com.capstone.pause.core.ai.coach.EvidenceView
import com.capstone.pause.core.ai.engine.coach.ContextBreakdownResult
import com.capstone.pause.core.ai.engine.coach.ContextItem
import com.capstone.pause.core.ai.engine.coach.Period
import com.capstone.pause.core.ai.engine.coach.SessionItem
import com.capstone.pause.core.ai.engine.coach.SessionsResult
import com.capstone.pause.core.ai.engine.coach.ToolNames
import com.capstone.pause.core.ai.engine.llm.AiFeature
import com.capstone.pause.core.ai.engine.llm.ChatMessage
import com.capstone.pause.core.ai.engine.llm.Provider
import com.capstone.pause.core.ai.engine.run.AiCallRecord
import com.capstone.pause.core.ai.engine.run.CallOutcome
import com.capstone.pause.core.ai.engine.schema.EvidenceOut
import com.capstone.pause.core.ai.log.AiCallMapping
import com.capstone.pause.core.ai.router.AiRouting
import com.capstone.pause.core.data.settings.AiMode
import com.capstone.pause.core.model.ContextSlot
import java.time.Instant
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import org.junit.Test

class AiRoutingTest {
    private data class Case(
        val mode: AiMode,
        val consented: Boolean,
        val cloud: Boolean,
        val onDevice: Boolean,
        val foreground: Boolean,
        val expected: Provider,
    )

    @Test
    fun decisionTable() {
        val cases = listOf(
            Case(AiMode.CLOUD, true, true, false, true, Provider.CLOUD),
            Case(AiMode.CLOUD, true, false, true, true, Provider.MOCK), // Firebase 없음 → 모의(기기 안으로 바꾸지 않음)
            Case(AiMode.CLOUD, false, true, true, true, Provider.MOCK), // 동의 없음
            Case(AiMode.CLOUD, true, true, true, false, Provider.MOCK), // 앱이 화면에 없음
            Case(AiMode.ON_DEVICE, true, true, true, true, Provider.ON_DEVICE),
            Case(AiMode.ON_DEVICE, true, true, false, true, Provider.MOCK), // 기기 안을 고른 사람 입력은 클라우드로 보내지 않음
            Case(AiMode.OFF, true, true, true, true, Provider.MOCK),
        )
        cases.forEach { c ->
            assertEquals(
                c.expected,
                AiRouting.decide(c.mode, c.consented, c.cloud, c.onDevice, c.foreground),
                c.toString(),
            )
        }
    }
}

class AiCallMappingTest {
    @Test
    fun roundTripKeepsEveryField() {
        val record = AiCallRecord(
            at = Instant.parse("2026-11-03T12:00:00Z"),
            feature = AiFeature.WEEKLY_REPORT,
            promptId = "weekly_report",
            promptVersion = 1,
            provider = Provider.CLOUD,
            model = "gemini-3.5-flash-lite",
            inputHash = "abcd",
            inputLength = 120,
            outputJson = "{}",
            outcome = CallOutcome.RETRIED_PASSED,
            reason = "스키마",
            latencyMs = 830,
            tokens = 512,
        )
        val row = AiCallMapping.toRow(record).copy(id = 7, reported = true)
        val entry = AiCallMapping.toEntry(row)
        assertEquals(record, entry.record)
        assertEquals(7, entry.id)
        assertEquals(true, entry.reported)
    }

    @Test
    fun unknownNamesFallBackSafely() {
        val row = AiCallMapping.toRow(
            AiCallRecord(
                Instant.EPOCH, AiFeature.COACH_CHAT, "c", 1, Provider.MOCK, null, "h", 0, null,
                CallOutcome.PASSED, null, 0, null,
            ),
        ).copy(provider = "SOMETHING", validation = "NEW")
        val entry = AiCallMapping.toEntry(row)
        assertEquals(Provider.MOCK, entry.record.provider)
        assertEquals(CallOutcome.SKIPPED, entry.record.outcome)
    }
}

class EvidenceResolverTest {
    private val json = Json

    @Test
    fun resolvesSessionsAndStatsFromToolResultsAndDropsUnknown() {
        val sessions = SessionsResult(
            listOf(SessionItem(42, "TUE", 23, 40, 32, "YOUTUBE", ContextSlot.PRE_SLEEP, "MESSENGER", "SCREEN_OFF", 2)),
        )
        val contexts = ContextBreakdownResult(
            Period.THIS_WEEK,
            listOf(ContextItem("ctx_PRE_SLEEP_THIS_WEEK", ContextSlot.PRE_SLEEP, 208, 12, 9)),
        )
        val results = listOf(
            ChatMessage.ToolResult("1", ToolNames.SESSIONS, json.encodeToString(SessionsResult.serializer(), sessions)),
            ChatMessage.ToolResult(
                "2",
                ToolNames.CONTEXT_BREAKDOWN,
                json.encodeToString(ContextBreakdownResult.serializer(), contexts),
            ),
        )
        val views = EvidenceResolver.resolve(
            listOf(
                EvidenceOut("session", id = 42),
                EvidenceOut("stat", key = "ctx_PRE_SLEEP_THIS_WEEK"),
                EvidenceOut("stat", key = "made_up"),
                EvidenceOut("session", id = 999),
            ),
            results,
        )
        assertEquals(
            listOf(
                EvidenceView.Session(42, "TUE", 23, 40, "YOUTUBE", 32),
                EvidenceView.Context(ContextSlot.PRE_SLEEP, 208),
            ),
            views,
        )
    }
}
