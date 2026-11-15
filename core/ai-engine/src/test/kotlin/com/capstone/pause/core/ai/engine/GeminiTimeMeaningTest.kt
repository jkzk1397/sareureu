package com.capstone.pause.core.ai.engine

import com.capstone.pause.core.ai.engine.llm.LlmResult
import com.capstone.pause.core.ai.engine.mock.MockLlmClient
import com.capstone.pause.core.ai.engine.prompt.PromptRegistry
import com.capstone.pause.core.ai.engine.run.AiRunner
import com.capstone.pause.core.ai.engine.run.CallOutcome
import com.capstone.pause.core.ai.engine.schema.AiJson
import com.capstone.pause.core.ai.engine.schema.WeeklyReportInput
import com.capstone.pause.core.ai.engine.schema.WeeklyReportOut
import com.capstone.pause.core.ai.engine.validate.ReportRules
import com.capstone.pause.core.ai.engine.validate.Validation
import com.capstone.pause.core.ai.engine.validate.WeeklyReportValidator
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * 실제 Gemini 원문으로 하는 회귀 시험 2차(2026-11-15 검증 활동).
 * 프롬프트 v3 출력에서 시간 자리표시자를 다른 맥락이나 기간의 값처럼 쓴 문장을 찾았다
 * ("밤마다 {topContextMin}", "잠들기 전 {topContextMin}"인데 1위 맥락은 '기타', "이동할 때도 {avgMin} 속에서").
 * 화면에서는 틀린 숫자가 사실처럼 보이므로 막고, 같은 입력의 v4 출력 중 정상인 것은 통과해야 한다.
 * 입력은 `gemini_raw/inputs/`, 판정은 앱과 같은 [WeeklyReportValidator]로 한다.
 */
class GeminiTimeMeaningTest {
    private val validator = WeeklyReportValidator(TestData.strategies.map { it.id }.toSet())

    @Test
    fun v3OutputsWithWrongTimeMeaningAreRejected() {
        val wrongMeaning = mapOf(
            "T-03" to "{topContextMin}을 {topContext} 없이",
            "T-05" to "'밤마다' 양처럼",
            "T-06" to "'하루' 양처럼",
        )
        wrongMeaning.forEach { (run, reason) ->
            val r = assertIs<Validation.Invalid>(judge("v3", run), run)
            assertTrue(r.reasons.any { reason in it }, "$run: $reason 없음 ${r.reasons}")
        }
        // T-01 3회차는 JSON부터 깨졌지만(아래 시험) 뜻이 틀린 문장도 있었다. 그 문장은 규칙으로 잡힌다.
        val run3 = "이동할 때도 {avgMin} 속에서 꽤 많은 시간을 보낸 것 같아."
        assertTrue(run3 in raw("v3", "T-01_run3"))
        assertTrue(ReportRules.problems(run3, "t", "").any { "{avgMin}을 '하루 평균' 없이" in it })
        // 새 규칙이 '하루'가 빠진 문장도 막는 것(2회차, T-02)은 기록지에 '불필요한 차단'으로 적었다.
        listOf("T-01_run2", "T-02").forEach { assertIs<Validation.Invalid>(judge("v3", it), it) }
        // 뜻이 맞는 v3 출력은 계속 통과한다. T-04의 "수업이나 과제 시간"은 CLASS_WORK 맥락이라 지어낸 이유가 아니다.
        listOf("T-01_run1", "T-04").forEach { assertIs<Validation.Valid<WeeklyReportOut>>(judge("v3", it), it) }
    }

    @Test
    fun weeklyTotalReadAsEveryTimeAmountIsRejected() {
        // 폰에서 본 사례(2026-11-15): 한 주 합계를 "누를 때마다", "볼 때마다"의 양처럼 쓴 문장이 통과했다.
        listOf(
            "메신저 알림을 누를 때마다 {topContext}에 {topContextMin} 동안 보게 되나 봐.",
            "{topContext}에 숏폼을 볼 때마다 {topContextMin}씩 흘러가네.",
        ).forEach { line ->
            assertTrue(ReportRules.problems(line, "t", "").any { "'때마다' 양처럼" in it }, line)
        }
        // 시간 자리표시자가 없는 '때마다'는 숫자를 틀리게 보이지 않으니 막지 않는다.
        val noNumber = "메신저 알림을 누를 때마다 자연스럽게 숏폼으로 빠지는 것 같아."
        assertTrue(ReportRules.problems(noNumber, "t", "").isEmpty(), noNumber)
        // 합계를 합계로 쓴 문장은 그대로 통과한다.
        val ok = "{topContext}에 이번 주 {topContextMin} 봤어."
        assertTrue(ReportRules.problems(ok, "t", "").isEmpty(), ok)
    }

    @Test
    fun v4OutputsPassUnlessTheyBreakOtherRules() {
        val pass = listOf("T-01_run1", "T-01_run2", "T-01_run3", "T-02", "T-03", "T-04", "T-06", "T-06_run3")
        pass.forEach { assertIs<Validation.Valid<WeeklyReportOut>>(judge("v4", it), it) }
        // 막아야 하는 것: 근거 1개 가설(T-05), "십 분"처럼 한글 수사로 쓴 시간(T-06 2회차).
        val t05 = assertIs<Validation.Invalid>(judge("v4", "T-05"))
        assertTrue(t05.reasons.any { "근거 2개 미만" in it }, t05.reasons.toString())
        val t06 = assertIs<Validation.Invalid>(judge("v4", "T-06_run2"))
        assertTrue(t06.reasons.any { "수사" in it }, t06.reasons.toString())
    }

    @Test
    fun brokenSchemaIsRejectedBeforeValidation() {
        // v3 T-01 3회차는 전략 배열에 {"_comment": "unused"}를 넣었다. JSON을 읽을 수 없어 재요청으로 간다.
        val read = runCatching { AiJson.decodeFromString(WeeklyReportOut.serializer(), raw("v3", "T-01_run3")) }
        assertTrue(read.isFailure)
    }

    @Test
    fun runnerRetriesThenFallsBackSoWrongNumbersNeverReachTheScreen() = runTest {
        val mock = MockLlmClient(TestData.defaultPack, TestData.strategies)
        val runner = AiRunner(PromptRegistry(), mock, TestData.defaultPack, TestData.strategies, TestData.clock, "르르")
        // 1) 첫 응답이 틀린 뜻(v3 T-03)이면 이유를 붙여 다시 요청하고, 두 번째 응답(v4 T-03)이 통과하면 그것을 쓴다.
        val retry = ScriptedClient(json = listOf(ok(raw("v3", "T-03")), ok(raw("v4", "T-03"))))
        val first = runner.weeklyReport(retry, input("T-03"))
        assertEquals(CallOutcome.RETRIED_PASSED, first.record.outcome)
        assertTrue(first.record.reason.orEmpty().contains("{topContextMin}"), first.record.reason)
        // 2) 두 번 다 틀리면(v3 T-05) 모의 결과로 대신한다.
        val twice = ScriptedClient(json = listOf(ok(raw("v3", "T-05")), ok(raw("v3", "T-05"))))
        val second = runner.weeklyReport(twice, input("T-05"))
        assertEquals(CallOutcome.MOCK_FALLBACK, second.record.outcome)
        assertEquals(mock.weekly(input("T-05")), second.value)
    }

    @Test
    fun timeRulesDoNotBlockNormalSentences() {
        fun problems(text: String) = ReportRules.problems(text, "t", "")
        listOf(
            "{topContext}에 {topContextMin} 동안 숏폼을 보게 되나 봐",
            "하루 평균 {avgMin} 동안 숏폼을 봤고 {reduction} 가벼워졌어",
            "{topContext}에 {topContextMin} 보느라 하루 평균 {avgMin}을 보게 됐나 봐",
        ).forEach { assertTrue(problems(it).isEmpty(), "$it ${problems(it)}") }
        assertTrue(problems("밤마다 {topContextMin}을 꽉 채우는 집중러").isNotEmpty())
        assertTrue(problems("이동할 때도 {avgMin} 속에서 꽤 보낸 것 같아").isNotEmpty())
    }

    private fun judge(version: String, run: String): Validation<WeeklyReportOut> {
        val out = AiJson.decodeFromString(WeeklyReportOut.serializer(), raw(version, run))
        return validator.validate(out, input(run.substringBefore("_run")))
    }

    private fun input(case: String): WeeklyReportInput =
        AiJson.decodeFromString(WeeklyReportInput.serializer(), read("gemini_raw/inputs/$case.json"))

    private fun raw(version: String, run: String) = read("gemini_raw/weekly_${version}_$run.json")

    private fun ok(text: String) = LlmResult.Success(text, "gemini-3.5-flash-lite")

    private fun read(path: String): String = File("src/test/resources/eval/$path").readText(Charsets.UTF_8)
}
