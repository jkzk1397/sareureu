package com.capstone.pause.core.ai.engine.llm

import com.capstone.pause.core.ai.engine.schema.AiJson
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * **평가 전용** Gemini Developer API REST 클라이언트(JDK HttpClient). 앱 코드에서 쓰지 않는다(AGENTS.md 7절).
 * 키는 환경변수 `GEMINI_API_KEY`에서만 읽고, 저장소에 두지 않는다.
 */
class RestGeminiClient(
    private val apiKey: String?,
    override val model: String = DEFAULT_MODEL,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(CONNECT_SEC)).build(),
) : LlmClient {
    override val provider: Provider = Provider.REST_EVAL
    override val isAvailable: Boolean get() = !apiKey.isNullOrBlank()

    override suspend fun generateJson(req: JsonRequest): LlmResult {
        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { add(buildJsonObject { put("text", req.systemPrompt) }) }
            }
            putJsonArray("contents") { add(content("user", listOf(buildJsonObject { put("text", req.userPrompt) }))) }
            putJsonObject("generationConfig") { put("responseMimeType", "application/json") }
        }
        return when (val r = post(body)) {
            is Http.Error -> LlmResult.Failure(r.reason)

            is Http.Ok -> {
                val text = r.parts.firstNotNullOfOrNull { it["text"]?.jsonPrimitive?.contentOrNull }
                if (text == null) LlmResult.Failure("빈 응답") else LlmResult.Success(text, model, r.tokens)
            }
        }
    }

    override suspend fun chat(req: ChatRequest): ChatResult {
        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { add(buildJsonObject { put("text", req.systemPrompt) }) }
            }
            put("contents", JsonArray(req.messages.map(::toContent)))
            putJsonArray("tools") {
                add(
                    buildJsonObject {
                        put(
                            "functionDeclarations",
                            buildJsonArray {
                                req.tools.forEach { t ->
                                    add(
                                        buildJsonObject {
                                            put("name", t.name)
                                            put("description", t.description)
                                            put("parameters", t.parametersSchema)
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            }
        }
        return when (val r = post(body)) {
            is Http.Error -> ChatResult.Failure(r.reason)

            is Http.Ok -> {
                val calls = r.parts.mapNotNull { it["functionCall"]?.jsonObject }.mapIndexed { i, fc ->
                    ToolCall(
                        id = "call-$i",
                        name = fc["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        args = fc["args"]?.jsonObject ?: JsonObject(emptyMap()),
                    )
                }
                val text = r.parts.firstNotNullOfOrNull { it["text"]?.jsonPrimitive?.contentOrNull }
                when {
                    calls.isNotEmpty() -> ChatResult.ToolCalls(calls, model, r.tokens)
                    text != null -> ChatResult.Final(text, model, r.tokens)
                    else -> ChatResult.Failure("빈 응답")
                }
            }
        }
    }

    private fun content(role: String, parts: List<JsonObject>) = buildJsonObject {
        put("role", role)
        put("parts", JsonArray(parts))
    }

    private fun toContent(m: ChatMessage): JsonObject = when (m) {
        is ChatMessage.User -> content("user", listOf(buildJsonObject { put("text", m.text) }))

        is ChatMessage.Model -> content(
            "model",
            listOfNotNull(m.text?.let { t -> buildJsonObject { put("text", t) } }) + m.toolCalls.map { c ->
                buildJsonObject {
                    putJsonObject("functionCall") {
                        put("name", c.name)
                        put("args", c.args)
                    }
                }
            },
        )

        is ChatMessage.ToolResult -> content(
            "user",
            listOf(
                buildJsonObject {
                    putJsonObject("functionResponse") {
                        put("name", m.name)
                        putJsonObject("response") { put("result", AiJson.parseToJsonElement(m.json)) }
                    }
                },
            ),
        )
    }

    private sealed interface Http {
        data class Ok(val parts: List<JsonObject>, val tokens: Int?) : Http

        data class Error(val reason: String) : Http
    }

    private suspend fun post(body: JsonObject): Http = withContext(Dispatchers.IO) {
        val key = apiKey ?: return@withContext Http.Error("GEMINI_API_KEY 없음")
        val request = HttpRequest.newBuilder(URI.create("$BASE/$model:generateContent"))
            .timeout(Duration.ofSeconds(TIMEOUT_SEC))
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", key)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()
        runCatching { http.send(request, HttpResponse.BodyHandlers.ofString()) }.fold(
            onSuccess = { res ->
                if (res.statusCode() !in HTTP_OK) {
                    Http.Error("HTTP ${res.statusCode()}")
                } else {
                    val root = AiJson.parseToJsonElement(res.body()).jsonObject
                    val parts = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("content")?.jsonObject?.get("parts")?.jsonArray?.map { it.jsonObject }.orEmpty()
                    val tokens = root["usageMetadata"]?.jsonObject?.get("totalTokenCount")?.jsonPrimitive?.intOrNull
                    Http.Ok(parts, tokens)
                }
            },
            onFailure = { Http.Error(it::class.simpleName ?: "network") },
        )
    }

    /** 상수. */
    companion object {
        /** 기본 모델(ONE_SHOT 0절 CLOUD_MODEL). */
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val CONNECT_SEC = 10L
        private const val TIMEOUT_SEC = 20L
        private val HTTP_OK = 200..299

        /** 환경변수 키로 만들기(없으면 [isAvailable]이 false). */
        fun fromEnv(): RestGeminiClient = RestGeminiClient(System.getenv("GEMINI_API_KEY"))
    }
}
