package com.capstone.pause.core.ai.engine.llm

import com.capstone.pause.core.ai.engine.schema.AiInput
import kotlinx.serialization.json.JsonObject

/** 제공자 종류. 감사 로그에 이름으로 남는다. */
enum class Provider { CLOUD, ON_DEVICE, MOCK, REST_EVAL }

/** AI 기능. 프롬프트 ID와 같다(05 문서 5절). */
enum class AiFeature(val promptId: String) {
    WEEKLY_REPORT("weekly_report"),
    COPY_PACK("copy_pack"),
    CONTEXT_CHECK("context_check"),
    COACH_CHAT("coach_chat"),
    POKE_DRAFT("poke_draft"),
}

/**
 * JSON 한 번 생성 요청. 실제 모델은 [systemPrompt]·[userPrompt]를 읽고,
 * 모의 제공자는 구조화된 [input]만 읽는다(같은 입력이면 같은 출력).
 */
data class JsonRequest(
    val feature: AiFeature,
    val systemPrompt: String,
    val userPrompt: String,
    /** 출력 스키마 이름(프롬프트 머리말의 `output_schema`). */
    val outputSchema: String,
    val input: AiInput,
    /** 모의·평가용으로만 쓴다(Gemini 3.x에는 보내지 않는다). */
    val temperature: Double? = null,
)

/** 생성 결과. 성공이면 모델이 낸 JSON 문자열 그대로(검증 전). */
sealed interface LlmResult {
    /** 성공. */
    data class Success(val text: String, val model: String?, val tokens: Int? = null) : LlmResult

    /** 실패(네트워크·차단·형식 오류 등). [reason]은 감사 로그용 짧은 설명. */
    data class Failure(val reason: String, val cause: Throwable? = null) : LlmResult
}

/** 함수(도구) 선언. [parametersSchema]는 OpenAPI 스타일 JSON 스키마. */
data class ToolDeclaration(val name: String, val description: String, val parametersSchema: JsonObject)

/** 모델이 요청한 도구 호출. */
data class ToolCall(val id: String, val name: String, val args: JsonObject)

/** 대화 메시지. */
sealed interface ChatMessage {
    /** 사용자 말(이미 [com.capstone.pause.core.ai.engine.safety.SafetyFilter]를 통과·마스킹한 것). */
    data class User(val text: String) : ChatMessage

    /** 모델 말. 도구를 불렀으면 [toolCalls]. */
    data class Model(val text: String?, val toolCalls: List<ToolCall> = emptyList()) : ChatMessage

    /** 앱이 실행한 도구 결과(JSON). */
    data class ToolResult(val callId: String, val name: String, val json: String) : ChatMessage
}

/** 채팅 한 번 요청. 도구 호출 루프는 [com.capstone.pause.core.ai.engine.coach.CoachEngine]이 돈다. */
data class ChatRequest(val systemPrompt: String, val messages: List<ChatMessage>, val tools: List<ToolDeclaration>)

/** 채팅 결과. */
sealed interface ChatResult {
    /** 모델이 도구를 부르기로 함. */
    data class ToolCalls(val calls: List<ToolCall>, val model: String?, val tokens: Int? = null) : ChatResult

    /** 최종 답(JSON 문자열, 검증 전). */
    data class Final(val text: String, val model: String?, val tokens: Int? = null) : ChatResult

    /** 실패. */
    data class Failure(val reason: String, val cause: Throwable? = null) : ChatResult
}

/** LLM 호출 창구. 앱 코드는 이 인터페이스로만 부른다(AGENTS.md 7절). */
interface LlmClient {
    /** 제공자. */
    val provider: Provider

    /** 모델 ID(모의면 null). */
    val model: String?

    /** 지금 쓸 수 있는지(설정 파일·지원 기기·키 등). */
    val isAvailable: Boolean

    /** JSON 한 번 생성. */
    suspend fun generateJson(req: JsonRequest): LlmResult

    /** 채팅 한 단계(도구 호출 또는 최종 답). */
    suspend fun chat(req: ChatRequest): ChatResult
}
