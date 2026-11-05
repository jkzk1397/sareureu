package com.capstone.pause.core.model

import java.time.Instant

/** 카드에 대한 사용자의 응답. */
enum class NudgeResponse { STOP, EXTEND, SNOOZE_TODAY, DISMISSED, IGNORED }

/** 개입 기록. */
data class NudgeEvent(
    val sessionId: Long,
    val at: Instant,
    val level: Int,
    val copyId: String?,
    val response: NudgeResponse,
    val reasonChip: String?,
)

/** 메시지를 보낸 쪽. */
sealed interface Sender {
    /** 르르. */
    data object Buddy : Sender

    /** 친구가 맡겨 둔 잔소리. */
    data class Friend(val friendId: String, val nickname: String) : Sender

    /** 나. */
    data object Me : Sender
}

/** 르르 메시지(메신저형 알림·코치 스레드). */
data class BuddyMessage(
    val id: Long,
    val at: Instant,
    val sender: Sender,
    val text: String,
    val copyId: String?,
    val sessionId: Long?,
)
