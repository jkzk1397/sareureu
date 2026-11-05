package com.capstone.pause.core.ai.engine.coach

import com.capstone.pause.core.ai.engine.llm.AiFeature
import com.capstone.pause.core.ai.engine.llm.ChatMessage
import com.capstone.pause.core.ai.engine.llm.ChatRequest
import com.capstone.pause.core.ai.engine.llm.ChatResult
import com.capstone.pause.core.ai.engine.llm.LlmClient
import com.capstone.pause.core.ai.engine.llm.ToolCall
import com.capstone.pause.core.ai.engine.prompt.PromptRegistry
import com.capstone.pause.core.ai.engine.run.AiCallRecord
import com.capstone.pause.core.ai.engine.run.CallOutcome
import com.capstone.pause.core.ai.engine.run.InputHash
import com.capstone.pause.core.ai.engine.safety.HelpLine
import com.capstone.pause.core.ai.engine.safety.SafetyFilter
import com.capstone.pause.core.ai.engine.safety.SafetyResult
import com.capstone.pause.core.ai.engine.schema.AiJson
import com.capstone.pause.core.ai.engine.schema.CoachReplyOut
import com.capstone.pause.core.ai.engine.schema.ProfileSummary
import com.capstone.pause.core.ai.engine.validate.CoachReplyValidator
import com.capstone.pause.core.ai.engine.validate.ToolEvidence
import com.capstone.pause.core.ai.engine.validate.Validation
import java.time.Clock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/** 코치 한 턴의 결과. */
sealed interface CoachTurn {
    /** 위기 표현: AI를 부르지 않았다. 도움 카드를 띄운다. */
    data class Crisis(val helpLines: List<HelpLine>) : CoachTurn

    /** 답. [toolResults]는 근거 칩을 그릴 때 쓴다. [record]는 감사 로그로 저장한다. */
    data class Reply(
        val reply: CoachReplyOut,
        val toolResults: List<ChatMessage.ToolResult>,
        val record: AiCallRecord,
        /** 이번 턴 사용자 말(가린 것). 대화 기록에 이것을 저장한다. */
        val maskedUserText: String,
    ) : CoachTurn
}

/**
 * 코치 채팅 도구 호출 루프(05 문서 5.4절, 03 문서 3.4절): 최대 3번 도구 호출 → 최종 JSON → 검증
 * → 실패하면 이유를 붙여 한 번 더 → 또 실패하면 모의 제공자로 처음부터.
 */
class CoachEngine(
    private val tools: CoachTools,
    private val registry: PromptRegistry,
    private val fallback: LlmClient,
    private val clock: Clock,
    private val buddyName: String,
) {
    /** 한 턴. [history]는 최근 대화(최대 10턴)이고 이번 사용자 말은 [userText]. */
    suspend fun reply(
        client: LlmClient,
        history: List<ChatMessage>,
        userText: String,
        profile: ProfileSummary,
    ): CoachTurn {
        val safety = SafetyFilter.check(userText)
        if (safety is SafetyResult.Crisis) return CoachTurn.Crisis(safety.helpLines)
        val masked = (safety as SafetyResult.Safe).maskedText
        val template = registry.current(AiFeature.COACH_CHAT)
        val system = template.renderParts(
            mapOf(
                "buddy_name" to buddyName,
                "tone" to profile.tone.name,
                "profile_json" to AiJson.encodeToString(ProfileSummary.serializer(), profile),
            ),
        ).system
        val base =
            history.takeLast(MAX_HISTORY) +
                ChatMessage.User(
                    "<user_text>${com.capstone.pause.core.ai.engine.prompt.PromptTemplate.fence(masked)}</user_text>",
                )
        val started = System.nanoTime()
        var reasons: List<String> = emptyList()
        var tokens = 0
        var usedClient = client
        var outcome = CallOutcome.PASSED

        for (attempt in 0 until MAX_ATTEMPTS + 1) {
            val c = if (attempt < MAX_ATTEMPTS && client.isAvailable) client else fallback
            usedClient = c
            if (c === fallback && attempt > 0) outcome = CallOutcome.MOCK_FALLBACK
            val start = if (attempt == 1 && reasons.isNotEmpty()) base + ChatMessage.User(retryNote(reasons)) else base
            val loop = runLoop(c, system, start)
            tokens += loop.tokens
            val final = loop.finalText
            if (final == null) {
                reasons = listOf(loop.failure ?: "최종 답 없음")
                continue
            }
            val parsed = runCatching {
                AiJson.decodeFromString(CoachReplyOut.serializer(), stripFence(final))
            }.getOrNull()
            if (parsed == null) {
                reasons = listOf("스키마: JSON을 읽을 수 없음")
                continue
            }
            when (val v = CoachReplyValidator.validate(parsed, ToolEvidence.from(loop.results.map { it.json }))) {
                is Validation.Valid -> {
                    if (attempt == 1 && c !== fallback) outcome = CallOutcome.RETRIED_PASSED
                    return CoachTurn.Reply(
                        v.value,
                        loop.results,
                        record(
                            usedClient,
                            outcome,
                            reasons.joinToString("; ").ifEmpty {
                                null
                            },
                            started,
                            tokens,
                            masked,
                            final,
                        ),
                        masked,
                    )
                }

                is Validation.Invalid -> reasons = v.reasons
            }
        }
        // 모의 제공자도 실패한 경우(도구 결과가 비었을 때 등): 고정 "모름" 답.
        return CoachTurn.Reply(
            CoachReplyOut(answer = UNKNOWN_FALLBACK),
            emptyList(),
            record(usedClient, CallOutcome.MOCK_FALLBACK, reasons.joinToString("; "), started, tokens, masked, null),
            masked,
        )
    }

    private class Loop(
        val finalText: String?,
        val results: List<ChatMessage.ToolResult>,
        val tokens: Int,
        val failure: String?,
    )

    private suspend fun runLoop(client: LlmClient, system: String, start: List<ChatMessage>): Loop {
        val messages = start.toMutableList()
        val results = mutableListOf<ChatMessage.ToolResult>()
        var calls = 0
        var tokens = 0
        repeat(MAX_TOOL_ROUNDS + 1) {
            val res =
                withTimeoutOrNull(TIMEOUT_MS) { client.chat(ChatRequest(system, messages, ToolNames.DECLARATIONS)) }
                    ?: return Loop(null, results, tokens, "시간 초과")
            when (res) {
                is ChatResult.Final -> return Loop(res.text, results, tokens + (res.tokens ?: 0), null)

                is ChatResult.Failure -> return Loop(null, results, tokens, res.reason)

                is ChatResult.ToolCalls -> {
                    tokens += res.tokens ?: 0
                    val allowed = res.calls.take((MAX_TOOL_CALLS - calls).coerceAtLeast(0))
                    if (allowed.isEmpty()) return Loop(null, results, tokens, "도구 호출 한도 초과")
                    messages += ChatMessage.Model(null, allowed)
                    allowed.forEach { call ->
                        calls++
                        val r = ChatMessage.ToolResult(call.id, call.name, execute(call))
                        results += r
                        messages += r
                    }
                }
            }
        }
        return Loop(null, results, tokens, "도구 호출 한도 초과")
    }

    /** 도구 실행(읽기 전용). 모르는 도구는 오류 JSON. */
    suspend fun execute(call: ToolCall): String {
        val a = call.args
        return when (call.name) {
            ToolNames.SUMMARY -> json.encodeToString(SummaryResult.serializer(), tools.getSummary(ToolNames.period(a)))

            ToolNames.CONTEXT_BREAKDOWN ->
                json.encodeToString(ContextBreakdownResult.serializer(), tools.getContextBreakdown(ToolNames.period(a)))

            ToolNames.SESSIONS -> json.encodeToString(
                SessionsResult.serializer(),
                tools.getSessions(
                    SessionQuery(
                        period = ToolNames.period(a),
                        context = ToolNames.context(a),
                        app = a["app"]?.toString()?.trim('"'),
                        minMinutes = ToolNames.int(a, "minMinutes"),
                        limit = (
                            ToolNames.int(
                                a,
                                "limit",
                            ) ?: SessionQuery.MAX_SESSIONS
                            ).coerceIn(1, SessionQuery.MAX_SESSIONS),
                    ),
                ),
            )

            ToolNames.FLOWS -> json.encodeToString(
                FlowsResult.serializer(),
                tools.getFlows(ToolNames.period(a), (ToolNames.int(a, "limit") ?: MAX_FLOWS).coerceIn(1, MAX_FLOWS)),
            )

            ToolNames.RISK_WINDOWS -> json.encodeToString(RiskWindowsResult.serializer(), tools.getRiskWindows())

            ToolNames.PLANS -> json.encodeToString(PlansResult.serializer(), tools.getPlans())

            ToolNames.PEER_BASELINE -> json.encodeToString(PeerBaselineResult.serializer(), tools.getPeerBaseline())

            ToolNames.SUGGEST_STRATEGIES ->
                json.encodeToString(StrategiesResult.serializer(), tools.suggestStrategies(ToolNames.context(a)))

            else -> """{"error":"unknown tool"}"""
        }
    }

    private fun record(
        client: LlmClient,
        outcome: CallOutcome,
        reason: String?,
        startedNanos: Long,
        tokens: Int,
        input: String,
        output: String?,
    ) = AiCallRecord(
        at = clock.instant(),
        feature = AiFeature.COACH_CHAT,
        promptId = AiFeature.COACH_CHAT.promptId,
        promptVersion = PromptRegistry.CURRENT.getValue(AiFeature.COACH_CHAT),
        provider = client.provider,
        model = client.model,
        inputHash = InputHash.of(input),
        inputLength = input.length,
        outputJson = output,
        outcome = outcome,
        reason = reason,
        latencyMs = (System.nanoTime() - startedNanos) / NANOS_PER_MS,
        tokens = tokens.takeIf { it > 0 },
    )

    private fun retryNote(reasons: List<String>) =
        "<validator_errors>${reasons.joinToString("; ")}</validator_errors> 위 규칙 위반을 고쳐 JSON만 다시 써 줘."

    private fun stripFence(text: String) =
        text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

    private companion object {
        val json = Json { encodeDefaults = true }
        const val MAX_HISTORY = 20
        const val MAX_ATTEMPTS = 2
        const val MAX_TOOL_ROUNDS = 3
        const val MAX_TOOL_CALLS = 3
        const val MAX_FLOWS = 5
        const val TIMEOUT_MS = 20_000L
        const val NANOS_PER_MS = 1_000_000L

        // 화면 문구가 아니라 마지막 안전망. 화면은 이 답을 받으면 strings.xml의 "모름" 문구로 바꿔도 된다.
        const val UNKNOWN_FALLBACK = "..."
    }
}
