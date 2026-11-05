package com.capstone.pause.core.ai.engine.run

import com.capstone.pause.core.ai.engine.llm.AiFeature
import com.capstone.pause.core.ai.engine.llm.JsonRequest
import com.capstone.pause.core.ai.engine.llm.LlmClient
import com.capstone.pause.core.ai.engine.llm.LlmResult
import com.capstone.pause.core.ai.engine.prompt.PromptRegistry
import com.capstone.pause.core.ai.engine.safety.SafetyFilter
import com.capstone.pause.core.ai.engine.schema.AiInput
import com.capstone.pause.core.ai.engine.schema.AiJson
import com.capstone.pause.core.ai.engine.schema.ContextCheckInput
import com.capstone.pause.core.ai.engine.schema.ContextCheckOut
import com.capstone.pause.core.ai.engine.schema.CopyPackInput
import com.capstone.pause.core.ai.engine.schema.PokeDraftInput
import com.capstone.pause.core.ai.engine.schema.PokeDraftOut
import com.capstone.pause.core.ai.engine.schema.ProfileSummary
import com.capstone.pause.core.ai.engine.schema.WeeklyReportInput
import com.capstone.pause.core.ai.engine.schema.WeeklyReportOut
import com.capstone.pause.core.ai.engine.validate.ContextCheckValidator
import com.capstone.pause.core.ai.engine.validate.CopyPackValidator
import com.capstone.pause.core.ai.engine.validate.PokeDraftValidator
import com.capstone.pause.core.ai.engine.validate.TextRules
import com.capstone.pause.core.ai.engine.validate.Validation
import com.capstone.pause.core.ai.engine.validate.WeeklyReportValidator
import com.capstone.pause.core.model.CopyPack
import com.capstone.pause.core.model.Strategy
import java.time.Clock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer

/** 검증을 통과한 결과와 감사 기록. [value]는 항상 화면에 보여도 되는 값이다(통과본 또는 모의). */
data class AiOutcome<T>(val value: T, val record: AiCallRecord)

/**
 * 생성 기능 실행기(05 문서 2절): 프롬프트 채우기 → 호출(20초) → JSON 읽기 → 검증
 * → 실패하면 이유를 덧붙여 한 번 더 → 또 실패하면 모의 결과. 결과마다 [AiCallRecord]를 만든다.
 */
class AiRunner(
    private val registry: PromptRegistry,
    private val mock: LlmClient,
    defaultPack: CopyPack,
    private val strategies: List<Strategy>,
    private val clock: Clock,
    private val buddyName: String,
) {
    private val weeklyValidator = WeeklyReportValidator(strategies.map { it.id }.toSet())
    private val copyValidator = CopyPackValidator(defaultPack)

    /** 주간 리포트. 메모에 위기 표현이 있으면 메모를 빼고 부른다. */
    suspend fun weeklyReport(client: LlmClient, input: WeeklyReportInput): AiOutcome<WeeklyReportOut> {
        val safeInput = input.copy(userNotes = safeNotes(input.userNotes))
        val vars = common(safeInput.profile) + mapOf(
            "observations_json" to AiJson.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(
                    com.capstone.pause.core.ai.engine.schema.ObservationIn.serializer(),
                ),
                safeInput.observations,
            ),
            "viewer_type" to safeInput.viewerType,
            "strategies_json" to AiJson.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(Strategy.serializer()),
                strategies,
            ),
            "past_json" to AiJson.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(
                    com.capstone.pause.core.ai.engine.schema.PastHypothesis.serializer(),
                ),
                safeInput.pastHypotheses,
            ),
            "user_notes" to safeInput.userNotes,
        )
        return run(client, AiFeature.WEEKLY_REPORT, safeInput, vars, WeeklyReportOut.serializer()) {
            weeklyValidator.validate(it, safeInput)
        }
    }

    /** 다음 주 문구 팩. 실패한 줄만 기본 팩으로 채운다. */
    suspend fun copyPack(client: LlmClient, input: CopyPackInput): AiOutcome<CopyPack> {
        val vars = common(input.profile) + mapOf("top_contexts" to input.topContexts.joinToString(",") { it.name })
        return run(client, AiFeature.COPY_PACK, input, vars, CopyPack.serializer()) { copyValidator.validate(it) }
    }

    /** 맥락 확인 질문. */
    suspend fun contextCheck(client: LlmClient, input: ContextCheckInput): AiOutcome<ContextCheckOut> {
        val vars = common(input.profile) + mapOf(
            "candidates" to input.candidates.joinToString(",") { it.name },
            "session_count" to input.sessionCount.toString(),
            "weekday" to input.weekday.toString(),
        )
        return run(client, AiFeature.CONTEXT_CHECK, input, vars, ContextCheckOut.serializer()) {
            ContextCheckValidator.validate(it)
        }
    }

    /** 잔소리 초안. */
    suspend fun pokeDraft(client: LlmClient, input: PokeDraftInput): AiOutcome<PokeDraftOut> {
        val safeInput = input.copy(userNotes = safeNotes(input.userNotes))
        val vars = common(ProfileSummary(tone = input.tone)) + mapOf(
            "goal_hint" to (safeInput.goalHint ?: ""),
            "user_notes" to safeInput.userNotes,
        )
        return run(client, AiFeature.POKE_DRAFT, safeInput, vars, PokeDraftOut.serializer()) {
            PokeDraftValidator.validate(it)
        }
    }

    private fun common(profile: ProfileSummary) = mapOf(
        "buddy_name" to buddyName,
        "tone" to profile.tone.name,
        "profile_json" to AiJson.encodeToString(ProfileSummary.serializer(), profile),
        "placeholders" to TextRules.PLACEHOLDERS.joinToString(" ") { "{$it}" },
    )

    // 위기 표현이 든 메모는 보내지 않는다(도움 카드는 메모를 쓰는 화면이 띄운다). 개인정보는 가린다.
    private fun safeNotes(notes: String): String = when (val r = SafetyFilter.check(notes)) {
        is com.capstone.pause.core.ai.engine.safety.SafetyResult.Crisis -> ""
        is com.capstone.pause.core.ai.engine.safety.SafetyResult.Safe -> r.maskedText
    }

    private suspend fun <T> run(
        client: LlmClient,
        feature: AiFeature,
        input: AiInput,
        vars: Map<String, String>,
        serializer: KSerializer<T>,
        validate: (T) -> Validation<T>,
    ): AiOutcome<T> {
        val template = registry.current(feature)
        val parts = template.renderParts(vars)
        val inputText = parts.system + parts.user
        val started = System.nanoTime()
        var reasons: List<String> = emptyList()
        var tokens = 0

        suspend fun attempt(c: LlmClient, retryReasons: List<String>): Pair<T?, String?> {
            val user = if (retryReasons.isEmpty()) parts.user else parts.user + "\n" + retryNote(retryReasons)
            val req = JsonRequest(feature, parts.system, user, template.outputSchema, input, template.temperature)
            val res = withTimeoutOrNull(TIMEOUT_MS) { c.generateJson(req) } ?: return null to "시간 초과"
            return when (res) {
                is LlmResult.Failure -> null to res.reason

                is LlmResult.Success -> {
                    tokens += res.tokens ?: 0
                    val parsed = runCatching { AiJson.decodeFromString(serializer, stripFence(res.text)) }.getOrNull()
                        ?: return null to "스키마: JSON을 읽을 수 없음"
                    when (val v = validate(parsed)) {
                        is Validation.Valid -> v.value to null
                        is Validation.Invalid -> null to v.reasons.joinToString("; ")
                    }
                }
            }
        }

        fun record(c: LlmClient, outcome: CallOutcome, value: T?) = AiCallRecord(
            at = clock.instant(),
            feature = feature,
            promptId = template.id,
            promptVersion = template.version,
            provider = c.provider,
            model = c.model,
            inputHash = InputHash.of(inputText),
            inputLength = inputText.length,
            outputJson = value?.let { AiJson.encodeToString(serializer, it) },
            outcome = outcome,
            reason = reasons.joinToString("; ").ifEmpty { null },
            latencyMs = (System.nanoTime() - started) / NANOS_PER_MS,
            tokens = tokens.takeIf { it > 0 },
        )

        if (client.isAvailable) {
            for (i in 0 until MAX_ATTEMPTS) {
                val (value, reason) = attempt(client, reasons)
                if (value !=
                    null
                ) {
                    return AiOutcome(
                        value,
                        record(
                            client,
                            if (i ==
                                0
                            ) {
                                CallOutcome.PASSED
                            } else {
                                CallOutcome.RETRIED_PASSED
                            },
                            value,
                        ),
                    )
                }
                reasons = reasons + listOfNotNull(reason)
            }
        } else {
            reasons = listOf("제공자 사용 불가: ${client.provider}")
        }
        val (value, reason) = attempt(mock, emptyList())
        val fallbackOutcome = if (client === mock ||
            client.provider == mock.provider
        ) {
            CallOutcome.PASSED
        } else {
            CallOutcome.MOCK_FALLBACK
        }
        if (value != null) return AiOutcome(value, record(mock, fallbackOutcome, value))
        // 모의도 검증을 못 넘는 일은 없어야 한다(테스트로 지킨다). 그래도 생기면 예외로 알린다.
        throw IllegalStateException("모의 결과가 검증 실패: ${feature.promptId}: $reason")
    }

    private fun retryNote(reasons: List<String>) =
        "<validator_errors>${reasons.joinToString("; ")}</validator_errors>\n위 규칙 위반을 고쳐 JSON만 다시 써 줘."

    private fun stripFence(text: String) =
        text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

    private companion object {
        const val MAX_ATTEMPTS = 2
        const val TIMEOUT_MS = 20_000L
        const val NANOS_PER_MS = 1_000_000L
    }
}
