package com.capstone.pause.core.ai.engine.eval

import com.capstone.pause.core.ai.engine.coach.CoachTools
import com.capstone.pause.core.ai.engine.coach.ContextBreakdownResult
import com.capstone.pause.core.ai.engine.coach.ContextItem
import com.capstone.pause.core.ai.engine.coach.FlowItem
import com.capstone.pause.core.ai.engine.coach.FlowsResult
import com.capstone.pause.core.ai.engine.coach.PeerBaselineResult
import com.capstone.pause.core.ai.engine.coach.Period
import com.capstone.pause.core.ai.engine.coach.PlanItem
import com.capstone.pause.core.ai.engine.coach.PlansResult
import com.capstone.pause.core.ai.engine.coach.RiskItem
import com.capstone.pause.core.ai.engine.coach.RiskWindowsResult
import com.capstone.pause.core.ai.engine.coach.SessionItem
import com.capstone.pause.core.ai.engine.coach.SessionQuery
import com.capstone.pause.core.ai.engine.coach.SessionsResult
import com.capstone.pause.core.ai.engine.coach.StrategiesResult
import com.capstone.pause.core.ai.engine.coach.StrategyItem
import com.capstone.pause.core.ai.engine.coach.SummaryResult
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.Strategy
import kotlinx.serialization.Serializable

/** 평가용 고정 기록(`eval/coach_chat/fixture.json`). */
@Serializable
data class CoachFixture(
    val summaries: Map<Period, SummaryResult>,
    val contexts: List<ContextItem>,
    val flows: List<FlowItem>,
    val sessions: List<SessionItem>,
    val risk: List<RiskItem>,
    val plans: List<PlanItem>,
    val peer: PeerBaselineResult,
)

/** 고정 기록으로 답하는 도구(평가·테스트용). 기간과 상관없이 같은 맥락·흐름을 돌려준다. */
class JsonCoachTools(private val fixture: CoachFixture, private val strategies: List<Strategy>) : CoachTools {
    /** 도구가 불린 순서(평가에서 기대 도구 확인용). */
    val calls = mutableListOf<String>()

    override suspend fun getSummary(period: Period): SummaryResult {
        calls += "get_summary"
        return fixture.summaries[period] ?: fixture.summaries.getValue(Period.THIS_WEEK)
    }

    override suspend fun getContextBreakdown(period: Period): ContextBreakdownResult {
        calls += "get_context_breakdown"
        return ContextBreakdownResult(period, fixture.contexts)
    }

    override suspend fun getSessions(query: SessionQuery): SessionsResult {
        calls += "get_sessions"
        return SessionsResult(
            fixture.sessions
                .filter { query.context == null || it.context == query.context }
                .filter { query.minMinutes == null || it.minutes >= query.minMinutes }
                .take(query.limit),
        )
    }

    override suspend fun getFlows(period: Period, limit: Int): FlowsResult {
        calls += "get_flows"
        return FlowsResult(period, fixture.flows.take(limit))
    }

    override suspend fun getRiskWindows(): RiskWindowsResult {
        calls += "get_risk_windows"
        return RiskWindowsResult(fixture.risk)
    }

    override suspend fun getPlans(): PlansResult {
        calls += "get_plans"
        return PlansResult(fixture.plans)
    }

    override suspend fun getPeerBaseline(): PeerBaselineResult {
        calls += "get_peer_baseline"
        return fixture.peer
    }

    override suspend fun suggestStrategies(context: ContextSlot?): StrategiesResult {
        calls += "suggest_strategies"
        return StrategiesResult(
            strategies.filter { context == null || it.context == context || it.context == null }
                .map { StrategyItem(it.id, it.context, it.ifText, it.thenText) },
        )
    }
}
