package com.capstone.pause.core.ai.client

import com.capstone.pause.core.ai.engine.llm.ChatRequest
import com.capstone.pause.core.ai.engine.llm.ChatResult
import com.capstone.pause.core.ai.engine.llm.JsonRequest
import com.capstone.pause.core.ai.engine.llm.LlmClient
import com.capstone.pause.core.ai.engine.llm.LlmResult
import com.capstone.pause.core.ai.engine.llm.Provider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 기기 안 모델(ML Kit GenAI Prompt API, 베타) 자리. 지금은 스텁이라 [isAvailable] = false → 라우터가 모의로 바꾼다.
 * TODO(BUILD_REPORT#12): `genai-prompt` 1.0.0-beta5를 붙이면 `checkStatus()` → `download()` 뒤 짧은 작업(context_check)에만 쓴다.
 * 그 API는 앱이 맨 앞에 있을 때만 돌고(백그라운드면 BACKGROUND_USE_BLOCKED) 한국어 지원이 문서에 없다.
 */
@Singleton
class OnDeviceLlmClient @Inject constructor() : LlmClient {
    override val provider: Provider = Provider.ON_DEVICE
    override val model: String? = null
    override val isAvailable: Boolean = false

    override suspend fun generateJson(req: JsonRequest): LlmResult = LlmResult.Failure("on_device_unavailable")

    override suspend fun chat(req: ChatRequest): ChatResult = ChatResult.Failure("on_device_unavailable")
}
