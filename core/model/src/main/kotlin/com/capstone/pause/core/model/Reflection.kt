package com.capstone.pause.core.model

import java.time.Instant

/** 만약~하면 계획. 사용자가 버튼을 눌러야 저장된다. */
data class IfThenPlan(
    val id: Long,
    val context: ContextSlot?,
    val ifText: String,
    val thenText: String,
    val createdAt: Instant,
    val active: Boolean,
)

/** AI가 쓴 추측. 근거는 관찰 ID나 세션을 가리킨다. */
data class Hypothesis(
    val id: String,
    val text: String,
    val confidence: Confidence,
    val evidence: List<EvidenceRef>,
    val userVerdict: Verdict?,
)

/** 근거 참조. */
sealed interface EvidenceRef {
    /** 세션 하나. */
    data class Session(val id: Long) : EvidenceRef

    /** 통계 키(관찰 ID 등). */
    data class Stat(val key: String) : EvidenceRef
}

/** 맞아 / 반쯤 / 아니야. */
enum class Verdict { YES, PARTLY, NO }
