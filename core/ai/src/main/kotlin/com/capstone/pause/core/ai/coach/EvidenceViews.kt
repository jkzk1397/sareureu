package com.capstone.pause.core.ai.coach

import com.capstone.pause.core.ai.engine.coach.ContextBreakdownResult
import com.capstone.pause.core.ai.engine.coach.FlowsResult
import com.capstone.pause.core.ai.engine.coach.PeerBaselineResult
import com.capstone.pause.core.ai.engine.coach.PlansResult
import com.capstone.pause.core.ai.engine.coach.RiskWindowsResult
import com.capstone.pause.core.ai.engine.coach.SessionsResult
import com.capstone.pause.core.ai.engine.coach.StrategiesResult
import com.capstone.pause.core.ai.engine.coach.SummaryResult
import com.capstone.pause.core.ai.engine.coach.ToolNames
import com.capstone.pause.core.ai.engine.llm.ChatMessage
import com.capstone.pause.core.ai.engine.schema.EvidenceOut
import com.capstone.pause.core.model.ContextSlot
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * 근거 칩에 그릴 값(문구는 화면이 strings.xml로 만든다). 모두 도구 결과에서 그대로 옮긴 숫자다.
 */
sealed interface EvidenceView {
    /** 세션 하나: "화 23:40 · 유튜브 32분". [dayOfWeek]는 MON..SUN 앞 세 글자, [app]은 YOUTUBE 등 코드. */
    data class Session(
        val id: Long,
        val dayOfWeek: String,
        val hour: Int,
        val minute: Int,
        val app: String,
        val minutes: Int,
    ) : EvidenceView

    /** 기간 요약: 하루 평균. */
    data class Summary(val avgMinPerDay: Int) : EvidenceView

    /** 맥락 합계. */
    data class Context(val slot: ContextSlot, val minutes: Int) : EvidenceView

    /** 흐름: 진입 → 앱 → 이탈(코드 키). */
    data class Flow(val entry: String, val app: String, val exit: String, val count: Int) : EvidenceView

    /** 위험 시간대 칸. */
    data class Risk(val dayOfWeek: String, val hour: Int, val averageMinutes: Int) : EvidenceView

    /** 계획·전략 문장. */
    data class Text(val text: String) : EvidenceView

    /** 또래 비교. [isDemo]면 "시연용"을 붙인다. */
    data class Peer(val peerMinutes: Int?, val myMinutes: Int?, val isDemo: Boolean) : EvidenceView
}

/** 근거 ID·키를 도구 결과에서 찾아 [EvidenceView]로 바꾼다(순수). 못 찾은 근거는 버린다. */
object EvidenceResolver {
    private val json = Json { ignoreUnknownKeys = true }

    /** 변환. */
    fun resolve(evidence: List<EvidenceOut>, results: List<ChatMessage.ToolResult>): List<EvidenceView> {
        fun <T> all(name: String, serializer: KSerializer<T>): List<T> = results.filter { it.name == name }
            .mapNotNull { runCatching { json.decodeFromString(serializer, it.json) }.getOrNull() }

        val sessions = all(ToolNames.SESSIONS, SessionsResult.serializer()).flatMap { it.sessions }
        val summaries = all(ToolNames.SUMMARY, SummaryResult.serializer())
        val contexts = all(ToolNames.CONTEXT_BREAKDOWN, ContextBreakdownResult.serializer()).flatMap { it.items }
        val flows = all(ToolNames.FLOWS, FlowsResult.serializer()).flatMap { it.flows }
        val risks = all(ToolNames.RISK_WINDOWS, RiskWindowsResult.serializer()).flatMap { it.windows }
        val plans = all(ToolNames.PLANS, PlansResult.serializer()).flatMap { it.plans }
        val peers = all(ToolNames.PEER_BASELINE, PeerBaselineResult.serializer())
        val strategies = all(ToolNames.SUGGEST_STRATEGIES, StrategiesResult.serializer()).flatMap { it.strategies }

        return evidence.mapNotNull { e ->
            if (e.type == "session") {
                sessions.firstOrNull { it.id == e.id }?.let {
                    EvidenceView.Session(it.id, it.dayOfWeek, it.startHour, it.startMinute, it.app, it.minutes)
                }
            } else {
                val key = e.key ?: return@mapNotNull null
                summaries.firstOrNull { it.key == key }?.let { EvidenceView.Summary(it.avgMinPerDay) }
                    ?: contexts.firstOrNull { it.key == key }?.let { EvidenceView.Context(it.context, it.minutes) }
                    ?: flows.firstOrNull {
                        it.key == key
                    }?.let { EvidenceView.Flow(it.entry, it.app, it.exit, it.count) }
                    ?: risks.firstOrNull {
                        it.key == key
                    }?.let { EvidenceView.Risk(it.dayOfWeek, it.hour, it.averageMinutes) }
                    ?: plans.firstOrNull { it.key == key }?.let { EvidenceView.Text("${it.ifText} ${it.thenText}") }
                    ?: strategies.firstOrNull {
                        it.key == key
                    }?.let { EvidenceView.Text("${it.ifText} ${it.thenText}") }
                    ?: peers.firstOrNull { it.key == key }?.let {
                        EvidenceView.Peer(it.peerMedianMinPerDay, it.myAvgMinPerDay, it.isDemo)
                    }
            }
        }.distinct()
    }
}
