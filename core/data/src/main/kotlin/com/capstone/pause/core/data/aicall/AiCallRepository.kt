package com.capstone.pause.core.data.aicall

import com.capstone.pause.core.database.dao.AiCallDao
import com.capstone.pause.core.database.entity.AiCallEntity
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** AI 감사 로그 한 줄(Room `ai_calls`). 입력은 해시와 길이만, 출력은 검증 통과본만. */
data class AiCallRow(
    val id: Long = 0,
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
    val reported: Boolean = false,
)

/** AI 감사 로그 저장소. `core:ai`의 `AiCallLogger`가 쓴다. */
interface AiCallRepository {
    /** 저장하고 ID를 돌려준다. */
    suspend fun insert(row: AiCallRow): Long

    /** 최근 기록. */
    fun observeRecent(limit: Int): Flow<List<AiCallRow>>

    /** 신고 표시. */
    suspend fun markReported(id: Long)
}

@Singleton
internal class RoomAiCallRepository @Inject constructor(private val dao: AiCallDao) : AiCallRepository {
    override suspend fun insert(row: AiCallRow): Long = dao.insert(
        AiCallEntity(
            at = row.at,
            feature = row.feature,
            promptId = row.promptId,
            promptVersion = row.promptVersion,
            provider = row.provider,
            model = row.model,
            inputHash = row.inputHash,
            inputLength = row.inputLength,
            outputJson = row.outputJson,
            validation = row.validation,
            reason = row.reason,
            latencyMs = row.latencyMs,
            tokens = row.tokens,
            reported = row.reported,
        ),
    )

    override fun observeRecent(limit: Int): Flow<List<AiCallRow>> = dao.observeRecent(limit).map { rows ->
        rows.map {
            AiCallRow(
                id = it.id,
                at = it.at,
                feature = it.feature,
                promptId = it.promptId,
                promptVersion = it.promptVersion,
                provider = it.provider,
                model = it.model,
                inputHash = it.inputHash,
                inputLength = it.inputLength,
                outputJson = it.outputJson,
                validation = it.validation,
                reason = it.reason,
                latencyMs = it.latencyMs,
                tokens = it.tokens,
                reported = it.reported,
            )
        }
    }

    override suspend fun markReported(id: Long) = dao.markReported(id)
}
