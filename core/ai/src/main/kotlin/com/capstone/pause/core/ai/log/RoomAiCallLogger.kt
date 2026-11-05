package com.capstone.pause.core.ai.log

import com.capstone.pause.core.ai.AiCallEntry
import com.capstone.pause.core.ai.AiCallLogger
import com.capstone.pause.core.ai.engine.llm.AiFeature
import com.capstone.pause.core.ai.engine.llm.Provider
import com.capstone.pause.core.ai.engine.run.AiCallRecord
import com.capstone.pause.core.ai.engine.run.CallOutcome
import com.capstone.pause.core.data.aicall.AiCallRepository
import com.capstone.pause.core.data.aicall.AiCallRow
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [AiCallRecord] ↔ 저장 행 변환(순수). */
object AiCallMapping {
    /** 기록 → 행. */
    fun toRow(record: AiCallRecord): AiCallRow = AiCallRow(
        at = record.at,
        feature = record.feature.name,
        promptId = record.promptId,
        promptVersion = record.promptVersion,
        provider = record.provider.name,
        model = record.model,
        inputHash = record.inputHash,
        inputLength = record.inputLength,
        outputJson = record.outputJson,
        validation = record.outcome.name,
        reason = record.reason,
        latencyMs = record.latencyMs,
        tokens = record.tokens,
    )

    /** 행 → 항목. 이름을 모르는 값(옛 기록)은 안전한 기본값으로. */
    fun toEntry(row: AiCallRow): AiCallEntry = AiCallEntry(
        id = row.id,
        record = AiCallRecord(
            at = row.at,
            feature = AiFeature.entries.firstOrNull { it.name == row.feature } ?: AiFeature.COACH_CHAT,
            promptId = row.promptId,
            promptVersion = row.promptVersion,
            provider = Provider.entries.firstOrNull { it.name == row.provider } ?: Provider.MOCK,
            model = row.model,
            inputHash = row.inputHash,
            inputLength = row.inputLength,
            outputJson = row.outputJson,
            outcome = CallOutcome.entries.firstOrNull { it.name == row.validation } ?: CallOutcome.SKIPPED,
            reason = row.reason,
            latencyMs = row.latencyMs,
            tokens = row.tokens,
        ),
        reported = row.reported,
    )
}

/** Room `ai_calls`에 남기는 감사 로그. */
@Singleton
internal class RoomAiCallLogger @Inject constructor(private val repository: AiCallRepository) : AiCallLogger {
    override suspend fun log(record: AiCallRecord): Long = repository.insert(AiCallMapping.toRow(record))

    override fun observeRecent(limit: Int): Flow<List<AiCallEntry>> =
        repository.observeRecent(limit).map { rows -> rows.map(AiCallMapping::toEntry) }

    override suspend fun report(id: Long) = repository.markReported(id)
}
