package com.capstone.pause.core.ai.engine.mock

import com.capstone.pause.core.ai.engine.coach.ContextBreakdownResult
import com.capstone.pause.core.ai.engine.coach.FlowsResult
import com.capstone.pause.core.ai.engine.coach.Period
import com.capstone.pause.core.ai.engine.coach.RiskWindowsResult
import com.capstone.pause.core.ai.engine.coach.StrategiesResult
import com.capstone.pause.core.ai.engine.coach.SummaryResult
import com.capstone.pause.core.ai.engine.coach.ToolNames
import com.capstone.pause.core.ai.engine.llm.ChatMessage
import com.capstone.pause.core.ai.engine.llm.ChatRequest
import com.capstone.pause.core.ai.engine.llm.ChatResult
import com.capstone.pause.core.ai.engine.llm.ToolCall
import com.capstone.pause.core.ai.engine.schema.AiJson
import com.capstone.pause.core.ai.engine.schema.CoachReplyOut
import com.capstone.pause.core.ai.engine.schema.EvidenceOut
import com.capstone.pause.core.ai.engine.schema.PlanProposalOut
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.Strategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 모의 코치: 추천 질문 5개 의도별로 도구를 부르고, 도구 결과 숫자로 틀을 채운다. */
internal class MockCoach(private val t: MockTemplates) {
    /** 질문 의도. */
    enum class Intent { WHEN, WHY, COMMUTE, DIFF, TYPE, SUMMARY }

    /** 한 단계: 마지막 사용자 말 뒤에 도구 결과가 없으면 도구 호출, 있으면 최종 답. */
    fun step(req: ChatRequest, strategies: List<Strategy>): ChatResult {
        val lastUser = req.messages.indexOfLast { it is ChatMessage.User }
        val userText = (req.messages.getOrNull(lastUser) as? ChatMessage.User)?.text.orEmpty()
        val intent = intentOf(userText)
        val results = req.messages.drop(lastUser + 1).filterIsInstance<ChatMessage.ToolResult>()
        if (results.isEmpty()) return ChatResult.ToolCalls(callsFor(intent), model = null)
        val reply = answer(intent, results)
        return ChatResult.Final(AiJson.encodeToString(CoachReplyOut.serializer(), reply), model = null)
    }

    fun intentOf(text: String): Intent = when {
        listOf("출근", "출퇴근", "이동", "지하철", "등교", "버스").any { it in text } -> Intent.COMMUTE
        listOf("지난주", "달라").any { it in text } -> Intent.DIFF
        "유형" in text -> Intent.TYPE
        listOf("왜", "못 끊", "못끊").any { it in text } -> Intent.WHY
        listOf("언제", "제일 많이", "시간대", "많이 봐").any { it in text } -> Intent.WHEN
        else -> Intent.SUMMARY
    }

    private fun period(p: Period) = buildJsonObject { put("period", p.name) }

    private fun callsFor(intent: Intent): List<ToolCall> {
        val calls = when (intent) {
            Intent.WHEN -> listOf(
                ToolNames.CONTEXT_BREAKDOWN to period(Period.THIS_WEEK),
                ToolNames.RISK_WINDOWS to JsonObject(emptyMap()),
            )

            Intent.WHY -> listOf(
                ToolNames.CONTEXT_BREAKDOWN to period(Period.THIS_WEEK),
                ToolNames.FLOWS to buildJsonObject {
                    put("period", Period.THIS_WEEK.name)
                    put("limit", FLOW_LIMIT)
                },
            )

            Intent.COMMUTE -> listOf(
                ToolNames.SUGGEST_STRATEGIES to buildJsonObject {
                    put("context", ContextSlot.COMMUTE.name)
                },
            )

            Intent.DIFF -> listOf(
                ToolNames.SUMMARY to period(Period.THIS_WEEK),
                ToolNames.SUMMARY to period(Period.LAST_WEEK),
            )

            Intent.TYPE -> listOf(ToolNames.CONTEXT_BREAKDOWN to period(Period.THIS_WEEK))

            Intent.SUMMARY -> listOf(ToolNames.SUMMARY to period(Period.THIS_WEEK))
        }
        return calls.mapIndexed { i, (name, args) -> ToolCall("mock-${i + 1}", name, args) }
    }

    private inline fun <reified T> List<ChatMessage.ToolResult>.decode(name: String): List<T> =
        filter { it.name == name }.mapNotNull { runCatching { AiJson.decodeFromString<T>(it.json) }.getOrNull() }

    private fun fill(template: String, values: Map<String, Any>): String =
        values.entries.fold(template) { acc, (k, v) -> acc.replace("%$k%", v.toString()) }

    private fun answer(intent: Intent, results: List<ChatMessage.ToolResult>): CoachReplyOut {
        val evidence = mutableListOf<EvidenceOut>()
        val ctx = results.decode<ContextBreakdownResult>(ToolNames.CONTEXT_BREAKDOWN).firstOrNull()
            ?.items?.maxByOrNull { it.minutes }
        val text: String? = when (intent) {
            Intent.WHEN -> {
                val risk = results.decode<RiskWindowsResult>(
                    ToolNames.RISK_WINDOWS,
                ).firstOrNull()?.windows?.firstOrNull()
                if (ctx == null || risk == null) {
                    null
                } else {
                    evidence += EvidenceOut("stat", key = ctx.key)
                    evidence += EvidenceOut("stat", key = risk.key)
                    fill(
                        t.coach.`when`,
                        mapOf(
                            "ctx" to label(ctx.context),
                            "ctxMin" to ctx.minutes,
                            "day" to day(risk.dayOfWeek),
                            "hour" to risk.hour,
                        ),
                    )
                }
            }

            Intent.WHY -> {
                val flow = results.decode<FlowsResult>(ToolNames.FLOWS).firstOrNull()?.flows?.firstOrNull()
                if (ctx == null || flow == null) {
                    null
                } else {
                    evidence += EvidenceOut("stat", key = ctx.key)
                    evidence += EvidenceOut("stat", key = flow.key)
                    val entry = t.coach.entryLabels[flow.entry] ?: t.coach.entryLabels.getValue("OTHER")
                    fill(
                        t.coach.why,
                        mapOf(
                            "ctx" to label(ctx.context),
                            "ctxMin" to ctx.minutes,
                            "entry" to entry,
                            "flowCount" to flow.count,
                        ),
                    )
                }
            }

            Intent.COMMUTE -> results.decode<StrategiesResult>(
                ToolNames.SUGGEST_STRATEGIES,
            ).firstOrNull()?.strategies?.firstOrNull()?.let { s ->
                evidence += EvidenceOut("stat", key = s.key)
                fill(t.coach.commute, mapOf("then" to s.thenText, "if" to s.ifText))
            }

            Intent.DIFF -> {
                val s = results.decode<SummaryResult>(ToolNames.SUMMARY)
                val now = s.firstOrNull { it.period == Period.THIS_WEEK }
                val last = s.firstOrNull { it.period == Period.LAST_WEEK }
                if (now == null || last == null) {
                    null
                } else {
                    evidence += EvidenceOut("stat", key = now.key)
                    evidence += EvidenceOut("stat", key = last.key)
                    fill(t.coach.diff, mapOf("thisAvg" to now.avgMinPerDay, "lastAvg" to last.avgMinPerDay))
                }
            }

            Intent.TYPE -> ctx?.let {
                evidence += EvidenceOut("stat", key = it.key)
                fill(t.coach.type, mapOf("ctx" to label(it.context), "ctxMin" to it.minutes))
            }

            Intent.SUMMARY -> results.decode<SummaryResult>(ToolNames.SUMMARY).firstOrNull()?.let {
                evidence += EvidenceOut("stat", key = it.key)
                fill(t.coach.summary, mapOf("avg" to it.avgMinPerDay, "sessions" to it.sessionCount))
            }
        }
        val plan = if (intent == Intent.COMMUTE) {
            results.decode<StrategiesResult>(ToolNames.SUGGEST_STRATEGIES).firstOrNull()?.strategies?.firstOrNull()
                ?.let { PlanProposalOut(it.ifText, it.thenText) }
        } else {
            null
        }
        val followUps = t.coach.followUps.filterIndexed { i, _ -> i != intent.ordinal }.take(2)
        return CoachReplyOut(text ?: t.coach.unknown, if (text == null) emptyList() else evidence, followUps, plan)
    }

    private fun label(slot: ContextSlot) = t.contextLabels[slot] ?: slot.name

    /** 도구 결과의 요일은 "MON"처럼 줄인 값일 수 있어 앞부분으로도 찾는다. */
    private fun day(name: String) = t.coach.dayLabels[name]
        ?: t.coach.dayLabels.entries.firstOrNull { name.length >= 3 && it.key.startsWith(name) }?.value
        ?: name

    private companion object {
        val FLOW_LIMIT = JsonPrimitive(3)
    }
}
