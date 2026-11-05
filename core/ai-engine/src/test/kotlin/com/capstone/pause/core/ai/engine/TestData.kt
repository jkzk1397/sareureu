package com.capstone.pause.core.ai.engine

import com.capstone.pause.core.ai.engine.coach.CoachTools
import com.capstone.pause.core.ai.engine.coach.ContextBreakdownResult
import com.capstone.pause.core.ai.engine.coach.ContextItem
import com.capstone.pause.core.ai.engine.coach.FlowItem
import com.capstone.pause.core.ai.engine.coach.FlowsResult
import com.capstone.pause.core.ai.engine.coach.PeerBaselineResult
import com.capstone.pause.core.ai.engine.coach.Period
import com.capstone.pause.core.ai.engine.coach.PlansResult
import com.capstone.pause.core.ai.engine.coach.RiskItem
import com.capstone.pause.core.ai.engine.coach.RiskWindowsResult
import com.capstone.pause.core.ai.engine.coach.SessionItem
import com.capstone.pause.core.ai.engine.coach.SessionQuery
import com.capstone.pause.core.ai.engine.coach.SessionsResult
import com.capstone.pause.core.ai.engine.coach.StrategiesResult
import com.capstone.pause.core.ai.engine.coach.StrategyItem
import com.capstone.pause.core.ai.engine.coach.SummaryResult
import com.capstone.pause.core.ai.engine.llm.ChatRequest
import com.capstone.pause.core.ai.engine.llm.ChatResult
import com.capstone.pause.core.ai.engine.llm.JsonRequest
import com.capstone.pause.core.ai.engine.llm.LlmClient
import com.capstone.pause.core.ai.engine.llm.LlmResult
import com.capstone.pause.core.ai.engine.llm.Provider
import com.capstone.pause.core.ai.engine.schema.AiJson
import com.capstone.pause.core.ai.engine.schema.ObservationIn
import com.capstone.pause.core.ai.engine.schema.WeeklyReportInput
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.CopyPack
import com.capstone.pause.core.model.Strategy
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.builtins.ListSerializer

/** 테스트 공용 자료(작업 폴더 = core/ai-engine). */
object TestData {
    val clock: Clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC)

    val defaultPack: CopyPack by lazy {
        AiJson.decodeFromString(
            CopyPack.serializer(),
            File("../data/src/main/assets/copy/default_pack.json").readText(Charsets.UTF_8),
        )
    }

    val strategies: List<Strategy> by lazy {
        AiJson.decodeFromString(
            ListSerializer(Strategy.serializer()),
            File("src/test/resources/eval/strategies.json").readText(Charsets.UTF_8),
        )
    }

    const val BUDDY = "Buddy"

    fun weeklyInput(notes: String = "") = WeeklyReportInput(
        observations = listOf(
            ObservationIn("o1", "WEEK_TOTAL", mapOf("avgMinPerDay" to 96.0, "totalMin" to 672.0)),
            ObservationIn("o2", "REDUCTION", mapOf("reductionRate" to 0.23)),
            ObservationIn("o3", "TOP_CONTEXT", mapOf("minutes" to 208.0), mapOf("context" to "PRE_SLEEP")),
            ObservationIn("o4", "SECOND_CONTEXT", mapOf("minutes" to 161.0), mapOf("context" to "COMMUTE")),
            ObservationIn("o7", "TOP_ENTRY", mapOf("count" to 22.0), mapOf("entry" to "FROM_APP:MESSENGER")),
            ObservationIn("o8", "TOP_FLOW", mapOf("count" to 7.0)),
            ObservationIn("o12", "TOP_CONTEXT_ENTRY", mapOf("count" to 7.0), mapOf("context" to "PRE_SLEEP")),
        ),
        viewerType = "NIGHT_OWL",
        userNotes = notes,
    )
}

/** 고정 값을 돌려주는 코치 도구(테스트용). */
class FakeCoachTools : CoachTools {
    val calls = mutableListOf<String>()

    override suspend fun getSummary(period: Period): SummaryResult {
        calls += "get_summary"
        val avg = if (period == Period.LAST_WEEK) 125 else 96
        return SummaryResult("${period.name}.summary", period, avg * 7, avg, 125, 23, 91, 58, 43)
    }

    override suspend fun getContextBreakdown(period: Period): ContextBreakdownResult {
        calls += "get_context_breakdown"
        return ContextBreakdownResult(
            period,
            listOf(ContextItem("${period.name}.byContext.PRE_SLEEP", ContextSlot.PRE_SLEEP, 208, 12, 9)),
        )
    }

    override suspend fun getSessions(query: SessionQuery): SessionsResult {
        calls += "get_sessions"
        return SessionsResult(
            listOf(
                SessionItem(101, "TUESDAY", 23, 40, 32, "YOUTUBE", ContextSlot.PRE_SLEEP, "MESSENGER", "SCREEN_OFF", 3),
            ),
        )
    }

    override suspend fun getFlows(period: Period, limit: Int): FlowsResult {
        calls += "get_flows"
        return FlowsResult(period, listOf(FlowItem("flow.1", "MESSENGER", "YOUTUBE", "SCREEN_OFF", 7, 18)))
    }

    override suspend fun getRiskWindows(): RiskWindowsResult {
        calls += "get_risk_windows"
        return RiskWindowsResult(listOf(RiskItem("risk.TUE.22", "TUE", 22, 38)))
    }

    override suspend fun getPlans(): PlansResult {
        calls += "get_plans"
        return PlansResult(emptyList())
    }

    override suspend fun getPeerBaseline(): PeerBaselineResult {
        calls += "get_peer_baseline"
        return PeerBaselineResult("peer", "AGE_18_24", "UNIVERSITY", 118, 96, "demo", true)
    }

    override suspend fun suggestStrategies(context: ContextSlot?): StrategiesResult {
        calls += "suggest_strategies"
        return StrategiesResult(listOf(StrategyItem("COMMUTE_1", ContextSlot.COMMUTE, "지하철에 타면", "끝이 있는 것부터 본다")))
    }
}

/** 미리 정한 응답을 차례로 돌려주는 가짜 제공자. */
class ScriptedClient(
    private val json: List<LlmResult> = emptyList(),
    private val chats: List<ChatResult> = emptyList(),
    override val isAvailable: Boolean = true,
) : LlmClient {
    override val provider = Provider.CLOUD
    override val model = "fake"
    var jsonCalls = 0
    var chatCalls = 0
    val chatRequests = mutableListOf<ChatRequest>()

    override suspend fun generateJson(req: JsonRequest): LlmResult = json[(jsonCalls++).coerceAtMost(json.lastIndex)]

    override suspend fun chat(req: ChatRequest): ChatResult {
        chatRequests += req
        return chats[(chatCalls++).coerceAtMost(chats.lastIndex)]
    }
}
