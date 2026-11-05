package com.capstone.pause.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.capstone.pause.core.model.Confidence
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.NudgeResponse
import com.capstone.pause.core.model.Verdict
import java.time.Instant

/** 개입 기록. */
@Entity(tableName = "nudge_events", indices = [Index("sessionId"), Index("at")])
data class NudgeEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val at: Instant,
    val level: Int,
    val copyId: String?,
    val response: NudgeResponse,
    val reasonChip: String?,
    val source: DataSource,
)

/** 르르 메시지·채팅. [senderType] = BUDDY / FRIEND / ME. */
@Entity(tableName = "messages", indices = [Index("at")])
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Instant,
    val senderType: String,
    val friendId: String?,
    val friendNickname: String?,
    val text: String,
    val copyId: String?,
    val sessionId: Long?,
    val source: DataSource,
)

/** 만약~하면 계획. */
@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val context: ContextSlot?,
    val ifText: String,
    val thenText: String,
    val createdAt: Instant,
    val active: Boolean,
    val source: DataSource,
)

/** AI 가설과 사용자 판정. 근거는 JSON 문자열. */
@Entity(tableName = "hypotheses", indices = [Index("weekStart")])
data class HypothesisEntity(
    @PrimaryKey val id: String,
    val weekStart: String,
    val text: String,
    val confidence: Confidence,
    val evidenceJson: String,
    val verdict: Verdict?,
    val source: DataSource,
)

/** 주간 리포트(검증 통과본 JSON). [weekStart]는 ISO 날짜. */
@Entity(tableName = "weekly_reports")
data class WeeklyReportEntity(
    @PrimaryKey val weekStart: String,
    val json: String,
    val createdAt: Instant,
    val source: DataSource,
)

/** 미리 만든 문구 팩(JSON). 활성은 하나. */
@Entity(tableName = "copy_pack")
data class CopyPackEntity(
    @PrimaryKey val version: String,
    val json: String,
    val createdAt: Instant,
    val active: Boolean,
)

/** AI 감사 로그(30일). 입력은 해시와 길이만. */
@Entity(tableName = "ai_calls", indices = [Index("at")])
data class AiCallEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Instant,
    val feature: String,
    val promptId: String,
    val promptVersion: Int,
    val provider: String,
    val model: String?,
    val inputHash: String,
    val inputLength: Int,
    val outputJson: String?,
    val validation: String,
    val reason: String?,
    val latencyMs: Long,
    val tokens: Int?,
    val reported: Boolean,
)

/** 친구 캐시(감소율·기록률만). */
@Entity(tableName = "friends_cache")
data class FriendCacheEntity(
    @PrimaryKey val friendId: String,
    val nickname: String,
    val reductionRate: Double?,
    val coverage: Double?,
    val source: DataSource,
)

/** 받은 잔소리(친구가 미리 맡겨 둔 메시지). */
@Entity(tableName = "pokes_inbox", indices = [Index("createdAt")])
data class PokeInboxEntity(
    @PrimaryKey val id: String,
    val fromId: String,
    val fromNickname: String,
    val text: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val used: Boolean,
    val source: DataSource,
)
