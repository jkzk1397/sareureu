package com.capstone.pause.core.ai.engine.schema

import com.capstone.pause.core.model.Confidence
import com.capstone.pause.core.model.ContextSlot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 모든 출력 파서. 모르는 키는 무시하고, 빠진 필수 키는 스키마 실패다. */
val AiJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/** `weekly_report` 출력(05 문서 5.1절). */
@Serializable
data class WeeklyReportOut(
    val summaryLine: String,
    val hypotheses: List<HypothesisOut>,
    val strategies: List<StrategyPickOut>,
    val viewerTypeLine: String,
    val thresholdSuggestions: List<ThresholdSuggestionOut> = emptyList(),
)

/** 가설. [evidence]는 관찰 ID. */
@Serializable
data class HypothesisOut(
    val text: String,
    val confidence: Confidence,
    val evidence: List<String>,
    val confirmQuestion: String,
)

/** 고른 전략. [strategyId]는 전략 라이브러리 ID. */
@Serializable
data class StrategyPickOut(val strategyId: String, val personalizedThen: String, val why: String)

/** 임계값 제안. 사용자가 수락할 때만 바뀐다. */
@Serializable
data class ThresholdSuggestionOut(val slot: ContextSlot, val minutes: Int, val why: String)

/** `context_check` 출력. */
@Serializable
data class ContextCheckOut(val question: String, val options: List<String>)

/** `poke_draft` 출력. */
@Serializable
data class PokeDraftOut(val options: List<String>)

/** 코치 답의 근거. [type] = "session"(id) 또는 "stat"(key). */
@Serializable
data class EvidenceOut(val type: String, val id: Long? = null, val key: String? = null)

/** 계획 제안. 화면에서 "계획으로 저장" 버튼으로만 저장된다. */
@Serializable
data class PlanProposalOut(val ifText: String, val thenText: String)

/** `coach_chat` 최종 답(05 문서 5.4절). */
@Serializable
data class CoachReplyOut(
    val answer: String,
    val evidence: List<EvidenceOut> = emptyList(),
    val followUps: List<String> = emptyList(),
    val planProposal: PlanProposalOut? = null,
)
