package com.capstone.pause.core.ai

import com.capstone.pause.core.ai.engine.llm.AiFeature
import com.capstone.pause.core.ai.engine.llm.LlmClient
import com.capstone.pause.core.ai.engine.run.AiCallRecord
import kotlinx.coroutines.flow.Flow

/**
 * 기능마다 쓸 [LlmClient]를 고른다(설정의 AI 방식 + 기기·Firebase 가용성).
 * 클라우드·기기 안 클라이언트를 쓸 수 없으면 모의 클라이언트를 돌려준다. 앱이 화면에 있을 때만 부른다.
 */
interface AiRouter {
    /** [feature]에 쓸 클라이언트. */
    suspend fun clientFor(feature: AiFeature): LlmClient

    /** 지금 실제(클라우드·기기 안) AI를 쓸 수 있는지. 화면의 "모의 AI" 표시에 쓴다. */
    val usingRealAi: Flow<Boolean>
}

/** 저장된 AI 호출 한 건(설정 › AI가 한 일). */
data class AiCallEntry(val id: Long, val record: AiCallRecord, val reported: Boolean)

/** AI 감사 로그(Room `ai_calls`, 30일). 모든 호출을 남긴다. */
interface AiCallLogger {
    /** 기록하고 ID를 돌려준다. */
    suspend fun log(record: AiCallRecord): Long

    /** 최근 호출. */
    fun observeRecent(limit: Int): Flow<List<AiCallEntry>>

    /** AI 문장 '신고'(Play AI 생성 콘텐츠 정책). 그 호출을 표시하고 그 문장을 다시 쓰지 않는다. */
    suspend fun report(id: Long)
}
