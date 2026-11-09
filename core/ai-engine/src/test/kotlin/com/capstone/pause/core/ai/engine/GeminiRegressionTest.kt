package com.capstone.pause.core.ai.engine

import com.capstone.pause.core.ai.engine.schema.AiJson
import com.capstone.pause.core.ai.engine.schema.WeeklyReportInput
import com.capstone.pause.core.ai.engine.schema.WeeklyReportOut
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
    fun v2OutputsPassUnlessUnitIsDoubled() {
        val passing = listOf("weekly_v2_T-01_run1", "weekly_v2_T-01_run2", "weekly_v2_T-01_run3", "weekly_v2_T-05")
        passing.forEach { name ->
            val out = AiJson.decodeFromString(WeeklyReportOut.serializer(), read("gemini_raw/$name.json"))
            assertIs<Validation.Valid<WeeklyReportOut>>(validator.validate(out, input), name)
        }
        val doubled = AiJson.decodeFromString(WeeklyReportOut.serializer(), read("gemini_raw/weekly_v2_T-06.json"))
        val result = assertIs<Validation.Invalid>(validator.validate(doubled, input))
        assertTrue(result.reasons.any { "단위 중복" in it }, result.reasons.toString())
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
