package com.capstone.pause.core.ai.engine.mock

import com.capstone.pause.core.ai.engine.llm.ChatRequest
import com.capstone.pause.core.ai.engine.llm.ChatResult
import com.capstone.pause.core.ai.engine.llm.JsonRequest
import com.capstone.pause.core.ai.engine.llm.LlmClient
import com.capstone.pause.core.ai.engine.llm.LlmResult
import com.capstone.pause.core.ai.engine.llm.Provider
import com.capstone.pause.core.ai.engine.schema.AiJson
import com.capstone.pause.core.ai.engine.schema.ContextCheckInput
import com.capstone.pause.core.ai.engine.schema.ContextCheckOut
import com.capstone.pause.core.ai.engine.schema.CopyPackInput
import com.capstone.pause.core.ai.engine.schema.HypothesisOut
import com.capstone.pause.core.ai.engine.schema.PokeDraftInput
import com.capstone.pause.core.ai.engine.schema.PokeDraftOut
import com.capstone.pause.core.ai.engine.schema.StrategyPickOut
import com.capstone.pause.core.ai.engine.schema.ThresholdSuggestionOut
import com.capstone.pause.core.ai.engine.schema.WeeklyReportInput
import com.capstone.pause.core.ai.engine.schema.WeeklyReportOut
import com.capstone.pause.core.ai.engine.validate.TextRules
import com.capstone.pause.core.model.Confidence
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.CopyPack
import com.capstone.pause.core.model.Strategy
import com.capstone.pause.core.model.Tone

/**
 * 규칙·틀로 만드는 모의 제공자(05 문서 8절). 같은 입력이면 같은 출력이고, 숫자는 입력·도구 결과에서만 나온다.
 * 기본 문구 팩과 전략 목록은 `core:data` 자산을 읽어 생성자로 받는다(이 모듈은 안드로이드 자산을 못 읽는다).
 */
class MockLlmClient(
    private val defaultPack: CopyPack,
    private val strategies: List<Strategy>,
    private val templates: MockTemplates = MockTemplates.load(),
) : LlmClient {
    override val provider: Provider = Provider.MOCK
    override val model: String? = null
    override val isAvailable: Boolean = true

    private val chat = MockCoach(templates)

    override suspend fun generateJson(req: JsonRequest): LlmResult = LlmResult.Success(
        text = when (val input = req.input) {
            is WeeklyReportInput -> AiJson.encodeToString(WeeklyReportOut.serializer(), weekly(input))
            is CopyPackInput -> AiJson.encodeToString(CopyPack.serializer(), defaultPack.copy(version = MOCK_VERSION))
            is ContextCheckInput -> AiJson.encodeToString(ContextCheckOut.serializer(), contextCheck(input))
            is PokeDraftInput -> AiJson.encodeToString(PokeDraftOut.serializer(), poke(input))
        },
        model = null,
    )

    override suspend fun chat(req: ChatRequest): ChatResult = chat.step(req, strategies)

    /** 주간 리포트(검증기를 통과하는 결정적 출력). */
    fun weekly(input: WeeklyReportInput): WeeklyReportOut {
        val tone = input.profile.tone
        val byId = input.observations.associateBy { it.id }
        val top =
            byId["o3"]?.labels?.get("context")?.let { name -> ContextSlot.entries.firstOrNull { it.name == name } }
                ?: ContextSlot.OTHER
        val ids = input.observations.map { it.id }

        fun evidence(vararg wanted: String): List<String> {
            val picked = wanted.filter { it in byId }.toMutableList()
            ids.filter { it !in picked }.take((2 - picked.size).coerceAtLeast(0)).forEach { picked += it }
            return picked
        }

        val hypotheses = buildList {
            val t = templates.hypothesisContext[top] ?: templates.hypothesisContext.getValue(ContextSlot.OTHER)
            add(HypothesisOut(t.text, Confidence.MEDIUM, evidence("o3", "o12", "o1"), t.question))
            if ("o7" in byId) {
                val e = templates.hypothesisEntry
                add(HypothesisOut(e.text, Confidence.LOW, evidence("o7", "o8"), e.question))
            }
            if ("o4" in byId) {
                val s = templates.hypothesisSecond
                add(HypothesisOut(s.text, Confidence.LOW, evidence("o4", "o3"), s.question))
            }
        }.filter { it.evidence.size >= 2 }.take(3)

        val usable = strategies.filter { TextRules.problems(it.thenText, MAX_SENTENCE).isEmpty() }
        val picks = (usable.filter { it.context == top } + usable.filter { it.context == null })
            .distinctBy { it.id }
            .take(2)
            .map { StrategyPickOut(it.id, it.thenText, templates.strategyWhy) }
        val reduction = byId["o2"]?.numbers?.get("reductionRate")
        val summary = if (reduction != null && reduction > 0) templates.summary else templates.summaryNoReduction
        return WeeklyReportOut(
            summaryLine = summary[tone] ?: summary.getValue(Tone.PLAYFUL),
            hypotheses = hypotheses,
            strategies = picks,
            viewerTypeLine = templates.viewerType[input.viewerType] ?: templates.viewerType.getValue("BALANCED"),
            thresholdSuggestions = if (top == ContextSlot.PRE_SLEEP) {
                listOf(ThresholdSuggestionOut(ContextSlot.PRE_SLEEP, SUGGESTED_PRE_SLEEP_MIN, templates.thresholdWhy))
            } else {
                emptyList()
            },
        )
    }

    private fun contextCheck(input: ContextCheckInput): ContextCheckOut {
        val options = input.candidates.distinct().take(MAX_OPTIONS).mapNotNull { templates.contextCheck.options[it] }
            .ifEmpty { listOfNotNull(templates.contextCheck.options[ContextSlot.OTHER]) }
        val padded = if (options.size >=
            2
        ) {
            options
        } else {
            options + listOfNotNull(templates.contextCheck.options[ContextSlot.OTHER])
        }
        return ContextCheckOut(templates.contextCheck.question, padded.distinct())
    }

    private fun poke(input: PokeDraftInput): PokeDraftOut =
        PokeDraftOut(templates.poke[input.tone] ?: templates.poke.getValue(Tone.PLAYFUL))

    private companion object {
        const val MOCK_VERSION = "mock-default"
        const val MAX_SENTENCE = 80
        const val MAX_OPTIONS = 3
        const val SUGGESTED_PRE_SLEEP_MIN = 10
    }
}
