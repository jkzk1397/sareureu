package com.capstone.pause.core.ai.client

import android.content.Context
import com.capstone.pause.core.ai.engine.llm.ChatMessage
import com.capstone.pause.core.ai.engine.llm.ChatRequest
import com.capstone.pause.core.ai.engine.llm.ChatResult
import com.capstone.pause.core.ai.engine.llm.JsonRequest
import com.capstone.pause.core.ai.engine.llm.LlmClient
import com.capstone.pause.core.ai.engine.llm.LlmResult
import com.capstone.pause.core.ai.engine.llm.Provider
import com.capstone.pause.core.ai.engine.llm.ToolCall
import com.capstone.pause.core.common.AppLog
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Part
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/** 클라우드 모델 설정. Remote Config는 아직 쓰지 않는다(BUILD_REPORT 가정). */
object AiConfig {
    /** 기본 모델(안정판, 무료 Spark 가능). */
    const val MODEL = "gemini-3.5-flash-lite"
}

/**
 * Firebase AI Logic(Gemini Developer API) 클라이언트. App Check 제한 토큰을 쓴다.
 * `google-services.json`이 없어 FirebaseApp이 없으면 [isAvailable] = false이고 라우터가 모의로 바꾼다(절대 크래시하지 않음).
 * Gemini 3.x에는 temperature·topK·topP를 넣지 않는다. 함수 호출은 앱이 직접 실행하는 수동 루프(자동 실행 없음).
 */
@Singleton
class CloudLlmClient @Inject constructor(@ApplicationContext private val context: Context, private val log: AppLog) :
    LlmClient {
    override val provider: Provider = Provider.CLOUD
    override val model: String = AiConfig.MODEL

    override val isAvailable: Boolean
        get() = firebaseReady()

    private val json = Json { ignoreUnknownKeys = true }

    private fun firebaseReady(): Boolean = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    private val appCheckOnce: Boolean by lazy { AppCheckSetup.install(context, log) }

    private fun newModel(system: String, tools: List<Tool>?, jsonOutput: Boolean): GenerativeModel {
        appCheckOnce
        return Firebase.ai(backend = GenerativeBackend.googleAI(), useLimitedUseAppCheckTokens = true)
            .generativeModel(
                modelName = model,
                generationConfig = generationConfig { if (jsonOutput) responseMimeType = JSON_MIME },
                tools = tools,
                systemInstruction = content { text(system) },
            )
    }

    override suspend fun generateJson(req: JsonRequest): LlmResult {
        if (!isAvailable) return LlmResult.Failure("firebase_not_configured")
        return runCatching {
            val response = newModel(req.systemPrompt, tools = null, jsonOutput = true)
                .generateContent(content("user") { text(req.userPrompt) })
            val text = response.text
            if (text.isNullOrBlank()) {
                LlmResult.Failure("empty_response")
            } else {
                LlmResult.Success(text, model, response.usageMetadata?.totalTokenCount)
            }
        }.getOrElse { e ->
            log.w(TAG, "cloud generate failed: ${e.javaClass.simpleName}")
            LlmResult.Failure(e.javaClass.simpleName, e)
        }
    }

    override suspend fun chat(req: ChatRequest): ChatResult {
        if (!isAvailable) return ChatResult.Failure("firebase_not_configured")
        return runCatching {
            val tools = listOf(Tool.functionDeclarations(req.tools.map(ToolSchemas::toFirebase)))
            val response = newModel(req.systemPrompt, tools, jsonOutput = false)
                .generateContent(req.messages.map(::toContent))
            val tokens = response.usageMetadata?.totalTokenCount
            val calls = response.functionCalls
            when {
                calls.isNotEmpty() -> ChatResult.ToolCalls(
                    calls.mapIndexed { i, c -> ToolCall(c.id ?: "${c.name}_$i", c.name, JsonObject(c.args)) },
                    model,
                    tokens,
                )

                !response.text.isNullOrBlank() -> ChatResult.Final(response.text.orEmpty(), model, tokens)

                else -> ChatResult.Failure("empty_response")
            }
        }.getOrElse { e ->
            log.w(TAG, "cloud chat failed: ${e.javaClass.simpleName}")
            ChatResult.Failure(e.javaClass.simpleName, e)
        }
    }

    private fun toContent(m: ChatMessage): Content = when (m) {
        is ChatMessage.User -> content("user") { text(m.text) }

        is ChatMessage.Model -> {
            val parts = mutableListOf<Part>()
            m.text?.takeIf { it.isNotBlank() }?.let { parts += TextPart(it) }
            m.toolCalls.forEach { parts += FunctionCallPart(it.name, it.args, it.id) }
            Content("model", parts)
        }

        is ChatMessage.ToolResult -> {
            val body = runCatching { json.parseToJsonElement(m.json).jsonObject }
                .getOrElse { buildJsonObject { put("result", json.parseToJsonElement("\"${m.json}\"")) } }
            Content("function", listOf(FunctionResponsePart(m.name, body, m.callId)))
        }
    }

    private companion object {
        const val TAG = "CloudLlmClient"
        const val JSON_MIME = "application/json"
    }
}
