package com.capstone.pause.core.ai.engine.validate

import com.capstone.pause.core.ai.engine.schema.ContextCheckOut
import com.capstone.pause.core.ai.engine.schema.PokeDraftOut
import com.capstone.pause.core.ai.engine.schema.WeeklyReportInput
import com.capstone.pause.core.ai.engine.schema.WeeklyReportOut
import com.capstone.pause.core.model.Confidence

/** `weekly_report` 검증(05 문서 5.1절). [strategyIds]는 전략 라이브러리 ID. */
class WeeklyReportValidator(private val strategyIds: Set<String>) {
    /** 통과하면 그대로, 아니면 이유 목록. */
    fun validate(out: WeeklyReportOut, input: WeeklyReportInput): Validation<WeeklyReportOut> {
        val r = mutableListOf<String>()
        val ids = input.observationIds
        r += TextRules.problems(out.summaryLine, MAX_SENTENCE, field = "summaryLine")
        r += TextRules.problems(out.viewerTypeLine, MAX_SENTENCE, field = "viewerTypeLine")
        if (out.hypotheses.isEmpty()) r += "hypotheses: 비어 있음"
        if (out.hypotheses.size > MAX_HYPOTHESES) r += "hypotheses: ${MAX_HYPOTHESES}개 초과"
        out.hypotheses.forEachIndexed { i, h ->
            val f = "hypotheses[$i]"
            r += TextRules.problems(h.text, MAX_SENTENCE, field = "$f.text")
            r += TextRules.problems(h.confirmQuestion, MAX_SENTENCE, field = "$f.confirmQuestion")
            val evidence = h.evidence.distinct()
            if (evidence.size < MIN_EVIDENCE) r += "$f: 근거 ${MIN_EVIDENCE}개 미만"
            if (h.confidence == Confidence.HIGH &&
                evidence.size < MIN_EVIDENCE_HIGH
            ) {
                r += "$f: HIGH는 근거 ${MIN_EVIDENCE_HIGH}개 이상"
            }
            evidence.filter { it !in ids }.forEach { r += "$f: 없는 관찰 ID $it" }
        }
        if (out.strategies.size > MAX_STRATEGIES) r += "strategies: ${MAX_STRATEGIES}개 초과"
        out.strategies.forEachIndexed { i, s ->
            val f = "strategies[$i]"
            if (s.strategyId !in strategyIds) r += "$f: 라이브러리에 없는 전략 ${s.strategyId}"
            r += TextRules.problems(s.personalizedThen, MAX_SENTENCE, field = "$f.personalizedThen")
            r += TextRules.problems(s.why, MAX_SENTENCE, field = "$f.why")
        }
        out.thresholdSuggestions.forEachIndexed { i, t ->
            val f = "thresholdSuggestions[$i]"
            if (t.minutes !in ALLOWED_MINUTES) r += "$f: minutes는 ${ALLOWED_MINUTES.joinToString()} 중 하나"
            r += TextRules.problems(t.why, MAX_SENTENCE, field = "$f.why")
        }
        return if (r.isEmpty()) Validation.Valid(out) else Validation.Invalid(r)
    }

    /** 상수(05 문서 5.1절). */
    companion object {
        /** 문장 최대 길이. */
        const val MAX_SENTENCE = 80

        /** 가설 최대 수. */
        const val MAX_HYPOTHESES = 3

        /** 전략 최대 수. */
        const val MAX_STRATEGIES = 3

        /** 가설당 최소 근거. */
        const val MIN_EVIDENCE = 2

        /** HIGH 가설의 최소 근거. */
        const val MIN_EVIDENCE_HIGH = 3

        /** 임계값으로 고를 수 있는 분. */
        val ALLOWED_MINUTES = setOf(5, 10, 15, 20, 30)
    }
}

/** `context_check` 검증(05 문서 5.3절). */
object ContextCheckValidator {
    private val allowed = setOf("timeRange", "context", "buddy")

    /** 질문 80자, 선택지 2~3개(각 20자), 숫자 정책. */
    fun validate(out: ContextCheckOut): Validation<ContextCheckOut> {
        val r = mutableListOf<String>()
        r += TextRules.problems(out.question, MAX_QUESTION, allowed, field = "question")
        if (out.options.size !in MIN_OPTIONS..MAX_OPTIONS) r += "options: ${MIN_OPTIONS}~${MAX_OPTIONS}개여야 함"
        out.options.forEachIndexed { i, o -> r += TextRules.problems(o, MAX_OPTION, emptySet(), field = "options[$i]") }
        return if (r.isEmpty()) Validation.Valid(out) else Validation.Invalid(r)
    }

    private const val MAX_QUESTION = 80
    private const val MAX_OPTION = 20
    private const val MIN_OPTIONS = 2
    private const val MAX_OPTIONS = 3
}

/** `poke_draft` 검증(05 문서 5.5절): 40자, 자리표시자는 `{friend}`만, 숫자 없음, 금지 표현. */
object PokeDraftValidator {
    /** 잔소리 최대 길이. */
    const val MAX_LENGTH = 40
    private val allowed = setOf("friend")

    /** 통과한 초안만 남긴다. 하나도 없으면 실패. */
    fun validate(out: PokeDraftOut): Validation<PokeDraftOut> {
        val r = mutableListOf<String>()
        val good = out.options.filterIndexed { i, o ->
            val p = TextRules.problems(o, MAX_LENGTH, allowed, field = "options[$i]")
            r += p
            p.isEmpty()
        }.take(MAX_OPTIONS)
        return if (good.isEmpty()) {
            Validation.Invalid(
                r.ifEmpty {
                    listOf("options: 비어 있음")
                },
            )
        } else {
            Validation.Valid(PokeDraftOut(good), r)
        }
    }

    /** 친구가 보낸 잔소리를 받는 쪽에서 다시 검사한다(표시 전). 사람이 쓴 글이라 숫자는 허용한다. */
    fun acceptsReceived(text: String): Boolean =
        text.isNotBlank() && text.length <= MAX_LENGTH && !BannedWords.contains(text)

    private const val MAX_OPTIONS = 3
}
