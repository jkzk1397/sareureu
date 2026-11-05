package com.capstone.pause.core.ai.engine.run

import com.capstone.pause.core.ai.engine.llm.AiFeature
import com.capstone.pause.core.ai.engine.llm.Provider
import java.security.MessageDigest
import java.time.Instant

/** 검증 결과 종류(감사 로그). */
enum class CallOutcome {
    /** 첫 시도에 통과. */
    PASSED,

    /** 실패 이유를 붙여 다시 불러 통과. */
    RETRIED_PASSED,

    /** 두 번 다 실패해서 모의 결과로 대신. */
    MOCK_FALLBACK,

    /** 위기 표현 등으로 AI를 부르지 않음. */
    SAFETY_BLOCKED,

    /** 일일 상한·기록 부족 등으로 부르지 않음. */
    SKIPPED,
}

/**
 * AI 호출 한 번의 감사 기록(05 문서 10절). 입력은 해시와 길이만 남기고, 출력은 검증 통과본만.
 * `core:ai`가 Room `ai_calls`에 그대로 옮긴다.
 */
data class AiCallRecord(
    val at: Instant,
    val feature: AiFeature,
    val promptId: String,
    val promptVersion: Int,
    val provider: Provider,
    val model: String?,
    val inputHash: String,
    val inputLength: Int,
    val outputJson: String?,
    val outcome: CallOutcome,
    /** 재시도·대체 이유(개인정보 없음). */
    val reason: String?,
    val latencyMs: Long,
    val tokens: Int?,
)

/** 입력 해시(SHA-256 앞 16자리). 원문은 남기지 않는다. */
object InputHash {
    /** 해시. */
    fun of(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .take(HASH_BYTES)
        .joinToString("") { "%02x".format(it) }

    private const val HASH_BYTES = 8
}

/** 사용량 상한(05 문서 2절): 하루 채팅 30턴, 리포트 재생성 하루 3회. 숫자는 호출하는 쪽이 센다. */
data class AiQuota(val chatTurnsPerDay: Int = CHAT_TURNS, val reportRegenerationsPerDay: Int = REPORT_REGENERATIONS) {
    /** 채팅을 더 할 수 있는지. */
    fun canChat(turnsToday: Int): Boolean = turnsToday < chatTurnsPerDay

    /** 리포트를 다시 만들 수 있는지. */
    fun canRegenerateReport(regenerationsToday: Int): Boolean = regenerationsToday < reportRegenerationsPerDay

    /** 기본값. */
    companion object {
        /** 하루 채팅 턴. */
        const val CHAT_TURNS = 30

        /** 하루 리포트 재생성. */
        const val REPORT_REGENERATIONS = 3
    }
}
