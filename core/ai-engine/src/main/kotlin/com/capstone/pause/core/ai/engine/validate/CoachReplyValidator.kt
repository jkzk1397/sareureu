package com.capstone.pause.core.ai.engine.validate

import com.capstone.pause.core.ai.engine.schema.AiJson
import com.capstone.pause.core.ai.engine.schema.CoachReplyOut
import kotlin.math.abs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * 한 턴 동안 도구가 돌려준 것들. 근거 ID와 숫자 검사의 기준이다.
 * 도구 결과 JSON 안의 `sessionId`/`id`(세션 목록 안) 값은 세션 근거, `key` 값은 통계 근거, 모든 숫자는 허용 숫자.
 */
class ToolEvidence private constructor(
    val sessionIds: Set<Long>,
    val statKeys: Set<String>,
    val numbers: Set<Double>,
) {
    /** 이 숫자를 답에 써도 되는지(반올림 ±1, 비율은 ×100, 분은 시간·분으로 나눈 값도 허용). */
    fun allows(n: Double): Boolean = numbers.any { v ->
        abs(v - n) <= 1.0 ||
            (v in 0.0..1.0 && abs(v * PERCENT - n) <= 1.0) ||
            (
                v >= MINUTES_PER_HOUR &&
                    (abs(Math.floor(v / MINUTES_PER_HOUR) - n) < EPS || abs(v % MINUTES_PER_HOUR - n) <= 1.0)
                )
    }

    /** 만들기. */
    companion object {
        private const val PERCENT = 100.0
        private const val MINUTES_PER_HOUR = 60.0
        private const val EPS = 0.001

        /** 도구 결과 JSON 문자열들에서 뽑는다. */
        fun from(toolResultsJson: List<String>): ToolEvidence {
            val ids = mutableSetOf<Long>()
            val keys = mutableSetOf<String>()
            val nums = mutableSetOf<Double>()
            fun walk(e: JsonElement, parentKey: String?) {
                when (e) {
                    is JsonObject -> e.forEach { (k, v) ->
                        if (v is JsonPrimitive && !v.isString) {
                            v.doubleOrNull?.let { nums += it }
                            if (k == "sessionId" ||
                                (k == "id" && parentKey == "sessions")
                            ) {
                                v.longOrNull?.let { ids += it }
                            }
                        } else if (v is JsonPrimitive && k == "key") {
                            keys += v.content
                        } else {
                            walk(v, k)
                        }
                    }

                    is JsonArray -> e.forEach { walk(it, parentKey) }

                    is JsonPrimitive -> if (!e.isString) e.doubleOrNull?.let { nums += it }
                }
            }
            toolResultsJson.forEach { runCatching { walk(AiJson.parseToJsonElement(it), null) } }
            return ToolEvidence(ids, keys, nums)
        }
    }
}

/** `coach_chat` 최종 답 검증(05 문서 5.4절): 근거는 이번 턴 도구 결과에만, 숫자는 도구 결과와 일치, 4문장 이하, 금지 표현. */
object CoachReplyValidator {
    private val number = Regex("[0-9]+(\\.[0-9]+)?")

    /** 통과하면 그대로. */
    fun validate(out: CoachReplyOut, evidence: ToolEvidence): Validation<CoachReplyOut> {
        val r = mutableListOf<String>()
        if (out.answer.isBlank()) r += "answer: 비어 있음"
        if (out.answer.length > MAX_ANSWER) r += "answer: ${MAX_ANSWER}자 넘음"
        if (TextRules.sentenceCount(out.answer) > MAX_SENTENCES) r += "answer: ${MAX_SENTENCES}문장 초과"
        BannedWords.find(out.answer)?.let { r += "answer: 금지 표현 '$it'" }
        number.findAll(out.answer).map { it.value.toDouble() }.filterNot(evidence::allows).forEach {
            r += "answer: 도구 결과에 없는 숫자 ${it.toBigDecimal().stripTrailingZeros().toPlainString()}"
        }
        out.evidence.forEachIndexed { i, e ->
            when (e.type) {
                "session" -> if (e.id == null || e.id !in evidence.sessionIds) r += "evidence[$i]: 이번 턴에 조회하지 않은 세션"
                "stat" -> if (e.key == null || e.key !in evidence.statKeys) r += "evidence[$i]: 이번 턴에 조회하지 않은 통계"
                else -> r += "evidence[$i]: type은 session 또는 stat"
            }
        }
        if (out.followUps.size > MAX_FOLLOW_UPS) r += "followUps: ${MAX_FOLLOW_UPS}개 초과"
        out.followUps.forEachIndexed { i, f ->
            if (f.length > MAX_FOLLOW_UP) r += "followUps[$i]: ${MAX_FOLLOW_UP}자 넘음"
            BannedWords.find(f)?.let { r += "followUps[$i]: 금지 표현 '$it'" }
        }
        out.planProposal?.let { p ->
            listOf("ifText" to p.ifText, "thenText" to p.thenText).forEach { (k, v) ->
                if (v.isBlank() || v.length > MAX_PLAN) r += "planProposal.$k: 비었거나 ${MAX_PLAN}자 넘음"
                BannedWords.find(v)?.let { r += "planProposal.$k: 금지 표현 '$it'" }
            }
        }
        return if (r.isEmpty()) Validation.Valid(out) else Validation.Invalid(r)
    }

    private const val MAX_ANSWER = 300
    private const val MAX_SENTENCES = 4
    private const val MAX_FOLLOW_UPS = 3
    private const val MAX_FOLLOW_UP = 40
    private const val MAX_PLAN = 60
}
