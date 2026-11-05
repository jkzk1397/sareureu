package com.capstone.pause.core.ai.engine.eval

import com.capstone.pause.core.ai.engine.coach.CoachEngine
import com.capstone.pause.core.ai.engine.coach.CoachTurn
import com.capstone.pause.core.ai.engine.llm.LlmClient
import com.capstone.pause.core.ai.engine.prompt.PromptRegistry
import com.capstone.pause.core.ai.engine.run.AiCallRecord
import com.capstone.pause.core.ai.engine.run.AiRunner
import com.capstone.pause.core.ai.engine.run.CallOutcome
import com.capstone.pause.core.ai.engine.safety.SafetyFilter
import com.capstone.pause.core.ai.engine.schema.AiJson
import com.capstone.pause.core.ai.engine.schema.ContextCheckOut
import com.capstone.pause.core.ai.engine.schema.CopyPackInput
import com.capstone.pause.core.ai.engine.schema.PokeDraftOut
import com.capstone.pause.core.ai.engine.schema.ProfileSummary
import com.capstone.pause.core.ai.engine.schema.WeeklyReportInput
import com.capstone.pause.core.ai.engine.schema.WeeklyReportOut
import com.capstone.pause.core.ai.engine.validate.BannedWords
import com.capstone.pause.core.ai.engine.validate.ContextCheckValidator
import com.capstone.pause.core.ai.engine.validate.PokeDraftValidator
import com.capstone.pause.core.ai.engine.validate.TextRules
import com.capstone.pause.core.ai.engine.validate.Validation
import com.capstone.pause.core.ai.engine.validate.WeeklyReportValidator
import com.capstone.pause.core.model.CopyPack
import com.capstone.pause.core.model.Strategy
import java.io.File
import java.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** 평가 한 줄(CSV 행). */
data class EvalRow(
    val caseId: String,
    val feature: String,
    val schemaOk: Boolean,
    val firstPass: Boolean,
    val finalPass: Boolean,
    val outcome: String,
    val bannedViolations: Int,
    val numberViolations: Int,
    val evidenceValid: Boolean?,
    val expectedToolsCalled: Boolean?,
    val safetyBlocked: Boolean?,
    val latencyMs: Long,
    val tokens: Int?,
)

@Serializable
private data class WeeklyCase(val name: String, val expectAi: Boolean, val input: WeeklyReportInput)

@Serializable
private data class CopyCase(val name: String, val input: CopyPackInput)

@Serializable
private data class CoachCase(
    val name: String,
    val question: String,
    val expectTools: List<String>,
    val forbid: List<String>,
)

@Serializable
private data class TextCase(val name: String, val text: String)

@Serializable
private data class BaitCase(val name: String, val feature: String, val output: JsonObject)

/** 05 문서 9절 평가를 돈다. [dataDir] = `src/test/resources/eval`. */
class EvalRunner(
    private val dataDir: File,
    private val defaultPack: CopyPack,
    private val strategies: List<Strategy>,
    private val mock: LlmClient,
    /** 캐릭터 이름(앱의 `R.string.buddy_name`과 같은 값을 넘긴다). */
    private val buddyName: String,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val registry = PromptRegistry()
    private val runner = AiRunner(registry, mock, defaultPack, strategies, clock, buddyName)

    private inline fun <reified T> read(rel: String): T =
        AiJson.decodeFromString(File(dataDir, rel).readText(Charsets.UTF_8))

    private inline fun <reified T> readFile(f: File): T = AiJson.decodeFromString(f.readText(Charsets.UTF_8))

    private fun files(dir: String) =
        File(dataDir, dir).listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }

    private fun isNumberProblem(text: String) =
        TextRules.problems(text, Int.MAX_VALUE).any { p -> "숫자" in p || "수사" in p }

    /** 생성 기능 한 줄. */
    private fun generated(id: String, feature: String, record: AiCallRecord, lines: List<String>) = EvalRow(
        caseId = id,
        feature = feature,
        schemaOk = !(record.reason ?: "").contains(SCHEMA),
        firstPass = record.outcome == CallOutcome.PASSED,
        finalPass = record.outcome == CallOutcome.PASSED || record.outcome == CallOutcome.RETRIED_PASSED,
        outcome = record.outcome.name,
        bannedViolations = lines.count { BannedWords.contains(it) },
        numberViolations = lines.count(::isNumberProblem),
        evidenceValid = null,
        expectedToolsCalled = null,
        safetyBlocked = null,
        latencyMs = record.latencyMs,
        tokens = record.tokens,
    )

    /** 통과/실패만 있는 검사 한 줄(안전·미끼). */
    private fun check(id: String, feature: String, ok: Boolean, okLabel: String, blocked: Boolean?) = EvalRow(
        caseId = id,
        feature = feature,
        schemaOk = true,
        firstPass = ok,
        finalPass = ok,
        outcome = if (ok) okLabel else MISSED,
        bannedViolations = 0,
        numberViolations = 0,
        evidenceValid = null,
        expectedToolsCalled = null,
        safetyBlocked = blocked,
        latencyMs = 0,
        tokens = null,
    )

    private fun weeklyLines(o: WeeklyReportOut) = listOf(o.summaryLine, o.viewerTypeLine) +
        o.hypotheses.flatMap { listOf(it.text, it.confirmQuestion) } +
        o.strategies.flatMap { listOf(it.personalizedThen, it.why) }

    /** 전부 실행. */
    suspend fun runAll(client: LlmClient): List<EvalRow> {
        val rows = mutableListOf<EvalRow>()
        for (f in files("weekly_report")) {
            val c: WeeklyCase = readFile(f)
            val id = "weekly_report/${c.name}"
            val enough = c.input.observations.size >= MIN_OBS && (c.input.coverage ?: 1.0) >= MIN_COVERAGE
            if (!enough) {
                rows += check(id, "weekly_report", !c.expectAi, CallOutcome.SKIPPED.name, null)
                continue
            }
            val out = runner.weeklyReport(client, c.input)
            val evidenceOk = out.value.hypotheses.all { h -> h.evidence.all { it in c.input.observationIds } }
            rows += generated(id, "weekly_report", out.record, weeklyLines(out.value)).copy(evidenceValid = evidenceOk)
        }
        for (f in files("copy_pack")) {
            val c: CopyCase = readFile(f)
            val out = runner.copyPack(client, c.input)
            val p = out.value
            val lines = p.card.values.flatMap { it.l1 + it.l2 + it.l3 + it.l4Question }.map { it.text } +
                p.buddyMessage.map { it.text } + p.praise.map { it.text }
            rows += generated("copy_pack/${c.name}", "copy_pack", out.record, lines)
        }
        rows += coach(client)
        read<List<TextCase>>("safety/crisis.json").forEach { c ->
            val blocked = SafetyFilter.isCrisis(c.text)
            rows += check("safety/${c.name}", "safety_crisis", blocked, CallOutcome.SAFETY_BLOCKED.name, blocked)
        }
        read<List<TextCase>>("safety/poke_abuse.json").forEach { c ->
            val rejected = !PokeDraftValidator.acceptsReceived(c.text)
            rows += check("safety/${c.name}", "poke_abuse", rejected, REJECTED, rejected)
        }
        rows += baits()
        return rows
    }

    private suspend fun coach(client: LlmClient): List<EvalRow> {
        val fixture: CoachFixture = read("coach_chat/fixture.json")
        return read<List<CoachCase>>("coach_chat/questions.json").map { c ->
            val id = "coach_chat/${c.name}"
            val engine = CoachEngine(JsonCoachTools(fixture, strategies), registry, mock, clock, buddyName)
            when (val turn = engine.reply(client, emptyList(), c.question, ProfileSummary())) {
                is CoachTurn.Crisis -> check(id, "coach_chat", false, "CRISIS", true)

                is CoachTurn.Reply -> {
                    val texts = listOf(turn.reply.answer) + turn.reply.followUps
                    val forbidden = texts.count { t -> c.forbid.any { it in t } }
                    val called = turn.toolResults.map { it.name }.toSet()
                    generated(id, "coach_chat", turn.record, emptyList()).copy(
                        finalPass = turn.record.outcome != CallOutcome.MOCK_FALLBACK,
                        bannedViolations = texts.count { BannedWords.contains(it) } + forbidden,
                        evidenceValid = true,
                        expectedToolsCalled = c.expectTools.all { it in called },
                    )
                }
            }
        }
    }

    private fun baits(): List<EvalRow> {
        val weekly = WeeklyReportValidator(strategies.map { it.id }.toSet())
        val input: WeeklyCase = read("weekly_report/night_owl_normal.json")
        return read<List<BaitCase>>("baits/outputs.json").map { b ->
            val rejected = runCatching {
                val result = when (b.feature) {
                    "weekly_report" -> weekly.validate(
                        AiJson.decodeFromJsonElement(WeeklyReportOut.serializer(), b.output),
                        input.input,
                    )

                    "poke_draft" -> PokeDraftValidator.validate(
                        AiJson.decodeFromJsonElement(PokeDraftOut.serializer(), b.output),
                    )

                    else -> ContextCheckValidator.validate(
                        AiJson.decodeFromJsonElement(ContextCheckOut.serializer(), b.output),
                    )
                }
                result is Validation.Invalid
            }.getOrDefault(true)
            check("baits/${b.name}", "validator_bait", rejected, REJECTED, null)
        }
    }

    private companion object {
        const val MIN_OBS = 3
        const val MIN_COVERAGE = 0.5
        const val SCHEMA = "스키마"
        const val REJECTED = "REJECTED"
        const val MISSED = "MISSED"
    }
}
