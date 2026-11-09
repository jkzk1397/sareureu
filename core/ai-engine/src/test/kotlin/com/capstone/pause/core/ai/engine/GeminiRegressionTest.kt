package com.capstone.pause.core.ai.engine

import com.capstone.pause.core.ai.engine.schema.AiJson
import com.capstone.pause.core.ai.engine.schema.WeeklyReportInput
import com.capstone.pause.core.ai.engine.schema.WeeklyReportOut
import com.capstone.pause.core.ai.engine.validate.ReportRules
import com.capstone.pause.core.ai.engine.validate.TextRules
import com.capstone.pause.core.ai.engine.validate.Validation
import com.capstone.pause.core.ai.engine.validate.WeeklyReportValidator
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonObject
import org.junit.Test

/**
 * 실제 Gemini 원문(`eval/gemini_raw/`)으로 하는 회귀 시험(2026-11-09 검증 활동).
 * 리포트 화면이 채우지 못하는 자리표시자를 쓴 출력은 막고, 정상 표현("이 시간만 되면")은 막지 않는다.
 */
class GeminiRegressionTest {
    private val validator = WeeklyReportValidator(TestData.strategies.map { it.id }.toSet())
    private val input: WeeklyReportInput = AiJson.decodeFromJsonElement(
        WeeklyReportInput.serializer(),
        AiJson.parseToJsonElement(read("weekly_report/night_owl_normal.json")).jsonObject.getValue("input"),
    )

    @Test
    fun rawOutputsWithUnfillablePlaceholdersAreRejected() {
        val names = listOf("weekly_T-01_run1", "weekly_T-01_run2", "weekly_T-01_run3", "weekly_T-05", "weekly_T-06")
        names.forEach { name ->
            val out = AiJson.decodeFromString(WeeklyReportOut.serializer(), read("gemini_raw/$name.json"))
            val result = assertIs<Validation.Invalid>(validator.validate(out, input), name)
            assertTrue(result.reasons.any { "허용되지 않은 자리표시자" in it }, "$name: ${result.reasons}")
            assertTrue(result.reasons.none { "수사" in it }, "$name 오탐: ${result.reasons}")
        }
    }

    @Test
    fun v2OutputsPassUnlessUnitIsDoubledOrBreakV3Rules() {
        // v2 출력 중 v3 규칙(ReportRules)에 걸리지 않는 것은 그대로 통과해야 한다(정상 출력을 막지 않는지).
        val out3 = AiJson.decodeFromString(WeeklyReportOut.serializer(), read("gemini_raw/weekly_v2_T-01_run3.json"))
        assertIs<Validation.Valid<WeeklyReportOut>>(validator.validate(out3, input), "weekly_v2_T-01_run3")
        val doubled = AiJson.decodeFromString(WeeklyReportOut.serializer(), read("gemini_raw/weekly_v2_T-06.json"))
        val result = assertIs<Validation.Invalid>(validator.validate(doubled, input))
        assertTrue(result.reasons.any { "단위 중복" in it }, result.reasons.toString())
        // 예전(v2 검증기)에는 통과했지만 v3 규칙으로 막아야 하는 것.
        mapOf(
            "weekly_v2_T-01_run1" to listOf("폰처럼", "녹는 이야기"),
            "weekly_v2_T-01_run2" to listOf("녹는 이야기"),
            "weekly_v2_T-05" to listOf("줄어든 양처럼"),
        ).forEach { (name, expected) ->
            val out = AiJson.decodeFromString(WeeklyReportOut.serializer(), read("gemini_raw/$name.json"))
            val r = assertIs<Validation.Invalid>(validator.validate(out, input), name)
            expected.forEach { e -> assertTrue(r.reasons.any { e in it }, "$name: $e 없음 ${r.reasons}") }
        }
    }

    @Test
    fun v3RulesCatchKnownMistakesButNotNormalSentences() {
        fun problems(text: String, inputText: String = "") = ReportRules.problems(text, "t", inputText)
        assertTrue(problems("자기 전엔 {buddy} 내려놓기!").any { "폰처럼" in it })
        assertTrue(problems("침대에 누울 땐 {buddy}를 충전기에 재워두자").any { "폰처럼" in it })
        assertTrue(problems("하루 평균 {avgMin}만큼 가벼워졌어").any { "줄어든 양처럼" in it })
        assertTrue(problems("이러다간 내가 다 녹아버리겠어!").any { "녹는" in it })
        assertTrue(problems("시험 끝나서 해방감에 많이 봤나 봐").any { "입력에 없는 이유" in it })
        // 입력(메모·관심사)에 있으면 지어낸 것이 아니다.
        assertTrue(problems("시험 기간이라 늦게까지 봤을까?", inputText = "user_notes: 다음 주 시험").isEmpty())
        // 정상 문장은 막지 않는다.
        listOf(
            "{buddy}와 함께 {topContext}에도 숏폼을 줄이며 달리는 중!",
            "{topContext} 시간에 딱 맞춰서 {buddy}가 살짝 신호를 줄게!",
            "하루 평균 {avgMin} 봤고 {reduction} 줄었어",
            "녹음 파일처럼 끝이 있는 것부터 들어 볼래?",
        ).forEach { assertTrue(problems(it).isEmpty(), it) }
    }

    @Test
    fun zwjEmojiCountsAsOne() {
        assertEquals(1, TextRules.emojiCount("열심히 달렸네! 🏃‍♂️"))
        assertEquals(2, TextRules.emojiCount("좋아 😀😀"))
    }

    @Test
    fun reportPlaceholdersStillPass() {
        val ok = "{topContext}에 {topContextMin}, 하루 평균 {avgMin}. {reduction} 줄고 {stopRate} 멈췄어"
        val allowed = WeeklyReportValidator.REPORT_PLACEHOLDERS
        val problems = TextRules.problems(ok, MAX, allowed)
        assertTrue(problems.isEmpty(), problems.toString())
        listOf("{nickname}", "{app}", "{context}", "{timeRange}", "{friend}").forEach { p ->
            assertTrue(TextRules.problems("$p 봤어", MAX, allowed).isNotEmpty(), p)
        }
    }

    @Test
    fun thisTimeIsNotANumeral() {
        assertTrue(TextRules.problems("이 시간만 되면 화면 속으로 빨려 들어가는 거야?", MAX).isEmpty())
        assertTrue(TextRules.problems("혹시 이 시간엔 손이 먼저 갔어?", MAX).isEmpty())
        assertTrue(TextRules.problems("삼 분만 더 볼까", MAX).any { "수사" in it })
        assertTrue(TextRules.problems("벌써 한 시간 넘었어", MAX).any { "수사" in it })
    }

    private fun read(path: String): String = File("src/test/resources/eval/$path").readText(Charsets.UTF_8)

    private companion object {
        const val MAX = 80
    }
}
