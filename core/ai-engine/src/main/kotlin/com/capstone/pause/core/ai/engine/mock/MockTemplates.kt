package com.capstone.pause.core.ai.engine.mock

import com.capstone.pause.core.ai.engine.schema.AiJson
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.Tone
import kotlinx.serialization.Serializable

/** 가설 문장과 확인 질문. */
@Serializable
data class HypothesisTemplate(val text: String, val question: String)

/** 확인 질문 틀. */
@Serializable
data class ContextCheckTemplate(val question: String, val options: Map<ContextSlot, String>)

/** 코치 답 틀(`%이름%` 자리에 도구 결과 값을 넣는다). */
@Serializable
data class CoachTemplates(
    val `when`: String,
    val why: String,
    val commute: String,
    val diff: String,
    val type: String,
    val summary: String,
    val unknown: String,
    val followUps: List<String>,
    val dayLabels: Map<String, String>,
    val entryLabels: Map<String, String>,
)

/**
 * 모의 제공자의 문장 틀(`resources/mock/templates_v1.json`). Kotlin 코드에 문장을 쓰지 않으려고 파일로 둔다.
 * 숫자는 자리표시자만 쓴다(코치 답만 도구 결과 숫자를 넣는다).
 */
@Serializable
data class MockTemplates(
    val contextLabels: Map<ContextSlot, String>,
    val summary: Map<Tone, String>,
    val summaryNoReduction: Map<Tone, String>,
    val hypothesisContext: Map<ContextSlot, HypothesisTemplate>,
    val hypothesisEntry: HypothesisTemplate,
    val hypothesisSecond: HypothesisTemplate,
    val strategyWhy: String,
    val viewerType: Map<String, String>,
    val thresholdWhy: String,
    val contextCheck: ContextCheckTemplate,
    val poke: Map<Tone, List<String>>,
    val coach: CoachTemplates,
) {
    /** 읽기. */
    companion object {
        /** 클래스패스 리소스 경로. */
        const val RESOURCE = "mock/templates_v1.json"

        /** 기본 틀. */
        fun load(): MockTemplates {
            val text = MockTemplates::class.java.classLoader?.getResourceAsStream(RESOURCE)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: throw IllegalStateException("리소스 없음: $RESOURCE")
            return AiJson.decodeFromString(serializer(), text)
        }
    }
}
