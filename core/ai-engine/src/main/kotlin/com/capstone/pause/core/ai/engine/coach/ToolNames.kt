package com.capstone.pause.core.ai.engine.coach

import com.capstone.pause.core.ai.engine.llm.ToolDeclaration
import com.capstone.pause.core.ai.engine.schema.AiJson
import com.capstone.pause.core.model.ContextSlot
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 도구 이름(함수 선언 이름, 05 문서 5.4절). */
object ToolNames {
    const val SUMMARY = "get_summary"
    const val CONTEXT_BREAKDOWN = "get_context_breakdown"
    const val SESSIONS = "get_sessions"
    const val FLOWS = "get_flows"
    const val RISK_WINDOWS = "get_risk_windows"
    const val PLANS = "get_plans"
    const val PEER_BASELINE = "get_peer_baseline"
    const val SUGGEST_STRATEGIES = "suggest_strategies"

    private val periods = Period.entries.joinToString(",") { "\"${it.name}\"" }
    private val contexts = ContextSlot.entries.joinToString(",") { "\"${it.name}\"" }

    private fun schema(json: String): JsonObject = AiJson.parseToJsonElement(json).jsonObject

    private val periodOnly =
        schema(
            """{"type":"object","properties":{"period":{"type":"string","enum":[$periods]}},"required":["period"]}""",
        )
    private val empty = schema("""{"type":"object","properties":{}}""")

    /** 모델에 넘길 함수 선언(설명은 모델용 영어 한 줄. 사용자에게 보이지 않는다). */
    val DECLARATIONS: List<ToolDeclaration> = listOf(
        ToolDeclaration(
            SUMMARY,
            "Totals, daily average, baseline, reduction, coverage, session count, stop rate.",
            periodOnly,
        ),
        ToolDeclaration(CONTEXT_BREAKDOWN, "Minutes, sessions and median per context.", periodOnly),
        ToolDeclaration(
            SESSIONS,
            "List sessions (max 15) with id, weekday, time, minutes, app, context, entry, exit, max level.",
            schema(
                """{"type":"object","properties":{"period":{"type":"string","enum":[$periods]},""" +
                    """"context":{"type":"string","enum":[$contexts]},"app":{"type":"string"},""" +
                    """"minMinutes":{"type":"integer"},"limit":{"type":"integer"}},"required":["period"]}""",
            ),
        ),
        ToolDeclaration(
            FLOWS,
            "Top entry -> app -> exit flows (max 5).",
            schema(
                """{"type":"object","properties":{"period":{"type":"string","enum":[$periods]},""" +
                    """"limit":{"type":"integer"}},"required":["period"]}""",
            ),
        ),
        ToolDeclaration(RISK_WINDOWS, "Top 3 weekday x hour risk cells.", empty),
        ToolDeclaration(PLANS, "User's if-then plans.", empty),
        ToolDeclaration(
            PEER_BASELINE,
            "Peer baseline for the user's age band and occupation, with source and demo flag.",
            empty,
        ),
        ToolDeclaration(
            SUGGEST_STRATEGIES,
            "Strategy library items for a context.",
            schema("""{"type":"object","properties":{"context":{"type":"string","enum":[$contexts]}}}"""),
        ),
    )

    /** 인자에서 기간 읽기(없거나 틀리면 이번 주). */
    fun period(args: JsonObject): Period =
        args["period"]?.jsonPrimitive?.contentOrNull?.let { p -> Period.entries.firstOrNull { it.name == p } }
            ?: Period.THIS_WEEK

    /** 인자에서 맥락 읽기. */
    fun context(args: JsonObject): ContextSlot? =
        args["context"]?.jsonPrimitive?.contentOrNull?.let { c -> ContextSlot.entries.firstOrNull { it.name == c } }

    /** 인자에서 정수 읽기. */
    fun int(args: JsonObject, name: String): Int? = args[name]?.jsonPrimitive?.intOrNull
}
