package com.capstone.pause.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 문구 팩(05 문서 5.2절). JSON 파일과 AI 출력이 같은 타입이다. 숫자는 자리표시자로만. */
@Serializable
data class CopyPack(
    val version: String,
    val card: Map<ContextSlot, CardCopy>,
    val chipReply: Map<String, List<CopyLine>>,
    val buddyMessage: List<CopyLine>,
    val praise: List<CopyLine>,
    val predictive: Map<ContextSlot, CopyLine>,
)

/** 카드 단계별 문구. JSON 키는 `L1`·`L2`·`L3`·`L4Question`(Kotlin 이름 규칙 때문에 소문자로 시작). */
@Serializable
data class CardCopy(
    @SerialName("L1") val l1: List<CopyLine>,
    @SerialName("L2") val l2: List<CopyLine>,
    @SerialName("L3") val l3: List<CopyLine>,
    @SerialName("L4Question") val l4Question: List<CopyLine>,
)

/** 문구 한 줄. [tones]에 맞는 말투일 때만 고른다. */
@Serializable
data class CopyLine(val id: String, val text: String, val tones: Set<Tone>)

/** 대처 전략(05 문서 6절). */
@Serializable
data class Strategy(val id: String, val context: ContextSlot?, val ifText: String, val thenText: String)
